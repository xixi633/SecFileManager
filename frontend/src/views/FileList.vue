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
  fetchChunkSessionStatus,
  resetChunkSession,
  uploadChunk,
  mergeChunks,
  previewFolderEntry,
  downloadFolderEntry,
  deleteFolderEntry,
} from "../api/file.js";
import pLimit from "p-limit"; // 需要安装 p-limit
import { TYPE_CATEGORY_OPTIONS, getFileTypeCategory } from "../utils/fileType.js";


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
const uploadAbortController = ref(null);
const currentUploadingIdentifier = ref('');
const pendingUploadName = ref('');
const pendingUploadSize = ref(0);
const pendingUploadIsFolder = ref(0);
const typeCategoryOptions = TYPE_CATEGORY_OPTIONS;




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

const updateUploadProgress = (loaded, total) => {
  if (!total || Number.isNaN(total)) {
    uploadPercentage.value = Math.min(99, Math.max(uploadPercentage.value, 1));
    uploadStatus.value = `${formatBytes(loaded)} / ?`;
  } else {
    const percentCompleted = Math.round((loaded * 100) / total);
    uploadPercentage.value = Math.min(100, percentCompleted);
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


const handleUpload = async (options) => {
  if (!validateUploadFile(options.file)) {
    return;
  }

  const file = options.file;
  const CHUNK_SIZE = 32 * 1024 * 1024; // 32MB 分片，减少请求次数
  const isLargeFile = file.size > CHUNK_SIZE;
  const CONCURRENT_UPLOADS = 2; // 小并发提升带宽利用，后端可重排写入
  const ENABLE_CHUNK_CHECK = false; // 关闭逐分片探测，减少额外请求

  uploadingFile.value = true;
  uploadPercentage.value = 0;
  uploadStartTime.value = Date.now();
  uploadAbortController.value = new AbortController();
  uploadProcessing.value = false;
  uploadSpeed.value = '';
  pendingUploadName.value = file.name;
  pendingUploadSize.value = file.size;
  pendingUploadIsFolder.value = 0;
  
  if (!isLargeFile) {
      // ===== 小文件直接上传 =====
      uploadStatus.value = '上传中...';
      const formData = new FormData();
      formData.append("file", file);

      try {
        const res = await uploadFile(formData, description.value, (progressEvent) => {
          updateUploadProgress(progressEvent.loaded, progressEvent.total);
        }, undefined, uploadAbortController.value?.signal);
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
          uploadStatus.value = '正在初始化上传...';
          const identifier = createUploadIdentifier(file);
          currentUploadingIdentifier.value = identifier;
          
          const totalChunks = Math.ceil(file.size / CHUNK_SIZE);

          let startChunkNumber = 0;
          try {
            const statusRes = await fetchChunkSessionStatus(identifier);
            const statusData = statusRes?.data?.data;
            if (statusData && ['UPLOADING', 'FINALIZING'].includes(statusData.stage)) {
              startChunkNumber = Number(statusData.nextChunkNumber || 0);
              if (startChunkNumber > 0) {
                uploadStatus.value = `检测到断点，准备从第 ${startChunkNumber + 1} 片继续上传...`;
              }
            }
          } catch (_) {
            // 会话不存在或已过期时从头上传
            startChunkNumber = 0;
          }
          
          // 维护每个分片的上传进度 { chunkNumber: loadedBytes }
          const chunkProgress = new Map();
          
          const updateTotalProgress = () => {
              let totalLoaded = 0;
              for (const loaded of chunkProgress.values()) {
                  totalLoaded += loaded;
              }
              updateUploadProgress(totalLoaded, file.size);
          };

          const limit = pLimit(CONCURRENT_UPLOADS);
          const tasks = [];
          
          uploadStatus.value = `正在上传分片（并发 ${CONCURRENT_UPLOADS}）...`;

          for (let chunkNumber = startChunkNumber; chunkNumber < totalChunks; chunkNumber++) {
              tasks.push(limit(async () => {
                  if (!uploadingFile.value) throw new Error("上传已取消");

                  // 默认不做分片探测，直接上传以减少 RTT；需要断点续传时再开启
                  if (ENABLE_CHUNK_CHECK) {
                      const res = await checkChunk(identifier, chunkNumber);
                      if (res.data.data) {
                          const start = chunkNumber * CHUNK_SIZE;
                          const end = Math.min(start + CHUNK_SIZE, file.size);
                          chunkProgress.set(chunkNumber, end - start);
                          updateTotalProgress();
                          return;
                      }
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
                  }, uploadAbortController.value?.signal);
                  
                  // Ensure full size is set when done
                  chunkProgress.set(chunkNumber, chunk.size);
                  updateTotalProgress();
              }));
          }
          
          await Promise.all(tasks);
          
          uploadStatus.value = '正在提交完成...';
          uploadProcessing.value = true;
          const mergeRes = await mergeChunks(identifier, file.name, totalChunks, file.size);
          if (mergeRes?.data?.code !== 200) {
               throw new Error(mergeRes?.data?.message || '完成上传失败');
          }
          await handleUploadSuccess(mergeRes?.data?.data || null, false);
      } catch (e) {
          handleUploadError(e);
      }
  }
};

const handleUploadSuccess = async (fileId, isAsync = false) => {
    uploadPercentage.value = 100;
    uploadStatus.value = '上传完成';
    uploadProcessing.value = false;
    ElMessage.success("上传成功");

    const canOptimisticInsert = !currentFolderId.value && pagination.value.page === 1
      && !searchFileName.value && !searchDescription.value && !searchKeyword.value;

    if (fileId && canOptimisticInsert) {
      const optimistic = {
        id: fileId,
        originalFilename: pendingUploadName.value || '新文件',
        fileSize: pendingUploadSize.value || 0,
        fileType: '',
        uploadTime: new Date().toISOString(),
        description: description.value || '',
        isFolder: pendingUploadIsFolder.value || 0,
      };
      files.value = [optimistic, ...files.value.filter(item => item.id !== fileId)].slice(0, pagination.value.size);
      pagination.value.total += 1;
      setTimeout(() => { loadFiles(); }, 800);
    } else {
      await loadFiles();
    }

    description.value = "";

    // 重置状态
    setTimeout(() => {
         uploadingFile.value = false;
         uploadPercentage.value = 0;
         uploadStatus.value = '';
         uploadAbortController.value = null;
         currentUploadingIdentifier.value = '';
         pendingUploadName.value = '';
         pendingUploadSize.value = 0;
         pendingUploadIsFolder.value = 0;
    }, 700);
}

const handleUploadError = (e) => {
    if (e?.name !== 'CanceledError' && e?.code !== 'ERR_CANCELED') {
      ElMessage.error(e.message || '上传失败');
    }
    uploadProcessing.value = false;
    uploadingFile.value = false;
    uploadPercentage.value = 0;
    uploadAbortController.value = null;
    currentUploadingIdentifier.value = '';
}


const onCancelUpload = async () => {
  if (!uploadingFile.value) return;
  try {
    if (uploadAbortController.value) {
      uploadAbortController.value.abort();
    }
    if (currentUploadingIdentifier.value) {
      await resetChunkSession(currentUploadingIdentifier.value);
    }
    uploadingFile.value = false;
    uploadProcessing.value = false;
    uploadPercentage.value = 0;
    uploadStatus.value = '';
    uploadSpeed.value = '';
    uploadAbortController.value = null;
    currentUploadingIdentifier.value = '';
    ElMessage.success('已取消上传');
  } catch (e) {
    // 由拦截器提示
  }
}

const onResetUploadSession = async () => {
  if (!uploadingFile.value) return;
  try {
    const fileInput = document.querySelector('.el-upload input[type=file]');
    if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
      ElMessage.warning('未找到当前上传文件，请重新选择后上传');
      return;
    }
    let identifier = currentUploadingIdentifier.value;
    if (!identifier) {
      const file = fileInput.files[0];
      identifier = createUploadIdentifier(file);
    }
    await resetChunkSession(identifier);
    uploadingFile.value = false;
    uploadProcessing.value = false;
    uploadPercentage.value = 0;
    uploadStatus.value = '';
    uploadSpeed.value = '';
    ElMessage.success('上传会话已重置，请重新上传');
  } catch (e) {
    // 由拦截器提示
  }
}

const createUploadIdentifier = (file) => `${file.name}__${file.size}__${file.lastModified}`;

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

const handleFolderSelect = async (event) => {
  const fileList = Array.from(event.target.files);
  if (fileList.length === 0) return;

  uploadingFile.value = true;
  uploadPercentage.value = 0;
  uploadStartTime.value = Date.now();
  uploadAbortController.value = new AbortController();

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
    pendingUploadName.value = `${folderName}.zip`;
    pendingUploadIsFolder.value = 1;
    
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
      compressionOptions: { level: 1 }
    }, (metadata) => {
      const packPercent = Math.round(metadata.percent || 0);
      uploadPercentage.value = Math.min(30, Math.round(packPercent * 0.3));
      uploadStatus.value = `正在压缩文件... ${packPercent}%`;
      uploadSpeed.value = '';
    });
    
    // 创建ZIP文件对象
    const zipFile = new File([zipBlob], `${folderName}.zip`, { type: 'application/zip' });
    pendingUploadSize.value = zipFile.size;
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
        const total = progressEvent.total || zipFile.size;
        const loaded = progressEvent.loaded || 0;
        const uploadPhasePercent = total > 0 ? Math.round((loaded * 70) / total) : 0;
        uploadPercentage.value = Math.min(99, 30 + uploadPhasePercent);
        uploadStatus.value = `上传压缩包: ${formatBytes(loaded)} / ${formatBytes(total)}`;

        const timeElapsed = (Date.now() - uploadStartTime.value) / 1000;
        if (timeElapsed > 0) {
          const speed = loaded / timeElapsed;
          uploadSpeed.value = `上传速度: ${formatBytes(speed)}/s`;
        }
      },
      1,
      uploadAbortController.value?.signal
    );
    if (res?.data?.code !== 200) {
      throw new Error(res?.data?.message || '上传失败');
    }
    
    const fileId = res?.data?.data;
    uploadPercentage.value = 100;
    uploadStatus.value = '上传完成';
    uploadSpeed.value = '';
    uploadProcessing.value = false;

    pagination.value.page = 1;
    await handleUploadSuccess(fileId, false);
    ElMessage.success(`文件夹上传成功: ${folderName}`);
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
