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
          
          <el-upload
            :http-request="handleUpload"
            :show-file-list="false"
            :disabled="uploadingFile"
            class="upload-inline"
          >
            <el-button type="primary" :loading="uploadingFile" :icon="Upload" class="action-btn">
              {{ uploadingFile ? '上传中...' : '上传文件' }}
            </el-button>
          </el-upload>
          
          <input
            ref="folderInput"
            type="file"
            webkitdirectory
            directory
            multiple
            style="display: none"
            @change="handleFolderSelect"
          />
          <el-button type="success" @click="selectFolder" :disabled="uploadingFile" :icon="FolderAdd" class="action-btn">
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
          </div>
          <el-progress 
            :percentage="uploadProcessing ? 100 : uploadPercentage" 
            :status="uploadProcessing ? 'success' : ''"
            :indeterminate="uploadProcessing"
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
            :percentage="browseProgress"
            :stroke-width="6"
            :show-text="false"
            class="custom-progress"
          />
        </div>
      </transition>
    </el-card>

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
      <file-table
        :key="multiSelectEnabled ? 'multi' : 'single'"
        :files="files"
        :enable-selection="multiSelectEnabled"
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
import { ref, onMounted, nextTick, watch, computed } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Document, HomeFilled, Folder, Search, Refresh, Upload, FolderAdd, Monitor, ArrowRight } from '@element-plus/icons-vue';
import JSZip from "jszip";
import api from "../api/index.js";
import FileTable from "../components/FileTable.vue";
import FilePreview from "../components/FilePreview.vue";
import HashWorker from '../workers/hash.worker.js?worker';
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
  checkChunk,
  uploadChunk,
  mergeChunks,
  previewFolderEntry,
  downloadFolderEntry,
  deleteFolderEntry,
} from "../api/file.js";
import pLimit from "p-limit"; // 需要安装 p-limit


const router = useRouter();
const files = ref([]);
const description = ref("");
const folderInput = ref(null);
const uploadProgress = ref({ current: 0, total: 0 });
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
let browseTimer = null;
const searchFileName = ref('');
const searchDescription = ref('');
const searchKeyword = ref('');
const editDescVisible = ref(false);
const editDescValue = ref('');
const editingRow = ref(null);
const multiSelectEnabled = ref(false);
const selectedRows = ref([]);
const selectedCount = computed(() => selectedRows.value.length);
const hasSelection = computed(() => selectedRows.value.length > 0);




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

  pagination.value.total = items.length;
  if (items.length === 0) {
    pagination.value.page = 1;
    files.value = [];
    return;
  }
  const totalPages = Math.ceil(items.length / pagination.value.size);
  if (pagination.value.page > totalPages) {
    pagination.value.page = totalPages;
  }
  const start = (pagination.value.page - 1) * pagination.value.size;
  const end = start + pagination.value.size;
  files.value = items.slice(start, end);
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
        keyword: searchKeyword.value || undefined
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

const onResetSearch = () => {
  searchFileName.value = '';
  searchDescription.value = '';
  searchKeyword.value = '';
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

const updateUploadProgress = (loaded, total) => {
  if (!total || Number.isNaN(total)) {
    uploadPercentage.value = Math.min(99, Math.max(uploadPercentage.value, 1));
    uploadStatus.value = `${formatBytes(loaded)} / ?`;
  } else {
    const percentCompleted = Math.round((loaded * 100) / total);
    uploadPercentage.value = Math.min(99, percentCompleted);
    uploadStatus.value = `${formatBytes(loaded)} / ${formatBytes(total)}`;
  }

  const timeElapsed = (Date.now() - uploadStartTime.value) / 1000;
  if (timeElapsed > 0) {
    const speed = loaded / timeElapsed;
    uploadSpeed.value = `上传速度: ${formatBytes(speed)}/s`;
  }
};

const waitForFileVisible = async (fileId, timeoutMs = 120000) => {
  const startTime = Date.now();
  while (Date.now() - startTime < timeoutMs) {
    await loadFiles();
    if (fileId && files.value.some(item => item.id === fileId)) {
      return true;
    }
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  return false;
};

const startBrowseProgress = () => {
  browseLoading.value = true;
  browseProgress.value = 1;
  browseStatus.value = '正在解压文件夹内容...';
  if (browseTimer) {
    clearInterval(browseTimer);
  }
  browseTimer = setInterval(() => {
    if (browseProgress.value < 90) {
      browseProgress.value += 1;
    }
  }, 500);
};

const stopBrowseProgress = () => {
  if (browseTimer) {
    clearInterval(browseTimer);
    browseTimer = null;
  }
  browseProgress.value = 100;
  setTimeout(() => {
    browseLoading.value = false;
    browseProgress.value = 0;
    browseStatus.value = '';
  }, 300);
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


const handleUpload = async (options) => {
  if (!validateUploadFile(options.file)) {
    return;
  }

  const file = options.file;
  const CHUNK_SIZE = 20 * 1024 * 1024; // 20MB 分片
  const isLargeFile = file.size > CHUNK_SIZE;

  uploadingFile.value = true;
  uploadPercentage.value = 0;
  uploadStartTime.value = Date.now();
  uploadProcessing.value = false;
  uploadSpeed.value = '';
  
  if (!isLargeFile) {
      // ===== 小文件直接上传 =====
      uploadStatus.value = '上传中...';
      const formData = new FormData();
      formData.append("file", file);

      try {
        const res = await uploadFile(formData, description.value, (progressEvent) => {
          updateUploadProgress(progressEvent.loaded, progressEvent.total);
        });
        if (res?.data?.code !== 200) {
          throw new Error(res?.data?.message || '上传失败');
        }
        await handleUploadSuccess(res?.data?.data);
      } catch (e) {
        handleUploadError(e);
      }
  } else {
      // ===== 大文件分片上传 =====
      try {
          uploadStatus.value = '正在计算文件指纹...';
          const identifier = await computeMD5(file);
          
          const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
          
          // 维护每个分片的上传进度 { chunkNumber: loadedBytes }
          const chunkProgress = new Map();
          
          const updateTotalProgress = () => {
              let totalLoaded = 0;
              for (const loaded of chunkProgress.values()) {
                  totalLoaded += loaded;
              }
              updateUploadProgress(totalLoaded, file.size);
          };

          const limit = pLimit(3); // 并发数 3
          const tasks = [];
          
          uploadStatus.value = '正在上传分片...';

          for (let chunkNumber = 0; chunkNumber < totalChunks; chunkNumber++) {
              tasks.push(limit(async () => {
                  if (!uploadingFile.value) throw new Error("上传已取消");

                  // check if chunk exists
                  const res = await checkChunk(identifier, chunkNumber);
                  if (res.data.data) {
                      // existing chunk
                      const start = chunkNumber * CHUNK_SIZE;
                      const end = Math.min(start + CHUNK_SIZE, file.size);
                      chunkProgress.set(chunkNumber, end - start);
                      updateTotalProgress();
                      return;
                  }
                  
                  const start = chunkNumber * CHUNK_SIZE;
                  const end = Math.min(start + CHUNK_SIZE, file.size);
                  const chunk = file.slice(start, end);
                  
                  const formData = new FormData();
                  formData.append("file", chunk);
                  formData.append("chunkNumber", chunkNumber);
                  formData.append("chunkSize", CHUNK_SIZE);
                  formData.append("currentChunkSize", chunk.size);
                  formData.append("totalSize", file.size);
                  formData.append("identifier", identifier);
                  formData.append("filename", file.name);
                  formData.append("totalChunks", totalChunks);
                  
                  await uploadChunk(formData, (p) => {
                       chunkProgress.set(chunkNumber, p.loaded);
                       updateTotalProgress();
                  });
                  
                  // Ensure full size is set when done
                  chunkProgress.set(chunkNumber, chunk.size);
                  updateTotalProgress();
              }));
          }
          
          await Promise.all(tasks);
          
          uploadStatus.value = '正在合并文件...';
          uploadProcessing.value = true;
          // 发送合并请求 (由于后端已改为同步处理，此请求会等待直到合并完成)
          const res = await mergeChunks(identifier, file.name, totalChunks, file.size);
          if (res.data.code === 200) {
              // 合并完成即代表入库成功，不再是异步处理
              await handleUploadSuccess(null, false); 
          } else {
               throw new Error(res.data.message);
          }
      } catch (e) {
          handleUploadError(e);
      }
  }
};

const handleUploadSuccess = async (fileId, isAsync = false) => {
    uploadPercentage.value = 100;
    uploadStatus.value = '处理完成';
    uploadProcessing.value = false;
    ElMessage.success("上传成功");
    description.value = "";
    
    // 立即刷新列表
    await loadFiles();
    
    // 重置状态
    setTimeout(() => {
         uploadingFile.value = false;
         uploadPercentage.value = 0;
         uploadStatus.value = '';
    }, 1000);
}

const handleUploadError = (e) => {
    ElMessage.error(e.message || '上传失败');
    uploadProcessing.value = false;
    uploadingFile.value = false;
    uploadPercentage.value = 0;
}

const computeMD5 = (file) => {
    return new Promise((resolve, reject) => {
        const worker = new HashWorker();
        // 通知Worker开始计算
        worker.postMessage({ file });
        
        // 监听进度与结果
        worker.onmessage = (e) => {
             const { type, hash, progress, error } = e.data;
             if (type === 'progress') {
                  uploadStatus.value = `计算指纹... ${progress}%`;
             } else if (type === 'result') {
                  worker.terminate();
                  resolve(hash);
             } else if (type === 'error') {
                  worker.terminate();
                  reject(error);
             }
        };
        
        worker.onerror = (err) => {
             worker.terminate();
             reject(err);
        };
    });
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
      ElMessage.success("删除成功");
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

    startBrowseProgress();
    const res = await fetchFolderEntries(row.id);
    folderEntries.value = res?.data?.data || [];
    currentPath.value = buildBreadcrumbForPath(row.originalFilename, '');
    renderFolderEntries();
    stopBrowseProgress();
    
  } catch (e) {
    stopBrowseProgress();
    ElMessage.error('浏览文件夹失败: ' + e.message);
  }
};

const handleFolderSelect = async (event) => {
  const fileList = Array.from(event.target.files);
  if (fileList.length === 0) return;

  uploadingFile.value = true;
  uploadPercentage.value = 0;
  uploadStartTime.value = Date.now();

  try {
    // 获取文件夹名称
    const folderName = fileList[0].webkitRelativePath?.split('/')[0] || 'folder';
    if (isInvalidFileName(folderName)) {
      ElMessage.warning('文件夹名称不合法或过长（<=255），请重命名后再上传');
      uploadingFile.value = false;
      uploadStatus.value = '';
      uploadSpeed.value = '';
      return;
    }
    
    uploadStatus.value = `正在打包 ${fileList.length} 个文件...`;
    
    // 创建ZIP压缩包
    const zip = new JSZip();
    
    for (const file of fileList) {
      const relativePath = file.webkitRelativePath || file.name;
      zip.file(relativePath, file);
    }
    
    uploadStatus.value = '正在压缩文件...';
    
    // 生成ZIP文件
    const zipBlob = await zip.generateAsync({ 
      type: "blob",
      compression: "DEFLATE",
      compressionOptions: { level: 6 }
    });
    
    // 创建ZIP文件对象
    const zipFile = new File([zipBlob], `${folderName}.zip`, { type: 'application/zip' });
    if (zipFile.size === 0) {
      ElMessage.warning('不能上传空文件夹');
      uploadingFile.value = false;
      uploadStatus.value = '';
      uploadSpeed.value = '';
      return;
    }
    
    uploadStatus.value = '开始上传...';
    
    // 上传ZIP文件
    const formData = new FormData();
    formData.append("file", zipFile);
    formData.append("isFolder", "1"); // 标记为文件夹
    
    const res = await uploadFile(
      formData,
      description.value || `文件夹: ${folderName}`,
      (progressEvent) => {
      updateUploadProgress(progressEvent.loaded, progressEvent.total);
      },
      1
    );
    if (res?.data?.code !== 200) {
      throw new Error(res?.data?.message || '上传失败');
    }
    
    const fileId = res?.data?.data;
    uploadPercentage.value = 0;
    uploadStatus.value = '上传完成，服务器处理中...';
    uploadSpeed.value = '';
    uploadProcessing.value = true;

    pagination.value.page = 1;

    const found = await waitForFileVisible(fileId);

    uploadProcessing.value = false;
    if (found) {
      uploadPercentage.value = 100;
      ElMessage.success(`文件夹上传成功: ${folderName}`);
      await loadFiles();
    } else {
      uploadPercentage.value = 99;
      ElMessage.warning(`文件夹上传成功: ${folderName}，入库较慢请稍后刷新`);
      await loadFiles();
    }
    description.value = "";
    event.target.value = ""; // 重置input
    
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '上传失败';
    ElMessage.error(`文件夹上传失败: ${msg}`);
  } finally {
    uploadProcessing.value = false;
    uploadingFile.value = false;
    uploadPercentage.value = 0;
    uploadStatus.value = '';
    uploadSpeed.value = '';
  }
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

/* Table Card */
.table-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  flex: 1;
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
