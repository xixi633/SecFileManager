<template>
  <div class="recycle-bin-container">
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar-content">
        <h3 class="page-title">回收站</h3>
        <div class="toolbar-actions">
          <el-button :icon="Refresh" circle @click="loadFiles" :loading="loading" />
        </div>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-header">
        <div class="table-header-actions">
          <el-button type="info" plain @click="toggleMultiSelect">
            {{ multiSelectEnabled ? '退出多选' : '多选' }}
          </el-button>
          <div class="bulk-actions" v-if="multiSelectEnabled">
            <span class="bulk-count">已选 {{ selectedCount }} 项</span>
            <el-button type="success" :disabled="!hasSelection" @click="onBulkRestore">批量还原</el-button>
            <el-button type="danger" :disabled="!hasSelection" @click="onBulkDeletePermanently">批量删除</el-button>
          </div>
        </div>
      </div>
      <el-table
        :key="multiSelectEnabled ? 'multi' : 'single'"
        :data="files"
        stripe
        v-loading="loading"
        style="width: 100%"
        row-key="id"
        @selection-change="onSelectionChange"
      >
        <el-table-column v-if="multiSelectEnabled" type="selection" width="50" />
        <el-table-column label="文件名" min-width="250">
          <template #default="scope">
            <div class="file-name-cell">
              <div class="icon-wrapper" :class="{ 'is-folder': isDir(scope.row) }">
                <el-icon :size="20">
                  <component :is="getFileIcon(scope.row)" />
                </el-icon>
              </div>
              <span class="filename">{{ scope.row.originalFilename }}</span>
              <el-tag v-if="scope.row.isFolder === 1" size="small" effect="plain" style="margin-left: 8px">目录</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="120" align="right">
          <template #default="scope">
            <span class="info-text">{{ formatFileSize(scope.row.fileSize) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="uploadTime" label="删除时间" width="180" align="center">
          <template #default="scope">
            <span class="info-text">{{ formatDateTime(scope.row.uploadTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center">
          <template #default="scope">
             <el-tooltip content="还原" placement="top">
                <el-button type="success" size="small" circle :icon="RefreshLeft" @click="onRestore(scope.row)" />
             </el-tooltip>
             <el-tooltip content="彻底删除" placement="top">
                <el-button type="danger" size="small" circle :icon="Delete" @click="onDeletePermanently(scope.row)" />
             </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadFiles"
          @current-change="loadFiles"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Folder, Document, Picture, VideoCamera, Headset, DataAnalysis, Refresh, RefreshLeft, Delete } from '@element-plus/icons-vue';
import { fetchRecycleList, restoreFile, deleteFilePermanently } from '../api/file.js';
const isDir = (row) => row.isFolder === 1;

const getFileIcon = (row) => {
  if (isDir(row)) return Folder;
  const filename = row.originalFilename || '';
  const ext = filename.split('.').pop().toLowerCase();
  if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg'].includes(ext)) {
    return Picture;
  }
  if (['mp4', 'avi', 'mov', 'wmv', 'mkv'].includes(ext)) {
    return VideoCamera;
  }
  if (['mp3', 'wav', 'flac', 'aac', 'ogg'].includes(ext)) {
    return Headset;
  }
  if (['xls', 'xlsx', 'csv'].includes(ext)) {
    return DataAnalysis;
  }
  return Document;
};

const files = ref([]);
const loading = ref(false);
const pagination = ref({ page: 1, size: 10, total: 0 });
const multiSelectEnabled = ref(false);
const selectedRows = ref([]);
const hasSelection = computed(() => selectedRows.value.length > 0);
const selectedCount = computed(() => selectedRows.value.length);

const loadFiles = async () => {
  loading.value = true;
  try {
    const res = await fetchRecycleList(pagination.value.page, pagination.value.size);
    const data = res.data.data;
    files.value = data.records || [];
    pagination.value.total = data.total || 0;
  } catch (error) {
    ElMessage.error('加载回收站失败');
  } finally {
    loading.value = false;
  }
};

const onRestore = async (file) => {
  try {
    await ElMessageBox.confirm(`确定还原 ${file.originalFilename} 吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    });
    await restoreFile(file.id);
    ElMessage.success('还原成功');
    loadFiles();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('还原失败');
    }
  }
};

const onDeletePermanently = async (file) => {
  try {
    await ElMessageBox.confirm(
      `确定彻底删除 ${file.originalFilename} 吗？此操作无法撤销。`,
      '警告',
      { type: 'error', confirmButtonText: '彻底删除', cancelButtonText: '取消' }
    );
    await deleteFilePermanently(file.id);
    ElMessage.success('已彻底删除');
    loadFiles();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败');
    }
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

const onBulkRestore = async () => {
  if (!hasSelection.value || !multiSelectEnabled.value) return;
  const count = selectedRows.value.length;
  try {
    await ElMessageBox.confirm(`确定还原选中的 ${count} 项吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    });
    for (const row of selectedRows.value) {
      await restoreFile(row.id);
    }
    ElMessage.success('批量还原成功');
    loadFiles();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('批量还原失败');
    }
  }
};

const onBulkDeletePermanently = async () => {
  if (!hasSelection.value || !multiSelectEnabled.value) return;
  const count = selectedRows.value.length;
  try {
    await ElMessageBox.confirm(`确定彻底删除选中的 ${count} 项吗？此操作无法撤销。`, '警告', {
      type: 'error',
      confirmButtonText: '彻底删除',
      cancelButtonText: '取消'
    });
    for (const row of selectedRows.value) {
      await deleteFilePermanently(row.id);
    }
    ElMessage.success('批量删除成功');
    loadFiles();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('批量删除失败');
    }
  }
};

const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i];
};

const formatDateTime = (dateTimeStr) => {
  if (!dateTimeStr) return '-';
  const date = new Date(dateTimeStr);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
};

onMounted(() => {
  loadFiles();
});
</script>

<style scoped>
.recycle-bin-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}
.toolbar-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.toolbar-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.table-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  flex: 1;
  display: flex;
  flex-direction: column;
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
.file-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}
.icon-wrapper {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background-color: #f0f9eb;
  color: #67C23A;
  flex-shrink: 0;
}
.icon-wrapper.is-folder {
  background-color: #ecf5ff;
  color: #409EFF;
}
.filename {
  font-weight: 500;
  color: #606266;
}
.info-text {
  color: #909399;
  font-size: 13px;
}
.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
