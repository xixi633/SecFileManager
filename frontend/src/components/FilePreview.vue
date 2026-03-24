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
      <!-- 文件过大提示（>2GB） -->
      <div v-if="file?.fileSize > MAX_PREVIEW_SIZE" style="padding: 100px 0; text-align: center;">
        <el-icon :size="100" color="#E6A23C"><Warning /></el-icon>
        <p style="color: #606266; margin-top: 30px; font-size: 18px;">文件过大（超过2GB），暂不支持在线预览</p>
        <p style="color: #909399; font-size: 14px;">请下载到本地查看</p>
        <el-button type="primary" @click="$emit('download', file)" style="margin-top: 20px;">
          下载文件
        </el-button>
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
          <el-button type="primary" @click="$emit('download', file)">下载完整文件</el-button>
        </div>
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
import { ref, watch, nextTick, onUnmounted } from 'vue';
import { Document, Headset, Warning, Download } from '@element-plus/icons-vue';
import { renderAsync } from 'docx-preview';
import * as pdfjsLib from 'pdfjs-dist';
import * as XLSX from 'xlsx';
import * as monaco from 'monaco-editor';
import api from '../api/index.js';

// Worker setup safely
try {
  if (pdfjsLib && pdfjsLib.GlobalWorkerOptions) {
    pdfjsLib.GlobalWorkerOptions.workerSrc = '/pdf.worker.min.mjs';
  }
} catch (e) {
  console.warn('PDF.js worker init failed:', e);
}

// ============ 文件大小阈值常量 ============
const MAX_PREVIEW_SIZE = 2147483648;            // 2GB - 超过此大小不支持预览
const SMALL_FILE_THRESHOLD = 512 * 1024 * 1024; // 512MB - 小于等于此大小完整加载到内存
const PARTIAL_PREVIEW_SIZE = 1024 * 1024;        // 1MB - 大文件文本预览的最大读取量

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
let monacoEditor = null;
let currentBlobUrl = null;

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

const formatBytes = (bytes, decimals = 2) => {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
};

// ============ 文件类型判断 ============

const isImageFile = (file) => /\.(jpg|jpeg|png|gif|bmp|webp|svg)$/i.test(file?.originalFilename || '');
const isVideoFile = (file) => /\.(mp4|webm|ogg|mov)$/i.test(file?.originalFilename || '');
const isAudioFile = (file) => /\.(mp3|wav|flac|aac)$/i.test(file?.originalFilename || '');
const isPdfFile = (file) => /\.(pdf)$/i.test(file?.originalFilename || '');
const isWordFile = (file) => /\.(docx)$/i.test(file?.originalFilename || '');
const isExcelFile = (file) => /\.(xlsx|xls)$/i.test(file?.originalFilename || '');
const isCodeFile = (file) => /\.(js|ts|java|py|html|css|json|xml|sql|md|yml|yaml|c|cpp|h|go|rs|php)$/i.test(file?.originalFilename || '');
const isTextFile = (file) => /\.(txt|log|ini|conf)$/i.test(file?.originalFilename || '');

/**
 * 判断是否为大文件（>512MB）
 */
const isLargeFile = (file) => (file?.fileSize || 0) > SMALL_FILE_THRESHOLD;

const onMediaLoadError = (e) => {
  console.error("Media load error", e);
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
};

watch(() => props.visible, async (val) => {
  if (val && props.file) {
    if (props.file.fileSize > MAX_PREVIEW_SIZE) {
      return; 
    }
    await nextTick();
    loadPreview(props.file);
  } else {
    cleanup();
  }
});

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
    // 图片/视频/音频：由模板src属性直接处理（URL流式加载，支持Range）
    if (isImageFile(file) || isVideoFile(file) || isAudioFile(file)) {
      return;
    }

    const large = isLargeFile(file);

    // 大文件（>512MB）文本/代码：使用Range请求只加载前1MB
    if (large && (isTextFile(file) || isCodeFile(file))) {
      await loadPartialTextPreview(file);
      return;
    }

    // 大文件的PDF/Word/Excel/其他：由模板直接显示下载提示，不需加载
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
    } else if (isWordFile(file)) {
      renderWord(blob);
    } else if (isExcelFile(file)) {
      renderExcel(blob);
    } else if (isCodeFile(file) || isTextFile(file)) {
      const text = await blob.text();
      if (isCodeFile(file)) {
        renderCode(text, file.originalFilename);
      } else {
        textContent.value = text;
      }
    }
  } catch (e) {
    console.error("Preview failed", e);
  }
};

/**
 * 大文件文本/代码部分预览
 * 使用HTTP Range请求只获取文件前1MB内容，避免完整加载到内存
 */
const loadPartialTextPreview = async (file) => {
  try {
    const url = getPreviewUrl(file);
    const res = await fetch(url, {
      headers: {
        'Range': `bytes=0-${PARTIAL_PREVIEW_SIZE - 1}`
      }
    });

    if (!res.ok && res.status !== 206) {
      throw new Error(`Range request failed: ${res.status}`);
    }

    const buffer = await res.arrayBuffer();
    // 使用TextDecoder处理可能被截断的多字节UTF-8字符
    const decoder = new TextDecoder('utf-8', { fatal: false });
    const text = decoder.decode(buffer);

    await nextTick();

    if (isCodeFile(file)) {
      renderCode(text, file.originalFilename);
    } else {
      textContent.value = text;
    }
  } catch (e) {
    console.error("Partial text preview failed", e);
    textContent.value = '预览加载失败，请尝试下载查看。';
  }
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

const renderExcel = async (blob) => {
  const data = await blob.arrayBuffer();
  const workbook = XLSX.read(data);
  const firstSheetName = workbook.SheetNames[0];
  const worksheet = workbook.Sheets[firstSheetName];
  const html = XLSX.utils.sheet_to_html(worksheet);
  if (xlsxContainer.value) {
    xlsxContainer.value.innerHTML = html;
    const table = xlsxContainer.value.querySelector('table');
    if (table) {
      table.style.borderCollapse = 'collapse';
      table.style.width = '100%';
      table.querySelectorAll('td, th').forEach(cell => {
        cell.style.border = '1px solid #ddd';
        cell.style.padding = '8px';
      });
    }
  }
};

const renderCode = (code, filename) => {
  if (monacoContainer.value) {
    const ext = filename.split('.').pop().toLowerCase();
    const langMap = {
      js: 'javascript', ts: 'typescript', py: 'python', java: 'java', 
      html: 'html', css: 'css', json: 'json', xml: 'xml', sql: 'sql', md: 'markdown'
    };
    const language = langMap[ext] || 'plaintext';

    if (monacoEditor) {
      monacoEditor.setValue(code);
      monaco.editor.setModelLanguage(monacoEditor.getModel(), language);
    } else {
      monacoEditor = monaco.editor.create(monacoContainer.value, {
        value: code,
        language: language,
        readOnly: true,
        theme: 'vs-light',
        automaticLayout: true
      });
    }
  }
};

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
