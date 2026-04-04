<template>
  <div class="admin-recycle-container">
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar-content">
        <h3 class="page-title">回收站管理</h3>
        <div class="filter-area">
          <el-input
            v-model="searchFileName"
            placeholder="搜索文件名"
            class="filter-input"
            clearable
            @clear="loadFiles"
            @keyup.enter="loadFiles"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>

          <el-select
            v-model="filterUserId"
            placeholder="按用户过滤"
            clearable
            @change="loadFiles"
            class="filter-select"
          >
            <el-option
              v-for="user in allUsers"
              :key="user.id"
              :label="user.username"
              :value="user.id"
            />
          </el-select>

          <el-select
            v-model="filterTypeCategory"
            placeholder="文件类型"
            @change="onTypeCategoryChange"
            class="filter-select"
          >
            <el-option
              v-for="item in typeCategoryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>

          <el-button type="primary" :icon="Search" circle @click="loadFiles" />
          <el-button :icon="Refresh" circle @click="onReset" />
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
              <div class="file-cell">
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
        <el-table-column prop="userId" label="所属用户" width="140" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ getUserName(row.userId) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="uploadTime" label="删除时间" width="180" align="center">
          <template #default="scope">
            <span class="info-text">{{ formatDateTime(scope.row.uploadTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="filterTypeCategory === 'all'" label="类型" width="110" align="center">
          <template #default="scope">
            <el-tag size="small" effect="plain">{{ getFileTypeLabel(scope.row) }}</el-tag>
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
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
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
import { Folder, Document, Picture, VideoCamera, Headset, DataAnalysis, Search, Refresh, RefreshLeft, Delete } from '@element-plus/icons-vue';
import api from '../api/index.js';
import { TYPE_CATEGORY_OPTIONS, getFileTypeCategory, getFileTypeLabel } from '../utils/fileType.js';

const files = ref([]);
const allUsers = ref([]);
const loading = ref(false);
const page = ref(1);
const size = ref(10);
const total = ref(0);
const searchFileName = ref('');
const filterUserId = ref(null);
const filterTypeCategory = ref('all');
const typeCategoryOptions = TYPE_CATEGORY_OPTIONS;
const multiSelectEnabled = ref(false);
const selectedRows = ref([]);
const hasSelection = computed(() => selectedRows.value.length > 0);
const selectedCount = computed(() => selectedRows.value.length);

const isDir = (row) => row.isFolder === 1;

const getFileIcon = (row) => {
  const category = getFileTypeCategory(row);
  if (category === 'folder') return Folder;
  if (category === 'image') return Picture;
  if (category === 'video') return VideoCamera;
  if (category === 'audio') return Headset;
  if (category === 'document') return DataAnalysis;
  return Document;
};

const loadFiles = async () => {
  loading.value = true;
  try {
    const params = { page: page.value, size: size.value };
    if (searchFileName.value) {
      params.fileName = searchFileName.value;
    }
    if (filterUserId.value) {
      params.userId = filterUserId.value;
    }
    if (filterTypeCategory.value !== 'all') {
      params.typeCategory = filterTypeCategory.value;
    }
    const res = await api.get('/admin/file/recycle/list', { params });
    const data = res.data.data;
    files.value = data.records;
    total.value = data.total;
  } catch (error) {
    ElMessage.error('加载回收站失败');
  } finally {
    loading.value = false;
  }
};

const loadAllUsers = async () => {
  try {
    const res = await api.get('/admin/user/list', {
      params: { page: 1, size: 1000 }
    });
    allUsers.value = res.data.data.records;
  } catch (error) {
    console.error('加载用户列表失败', error);
  }
};

const getUserName = (userId) => {
  const user = allUsers.value.find(u => u.id === userId);
  return user ? user.username : userId;
};

const onReset = () => {
  searchFileName.value = '';
  filterUserId.value = null;
  filterTypeCategory.value = 'all';
  page.value = 1;
  loadFiles();
};

const onTypeCategoryChange = () => {
  page.value = 1;
  loadFiles();
};

const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i];
};

const formatDateTime = (dateTimeStr) => {
  if (!dateTimeStr) return '';
  const date = new Date(dateTimeStr);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
};

const onRestore = async (file) => {
  try {
    await ElMessageBox.confirm(`确定还原 ${file.originalFilename} 吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    });
    await api.post(`/admin/file/recycle/restore/${file.id}`);
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
      `确定彻底删除 ${file.originalFilename} 吗？该操作不可恢复。`,
      '警告',
      { type: 'error', confirmButtonText: '彻底删除', cancelButtonText: '取消' }
    );
    await api.delete(`/admin/file/recycle/${file.id}`);
    ElMessage.success('彻底删除成功');
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
      await api.post(`/admin/file/recycle/restore/${row.id}`);
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
    await ElMessageBox.confirm(`确定彻底删除选中的 ${count} 项吗？该操作不可恢复。`, '警告', {
      type: 'error',
      confirmButtonText: '彻底删除',
      cancelButtonText: '取消'
    });
    for (const row of selectedRows.value) {
      await api.delete(`/admin/file/recycle/${row.id}`);
    }
    ElMessage.success('批量删除成功');
    loadFiles();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('批量删除失败');
    }
  }
};

onMounted(() => {
  loadAllUsers();
  loadFiles();
});
</script>

<style scoped>
.admin-recycle-container {
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
.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.filter-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.filter-input {
  width: 200px;
}
.filter-select {
  width: 160px;
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
.file-cell {
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
