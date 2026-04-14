<template>
  <el-table
    ref="tableRef"
    :data="files"
    stripe
    border
    style="width: 100%"
    class="custom-table"
    :header-cell-style="{background:'#fafafa', color:'#606266', fontWeight:'600'}"
    row-key="id"
    @selection-change="onSelectionChange"
  >
    <el-table-column v-if="enableSelection" type="selection" width="50" />
    <el-table-column label="文件名" width="300">
      <template #default="scope">
        <el-popover
          trigger="hover"
          :width="280"
          placement="right-start"
          :show-after="500"
          :hide-after="100"
          :offset="8"
          popper-class="file-info-popper"
        >
          <template #reference>
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

          <div class="file-info-card">
            <div class="file-info-header">
              <div class="file-info-icon" :class="{ 'is-folder': isDir(scope.row) }">
                <el-icon :size="24">
                  <component :is="getFileIcon(scope.row)" />
                </el-icon>
              </div>
              <div class="file-info-title">{{ scope.row.originalFilename }}</div>
            </div>
            <div class="file-info-divider"></div>
            <div class="file-info-row">
              <span class="file-info-label">类型</span>
              <span class="file-info-value">{{ getFileTypeLabel(scope.row) }}</span>
            </div>
            <div class="file-info-row">
              <span class="file-info-label">大小</span>
              <span class="file-info-value">{{ formatFileSize(scope.row.fileSize) }}</span>
            </div>
            <div class="file-info-row">
              <span class="file-info-label">修改时间</span>
              <span class="file-info-value">{{ formatDateTime(scope.row.uploadTime) }}</span>
            </div>
            <div v-if="scope.row.description" class="file-info-row">
              <span class="file-info-label">描述</span>
              <span class="file-info-value file-info-desc">{{ scope.row.description }}</span>
            </div>
          </div>
        </el-popover>
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
    
    <el-table-column prop="description" label="描述" width="250" show-overflow-tooltip>
      <template #default="scope">
        <span class="text-secondary">{{ scope.row.description || '-' }}</span>
      </template>
    </el-table-column>
    
    <el-table-column label="操作" width="260">
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
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue';
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

const getFileIcon = (row) => {
  const category = getFileTypeCategory(row);
  if (category === 'folder') return Folder;
  if (category === 'image') return Picture;
  if (category === 'video') return VideoCamera;
  if (category === 'audio') return Headset;
  if (category === 'document') return DataAnalysis;
  return Document;
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
  return date.toLocaleString('zh-CN', { hour12: false });
};

const tableRef = ref(null);
let resizeState = null;
let rafId = null;

const getFlattenColumns = () => {
  const table = tableRef.value;
  if (!table) return [];

  if (table.layout && typeof table.layout.getFlattenColumns === 'function') {
    return table.layout.getFlattenColumns();
  }

  const store = table.store;
  if (!store || !store.states) return [];

  try {
    const columnsRef = store.states.columns;
    const cols = Array.isArray(columnsRef) ? columnsRef : (columnsRef.value || []);
    const flat = [];
    const walk = (list) => {
      list.forEach((col) => {
        if (col.children && col.children.length) {
          walk(col.children);
        } else {
          flat.push(col);
        }
      });
    };
    walk(Array.isArray(cols) ? cols : []);
    return flat;
  } catch {
    return [];
  }
};

const onHeaderMouseDown = (e) => {
  const th = e.target.closest('th');
  if (!th) return;

  const rect = th.getBoundingClientRect();
  const isNearRightBorder = rect.right - e.clientX < 10;

  if (!isNearRightBorder) return;

  const headerCells = Array.from(th.parentElement.children);
  const colIndex = headerCells.indexOf(th);
  const flatColumns = getFlattenColumns();
  const column = flatColumns[colIndex];

  if (!column) return;

  e.stopImmediatePropagation();
  e.preventDefault();

  const startWidth = column.realWidth || column.width || column.minWidth || 80;
  const startX = e.clientX;
  const minWidth = column.minWidth || 50;

  resizeState = { column, startWidth, startX, minWidth, lastWidth: startWidth };

  const el = tableRef.value?.$el;
  if (el) el.classList.add('is-resizing');

  document.addEventListener('mousemove', onResizeMouseMove);
  document.addEventListener('mouseup', onResizeMouseUp);
  document.addEventListener('pointermove', onResizeMouseMove);
  document.addEventListener('pointerup', onResizeMouseUp);
  document.body.style.cursor = 'col-resize';
  document.body.style.userSelect = 'none';
};

const onResizeMouseMove = (e) => {
  if (!resizeState) return;
  e.preventDefault();

  if (rafId) cancelAnimationFrame(rafId);

  rafId = requestAnimationFrame(() => {
    if (!resizeState) return;

    const delta = e.clientX - resizeState.startX;
    const newWidth = Math.max(resizeState.minWidth, resizeState.startWidth + delta);
    const widthDelta = newWidth - resizeState.lastWidth;

    if (widthDelta === 0) return;

    resizeState.column.width = resizeState.column.realWidth = newWidth;
    resizeState.lastWidth = newWidth;

    const el = tableRef.value?.$el;
    if (el && resizeState.column.id) {
      const cols = el.querySelectorAll(`col[name="${resizeState.column.id}"]`);
      cols.forEach(col => col.setAttribute('width', String(newWidth)));

      const tables = el.querySelectorAll(
        '.el-table__header-wrapper table, .el-table__body-wrapper table'
      );
      tables.forEach(tbl => {
        const current = parseInt(tbl.style.width) || 0;
        tbl.style.width = `${current + widthDelta}px`;
      });
    }
  });
};

const cleanupResize = () => {
  resizeState = null;
  if (rafId) {
    cancelAnimationFrame(rafId);
    rafId = null;
  }

  const el = tableRef.value?.$el;
  if (el) el.classList.remove('is-resizing');

  document.removeEventListener('mousemove', onResizeMouseMove);
  document.removeEventListener('mouseup', onResizeMouseUp);
  document.removeEventListener('pointermove', onResizeMouseMove);
  document.removeEventListener('pointerup', onResizeMouseUp);
  document.body.style.cursor = '';
  document.body.style.userSelect = '';
};

const onResizeMouseUp = () => {
  try {
    const table = tableRef.value;
    if (table) {
      table.doLayout?.();
    }
  } catch {}
  cleanupResize();
};

onMounted(() => {
  nextTick(() => {
    const el = tableRef.value?.$el;
    if (!el) return;
    const headerWrapper = el.querySelector('.el-table__header-wrapper');
    if (headerWrapper) {
      headerWrapper.addEventListener('mousedown', onHeaderMouseDown, true);
    }
  });
});

onBeforeUnmount(() => {
  const el = tableRef.value?.$el;
  if (el) {
    const headerWrapper = el.querySelector('.el-table__header-wrapper');
    if (headerWrapper) {
      headerWrapper.removeEventListener('mousedown', onHeaderMouseDown, true);
    }
  }
  document.removeEventListener('mousemove', onResizeMouseMove);
  document.removeEventListener('mouseup', onResizeMouseUp);
  document.removeEventListener('pointermove', onResizeMouseMove);
  document.removeEventListener('pointerup', onResizeMouseUp);
  if (rafId) cancelAnimationFrame(rafId);
});
</script>

<style scoped>
.custom-table {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  --el-table-border-color: #f0f0f0;
}

.custom-table :deep(.el-table__body-wrapper),
.custom-table :deep(.el-table__header-wrapper) {
  overflow-x: auto;
}

.custom-table :deep(td),
.custom-table :deep(th) {
  border-right-color: #f5f5f5 !important;
  border-bottom-color: #f0f0f0 !important;
}

.custom-table :deep(th.is-leaf) {
  border-right-color: #f5f5f5 !important;
  border-bottom-color: #ebeef5 !important;
}

.custom-table :deep(.el-table__border-left-patch),
.custom-table :deep(.el-table__fixed-right-patch) {
  border-bottom-color: #f0f0f0 !important;
}

.custom-table :deep(.el-table__inner-wrapper::before) {
  background-color: #f0f0f0 !important;
}

.custom-table :deep(.el-table__header th .el-table__cell) {
  border-right-color: #f5f5f5 !important;
}

.custom-table :deep(.el-table__column-resize-proxy) {
  display: none !important;
}

.custom-table.is-resizing :deep(.el-table__body-wrapper),
.custom-table.is-resizing :deep(.el-table__header-wrapper) {
  pointer-events: none;
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

<style>
.file-info-popper {
  padding: 0 !important;
  border-radius: 8px !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12) !important;
  overflow: hidden;
}

.file-info-card {
  padding: 14px 16px;
  font-size: 13px;
  color: #303133;
  line-height: 1.5;
}

.file-info-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.file-info-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background-color: #f0f9eb;
  color: #67C23A;
  flex-shrink: 0;
}

.file-info-icon.is-folder {
  background-color: #ecf5ff;
  color: #409EFF;
}

.file-info-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  word-break: break-all;
  line-height: 1.4;
}

.file-info-divider {
  height: 1px;
  background-color: #ebeef5;
  margin: 10px 0;
}

.file-info-row {
  display: flex;
  align-items: flex-start;
  padding: 3px 0;
}

.file-info-label {
  color: #909399;
  width: 64px;
  flex-shrink: 0;
  font-size: 12px;
}

.file-info-value {
  color: #303133;
  font-size: 12px;
  word-break: break-all;
}

.file-info-desc {
  max-height: 60px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}
</style>
