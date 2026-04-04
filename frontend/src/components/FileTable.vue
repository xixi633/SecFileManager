<template>
  <el-table
    :data="files"
    stripe
    style="width: 100%"
    class="custom-table"
    :header-cell-style="{background:'#fafafa', color:'#606266', fontWeight:'600'}"
    row-key="id"
    @selection-change="onSelectionChange"
  >
    <el-table-column v-if="enableSelection" type="selection" width="50" />
    <el-table-column label="文件名" min-width="250">
      <template #default="scope">
        <div class="file-name-cell">
          <div class="icon-wrapper" :class="{ 'is-folder': isDir(scope.row) }">
            <el-icon :size="20">
              <component :is="getFileIcon(scope.row)" />
            </el-icon>
          </div>
          
          <div class="name-wrapper" @click="isDir(scope.row) && $emit('browse', scope.row)">
            <span
              class="file-name"
              :class="{ 'clickable': isDir(scope.row) }"
            >
              {{ scope.row.originalFilename }}
            </span>
          </div>
          
          <el-tag v-if="isDir(scope.row)" size="small" type="info" effect="light" round>文件夹</el-tag>
        </div>
      </template>
    </el-table-column>
    
    <el-table-column prop="fileSize" label="大小" width="120" sortable>
      <template #default="scope">
        <span class="text-secondary">{{ formatFileSize(scope.row.fileSize) }}</span>
      </template>
    </el-table-column>
    
    <el-table-column prop="uploadTime" label="修改时间" width="180" sortable>
      <template #default="scope">
        <span class="text-secondary">{{ formatDateTime(scope.row.uploadTime) }}</span>
      </template>
    </el-table-column>

    <el-table-column v-if="showTypeColumn" label="类型" width="110" align="center">
      <template #default="scope">
        <el-tag size="small" effect="plain">{{ getFileTypeLabel(scope.row) }}</el-tag>
      </template>
    </el-table-column>
    
    <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
      <template #default="scope">
        <span class="text-secondary">{{ scope.row.description || '-' }}</span>
      </template>
    </el-table-column>
    
    <el-table-column label="操作" width="260" fixed="right">
      <template #default="scope">
        <div class="action-buttons">
          <template v-if="isVirtualDir(scope.row)">
            <el-button link type="primary" size="small" @click="$emit('browse', scope.row)">打开</el-button>
          </template>

          <template v-else-if="isFolderZip(scope.row)">
            <el-button link type="primary" size="small" @click="$emit('browse', scope.row)">打开</el-button>
            <el-tooltip content="下载文件夹" placement="top" :enterable="false">
              <el-button link type="primary" :icon="Download" circle @click="$emit('download', scope.row)" />
            </el-tooltip>
            <el-tooltip content="编辑描述" placement="top" :enterable="false">
              <el-button link type="warning" :icon="Edit" circle @click="$emit('edit-description', scope.row)" />
            </el-tooltip>
          </template>

          <template v-else>
            <el-tooltip content="预览文件" placement="top" :enterable="false">
              <el-button link type="primary" :icon="View" circle @click="$emit('preview', scope.row)" />
            </el-tooltip>
            
            <el-tooltip content="下载文件" placement="top" :enterable="false">
              <el-button link type="primary" :icon="Download" circle @click="$emit('download', scope.row)" />
            </el-tooltip>
            
            <el-tooltip content="编辑描述" placement="top" :enterable="false">
              <el-button link type="warning" :icon="Edit" circle @click="$emit('edit-description', scope.row)" />
            </el-tooltip>
          </template>
          
          <el-divider direction="vertical" />
          
          <el-tooltip content="删除" placement="top" :enterable="false">
            <el-button link type="danger" :icon="Delete" circle @click="$emit('remove', scope.row)" />
          </el-tooltip>
        </div>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { Folder, Document, Picture, VideoCamera, Headset, DataAnalysis, View, Download, Edit, Delete } from '@element-plus/icons-vue';
import { getFileTypeCategory, getFileTypeLabel } from '../utils/fileType.js';

const props = defineProps({
  files: {
    type: Array,
    default: () => [],
  },
  enableSelection: {
    type: Boolean,
    default: false,
  },
  showTypeColumn: {
    type: Boolean,
    default: true,
  },
});

const emit = defineEmits(['selection-change']);

const onSelectionChange = (rows) => {
  emit('selection-change', rows);
};

const isFolderZip = (row) => row.isFolder === 1;
const isVirtualDir = (row) => row.isDirectory === true;
const isDir = (row) => isFolderZip(row) || isVirtualDir(row);

/**
 * 根据文件名获取对应图标组件
 */
const getFileIcon = (row) => {
  const category = getFileTypeCategory(row);
  if (category === 'folder') return Folder;
  if (category === 'image') return Picture;
  if (category === 'video') return VideoCamera;
  if (category === 'audio') return Headset;
  if (category === 'document') return DataAnalysis;
  return Document;
};

/**
 * 格式化文件大小
 */
const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i];
};

/**
 * 格式化日期时间
 */
const formatDateTime = (dateTimeStr) => {
  if (!dateTimeStr) return '-';
  const date = new Date(dateTimeStr);
  return date.toLocaleString('zh-CN', { hour12: false });
};
</script>

<style scoped>
.custom-table {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  --el-table-border-color: #f0f0f0;
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

.name-wrapper {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-name {
  font-weight: 500;
  color: #303133;
  font-size: 14px;
}

.file-name.clickable {
  cursor: pointer;
  color: #409EFF;
  transition: color 0.2s;
}

.file-name.clickable:hover {
  text-decoration: underline;
  color: #66b1ff;
}

.text-secondary {
  color: #909399;
  font-size: 13px;
}

.action-buttons {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
}
</style>
