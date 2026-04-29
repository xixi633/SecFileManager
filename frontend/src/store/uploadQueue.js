import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import JSZip from 'jszip';
import {
  cancelChunkSession,
  fetchChunkSessionStatus,
  mergeChunks,
  uploadChunk,
  uploadFile
} from '../api/file.js';
import {
  uploadingFile,
  uploadPercentage,
  uploadStatus,
  uploadSpeed,
  uploadProcessing,
  uploadStartTime
} from './uploadState.js';
import { addUploadFailureNotification } from './messageCenter.js';

const LARGE_FILE_THRESHOLD = 32 * 1024 * 1024;
const LARGE_FILE_CHUNK_SIZE = 16 * 1024 * 1024;
const MAX_CHUNK_UPLOAD_CONCURRENCY = 8;
const DEFAULT_CHUNK_UPLOAD_CONCURRENCY = 6;
const LOW_CORE_CHUNK_UPLOAD_CONCURRENCY = 4;
const MEDIUM_NETWORK_CHUNK_UPLOAD_CONCURRENCY = 4;
const SLOW_NETWORK_CHUNK_UPLOAD_CONCURRENCY = 2;
const PROGRESS_UPDATE_INTERVAL_MS = 80;

export const uploadTasks = ref([]);
export const uploadQueueRunning = ref(false);
export const currentTaskId = ref('');

let uploadTaskCounter = 0;

const TASK_STATUS_LABEL = {
  queued: '排队中',
  compressing: '压缩中',
  uploading: '上传中',
  finalizing: '处理中',
  success: '已完成',
  failed: '失败',
  canceled: '已取消'
};

const TASK_STATUS_TAG_TYPE = {
  queued: 'info',
  compressing: 'warning',
  uploading: 'primary',
  finalizing: 'warning',
  success: 'success',
  failed: 'danger',
  canceled: 'info'
};

const formatBytes = (bytes) => {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i];
};

export const getTaskStatusText = (status) => TASK_STATUS_LABEL[status] || status;
export const getTaskStatusType = (status) => TASK_STATUS_TAG_TYPE[status] || 'info';

export const canCancelTask = (task) => ['queued', 'compressing', 'uploading', 'finalizing'].includes(task?.status);
export const canRetryTask = (task) => ['failed', 'canceled'].includes(task?.status);
export const canRemoveTask = (task) => !['compressing', 'uploading', 'finalizing'].includes(task?.status);

const createTaskId = () => {
  uploadTaskCounter += 1;
  return `upload_${Date.now()}_${uploadTaskCounter}`;
};

const createUploadTask = ({
  kind,
  name,
  file,
  folderFiles,
  folderName,
  description = '',
  onUploaded,
  successMessage
}) => ({
  id: createTaskId(),
  kind,
  name,
  file,
  folderFiles,
  folderName,
  description,
  onUploaded,
  successMessage,
  progress: 0,
  status: 'queued',
  statusText: '等待上传',
  speed: '',
  errorMessage: '',
  abortController: null,
  identifier: '',
  startedAt: 0,
  finishedAt: 0
});

export const createFileUploadTask = (file, description = '', options = {}) => createUploadTask({
  kind: 'file',
  name: options.name || file?.name || '未命名文件',
  file,
  description,
  onUploaded: options.onUploaded,
  successMessage: options.successMessage
});

export const createFolderUploadTask = (folderFiles, folderName, description = '', options = {}) => createUploadTask({
  kind: 'folder',
  name: options.name || `${folderName || 'folder'}.zip`,
  folderFiles,
  folderName,
  description,
  onUploaded: options.onUploaded,
  successMessage: options.successMessage
});

const getTaskById = (taskId) => uploadTasks.value.find((task) => task.id === taskId);

const resetCurrentUploadDisplay = () => {
  uploadingFile.value = false;
  uploadPercentage.value = 0;
  uploadStatus.value = '';
  uploadSpeed.value = '';
  uploadProcessing.value = false;
};

const syncCurrentUploadDisplay = (task) => {
  if (!task) {
    resetCurrentUploadDisplay();
    return;
  }

  uploadingFile.value = ['compressing', 'uploading', 'finalizing'].includes(task.status);
  uploadProcessing.value = task.status === 'finalizing';
  uploadPercentage.value = task.progress;
  uploadStatus.value = `${task.name} - ${task.statusText || getTaskStatusText(task.status)}`;
  uploadSpeed.value = task.speed || '';
};

const setTaskStatus = (task, status, statusText = '') => {
  task.status = status;
  if (statusText) {
    task.statusText = statusText;
  }
  if (task.id === currentTaskId.value) {
    syncCurrentUploadDisplay(task);
  }
};

const updateTaskSpeed = (task, loadedBytes) => {
  if (!task.startedAt) return;
  const elapsedSeconds = (Date.now() - task.startedAt) / 1000;
  if (elapsedSeconds <= 0) return;
  task.speed = `上传速度: ${formatBytes(loadedBytes / elapsedSeconds)}/s`;
};

const updateTaskProgressByBytes = (task, loaded, total, statusPrefix = '上传中') => {
  if (!total || Number.isNaN(total)) {
    task.progress = Math.min(99, Math.max(task.progress, 1));
    task.statusText = `${statusPrefix}: ${formatBytes(loaded)} / ?`;
  } else {
    task.progress = Math.min(99, Math.round((loaded * 100) / total));
    task.statusText = `${statusPrefix}: ${formatBytes(loaded)} / ${formatBytes(total)}`;
  }
  updateTaskSpeed(task, loaded);
  if (task.id === currentTaskId.value) {
    syncCurrentUploadDisplay(task);
  }
};

const updateTaskProgressByPercent = (task, percent, statusText = '') => {
  task.progress = Math.max(0, Math.min(99, Math.round(percent)));
  if (statusText) {
    task.statusText = statusText;
  }
  if (task.id === currentTaskId.value) {
    syncCurrentUploadDisplay(task);
  }
};

const isCanceledError = (error) => error?.name === 'CanceledError'
  || error?.code === 'ERR_CANCELED'
  || error?.message === 'UPLOAD_CANCELED';

const createUploadIdentifier = (file) => `${file.name}__${file.size}__${file.lastModified}`;

const resolveChunkUploadConcurrency = (totalChunks) => {
  if (totalChunks <= 1) return 1;

  const connectionType = typeof navigator !== 'undefined'
    ? navigator.connection?.effectiveType || ''
    : '';

  let concurrency = DEFAULT_CHUNK_UPLOAD_CONCURRENCY;
  if (connectionType === 'slow-2g' || connectionType === '2g') {
    concurrency = SLOW_NETWORK_CHUNK_UPLOAD_CONCURRENCY;
  } else if (connectionType === '3g') {
    concurrency = Math.min(concurrency, MEDIUM_NETWORK_CHUNK_UPLOAD_CONCURRENCY);
  }

  const hardwareConcurrency = typeof navigator !== 'undefined'
    ? Number(navigator.hardwareConcurrency || 0)
    : 0;

  if (hardwareConcurrency >= 16) {
    concurrency = MAX_CHUNK_UPLOAD_CONCURRENCY;
  } else if (hardwareConcurrency > 0 && hardwareConcurrency <= 4) {
    concurrency = Math.min(concurrency, LOW_CORE_CHUNK_UPLOAD_CONCURRENCY);
  }

  return Math.max(1, Math.min(concurrency, MAX_CHUNK_UPLOAD_CONCURRENCY, totalChunks));
};

const runFileUploadTask = async (task) => {
  const file = task.file;
  if (!file) throw new Error('文件对象丢失，无法上传');

  if (file.size <= LARGE_FILE_THRESHOLD) {
    setTaskStatus(task, 'uploading', '上传中');
    const formData = new FormData();
    formData.append('file', file);

    const response = await uploadFile(
      formData,
      task.description,
      (progressEvent) => {
        updateTaskProgressByBytes(task, progressEvent.loaded, progressEvent.total, '上传中');
      },
      undefined,
      task.abortController?.signal
    );

    if (response?.data?.code !== 200) {
      throw new Error(response?.data?.message || '上传失败');
    }
    return response?.data?.data;
  }

  const identifier = createUploadIdentifier(file);
  task.identifier = identifier;
  const totalChunks = Math.ceil(file.size / LARGE_FILE_CHUNK_SIZE);
  const chunkUploadConcurrency = resolveChunkUploadConcurrency(totalChunks);

  let startChunkNumber = 0;
  try {
    const statusResponse = await fetchChunkSessionStatus(identifier);
    const statusData = statusResponse?.data?.data;
    if (statusData && ['UPLOADING', 'FINALIZING'].includes(statusData.stage)) {
      startChunkNumber = Number(statusData.nextChunkNumber || 0);
    }
  } catch (_) {
    startChunkNumber = 0;
  }

  const resumedBytes = Math.min(file.size, startChunkNumber * LARGE_FILE_CHUNK_SIZE);
  const chunkProgress = new Map();
  let uploadedBytes = resumedBytes;
  let lastProgressUpdateAt = 0;

  const updateChunkLoadedBytes = (chunkNumber, nextLoaded, chunkSize) => {
    const safeLoaded = Math.max(0, Math.min(nextLoaded, chunkSize));
    const previousLoaded = chunkProgress.get(chunkNumber) || 0;
    if (safeLoaded <= previousLoaded) {
      return;
    }
    chunkProgress.set(chunkNumber, safeLoaded);
    uploadedBytes += safeLoaded - previousLoaded;
  };

  const syncChunkProgress = (force = false) => {
    const now = Date.now();
    if (!force && now - lastProgressUpdateAt < PROGRESS_UPDATE_INTERVAL_MS) {
      return;
    }
    lastProgressUpdateAt = now;
    updateTaskProgressByBytes(task, uploadedBytes, file.size, '上传分片');
  };

  const uploadChunkByNumber = async (chunkNumber) => {
    if (task.status === 'canceled' || task.abortController?.signal.aborted) {
      throw new Error('UPLOAD_CANCELED');
    }

    const start = chunkNumber * LARGE_FILE_CHUNK_SIZE;
    const end = Math.min(start + LARGE_FILE_CHUNK_SIZE, file.size);
    const chunk = file.slice(start, end);

    const formData = new FormData();
    formData.append('file', chunk);
    formData.append('chunkNumber', chunkNumber);
    formData.append('chunkSize', LARGE_FILE_CHUNK_SIZE);
    formData.append('currentChunkSize', chunk.size);
    formData.append('totalSize', file.size);
    formData.append('identifier', identifier);
    formData.append('filename', file.name);
    formData.append('totalChunks', totalChunks);

    await uploadChunk(
      formData,
      (progress) => {
        updateChunkLoadedBytes(chunkNumber, progress.loaded, chunk.size);
        syncChunkProgress();
      },
      task.abortController?.signal
    );

    updateChunkLoadedBytes(chunkNumber, chunk.size, chunk.size);
    syncChunkProgress(true);
  };

  const workerCount = Math.min(chunkUploadConcurrency, totalChunks - startChunkNumber);
  let nextChunkNumber = startChunkNumber;
  const workers = [];

  setTaskStatus(task, 'uploading', `上传分片中（并发 ${chunkUploadConcurrency}）`);
  if (resumedBytes > 0) {
    updateTaskProgressByBytes(task, resumedBytes, file.size, '上传分片');
  }

  for (let i = 0; i < workerCount; i += 1) {
    workers.push((async () => {
      while (true) {
        const chunkNumber = nextChunkNumber;
        nextChunkNumber += 1;
        if (chunkNumber >= totalChunks) {
          return;
        }
        await uploadChunkByNumber(chunkNumber);
      }
    })());
  }

  await Promise.all(workers);
  syncChunkProgress(true);

  setTaskStatus(task, 'finalizing', '正在提交完成');
  const mergeResponse = await mergeChunks(identifier, file.name, totalChunks, file.size);
  if (mergeResponse?.data?.code !== 200) {
    throw new Error(mergeResponse?.data?.message || '完成上传失败');
  }
  return mergeResponse?.data?.data;
};

const runFolderUploadTask = async (task) => {
  const fileList = task.folderFiles || [];
  if (fileList.length === 0) throw new Error('文件夹为空，无法上传');

  const folderName = task.folderName || 'folder';
  setTaskStatus(task, 'compressing', `正在打包 ${fileList.length} 个文件`);

  const zip = new JSZip();
  for (const file of fileList) {
    const relativePath = file.webkitRelativePath || file.name;
    zip.file(relativePath, file);
  }

  const zipBlob = await zip.generateAsync(
    {
      type: 'blob',
      compression: 'DEFLATE',
      compressionOptions: { level: 1 }
    },
    (metadata) => {
      const packPercent = Math.round(metadata.percent || 0);
      updateTaskProgressByPercent(task, packPercent * 0.3, `压缩中 ${packPercent}%`);
    }
  );

  if (task.status === 'canceled' || task.abortController?.signal.aborted) {
    throw new Error('UPLOAD_CANCELED');
  }

  const zipFile = new File([zipBlob], `${folderName}.zip`, { type: 'application/zip' });
  if (zipFile.size === 0) {
    throw new Error('不能上传空文件夹');
  }

  setTaskStatus(task, 'uploading', '上传压缩包中');
  const formData = new FormData();
  formData.append('file', zipFile);
  formData.append('isFolder', '1');

  const response = await uploadFile(
    formData,
    task.description || `文件夹: ${folderName}`,
    (progressEvent) => {
      const total = progressEvent.total || zipFile.size;
      const loaded = progressEvent.loaded || 0;
      const uploadPercent = total > 0 ? Math.round((loaded * 70) / total) : 0;
      updateTaskProgressByPercent(task, 30 + uploadPercent, `上传压缩包: ${formatBytes(loaded)} / ${formatBytes(total)}`);
      updateTaskSpeed(task, loaded);
    },
    1,
    task.abortController?.signal
  );

  if (response?.data?.code !== 200) {
    throw new Error(response?.data?.message || '上传失败');
  }
  return response?.data?.data;
};

const executeUploadTask = async (task) => {
  currentTaskId.value = task.id;
  task.startedAt = Date.now();
  task.abortController = new AbortController();
  task.errorMessage = '';
  uploadStartTime.value = task.startedAt;

  try {
    let fileId;
    if (task.kind === 'folder') {
      fileId = await runFolderUploadTask(task);
    } else {
      fileId = await runFileUploadTask(task);
    }

    if (task.status !== 'canceled') {
      task.progress = 100;
      task.speed = '';
      setTaskStatus(task, 'success', '上传完成');

      if (typeof task.onUploaded === 'function') {
        try {
          await task.onUploaded({ fileId, task });
        } catch (callbackError) {
          ElMessage.error(`${task.name} 上传完成，但后续处理失败: ${callbackError?.message || '未知错误'}`);
        }
      }

      const message = task.successMessage === undefined ? `${task.name} 上传成功` : task.successMessage;
      if (message) {
        ElMessage.success(message);
      }
    }
  } catch (error) {
    if (isCanceledError(error) || task.status === 'canceled') {
      setTaskStatus(task, 'canceled', '已取消');
    } else {
      task.errorMessage = error?.message || '上传失败';
      task.speed = '';
      setTaskStatus(task, 'failed', task.errorMessage);
      addUploadFailureNotification({
        fileName: task.name,
        reason: task.errorMessage
      });
      ElMessage.error(`${task.name} 上传失败: ${task.errorMessage}`);
    }
  } finally {
    task.abortController = null;
    task.identifier = '';
    task.finishedAt = Date.now();
    if (currentTaskId.value === task.id) {
      syncCurrentUploadDisplay(task);
    }
  }
};

const runUploadQueue = async () => {
  if (uploadQueueRunning.value) return;
  uploadQueueRunning.value = true;

  while (true) {
    const nextTask = uploadTasks.value.find((task) => task.status === 'queued');
    if (!nextTask) break;
    await executeUploadTask(nextTask);
  }

  uploadQueueRunning.value = false;
  currentTaskId.value = '';
  resetCurrentUploadDisplay();
};

export const enqueueUploadTasks = (tasks) => {
  if (!tasks.length) return;
  uploadTasks.value.push(...tasks);
  runUploadQueue();
};

export const clearFinishedTasks = () => {
  uploadTasks.value = uploadTasks.value.filter((task) => ['queued', 'compressing', 'uploading', 'finalizing'].includes(task.status));
};

export const removeTask = (task) => {
  if (!canRemoveTask(task)) return;
  uploadTasks.value = uploadTasks.value.filter((item) => item.id !== task.id);
};

export const retryTask = (task) => {
  if (!canRetryTask(task)) return;

  const retrySource = task.kind === 'folder'
    ? createUploadTask({
        kind: 'folder',
        name: task.name,
        folderName: task.folderName,
        folderFiles: task.folderFiles,
        description: task.description,
        onUploaded: task.onUploaded,
        successMessage: task.successMessage
      })
    : createUploadTask({
        kind: 'file',
        name: task.name,
        file: task.file,
        description: task.description,
        onUploaded: task.onUploaded,
        successMessage: task.successMessage
      });

  uploadTasks.value.push(retrySource);
  runUploadQueue();
};

export const cancelTask = async (task) => {
  if (!task || !canCancelTask(task)) return;

  if (task.status === 'queued') {
    setTaskStatus(task, 'canceled', '已取消（未开始）');
    return;
  }

  setTaskStatus(task, 'canceled', '正在取消...');

  if (task.abortController) {
    task.abortController.abort();
  }

  if (task.identifier) {
    try {
      await cancelChunkSession(task.identifier);
    } catch (_) {
      // 会话已结束时无需额外处理
    }
  }
};

export const cancelCurrentTask = async () => {
  const task = getTaskById(currentTaskId.value);
  if (!task) return false;
  await cancelTask(task);
  return true;
};

export const retryCurrentTask = async () => {
  const task = getTaskById(currentTaskId.value);
  if (!task) return false;
  await cancelTask(task);
  retryTask(task);
  return true;
};

export const isInvalidFileName = (name) => {
  if (!name) return true;
  if (name.length > 255) return true;
  if (name.includes('..') || name.includes('/') || name.includes('\\')) return true;
  return false;
};

export const validateUploadFile = (file) => {
  if (!file) return false;
  if (file.size === 0) {
    ElMessage.warning('不能上传空文件');
    return false;
  }
  if (isInvalidFileName(file.name)) {
    ElMessage.warning('文件名不合法或过长（<=255），请修改后再上传');
    return false;
  }
  return true;
};
