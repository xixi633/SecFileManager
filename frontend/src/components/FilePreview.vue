<template>
  <el-dialog
    :model-value="visible"
    :title="file?.originalFilename || '文件预览'"
    width="85%"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="$emit('update:visible', $event)"
  >
    <div style="min-height: 600px;">
      <el-alert
        v-if="file && isOverKkOnlyLimit(file)"
        title=">2GB 文件预览策略"
        description="超过 2GB 的文件仅支持 kkFileView 预览，已自动切换为 kkFileView。"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 12px;"
      />

      <div
        v-else-if="file"
        style="display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px;"
      >
        <span style="font-size: 13px; color: #606266;">预览方式</span>
        <el-radio-group v-model="previewMode" size="small" @change="onPreviewModeChange">
          <el-radio-button label="auto">自动</el-radio-button>
          <el-radio-button label="builtin">原生预览</el-radio-button>
          <el-radio-button label="internal">kkFileView</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 预览失败提示 -->
      <div v-if="previewError" style="padding: 100px 0; text-align: center;">
        <el-icon :size="80" color="#F56C6C"><Warning /></el-icon>
        <p style="color: #606266; margin-top: 24px; font-size: 18px;">预览失败</p>
        <p style="color: #909399; font-size: 14px; margin-top: 8px;">{{ previewError }}</p>
        <div style="margin-top: 20px; display: flex; justify-content: center; gap: 12px;">
          <el-button @click="loadPreview(file)">重试</el-button>
          <el-button type="primary" @click="$emit('download', file)">下载文件</el-button>
        </div>
      </div>

      <!-- 内置查看器预览 -->
      <div v-else-if="useInternalViewer && viewerUrl" style="height: 700px; display: flex; flex-direction: column;">
        <el-alert
          title="系统高级查看器 (kkFileView)"
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 12px;"
        >
          <template #default>
            <div style="font-size: 13px; line-height: 1.5; color: #606266; margin-top: 4px;">
              当前正在使用 kkFileView 预览。若遇到不兼容格式或加载失败，您可以手动切换：
              <el-button size="small" type="primary" plain @click="fallbackToBuiltinPreview(file)" style="margin-left: 10px;">
                切换为原生基础预览
              </el-button>
            </div>
          </template>
        </el-alert>
        <div style="margin-bottom: 10px; text-align: right;">
          <el-button size="small" @click="openViewerPreview">在新窗口打开</el-button>
          <el-button size="small" type="primary" @click="$emit('download', file)">下载文件</el-button>
        </div>
        <iframe
          ref="viewerIframe"
          :src="viewerUrl"
          style="width: 100%; flex: 1; border: 1px solid #dcdfe6; border-radius: 6px;"
          frameborder="0"
          @load="onIframeLoad"
          @error="onIframeError"
        ></iframe>
      </div>

      <!-- 图片预览（所有大小，URL流式加载） -->
      <div v-else-if="isImageFile(file)" style="text-align: center;">
        <img
          :src="getPreviewUrl(file)"
          style="max-width: 100%; max-height: 700px;"
          alt="图片预览"
          @error="onMediaLoadError"
        />
      </div>

      <!-- 视频预览（所有大小，流式Range播放） -->
      <div v-else-if="isVideoFile(file)" class="media-container video-wrapper" style="height: 100%; min-height: 400px; display: flex; justify-content: center; align-items: center;">
        <video
          controls
          preload="metadata"
          class="custom-video-player"
          :src="getPreviewUrl(file)"
          style="max-width: 100%; max-height: 700px; width: auto; height: auto; object-fit: contain;"
        >
          您的浏览器不支持视频播放
        </video>
      </div>

      <!-- 音频预览（所有大小，流式Range播放） -->
      <div v-else-if="isAudioFile(file)" class="media-container audio-wrapper">
        <div class="audio-card">
          <div class="audio-cover-art">
            <el-icon :size="60" color="#fff"><Headset /></el-icon>
          </div>
          <div class="audio-info">
            <h3>{{ file?.originalFilename }}</h3>
            <p>{{ formatBytes(file?.fileSize) }}</p>
          </div>
          <audio
            controls
            preload="metadata"
            class="custom-audio-player"
            :src="getPreviewUrl(file)"
          >
            您的浏览器不支持音频播放
          </audio>
        </div>
      </div>

      <!-- ====== 大文件（>512MB且≤2GB）非流式类型 ====== -->

      <!-- 大文件 文本/代码 部分预览（取前1MB） -->
      <div v-else-if="isLargeFile(file) && (isTextFile(file) || isCodeFile(file))">
        <el-alert
          title="大文件部分预览"
          :description="`文件大小 ${formatBytes(file?.fileSize)}，仅显示前 1MB 内容。如需查看完整内容请下载。`"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 12px;"
        />
        <div 
          v-if="isCodeFile(file)" 
          ref="monacoContainer"
          style="width: 100%; height: 650px; border: 1px solid #dcdfe6;"
        ></div>
        <div v-else>
          <pre style="white-space: pre-wrap; height: 650px; overflow-y: auto; padding: 20px; background: #f5f7fa; border: 1px solid #dcdfe6;">{{ textContent }}</pre>
        </div>
        <div style="text-align: center; margin-top: 12px;">
          <el-button v-if="!partialPreviewDone" :loading="loadingMorePartial" @click="onLoadMorePartial(file)">继续加载下一段</el-button>
          <el-button type="primary" @click="$emit('download', file)">下载完整文件</el-button>
        </div>
      </div>

      <!-- 压缩包目录预览（ZIP） -->
      <div v-else-if="isZipFile(file)">
        <el-alert
          :title="`压缩包目录预览（共 ${archiveEntryCount} 项，当前显示 ${filteredArchiveEntries.length} 项）`"
          description="当前展示 ZIP 内的目录与文件清单，暂不支持在线打开压缩包内文件。"
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 12px;"
        />
        <div style="display: flex; justify-content: flex-end; margin-bottom: 10px;">
          <el-input
            v-model="archiveSearchKeyword"
            clearable
            placeholder="搜索压缩包路径"
            style="width: 300px"
          />
        </div>
        <div style="height: 650px; overflow: auto; border: 1px solid #dcdfe6; border-radius: 6px;">
          <el-table :data="filteredArchiveEntries" stripe style="width: 100%">
            <el-table-column prop="name" label="路径" min-width="420" />
            <el-table-column label="类型" width="100" align="center">
              <template #default="scope">
                <el-tag size="small" :type="scope.row.isDir ? 'info' : 'success'">{{ scope.row.isDir ? '目录' : '文件' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="140" align="right">
              <template #default="scope">
                <span>{{ scope.row.isDir ? '-' : formatBytes(scope.row.size || 0) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <!-- 非 ZIP 压缩包降级 -->
      <div v-else-if="isArchiveFile(file)" style="padding: 100px 0; text-align: center;">
        <el-icon :size="80" color="#909399"><Document /></el-icon>
        <p style="color: #606266; margin-top: 24px; font-size: 18px;">该压缩格式暂不支持在线目录解析</p>
        <p style="color: #909399; font-size: 14px; margin-top: 8px;">当前仅支持 ZIP 目录预览；你可以下载到本地查看。</p>
        <el-button type="primary" @click="$emit('download', file)" style="margin-top: 24px;" :icon="Download">下载文件</el-button>
      </div>

      <!-- 大文件 PDF/Word/Excel 不支持浏览器内渲染 -->
      <div v-else-if="isLargeFile(file) && (isPdfFile(file) || isWordFile(file) || isExcelFile(file))" style="padding: 80px 0; text-align: center;">
        <el-icon :size="80" color="#E6A23C"><Document /></el-icon>
        <p style="color: #606266; margin-top: 24px; font-size: 18px;">
          文件较大（{{ formatBytes(file?.fileSize) }}），该格式暂不支持大文件在线预览
        </p>
        <p style="color: #909399; font-size: 14px; margin-top: 8px;">
          文档类文件（PDF/Word/Excel）需要完整加载到浏览器内存才能渲染，超过512MB可能导致浏览器崩溃
        </p>
        <el-button type="primary" @click="$emit('download', file)" style="margin-top: 24px;" :icon="Download">
          下载到本地查看
        </el-button>
      </div>

      <!-- ====== 小文件（≤512MB）完整加载预览 ====== -->

      <!-- PDF预览 -->
      <div 
        v-else-if="isPdfFile(file)" 
        ref="pdfContainer"
        style="width: 100%; height: 700px; overflow-y: auto; border: 1px solid #dcdfe6;"
      >
        <canvas v-for="pageNum in pdfPages" :key="pageNum" :ref="el => setPdfCanvasRef(el, pageNum)" style="display: block; margin: 10px auto;"></canvas>
      </div>

      <!-- Word文档预览 -->
      <div 
        v-else-if="isWordFile(file)" 
        ref="docxContainer"
        style="width: 100%; height: 700px; overflow-y: auto; border: 1px solid #dcdfe6; padding: 20px; background: white;"
      ></div>

      <!-- Excel预览 -->
      <div 
        v-else-if="isExcelFile(file)" 
        ref="xlsxContainer"
        style="width: 100%; height: 700px; overflow: auto; border: 1px solid #dcdfe6;"
      ></div>
      
      <!-- 代码预览（小文件） -->
      <div 
        v-else-if="isCodeFile(file)" 
        ref="monacoContainer"
        style="width: 100%; height: 700px; border: 1px solid #dcdfe6;"
      ></div>

      <!-- 纯文本预览（小文件） -->
      <div v-else-if="isTextFile(file)" style="text-align: left;">
        <pre style="white-space: pre-wrap; height: 700px; overflow-y: auto; padding: 20px; background: #f5f7fa; border: 1px solid #dcdfe6;">{{ textContent }}</pre>
      </div>

      <!-- PPTX 文本预览（小文件） -->
      <div v-else-if="isPptxFile(file)">
        <el-alert
          title="PPTX 文本预览"
          description="当前展示幻灯片中的可提取文本内容（不含完整版式与动画）。"
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 12px;"
        />
        <div style="height: 650px; overflow: auto; border: 1px solid #dcdfe6; border-radius: 6px; padding: 16px; background: #fff;">
          <div v-if="pptSlidesText.length === 0" style="color: #909399;">未提取到可预览文本内容。</div>
          <div v-for="slide in pptSlidesText" :key="slide.index" style="margin-bottom: 16px;">
            <div style="font-weight: 600; color: #303133; margin-bottom: 8px;">第 {{ slide.index }} 页</div>
            <pre style="white-space: pre-wrap; margin: 0; color: #606266;">{{ slide.text }}</pre>
          </div>
        </div>
      </div>

      <!-- DOC/PPT 旧格式提示 -->
      <div v-else-if="isLegacyWordFile(file) || isLegacyPptFile(file)" style="padding: 100px 0; text-align: center;">
        <el-icon :size="80" color="#E6A23C"><Document /></el-icon>
        <p style="color: #606266; margin-top: 24px; font-size: 18px;">当前文件为旧版 Office 格式，浏览器内核无法稳定解析</p>
        <p style="color: #909399; font-size: 14px; margin-top: 8px;">建议转换为 docx/pptx 后在线预览，或下载到本地查看。</p>
        <el-button type="primary" @click="$emit('download', file)" style="margin-top: 24px;" :icon="Download">下载文件</el-button>
      </div>

      <!-- 大文件其他类型 -->
      <div v-else-if="isLargeFile(file)" style="padding: 100px 0; text-align: center;">
        <el-icon :size="80" color="#909399"><Document /></el-icon>
        <p style="color: #606266; margin-top: 24px; font-size: 18px;">
          文件较大（{{ formatBytes(file?.fileSize) }}），该类型暂不支持大文件在线预览
        </p>
        <el-button type="primary" @click="$emit('download', file)" style="margin-top: 24px;">
          下载文件
        </el-button>
      </div>
      
      <!-- 不支持的文件类型 -->
      <div v-else style="padding: 200px 0; text-align: center;">
        <el-icon :size="100" color="#909399"><Document /></el-icon>
        <p style="color: #909399; margin-top: 30px; font-size: 16px;">该文件类型暂不支持预览</p>
        <el-button type="primary" @click="$emit('download', file)" style="margin-top: 30px;">
          下载文件
        </el-button>
      </div>
    </div>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">关闭</el-button>
      <el-button type="primary" @click="$emit('download', file)">下载</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, nextTick, onUnmounted, onMounted, computed } from 'vue';
import { Document, Headset, Warning, Download } from '@element-plus/icons-vue';
import { renderAsync } from 'docx-preview';
import * as pdfjsLib from 'pdfjs-dist';
import * as XLSX from 'xlsx';
import * as monaco from 'monaco-editor';
import JSZip from 'jszip';
import api from '../api/index.js';
import { fetchPreviewConfig, fetchViewerUrl, fetchFolderViewerUrl } from '../api/file.js';
import { addPreviewFailureNotification } from '../store/messageCenter.js';

const PREVIEW_MODE_STORAGE_KEY = 'previewModePreference';

const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg'];
const VIDEO_EXTENSIONS = ['mp4', 'webm', 'ogg', 'mov', 'm4v'];
const AUDIO_EXTENSIONS = ['mp3', 'wav', 'flac', 'aac', 'ogg', 'm4a'];
const PDF_EXTENSIONS = ['pdf'];
const WORD_EXTENSIONS = ['docx'];
const LEGACY_WORD_EXTENSIONS = ['doc', 'rtf'];
const EXCEL_EXTENSIONS = ['xlsx', 'xls', 'csv'];
const PPTX_EXTENSIONS = ['pptx'];
const LEGACY_PPT_EXTENSIONS = ['ppt'];
const TEXT_EXTENSIONS = ['txt', 'log', 'ini', 'conf', 'properties', 'env'];
const CODE_EXTENSIONS = ['js', 'ts', 'jsx', 'tsx', 'java', 'py', 'html', 'css', 'scss', 'less', 'json', 'xml', 'sql', 'md', 'markdown', 'yml', 'yaml', 'c', 'cpp', 'h', 'hpp', 'go', 'rs', 'php', 'vue', 'sh', 'bat', 'ps1', 'kt', 'swift', 'rb', 'dockerfile'];
const ZIP_EXTENSIONS = ['zip'];
const ARCHIVE_EXTENSIONS = ['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz'];

// Worker setup safely
try {
  if (pdfjsLib && pdfjsLib.GlobalWorkerOptions) {
    pdfjsLib.GlobalWorkerOptions.workerSrc = '/pdf.worker.min.mjs';
  }
} catch (e) {
  console.warn('PDF.js worker init failed:', e);
}

// ============ 文件大小阈值常量（后端配置） ============
const DEFAULT_MAX_PREVIEW_SIZE = 2147483648; // 2GB
const DEFAULT_SMALL_FILE_THRESHOLD = 512 * 1024 * 1024; // 512MB
const DEFAULT_PARTIAL_PREVIEW_SIZE = 1024 * 1024; // 1MB

const previewConfig = ref({
  maxPreviewSize: DEFAULT_MAX_PREVIEW_SIZE,
  smallFileThreshold: DEFAULT_SMALL_FILE_THRESHOLD,
  partialPreviewSize: DEFAULT_PARTIAL_PREVIEW_SIZE
});

const props = defineProps({
  visible: Boolean,
  file: Object
});

const emit = defineEmits(['update:visible', 'download']);

// Refs for containers
const pdfContainer = ref(null);
const docxContainer = ref(null);
const xlsxContainer = ref(null);
const monacoContainer = ref(null);
const pdfPages = ref(0);
const pdfCanvasRefs = ref([]);
const textContent = ref('');
const previewError = ref('');
const partialPreviewOffset = ref(0);
const partialPreviewDone = ref(false);
const loadingMorePartial = ref(false);
const archiveEntries = ref([]);
const archiveEntryCount = ref(0);
const archiveSearchKeyword = ref('');
const viewerUrl = ref('');
const useInternalViewer = ref(false);
const viewerIframe = ref(null);
const pptSlidesText = ref([]);
const previewMode = ref(localStorage.getItem(PREVIEW_MODE_STORAGE_KEY) || 'auto');

const onIframeLoad = (e) => {
  try {
    const iframe = e.target;
    const doc = iframe.contentDocument || iframe.contentWindow?.document;
    if (doc) {
      const text = doc.body?.innerText || '';
      if (text.includes('暂不支持') || text.includes('失败') || text.includes('404')) {
        console.warn('kkFileView 渲染失败（内部提示报错），自动降级原生预览');
        fallbackToBuiltinPreview(props.file);
      }
    }
  } catch (err) {
    // 跨域拦截，如果发生了则无法通过DOM直接读取，这是预期内的安全策略
  }
};

const onIframeError = () => {
  console.warn('Iframe 加载失败，自动降级原生预览');
  fallbackToBuiltinPreview(props.file);
};

let monacoEditor = null;
let currentBlobUrl = null;

const filteredArchiveEntries = computed(() => {
  const keyword = archiveSearchKeyword.value.trim().toLowerCase();
  if (!keyword) return archiveEntries.value;
  return archiveEntries.value.filter((item) => item.name.toLowerCase().includes(keyword));
});

// ============ 工具函数 ============

const getPreviewUrl = (file) => {
  if (!file) return '';
  const token = localStorage.getItem('token');
  const baseUrl = (api.defaults.baseURL || '/api').replace(/\/$/, '');
  // 文件夹内文件使用专用的 folder/preview 接口
  if (file.isFolderEntry && file.parentFolderId && file.entryPath) {
    const encodedPath = encodeURIComponent(file.entryPath);
    return `${baseUrl}/file/folder/preview/${file.parentFolderId}?path=${encodedPath}&token=${token}`;
  }
  if (!file.id) return '';
  return `${baseUrl}/file/preview/${file.id}?token=${token}`;
};

const getFilename = (file) => String(file?.originalFilename || '');

const reportPreviewFailure = (message, file = props.file) => {
  if (!message) return;
  addPreviewFailureNotification({
    fileName: getFilename(file) || '文件预览',
    message
  });
};

const getExtension = (file) => {
  const filename = getFilename(file).toLowerCase();
  const idx = filename.lastIndexOf('.');
  if (idx < 0 || idx === filename.length - 1) {
    return filename === 'dockerfile' ? 'dockerfile' : '';
  }
  return filename.slice(idx + 1);
};

const getMimeType = (file) => String(file?.fileType || '').toLowerCase();

const hasExtension = (file, list) => list.includes(getExtension(file));

const hasMimePrefix = (file, prefixes) => prefixes.some((prefix) => getMimeType(file).startsWith(prefix));

const hasMimeIncludes = (file, keywords) => keywords.some((keyword) => getMimeType(file).includes(keyword));

const formatBytes = (bytes, decimals = 2) => {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
};

// ============ 文件类型判断 ============

const isImageFile = (file) => hasExtension(file, IMAGE_EXTENSIONS) || hasMimePrefix(file, ['image/']);
const isVideoFile = (file) => hasExtension(file, VIDEO_EXTENSIONS) || hasMimePrefix(file, ['video/']);
const isAudioFile = (file) => hasExtension(file, AUDIO_EXTENSIONS) || hasMimePrefix(file, ['audio/']);
const isPdfFile = (file) => hasExtension(file, PDF_EXTENSIONS) || hasMimeIncludes(file, ['pdf']);
const isWordFile = (file) => hasExtension(file, WORD_EXTENSIONS);
const isExcelFile = (file) => hasExtension(file, EXCEL_EXTENSIONS) || hasMimeIncludes(file, ['spreadsheet', 'excel', 'csv']);
const isLegacyWordFile = (file) => hasExtension(file, LEGACY_WORD_EXTENSIONS) || hasMimeIncludes(file, ['msword', 'rtf']);
const isPptxFile = (file) => hasExtension(file, PPTX_EXTENSIONS) || hasMimeIncludes(file, ['presentationml.presentation']);
const isLegacyPptFile = (file) => hasExtension(file, LEGACY_PPT_EXTENSIONS) || hasMimeIncludes(file, ['presentation']);
const isCodeFile = (file) => hasExtension(file, CODE_EXTENSIONS);
const isTextFile = (file) => hasExtension(file, TEXT_EXTENSIONS) || hasMimePrefix(file, ['text/']);
const isZipFile = (file) => hasExtension(file, ZIP_EXTENSIONS) || hasMimeIncludes(file, ['zip']);
const isArchiveFile = (file) => hasExtension(file, ARCHIVE_EXTENSIONS) || hasMimeIncludes(file, ['zip', 'rar', '7z', 'tar', 'gzip', 'bzip', 'xz', 'compress']);

const isOverKkOnlyLimit = (file) => (file?.fileSize || 0) > previewConfig.value.maxPreviewSize;

const isFolderEntryFile = (file) => !!(file?.isFolderEntry && file?.parentFolderId && file?.entryPath);

const shouldAutoUseInternalViewer = (file) => {
  if (!file) return false;
  // 文件夹内条目优先使用原生，避免 kkFileView 通过容器回源导致性能过慢。
  if (isFolderEntryFile(file)) return false;

  if (isLegacyWordFile(file) || isLegacyPptFile(file)) return true;
  if (isPdfFile(file) || isWordFile(file) || isExcelFile(file) || isPptxFile(file)) return true;
  if (isArchiveFile(file) && !isZipFile(file)) return true;
  return false;
};

const resolvePreviewTarget = (file) => {
  if (!file) return 'builtin';
  if (isOverKkOnlyLimit(file)) return 'internal';

  if (previewMode.value === 'internal') return 'internal';
  if (previewMode.value === 'builtin') return 'builtin';
  return shouldAutoUseInternalViewer(file) ? 'internal' : 'builtin';
};

const requestInternalViewerUrl = async (file) => {
  try {
    if (file?.isFolderEntry && file?.parentFolderId && file?.entryPath) {
      const res = await fetchFolderViewerUrl(file.parentFolderId, file.entryPath);
      return res?.data?.data || '';
    }
    if (!file?.id) return '';
    const res = await fetchViewerUrl(file.id);
    return res?.data?.data || '';
  } catch (_) {
    return '';
  }
};

const openViewerPreview = () => {
  if (!viewerUrl.value) return;
  const url = viewerUrl.value;
  window.open(url, '_blank', 'noopener,noreferrer');
};

/**
 * 判断是否为大文件（>512MB）
 */
const isLargeFile = (file) => (file?.fileSize || 0) > previewConfig.value.smallFileThreshold;

const onMediaLoadError = () => {
  previewError.value = '媒体文件加载失败，请检查网络后重试或下载查看。';
  reportPreviewFailure(previewError.value);
};

const loadPreviewConfig = async () => {
  try {
    const res = await fetchPreviewConfig();
    const cfg = res?.data?.data || {};
    previewConfig.value = {
      maxPreviewSize: cfg.maxPreviewSize || DEFAULT_MAX_PREVIEW_SIZE,
      smallFileThreshold: cfg.smallFileThreshold || DEFAULT_SMALL_FILE_THRESHOLD,
      partialPreviewSize: cfg.partialPreviewSize || DEFAULT_PARTIAL_PREVIEW_SIZE
    };
  } catch (e) {
    console.warn('Load preview config failed, fallback to defaults', e);
  }
};

// Canvas refs for PDF
const setPdfCanvasRef = (el, pageNum) => {
  if (el) {
    pdfCanvasRefs.value[pageNum - 1] = el;
  }
};

// ============ 预览加载逻辑 ============

/**
 * 清理资源
 */
const cleanup = () => {
  if (currentBlobUrl) {
    URL.revokeObjectURL(currentBlobUrl);
    currentBlobUrl = null;
  }
  if (monacoEditor) {
    monacoEditor.dispose();
    monacoEditor = null;
  }
  pdfPages.value = 0;
  pdfCanvasRefs.value = [];
  textContent.value = '';
  previewError.value = '';
  partialPreviewOffset.value = 0;
  partialPreviewDone.value = false;
  loadingMorePartial.value = false;
  archiveEntries.value = [];
  archiveEntryCount.value = 0;
  archiveSearchKeyword.value = '';
  viewerUrl.value = '';
  useInternalViewer.value = false;
  pptSlidesText.value = [];
};

watch(() => props.visible, async (val) => {
  if (val && props.file) {
    await nextTick();
    previewError.value = '';
    loadPreview(props.file);
  } else {
    cleanup();
  }
});

watch(previewMode, (val) => {
  localStorage.setItem(PREVIEW_MODE_STORAGE_KEY, val);
});

const onPreviewModeChange = () => {
  if (!props.visible || !props.file) return;
  previewError.value = '';
  loadPreview(props.file);
};

const fallbackToBuiltinPreview = (file) => {
  previewMode.value = 'builtin';
  useInternalViewer.value = false;
  previewError.value = '';
  loadPreview(file);
};

/**
 * 根据文件类型和大小选择预览策略
 * 
 * ≤512MB: 完整加载到内存进行预览（blob方式）
 * >512MB: 
 *   - 图片/视频/音频: URL流式加载（由模板src属性处理）
 *   - 文本/代码: Range请求获取前1MB进行部分预览
 *   - PDF/Word/Excel: 由模板显示下载提示
 */
const loadPreview = async (file) => {
  try {
    useInternalViewer.value = false;

    const previewTarget = resolvePreviewTarget(file);
    if (previewTarget === 'internal') {
      const internalViewerUrl = await requestInternalViewerUrl(file);
      if (internalViewerUrl) {
        viewerUrl.value = internalViewerUrl;
        useInternalViewer.value = true;
        return;
      }

      if (isOverKkOnlyLimit(file)) {
        previewError.value = '当前文件超过 2GB，仅支持 kkFileView 预览，但未获取到有效查看器地址。';
        reportPreviewFailure(previewError.value, file);
        return;
      }

      // 在自动模式下，kkFileView 不可用时降级回原生预览。
      if (previewMode.value !== 'auto') {
        previewError.value = 'kkFileView 地址获取失败，请稍后重试或切换为原生预览。';
        reportPreviewFailure(previewError.value, file);
        return;
      }
    }

    if (isOverKkOnlyLimit(file)) {
      previewError.value = '超过 2GB 的文件仅支持 kkFileView 预览。';
      reportPreviewFailure(previewError.value, file);
      return;
    }

    // 降级为原生基础预览
    if (isLegacyWordFile(file) || isLegacyPptFile(file) || (!isZipFile(file) && isArchiveFile(file))) {
      previewError.value = '当前格式无法使用基础原生预览。如 kkFileView 无法使用，请下载到本地查看。';
      reportPreviewFailure(previewError.value, file);
      return;
    }

    // 图片/视频/音频：由模板src属性直接处理（URL流式加载，支持Range）
    if (isImageFile(file) || isVideoFile(file) || isAudioFile(file)) {
      return;
    }

    const large = isLargeFile(file);

    // 大文件（>512MB）文本/代码：使用Range请求只加载前1MB
    if (large && (isTextFile(file) || isCodeFile(file))) {
      await loadPartialTextPreview(file, false);
      return;
    }

    if (large && isZipFile(file)) {
      previewError.value = 'ZIP 文件较大，暂不支持原生基础预览，请下载到本地查看。';
      reportPreviewFailure(previewError.value, file);
      return;
    }

    // 大文件的PDF/Word/Excel/其他：优先交给内置查看器，否则由模板直接显示下载提示
    if (large) {
      return;
    }

    // ====== 小文件（≤512MB）：完整加载到浏览器内存预览 ======
    let blob;
    if (file.isFolderEntry && file.parentFolderId && file.entryPath) {
      // 文件夹内文件使用专用接口
      const res = await api.get(`/file/folder/preview/${file.parentFolderId}`, {
        params: { path: file.entryPath },
        responseType: 'blob'
      });
      blob = res.data;
    } else {
      const res = await api.get(`/file/preview/${file.id}`, {
        responseType: 'blob'
      });
      blob = res.data;
    }
    
    if (isPdfFile(file)) {
      renderPdf(blob);
    } else if (isZipFile(file)) {
      await renderZipEntries(blob);
    } else if (isWordFile(file)) {
      renderWord(blob);
    } else if (isExcelFile(file)) {
      renderExcel(blob, file);
    } else if (isPptxFile(file)) {
      await renderPptxText(blob);
    } else if (isCodeFile(file) || isTextFile(file)) {
      const text = await readBlobAsText(blob);
      if (isCodeFile(file)) {
        renderCode(text, file.originalFilename);
      } else {
        textContent.value = text;
      }
    }
  } catch (e) {
    previewError.value = e?.response?.data?.message || e?.message || '预览加载失败，请重试或下载查看。';
    reportPreviewFailure(previewError.value, file);
  }
};

/**
 * 大文件文本/代码部分预览
 * 使用HTTP Range请求只获取文件前1MB内容，避免完整加载到内存
 */
const loadPartialTextPreview = async (file, append = false) => {
  try {
    const url = getPreviewUrl(file);
    const start = append ? partialPreviewOffset.value : 0;
    const end = start + previewConfig.value.partialPreviewSize - 1;
    const token = localStorage.getItem('token');
    const res = await fetch(url, {
      headers: {
        'Range': `bytes=${start}-${end}`,
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      }
    });

    if (!res.ok && res.status !== 206) {
      throw new Error(`Range request failed: ${res.status}`);
    }

    const buffer = await res.arrayBuffer();
    // 使用TextDecoder处理可能被截断的多字节UTF-8字符
    const decoder = new TextDecoder('utf-8', { fatal: false });
    const text = decoder.decode(buffer);
    const contentRange = res.headers.get('Content-Range');
    let totalSize = null;
    if (contentRange) {
      const parts = contentRange.split('/');
      if (parts.length === 2) {
        totalSize = Number(parts[1]);
      }
    }

    partialPreviewOffset.value = start + buffer.byteLength;
    if (Number.isFinite(totalSize) && totalSize > 0) {
      partialPreviewDone.value = partialPreviewOffset.value >= totalSize;
    } else {
      partialPreviewDone.value = buffer.byteLength < previewConfig.value.partialPreviewSize;
    }

    textContent.value = append ? `${textContent.value}${text}` : text;

    await nextTick();

    if (isCodeFile(file)) {
      renderCode(textContent.value, file.originalFilename);
    }
  } catch (e) {
    previewError.value = e?.message || '文本预览加载失败，请尝试下载查看。';
    reportPreviewFailure(previewError.value, file);
    textContent.value = '预览加载失败，请尝试下载查看。';
    partialPreviewDone.value = true;
  }
};

const onLoadMorePartial = async (file) => {
  if (loadingMorePartial.value || partialPreviewDone.value) return;
  loadingMorePartial.value = true;
  try {
    await loadPartialTextPreview(file, true);
  } finally {
    loadingMorePartial.value = false;
  }
};

const readBlobAsText = async (blob) => {
  const buffer = await blob.arrayBuffer();
  const utf8Decoder = new TextDecoder('utf-8', { fatal: false });
  const utf8Text = utf8Decoder.decode(buffer);

  if (!utf8Text.includes('\uFFFD')) {
    return utf8Text;
  }

  const gb18030Decoder = new TextDecoder('gb18030', { fatal: false });
  const gbText = gb18030Decoder.decode(buffer);
  const utf8ReplacementCount = (utf8Text.match(/\uFFFD/g) || []).length;
  const gbReplacementCount = (gbText.match(/\uFFFD/g) || []).length;
  return gbReplacementCount < utf8ReplacementCount ? gbText : utf8Text;
};

const renderZipEntries = async (blob) => {
  const zip = await JSZip.loadAsync(blob);
  const entries = [];
  Object.keys(zip.files).forEach((name) => {
    const item = zip.files[name];
    const size = item?.dir ? 0 : (item?._data?.uncompressedSize ?? 0);
    entries.push({
      name,
      isDir: !!item?.dir,
      size
    });
  });

  entries.sort((a, b) => {
    if (a.isDir !== b.isDir) return a.isDir ? -1 : 1;
    return a.name.localeCompare(b.name, 'zh-CN');
  });

  archiveEntryCount.value = entries.length;
  archiveEntries.value = entries.slice(0, 5000);
};

const extractXmlText = (xmlText) => {
  const parser = new DOMParser();
  const xml = parser.parseFromString(xmlText, 'application/xml');
  const texts = Array.from(xml.getElementsByTagName('a:t'))
    .map((node) => (node.textContent || '').trim())
    .filter(Boolean);
  return texts.join('\n').trim();
};

const renderPptxText = async (blob) => {
  const zip = await JSZip.loadAsync(blob);
  const slidePaths = Object.keys(zip.files)
    .filter((name) => /^ppt\/slides\/slide\d+\.xml$/i.test(name))
    .sort((a, b) => {
      const ai = Number(a.match(/slide(\d+)\.xml/i)?.[1] || 0);
      const bi = Number(b.match(/slide(\d+)\.xml/i)?.[1] || 0);
      return ai - bi;
    });

  const slides = [];
  for (const slidePath of slidePaths) {
    const xmlText = await zip.files[slidePath].async('text');
    const index = Number(slidePath.match(/slide(\d+)\.xml/i)?.[1] || slides.length + 1);
    slides.push({
      index,
      text: extractXmlText(xmlText) || '（该页未提取到文本）'
    });
  }

  pptSlidesText.value = slides;
};

// ============ 渲染函数 ============

const renderPdf = async (blob) => {
  try {
    const url = URL.createObjectURL(blob);
    currentBlobUrl = url;
    const pdf = await pdfjsLib.getDocument(url).promise;
    pdfPages.value = pdf.numPages;
    await nextTick();

    for (let i = 1; i <= pdf.numPages; i++) {
      const page = await pdf.getPage(i);
      const scale = 1.5;
      const viewport = page.getViewport({ scale });
      const canvas = pdfCanvasRefs.value[i - 1];
      if (canvas) {
        const context = canvas.getContext('2d');
        canvas.height = viewport.height;
        canvas.width = viewport.width;
        await page.render({ canvasContext: context, viewport }).promise;
      }
    }
  } catch (e) {
    console.error("PDF render error", e);
  }
};

const renderWord = async (blob) => {
  if (docxContainer.value) {
    await renderAsync(blob, docxContainer.value);
  }
};

const renderExcel = async (blob, file) => {
  const data = await blob.arrayBuffer();
  const workbook = XLSX.read(data, {
    type: 'array',
    cellDates: true,
    cellText: true,
    dense: true
  });

  const tabs = workbook.SheetNames.map((sheetName, index) => {
    return `
      <button type="button" class="sheet-tab ${index === 0 ? 'is-active' : ''}" data-sheet-tab="${index}">${sheetName}</button>
    `;
  }).join('');

  const panels = workbook.SheetNames.map((sheetName, index) => {
    const worksheet = workbook.Sheets[sheetName];
    const html = XLSX.utils.sheet_to_html(worksheet, {
      editable: false,
      header: '',
      footer: ''
    });
    return `
      <section class="sheet-panel ${index === 0 ? 'is-active' : ''}" data-sheet-panel="${index}">
        <div class="sheet-panel-header">工作表：${sheetName}</div>
        <div class="sheet-table-wrap">${html}</div>
      </section>
    `;
  }).join('');

  if (xlsxContainer.value) {
    xlsxContainer.value.innerHTML = `
      <div class="xlsx-preview-shell">
        <div class="xlsx-preview-header">
          <div class="xlsx-preview-title">${getFilename(file)}</div>
          <div class="xlsx-preview-meta">共 ${workbook.SheetNames.length} 个工作表</div>
        </div>
        <div class="sheet-tabs">${tabs}</div>
        <div class="sheet-panels">${panels}</div>
      </div>
    `;

    xlsxContainer.value.querySelectorAll('[data-sheet-tab]').forEach((tab) => {
      tab.addEventListener('click', () => {
        const index = tab.getAttribute('data-sheet-tab');
        xlsxContainer.value.querySelectorAll('[data-sheet-tab]').forEach((item) => {
          item.classList.toggle('is-active', item === tab);
        });
        xlsxContainer.value.querySelectorAll('[data-sheet-panel]').forEach((panel) => {
          panel.classList.toggle('is-active', panel.getAttribute('data-sheet-panel') === index);
        });
      });
    });

    xlsxContainer.value.querySelectorAll('table').forEach((table) => {
      table.classList.add('excel-sheet-table');
      table.querySelectorAll('td, th').forEach((cell) => {
        cell.classList.add('excel-sheet-cell');
      });
    });
  }
};

const renderCode = (code, filename) => {
  if (monacoContainer.value) {
    const ext = getExtension({ originalFilename: filename });
    const langMap = {
      js: 'javascript',
      ts: 'typescript',
      jsx: 'javascript',
      tsx: 'typescript',
      py: 'python',
      java: 'java',
      html: 'html',
      htm: 'html',
      css: 'css',
      scss: 'scss',
      less: 'less',
      json: 'json',
      xml: 'xml',
      sql: 'sql',
      md: 'markdown',
      markdown: 'markdown',
      yml: 'yaml',
      yaml: 'yaml',
      sh: 'shell',
      bat: 'bat',
      ps1: 'powershell',
      cpp: 'cpp',
      c: 'c',
      h: 'cpp',
      hpp: 'cpp',
      go: 'go',
      rs: 'rust',
      php: 'php',
      vue: 'html',
      rb: 'ruby',
      kt: 'kotlin',
      swift: 'swift',
      dockerfile: 'dockerfile'
    };
    const language = langMap[ext] || 'plaintext';

    if (monacoEditor) {
      monacoEditor.setValue(code);
      monaco.editor.setModelLanguage(monacoEditor.getModel(), language);
    } else {
      monacoEditor = monaco.editor.create(monacoContainer.value, {
        value: code,
        language,
        readOnly: true,
        theme: 'vs-light',
        automaticLayout: true,
        minimap: { enabled: false },
        wordWrap: 'on',
        scrollBeyondLastLine: false
      });
    }
  }
};

onMounted(() => {
  loadPreviewConfig();
});

onUnmounted(() => {
  cleanup();
});
</script>

<style scoped>
.media-container {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
  background: #f8f9fa;
  border-radius: 8px;
  width: 100%;
  box-sizing: border-box;
}

:deep(.xlsx-preview-shell) {
  min-height: 100%;
  background: linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%);
}

:deep(.xlsx-preview-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  position: sticky;
  top: 0;
  z-index: 2;
}

:deep(.xlsx-preview-title) {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

:deep(.xlsx-preview-meta) {
  font-size: 13px;
  color: #6b7280;
}

:deep(.sheet-tabs) {
  display: flex;
  gap: 10px;
  padding: 14px 20px 0;
  flex-wrap: wrap;
}

:deep(.sheet-tab) {
  border: 1px solid #c7d2fe;
  background: #eef2ff;
  color: #4338ca;
  border-radius: 999px;
  padding: 8px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

:deep(.sheet-tab.is-active) {
  background: #4338ca;
  color: #fff;
  border-color: #4338ca;
  box-shadow: 0 10px 20px rgba(67, 56, 202, 0.18);
}

:deep(.sheet-panels) {
  padding: 16px 20px 20px;
}

:deep(.sheet-panel) {
  display: none;
}

:deep(.sheet-panel.is-active) {
  display: block;
}

:deep(.sheet-panel-header) {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 12px;
}

:deep(.sheet-table-wrap) {
  overflow: auto;
  background: #fff;
  border: 1px solid #dbe2ea;
  border-radius: 12px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
}

:deep(.excel-sheet-table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

:deep(.excel-sheet-table tr:nth-child(even)) {
  background: #f8fafc;
}

:deep(.excel-sheet-table th) {
  position: sticky;
  top: 0;
  background: #e0e7ff;
  color: #312e81;
  z-index: 1;
}

:deep(.excel-sheet-cell) {
  border: 1px solid #dbe2ea;
  padding: 8px 10px;
  white-space: nowrap;
}
.custom-audio-player {
  width: 100%;
  outline: none;
}
.custom-video-player {
  outline: none;
  /* width, height handled by inline style */
}
.audio-wrapper {
  flex-direction: column;
}
.audio-card {
  background: white;
  padding: 30px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  text-align: center;
  width: 300px;
}
.audio-cover-art {
  width: 100px;
  height: 100px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  margin: 0 auto 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.audio-info h3 {
  margin: 0 0 5px;
  font-size: 18px;
  color: #303133;
}
.audio-info p {
  margin: 0 0 20px;
  color: #909399;
  font-size: 14px;
}
</style>
