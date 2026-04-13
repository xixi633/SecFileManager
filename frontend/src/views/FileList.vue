<template>
  <div class="file-list-container">
    <!-- Toolbar: Search & Actions -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar-content">
        <!-- Search Area -->
        <div class="search-area">
          <el-input 
            v-model="searchFileName" 
            placeholder="文件名" 
            :prefix-icon="Search"
            clearable 
            class="filter-input"
          />
          <el-input 
            v-model="searchDescription" 
            placeholder="描述" 
            :prefix-icon="Document"
            clearable 
            class="filter-input"
          />
          <el-input 
            v-model="searchKeyword" 
            placeholder="关键词" 
            :prefix-icon="Monitor"
            clearable 
            class="filter-input"
          />
          <el-tooltip content="搜索" placement="top">
            <el-button type="primary" :icon="Search" circle @click="onSearch" />
          </el-tooltip>
          <el-tooltip content="重置" placement="top">
            <el-button :icon="Refresh" circle @click="onResetSearch" />
          </el-tooltip>
        </div>

        <div class="spacer"></div>

        <!-- Upload Area -->
        <div class="upload-area">
          <el-input 
            v-model="description" 
            placeholder="上传文件描述..." 
            class="desc-input"
          />

          <input
            ref="fileInput"
            type="file"
            multiple
            style="display: none"
            @change="handleFileSelect"
          />
          <el-button type="primary" :icon="Upload" class="action-btn" @click="selectFiles">
            上传文件（最多10个）
          </el-button>
          
          <input
            ref="folderInput"
            type="file"
            webkitdirectory
            directory
            multiple
            style="display: none"
            @change="handleFolderSelect"
          />
          <el-button type="success" @click="selectFolder" :icon="FolderAdd" class="action-btn">
            上传目录
          </el-button>
        </div>
      </div>

      <!-- Upload Progress -->
      <transition name="el-zoom-in-top">
        <div v-if="uploadingFile" class="upload-progress-bar">
          <div class="progress-info">
            <span class="status-text">{{ uploadStatus }}</span>
            <span class="speed-text">{{ uploadSpeed }}</span>
            <el-button
              size="small"
              type="danger"
              text
              :disabled="!uploadingFile"
              @click="onResetUploadSession"
            >重传</el-button>
            <el-button
              size="small"
              type="warning"
              text
              :disabled="!uploadingFile"
              @click="onCancelUpload"
            >取消</el-button>
          </div>
          <el-progress 
            :percentage="uploadPercentage" 
            :status="uploadProcessing ? 'warning' : (uploadPercentage === 100 ? 'success' : '')"
            :stroke-width="6"
            :show-text="false"
            class="custom-progress"
          />
        </div>
      </transition>

      <!-- Browse Progress -->
      <transition name="el-zoom-in-top">
        <div v-if="browseLoading" class="upload-progress-bar">
          <div class="progress-info">
            <span class="status-text">{{ browseStatus }}</span>
            <span class="speed-text"></span>
          </div>
          <el-progress
            :percentage="browseIndeterminate ? 100 : browseProgress"
            :indeterminate="browseIndeterminate"
            :duration="1.4"
            :stroke-width="6"
            :show-text="false"
            class="custom-progress"
          />
        </div>
      </transition>
    </el-card>

    <transition name="el-zoom-in-top">
      <el-card v-if="uploadTasks.length > 0" class="upload-list-card" shadow="never">
        <div class="upload-list-header">
          <span>上传列表（{{ uploadTasks.length }}）</span>
          <el-button text type="primary" @click="clearFinishedTasks">清理已结束</el-button>
        </div>
        <el-table :data="uploadTasks" size="small" border max-height="260">
          <el-table-column prop="name" label="文件" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="110" align="center">
            <template #default="scope">
              <el-tag size="small" :type="getTaskStatusType(scope.row.status)">
                {{ getTaskStatusText(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="进度" min-width="180">
            <template #default="scope">
              <el-progress :percentage="scope.row.progress" :stroke-width="6" :show-text="false" />
            </template>
          </el-table-column>
          <el-table-column prop="speed" label="速度" width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="220" align="center">
            <template #default="scope">
              <el-button link type="warning" :disabled="!canCancelTask(scope.row)" @click="cancelTask(scope.row)">
                取消
              </el-button>
              <el-button link type="primary" :disabled="!canRetryTask(scope.row)" @click="retryTask(scope.row)">
                重试
              </el-button>
              <el-button link type="danger" :disabled="!canRemoveTask(scope.row)" @click="removeTask(scope.row)">
                移除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </transition>

    <el-card class="table-card" shadow="never">
      <div class="table-header">
        <transition name="el-fade-in">
          <div class="breadcrumb-bar" v-if="currentPath.length > 0">
            <el-breadcrumb :separator-icon="ArrowRight">
              <el-breadcrumb-item>
                <a @click="navigateToRoot" class="breadcrumb-link box-link">
                  <el-icon class="icon-margin"><HomeFilled /></el-icon> 根目录
                </a>
              </el-breadcrumb-item>
              <el-breadcrumb-item v-for="(item, index) in currentPath" :key="item.id">
                <a @click="navigateToFolder(index)" class="breadcrumb-link box-link">
                  <el-icon class="icon-margin"><Folder /></el-icon> {{ item.name }}
                </a>
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>
        </transition>
        <div class="table-header-actions">
          <el-button type="info" plain @click="toggleMultiSelect" class="action-btn">
            {{ multiSelectEnabled ? '退出多选' : '多选' }}
          </el-button>
          <div class="bulk-actions" v-if="multiSelectEnabled">
            <span class="bulk-count">已选 {{ selectedCount }} 项</span>
            <el-button type="primary" :disabled="!hasSelection" @click="onBulkDownload">批量下载</el-button>
            <el-button type="danger" :disabled="!hasSelection" @click="onBulkRemove">批量删除</el-button>
          </div>
        </div>
      </div>

      <div class="type-nav-bar">
        <button
          v-for="item in typeCategoryOptions"
          :key="item.value"
          type="button"
          class="type-nav-item"
          :class="{ 'is-active': searchTypeCategory === item.value }"
          @click="onTypeCategoryNavClick(item.value)"
        >
          {{ item.label }}
        </button>
      </div>

      <file-table
        :key="multiSelectEnabled ? 'multi' : 'single'"
        :files="files"
        :enable-selection="multiSelectEnabled"
        :show-type-column="searchTypeCategory === 'all'"
        @download="onDownload"
        @remove="onRemove"
        @browse="onBrowseFolder"
        @preview="onPreviewFile"
        @edit-description="onEditDescription"
        @selection-change="onSelectionChange"
      />
      
      <!-- 分页组件 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: center"
        @size-change="loadFiles"
        @current-change="loadFiles"
      />
    </el-card>

    <el-dialog v-model="editDescVisible" title="修改描述" width="500px">
      <el-input
        v-model="editDescValue"
        type="textarea"
        :rows="4"
        placeholder="请输入描述"
      />
      <template #footer>
        <el-button @click="editDescVisible = false">取消</el-button>
        <el-button type="primary" @click="onSaveDescription">保存</el-button>
      </template>
    </el-dialog>

    <!-- 文件夹浏览对话框 -->
    <el-dialog
      v-model="folderDialogVisible"
      :title="currentFolder?.originalFilename || '文件夹内容'"
      width="600px"
    >
      <el-table :data="folderFiles" stripe max-height="400">
        <el-table-column prop="name" label="文件名" />
      </el-table>
      <template #footer>
        <el-button @click="folderDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 文件预览组件 -->
    <file-preview
      v-model:visible="previewDialogVisible"
      :file="previewFile"
      @download="onDownload"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Document, HomeFilled, Folder, Search, Refresh, Upload, FolderAdd, Monitor, ArrowRight } from '@element-plus/icons-vue';
import JSZip from "jszip";
import FileTable from "../components/FileTable.vue";
import FilePreview from "../components/FilePreview.vue";
import {
  uploadingFile,
  uploadPercentage,
  uploadStatus,
  uploadSpeed,
  uploadProcessing,
  uploadStartTime
} from '../store/uploadState.js';
import {
  getDirectoryHandle,
  verifyPermission,
  writeBlobToDirectory,
  isFileSystemAccessSupported
} from '../utils/fileSystem.js';
import {
  fetchFileList,
  uploadFile,
  downloadFile,
  downloadFolder,
  deleteFile,
  updateFileDescription,
  fetchFolderEntries,
  fetchChunkSessionStatus,
  cancelChunkSession,
  uploadChunk,
  mergeChunks,
  downloadFolderEntry,
  deleteFolderEntry,
} from "../api/file.js";
import pLimit from "p-limit"; // 需要安装 p-limit
import { TYPE_CATEGORY_OPTIONS, getFileTypeCategory } from "../utils/fileType.js";


const router = useRouter();
const files = ref([]);
const description = ref("");
const fileInput = ref(null);
const folderInput = ref(null);
const folderDialogVisible = ref(false);
const currentFolder = ref(null);
const folderFiles = ref([]);
const pagination = ref({ page: 1, size: 10, total: 0 });

// 文件夹导航相关
const currentPath = ref([]); // 当前路径 [{id, name}, ...]
const currentFolderId = ref(null); // 当前文件夹ID
const currentFolderPath = ref(''); // 文件夹内路径
const folderEntries = ref([]); // 文件夹内条目列表
const previewDialogVisible = ref(false);
const previewFile = ref(null);
const browseLoading = ref(false);
const browseProgress = ref(0);
const browseStatus = ref('');
const browseIndeterminate = ref(false);
const browseStartedAt = ref(0);
const folderBrowseCache = new Map();
const searchFileName = ref('');
const searchDescription = ref('');
const searchKeyword = ref('');
const searchTypeCategory = ref('all');
const editDescVisible = ref(false);
const editDescValue = ref('');
const editingRow = ref(null);
const multiSelectEnabled = ref(false);
const selectedRows = ref([]);
const selectedCount = computed(() => selectedRows.value.length);
const hasSelection = computed(() => selectedRows.value.length > 0);
const typeCategoryOptions = TYPE_CATEGORY_OPTIONS;

const MAX_BATCH_UPLOAD_COUNT = 10;
const LARGE_FILE_CHUNK_SIZE = 32 * 1024 * 1024;
const CHUNK_UPLOAD_CONCURRENCY = 2;
const uploadTasks = ref([]);
const uploadQueueRunning = ref(false);
const currentTaskId = ref('');
let uploadTaskCounter = 0;




// 导航函数
const navigateToRoot = () => {
  currentPath.value = [];
  currentFolderId.value = null;
  currentFolderPath.value = '';
  folderEntries.value = [];
  pagination.value.page = 1;
  loadFiles();
};

const navigateToFolder = (index) => {
  // 点击某一层，截断后面的路径
  currentPath.value = currentPath.value.slice(0, index + 1);
  if (currentFolderId.value) {
    pagination.value.page = 1;
    currentFolderPath.value = currentPath.value[index].path || '';
    renderFolderEntries();
    return;
  }
  currentFolderId.value = currentPath.value[index].id;
  pagination.value.page = 1;
  
  // 重新加载该文件夹内容
  onBrowseFolder({ id: currentFolderId.value, originalFilename: currentPath.value[index].name });
};

const buildBreadcrumbForPath = (folderName, path) => {
  const crumbs = [{ id: currentFolderId.value, name: folderName, path: '' }];
  if (!path) return crumbs;
  const parts = path.split('/').filter(Boolean);
  let accum = '';
  parts.forEach(part => {
    accum += `${part}/`;
    crumbs.push({ id: currentFolderId.value, name: part, path: accum });
  });
  return crumbs;
};

const renderFolderEntries = () => {
  const prefix = currentFolderPath.value || '';
  const items = [];
  const dirSet = new Set();
  const dirItems = new Map();

  folderEntries.value.forEach(entry => {
    if (!entry.path.startsWith(prefix) || entry.path === prefix) return;
    const relative = entry.path.substring(prefix.length);
    const parts = relative.split('/').filter(Boolean);
    const isDirEntry = entry.directory || relative.endsWith('/');
     const hasChildDir = parts.length > 1;

    if (isDirEntry || hasChildDir) {
      const dirName = parts[0];
      const dirPath = prefix + dirName + '/';
      if (!dirSet.has(dirPath)) {
        dirSet.add(dirPath);
        const dirItem = {
          id: `${currentFolderId.value}_${dirPath}`,
          originalFilename: dirName,
          fileSize: 0,
          fileType: '',
          uploadTime: '',
          isFolder: 0,
          isDirectory: true,
          isFolderEntry: true,
          parentFolderId: currentFolderId.value,
          entryPath: dirPath
        };
        dirItems.set(dirPath, dirItem);
        items.push(dirItem);
      }
    } else {
      items.push({
        id: `${currentFolderId.value}_${entry.path}`,
        originalFilename: entry.name,
        fileSize: entry.size || 0,
        fileType: entry.fileType || '',
        uploadTime: '',
        description: '',
        isFolder: 0,
        isDirectory: false,
        isFolderEntry: true,
        parentFolderId: currentFolderId.value,
        entryPath: entry.path
      });
    }

    if (!isDirEntry && hasChildDir) {
      const dirName = parts[0];
      const dirPath = prefix + dirName + '/';
      const dirItem = dirItems.get(dirPath);
      if (dirItem) {
        dirItem.fileSize = (dirItem.fileSize || 0) + (entry.size || 0);
      }
    }
  });

  const filteredItems = searchTypeCategory.value === 'all'
    ? items
    : items.filter((item) => getFileTypeCategory(item) === searchTypeCategory.value);

  pagination.value.total = filteredItems.length;
  if (filteredItems.length === 0) {
    pagination.value.page = 1;
    files.value = [];
    return;
  }
  const totalPages = Math.ceil(filteredItems.length / pagination.value.size);
  if (pagination.value.page > totalPages) {
    pagination.value.page = totalPages;
  }
  const start = (pagination.value.page - 1) * pagination.value.size;
  const end = start + pagination.value.size;
  files.value = filteredItems.slice(start, end);
};

const loadFiles = async () => {
  if (currentFolderId.value) {
    renderFolderEntries();
    return;
  }
  try {
    const res = await fetchFileList(
      pagination.value.page,
      pagination.value.size,
      {
        fileName: searchFileName.value || undefined,
        description: searchDescription.value || undefined,
        keyword: searchKeyword.value || undefined,
        typeCategory: searchTypeCategory.value === 'all' ? undefined : searchTypeCategory.value
      }
    );
    const data = res?.data?.data;
    if (data && data.records) {
      files.value = data.records;
      pagination.value.total = data.total;
    } else {
      files.value = [];
      pagination.value.total = 0;
    }
  } catch (e) {
    // 错误已由拦截器提示
  }
};

const onSearch = () => {
  pagination.value.page = 1;
  loadFiles();
};

const onTypeCategoryNavClick = (value) => {
  if (searchTypeCategory.value === value) return;
  searchTypeCategory.value = value;
  pagination.value.page = 1;
  loadFiles();
};

const onResetSearch = () => {
  searchFileName.value = '';
  searchDescription.value = '';
  searchKeyword.value = '';
  searchTypeCategory.value = 'all';
  pagination.value.page = 1;
  loadFiles();
};

const formatBytes = (bytes) => {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i];
};

const startBrowseProgress = (status = '正在加载文件夹内容...') => {
  browseLoading.value = true;
  browseIndeterminate.value = true;
  browseProgress.value = 0;
  browseStartedAt.value = Date.now();
  browseStatus.value = status;
};

const stopBrowseProgress = (status = '') => {
  const elapsed = Math.max(1, Date.now() - browseStartedAt.value);
  const seconds = (elapsed / 1000).toFixed(2);
  browseIndeterminate.value = false;
  browseProgress.value = 100;
  browseStatus.value = status || `目录加载完成（${seconds}s）`;
  setTimeout(() => {
    browseLoading.value = false;
    browseProgress.value = 0;
    browseStatus.value = '';
  }, 220);
};

const isInvalidFileName = (name) => {
  if (!name) return true;
  if (name.length > 255) return true;
  if (name.includes('..') || name.includes('/') || name.includes('\\')) return true;
  return false;
};

const validateUploadFile = (file) => {
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

const getTaskStatusText = (status) => TASK_STATUS_LABEL[status] || status;
const getTaskStatusType = (status) => TASK_STATUS_TAG_TYPE[status] || 'info';

const canCancelTask = (task) => ['queued', 'compressing', 'uploading', 'finalizing'].includes(task?.status);
const canRetryTask = (task) => ['failed', 'canceled'].includes(task?.status);
const canRemoveTask = (task) => !['compressing', 'uploading', 'finalizing'].includes(task?.status);

const createTaskId = () => {
  uploadTaskCounter += 1;
  return `upload_${Date.now()}_${uploadTaskCounter}`;
};

const createUploadTask = ({ kind, name, file, folderFiles, folderName }) => ({
  id: createTaskId(),
  kind,
  name,
  file,
  folderFiles,
  folderName,
  description: description.value || '',
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

const isCanceledError = (error) => {
  return error?.name === 'CanceledError'
    || error?.code === 'ERR_CANCELED'
    || error?.message === 'UPLOAD_CANCELED';
};

const createUploadIdentifier = (file) => `${file.name}__${file.size}__${file.lastModified}`;

const enqueueUploadTasks = (tasks) => {
  if (!tasks.length) return;
  uploadTasks.value.push(...tasks);
  runUploadQueue();
};

const selectFiles = () => {
  fileInput.value?.click();
};

const handleFileSelect = (event) => {
  const selectedFiles = Array.from(event.target.files || []);
  if (selectedFiles.length === 0) return;

  if (selectedFiles.length > MAX_BATCH_UPLOAD_COUNT) {
    ElMessage.warning(`一次最多上传 ${MAX_BATCH_UPLOAD_COUNT} 个文件`);
    event.target.value = '';
    return;
  }

  const validFiles = selectedFiles.filter((file) => validateUploadFile(file));
  if (validFiles.length === 0) {
    event.target.value = '';
    return;
  }

  const tasks = validFiles.map((file) => createUploadTask({
    kind: 'file',
    name: file.name,
    file
  }));

  enqueueUploadTasks(tasks);
  ElMessage.success(`已加入上传列表: ${tasks.length} 个文件`);
  event.target.value = '';
};

const clearFinishedTasks = () => {
  uploadTasks.value = uploadTasks.value.filter((task) => ['queued', 'compressing', 'uploading', 'finalizing'].includes(task.status));
};

const removeTask = (task) => {
  if (!canRemoveTask(task)) return;
  uploadTasks.value = uploadTasks.value.filter((item) => item.id !== task.id);
};

const retryTask = (task) => {
  if (!canRetryTask(task)) return;

  const retrySource = task.kind === 'folder'
    ? createUploadTask({
        kind: 'folder',
        name: task.name,
        folderName: task.folderName,
        folderFiles: task.folderFiles
      })
    : createUploadTask({
        kind: 'file',
        name: task.name,
        file: task.file
      });

  uploadTasks.value.push(retrySource);
  runUploadQueue();
};

const cancelTask = async (task) => {
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

const onCancelUpload = async () => {
  const task = getTaskById(currentTaskId.value);
  if (!task) return;
  await cancelTask(task);
  ElMessage.success('已取消当前上传');
};

const onResetUploadSession = async () => {
  const task = getTaskById(currentTaskId.value);
  if (!task) {
    ElMessage.warning('当前没有可重传任务');
    return;
  }
  await cancelTask(task);
  retryTask(task);
  ElMessage.success('已加入重传队列');
};

const runFileUploadTask = async (task) => {
  const file = task.file;
  if (!file) throw new Error('文件对象丢失，无法上传');

  if (file.size <= LARGE_FILE_CHUNK_SIZE) {
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
    return;
  }

  const identifier = createUploadIdentifier(file);
  task.identifier = identifier;
  const totalChunks = Math.ceil(file.size / LARGE_FILE_CHUNK_SIZE);

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

  const chunkProgress = new Map();
  const limit = pLimit(CHUNK_UPLOAD_CONCURRENCY);
  const chunkTasks = [];

  setTaskStatus(task, 'uploading', `上传分片中（并发 ${CHUNK_UPLOAD_CONCURRENCY}）`);

  for (let chunkNumber = startChunkNumber; chunkNumber < totalChunks; chunkNumber += 1) {
    chunkTasks.push(limit(async () => {
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
          chunkProgress.set(chunkNumber, progress.loaded);
          let loadedBytes = 0;
          for (const loaded of chunkProgress.values()) {
            loadedBytes += loaded;
          }
          updateTaskProgressByBytes(task, loadedBytes, file.size, '上传分片');
        },
        task.abortController?.signal
      );

      chunkProgress.set(chunkNumber, chunk.size);
      let loadedBytes = 0;
      for (const loaded of chunkProgress.values()) {
        loadedBytes += loaded;
      }
      updateTaskProgressByBytes(task, loadedBytes, file.size, '上传分片');
    }));
  }

  await Promise.all(chunkTasks);

  setTaskStatus(task, 'finalizing', '正在提交完成');
  const mergeResponse = await mergeChunks(identifier, file.name, totalChunks, file.size);
  if (mergeResponse?.data?.code !== 200) {
    throw new Error(mergeResponse?.data?.message || '完成上传失败');
  }
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
};

const executeUploadTask = async (task) => {
  currentTaskId.value = task.id;
  task.startedAt = Date.now();
  task.abortController = new AbortController();
  task.errorMessage = '';
  uploadStartTime.value = task.startedAt;

  try {
    if (task.kind === 'folder') {
      await runFolderUploadTask(task);
    } else {
      await runFileUploadTask(task);
    }

    if (task.status !== 'canceled') {
      task.progress = 100;
      task.speed = '';
      setTaskStatus(task, 'success', '上传完成');
      await loadFiles();
      ElMessage.success(`${task.name} 上传成功`);
    }
  } catch (error) {
    if (isCanceledError(error) || task.status === 'canceled') {
      setTaskStatus(task, 'canceled', '已取消');
    } else {
      task.errorMessage = error?.message || '上传失败';
      task.speed = '';
      setTaskStatus(task, 'failed', task.errorMessage);
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


const onDownload = async (row) => {
  try {
    const res = row.isFolderEntry
      ? await downloadFolderEntry(row.parentFolderId, row.entryPath)
      : row.isFolder === 1
        ? await downloadFolder(row.id)
        : await downloadFile(row.id);
    const blob = new Blob([res.data]);
    let downloadName = row.originalFilename || "download";
    if (row.isFolder === 1 && !downloadName.toLowerCase().endsWith('.zip')) {
      downloadName = `${downloadName}.zip`;
    }
    if (isFileSystemAccessSupported()) {
      const dirHandle = await getDirectoryHandle();
      if (dirHandle && await verifyPermission(dirHandle, 'readwrite')) {
        await writeBlobToDirectory(dirHandle, downloadName, blob);
        ElMessage.success('已保存到本地目录');
        return;
      }
    }
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = downloadName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  } catch (e) {
    // 错误已由拦截器提示
  }
};

const onRemove = async (row) => {
  try {
    const message = row.isFolderEntry ? "确认删除该文件？" : "确认将该文件移入回收站？";
    await ElMessageBox.confirm(message, "提示", {
      type: "warning",
    });
    if (row.isFolderEntry) {
      await deleteFolderEntry(row.parentFolderId, row.entryPath);
        folderBrowseCache.delete(String(row.parentFolderId));
      ElMessage.success("删除成功");
      folderBrowseCache.delete(String(row.parentFolderId));
      const res = await fetchFolderEntries(row.parentFolderId);
      folderEntries.value = res?.data?.data || [];
      renderFolderEntries();
    } else {
      await deleteFile(row.id);
      ElMessage.success("已移入回收站");
      await loadFiles();
    }
  } catch (e) {
    // 取消或错误
  }
};

const onSelectionChange = (rows) => {
  selectedRows.value = rows || [];
};

const toggleMultiSelect = () => {
  multiSelectEnabled.value = !multiSelectEnabled.value;
  if (!multiSelectEnabled.value) {
    selectedRows.value = [];
  }
};

const onBulkDownload = async () => {
  if (!hasSelection.value || !multiSelectEnabled.value) return;
  let skipped = 0;
  for (const row of selectedRows.value) {
    if (row.isFolderEntry && row.isDirectory) {
      skipped += 1;
      continue;
    }
    await onDownload(row);
  }
  if (skipped > 0) {
    ElMessage.warning(`已跳过 ${skipped} 个目录项`);
  }
};

const onBulkRemove = async () => {
  if (!hasSelection.value || !multiSelectEnabled.value) return;
  const count = selectedRows.value.length;
  const message = currentFolderId.value
    ? `确认删除选中的 ${count} 项？`
    : `确认将选中的 ${count} 项移入回收站？`;
  try {
    await ElMessageBox.confirm(message, "提示", {
      type: "warning",
    });
    const rows = [...selectedRows.value];
    const hasFolderEntries = rows.some(row => row.isFolderEntry);
    const hasNormalFiles = rows.some(row => !row.isFolderEntry);

    for (const row of rows) {
      if (row.isFolderEntry) {
        await deleteFolderEntry(row.parentFolderId, row.entryPath);
        folderBrowseCache.delete(String(row.parentFolderId));
      } else {
        await deleteFile(row.id);
      }
    }

    if (hasFolderEntries && currentFolderId.value) {
      const res = await fetchFolderEntries(currentFolderId.value);
      folderEntries.value = res?.data?.data || [];
      renderFolderEntries();
    }
    if (hasNormalFiles) {
      await loadFiles();
    }
    ElMessage.success("批量操作完成");
  } catch (e) {
    // 取消或错误
  }
};

const onEditDescription = (row) => {
  if (row.isFolderEntry || row.isDirectory) return;
  editingRow.value = row;
  editDescValue.value = row.description || '';
  editDescVisible.value = true;
};

const onSaveDescription = async () => {
  if (!editingRow.value) return;
  try {
    await updateFileDescription(editingRow.value.id, editDescValue.value);
    ElMessage.success('描述已更新');
    editDescVisible.value = false;
    await loadFiles();
  } catch (e) {
    ElMessage.error('更新描述失败');
  }
};

const onLogout = () => {
  localStorage.clear();
  router.push("/login");
};

const selectFolder = () => {
  folderInput.value.click();
};

const prefetchSiblingFolders = async (currentFolderId) => {
  const candidates = (files.value || [])
    .filter(item => item?.isFolder === 1 && item?.id !== currentFolderId)
    .slice(0, 2);
  for (const item of candidates) {
    const key = String(item.id);
    if (folderBrowseCache.has(key)) continue;
    try {
      const res = await fetchFolderEntries(item.id);
      folderBrowseCache.set(key, { ts: Date.now(), entries: res?.data?.data || [] });
    } catch (_) {
      // 预热失败不影响主流程
    }
  }
};

const onBrowseFolder = async (row) => {
  try {
    if (row.isFolderEntry && row.isDirectory) {
      pagination.value.page = 1;
      currentFolderPath.value = row.entryPath;
      currentPath.value = buildBreadcrumbForPath(currentPath.value[0]?.name || '文件夹', currentFolderPath.value);
      renderFolderEntries();
      return;
    }

    currentFolderId.value = row.id;
    currentFolderPath.value = '';
    pagination.value.page = 1;

    startBrowseProgress('正在读取目录信息...');

    const cacheKey = String(row.id);
    const cached = folderBrowseCache.get(cacheKey);
    const now = Date.now();
    if (cached && now - cached.ts < 60_000) {
      browseStatus.value = '命中缓存，正在渲染目录...';
      folderEntries.value = cached.entries;
      currentPath.value = buildBreadcrumbForPath(row.originalFilename, '');
      renderFolderEntries();
      stopBrowseProgress('目录加载完成（缓存）');
      return;
    }

    browseStatus.value = '正在从服务器获取目录（真实进度不可得）...';
    const res = await fetchFolderEntries(row.id);
    const entries = res?.data?.data || [];

    browseStatus.value = '正在渲染目录...';
    folderEntries.value = entries;
    folderBrowseCache.set(cacheKey, { ts: now, entries });
    currentPath.value = buildBreadcrumbForPath(row.originalFilename, '');
    renderFolderEntries();
    stopBrowseProgress();
    prefetchSiblingFolders(row.id);
  } catch (e) {
    stopBrowseProgress();
    ElMessage.error('浏览文件夹失败: ' + e.message);
  }
};

const handleFolderSelect = (event) => {
  const fileList = Array.from(event.target.files || []);
  if (fileList.length === 0) return;

  const folderName = fileList[0].webkitRelativePath?.split('/')[0] || 'folder';
  if (isInvalidFileName(folderName)) {
    ElMessage.warning('文件夹名称不合法或过长（<=255），请重命名后再上传');
    event.target.value = '';
    return;
  }

  const task = createUploadTask({
    kind: 'folder',
    name: `${folderName}.zip`,
    folderName,
    folderFiles: fileList
  });

  enqueueUploadTasks([task]);
  ElMessage.success(`目录已加入上传列表: ${folderName}`);
  event.target.value = '';
};

// 文件预览相关方法
const onPreviewFile = (row) => {
  previewFile.value = row;
  previewDialogVisible.value = true;
};


watch(files, () => {
  selectedRows.value = [];
});

onMounted(loadFiles);
</script>

<style scoped>
.file-list-container {
  /* Using flex column to push footer/pagination if needed, but mainly for clean vertical layout */
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Breadcrumb */
.breadcrumb-bar {
  padding: 0 4px;
}
.breadcrumb-link {
  display: inline-flex;
  align-items: center;
  font-weight: 500;
  color: #606266;
  cursor: pointer;
  transition: color 0.3s;
  text-decoration: none;
}
.breadcrumb-link:hover {
  color: #409EFF;
}
.icon-margin {
  margin-right: 4px;
  font-size: 16px;
  position: relative;
  top: 1px;
}

/* Toolbar Card */
.toolbar-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.toolbar-content {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}
.search-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.filter-input {
  width: 160px;
  transition: width 0.3s;
}
.filter-input:focus-within {
  width: 200px;
}
.spacer {
  flex: 1;
}
.upload-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.desc-input {
  width: 200px;
}
.upload-inline {
  display: inline-flex;
}

/* Upload Progress */
.upload-progress-bar {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f2f3f5;
}
.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 12px;
  color: #909399;
}

.upload-list-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}

.upload-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #606266;
  font-size: 14px;
  font-weight: 500;
}

/* Table Card */
.table-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  flex: 1;
}

.type-nav-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid #f2f3f5;
}

.type-nav-item {
  border: 1px solid #dcdfe6;
  background: #fff;
  color: #606266;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
  transition: all 0.2s ease;
}

.type-nav-item:hover {
  color: #409eff;
  border-color: #a0cfff;
  background: #ecf5ff;
}

.type-nav-item.is-active {
  color: #fff;
  border-color: #409eff;
  background: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.25);
}

.table-header {
  position: sticky;
  top: 0;
  z-index: 2;
  background: #fff;
  padding: 10px 12px;
  border-bottom: 1px solid #f2f3f5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.table-header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.bulk-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.bulk-count {
  color: #606266;
  font-size: 13px;
}

/* Responsive adjustments */
@media (max-width: 1200px) {
  .filter-input {
    width: 140px;
  }
}
</style>

<style scoped>
/* Media Preview Styles */
.media-container {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 100%;
  min-height: 400px;
  background-color: #f5f7fa;
  border-radius: 8px;
}

.video-wrapper {
  background-color: #000;
  overflow: hidden;
}

.custom-video-player {
  width: 100%;
  max-height: 700px;
  outline: none;
}

.audio-wrapper {
  background: linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%);
  padding: 40px;
  overflow: hidden; /* Fix overflow issue */
  box-sizing: border-box; /* Ensure padding is included */
}

.audio-card {
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 30px;
  width: 100%;
  max-width: 500px;
  box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.37);
  display: flex;
  flex-direction: column;
  align-items: center;
  border: 1px solid rgba(255, 255, 255, 0.18);
}

.audio-cover-art {
  width: 120px;
  height: 120px;
  background: linear-gradient(45deg, #ff9a9e 0%, #fad0c4 99%, #fad0c4 100%);
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 20px;
  box-shadow: 0 4px 15px rgba(0,0,0,0.2);
  animation: rotate 10s linear infinite; 
  /* Note: Animation will run even if paused, for simple visual effect */
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.audio-info {
  text-align: center;
  margin-bottom: 20px;
  color: #2c3e50;
}

.audio-info h3 {
  margin: 0 0 5px 0;
  font-size: 18px;
}

.audio-info p {
  margin: 0;
  font-size: 12px;
  opacity: 0.8;
}

.custom-audio-player {
  width: 100%;
  height: 40px;
  border-radius: 20px;
}
</style>
