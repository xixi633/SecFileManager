<template>
  <div class="ai-chat-float">
    <div class="ai-chat-btn" @click="toggleChat" :class="{ 'has-unread': hasUnread }">
      <el-icon :size="24"><ChatDotRound /></el-icon>
    </div>

    <transition name="ai-chat-slide">
      <div v-if="isOpen" class="ai-chat-panel">
        <div class="ai-chat-header">
          <span>AI 助手</span>
          <el-icon class="ai-chat-close" @click="toggleChat"><Close /></el-icon>
        </div>

        <div class="ai-chat-messages" ref="messagesRef">
          <div v-for="(msg, idx) in chatMessages" :key="idx" class="ai-msg" :class="'ai-msg-' + msg.role">
            <div v-if="msg.role === 'assistant'" class="ai-msg-avatar">
              <el-icon :size="18"><Monitor /></el-icon>
            </div>
            <div class="ai-msg-bubble">
              <div v-if="msg.loading" class="ai-typing">
                <span></span><span></span><span></span>
              </div>
              <template v-else>
                <div v-if="msg.content" class="ai-msg-text" v-html="renderMarkdown(msg.content)"></div>
                <div v-if="msg.files && msg.files.length > 0" class="ai-file-card">
                  <div class="ai-file-card-header">
                    <el-icon :size="14"><Folder /></el-icon>
                    <span>找到 {{ msg.total }} 个文件</span>
                    <el-tag v-if="msg.searchLabel" size="small" effect="dark" class="ai-card-type-tag">{{ msg.searchLabel }}</el-tag>
                  </div>
                  <div v-for="(file, fIdx) in msg.files" :key="fIdx" class="ai-file-item">
                    <el-icon :size="16" class="ai-file-icon" :style="{ color: getCategoryColor(file.category) }">
                      <component :is="getFileIcon(file)" />
                    </el-icon>
                    <div class="ai-file-info">
                      <div class="ai-file-name-row">
                        <span class="ai-file-name">{{ file.originalFilename }}</span>
                        <el-tag size="small" effect="plain" :type="getCategoryTagType(file.category)" class="ai-file-category-tag">{{ file.categoryLabel }}</el-tag>
                      </div>
                      <span class="ai-file-meta">{{ formatSize(file.fileSize) }}</span>
                    </div>
                  </div>
                </div>
                <div v-if="msg.searching" class="ai-searching">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  <span>正在搜索...</span>
                </div>
                <div v-if="msg.noResult" class="ai-no-result">
                  没有找到匹配的文件
                </div>
              </template>
            </div>
            <div v-if="msg.role === 'user'" class="ai-msg-avatar ai-msg-avatar-user">
              <el-icon :size="18"><User /></el-icon>
            </div>
          </div>
        </div>

        <div class="ai-chat-input">
          <el-input
            v-model="inputText"
            placeholder="问我任何问题..."
            @keyup.enter="sendMessage"
            :disabled="isSending"
            clearable
          >
            <template #append>
              <el-button @click="sendMessage" :loading="isSending" :disabled="!inputText.trim()">
                <el-icon><Promotion /></el-icon>
              </el-button>
            </template>
          </el-input>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue';
import { ChatDotRound, Close, Monitor, User, Promotion, Folder, Document, Picture, VideoCamera, Headset, Files, Notebook, Loading } from '@element-plus/icons-vue';
import { streamAiChat } from '../api/ai.js';
import { fetchFileList } from '../api/file.js';
import { getFileTypeCategory, getFileTypeLabel } from '../utils/fileType.js';

const isOpen = ref(false);
const hasUnread = ref(false);
const inputText = ref('');
const isSending = ref(false);
const chatMessages = ref([]);
const messagesRef = ref(null);

onMounted(() => {
  chatMessages.value.push({
    role: 'assistant',
    content: '你好！我是 AI 助手，可以帮你查找文件或回答问题。试试说"帮我找Java文件"吧～',
    files: null,
    total: 0,
    searchLabel: '',
    loading: false,
    searching: false,
    noResult: false
  });
});

function toggleChat() {
  isOpen.value = !isOpen.value;
  if (isOpen.value) {
    hasUnread.value = false;
    nextTick(() => scrollToBottom());
  }
}

function sendMessage() {
  const text = inputText.value.trim();
  if (!text || isSending.value) return;

  chatMessages.value.push({ role: 'user', content: text, files: null, total: 0, searchLabel: '', loading: false, searching: false, noResult: false });
  inputText.value = '';
  isSending.value = true;

  const assistantIdx = chatMessages.value.length;
  chatMessages.value.push({ role: 'assistant', content: '', files: null, total: 0, searchLabel: '', loading: true, searching: false, noResult: false });
  scrollToBottom();

  const messages = chatMessages.value
    .filter(m => !m.loading && m.content)
    .map(m => ({ role: m.role, content: m.content }));

  streamAiChat(
    messages,
    (chunk) => {
      chatMessages.value[assistantIdx].loading = false;
      chatMessages.value[assistantIdx].content += chunk;
      scrollToBottom();
    },
    async (searchParams) => {
      chatMessages.value[assistantIdx].loading = false;
      chatMessages.value[assistantIdx].searching = true;
      scrollToBottom();

      try {
        const filters = {};
        if (searchParams.keyword) filters.keyword = searchParams.keyword;
        if (searchParams.typeCategory) filters.typeCategory = searchParams.typeCategory;

        const label = searchParams.keyword || (searchParams.typeCategory ? getCategoryLabel(searchParams.typeCategory) : '');
        chatMessages.value[assistantIdx].searchLabel = label;

        const res = await fetchFileList(1, 20, filters);
        const data = res?.data?.data;

        chatMessages.value[assistantIdx].searching = false;

        if (data.records && data.records.length > 0) {
          const filesWithType = data.records.map(f => {
            const category = getFileTypeCategory(f);
            const categoryLabel = getFileTypeLabel(f);
            return { ...f, category, categoryLabel };
          });
          chatMessages.value[assistantIdx].files = filesWithType;
          chatMessages.value[assistantIdx].total = data.total || filesWithType.length;
          chatMessages.value[assistantIdx].content = `找到 ${data.total || filesWithType.length} 个文件`;
        } else {
          chatMessages.value[assistantIdx].noResult = true;
          chatMessages.value[assistantIdx].content = '没有找到匹配的文件，换个关键词试试？';
        }
      } catch (e) {
        chatMessages.value[assistantIdx].searching = false;
        chatMessages.value[assistantIdx].content = '搜索出错了，请稍后再试。';
      }

      isSending.value = false;
      scrollToBottom();
    },
    () => {
      chatMessages.value[assistantIdx].loading = false;
      chatMessages.value[assistantIdx].searching = false;
      isSending.value = false;
      if (!isOpen.value) hasUnread.value = true;
      scrollToBottom();
    },
    (err) => {
      chatMessages.value[assistantIdx].loading = false;
      chatMessages.value[assistantIdx].searching = false;
      chatMessages.value[assistantIdx].content = '抱歉，连接出了点问题，请稍后再试。';
      isSending.value = false;
      scrollToBottom();
    }
  );
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight;
    }
  });
}

function renderMarkdown(text) {
  if (!text) return '';
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>');
}

function formatSize(bytes) {
  if (!bytes || bytes <= 0) return '0B';
  const units = ['B', 'KB', 'MB', 'GB'];
  let i = 0;
  let size = bytes;
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++; }
  return size.toFixed(1) + units[i];
}

function getCategoryLabel(cat) {
  const map = { folder: '目录', image: '图片', video: '视频', audio: '音频', text: '文本', code: '代码', archive: '压缩包', document: '文档', other: '其他' };
  return map[cat] || cat;
}

function getFileIcon(file) {
  const cat = file.category || 'other';
  switch (cat) {
    case 'image': return Picture;
    case 'video': return VideoCamera;
    case 'audio': return Headset;
    case 'folder': return Files;
    case 'document': return Notebook;
    case 'archive': return Folder;
    default: return Document;
  }
}

function getCategoryColor(category) {
  const map = { image: '#e6a23c', video: '#f56c6c', audio: '#67c23a', code: '#409eff', text: '#909399', archive: '#e6a23c', document: '#409eff', folder: '#e6a23c', other: '#909399' };
  return map[category] || '#909399';
}

function getCategoryTagType(category) {
  const map = { image: 'warning', video: 'danger', audio: 'success', code: '', text: 'info', archive: 'warning', document: '', folder: 'warning', other: 'info' };
  return map[category] || 'info';
}
</script>

<style scoped>
.ai-chat-float {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 9999;
}

.ai-chat-btn {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.4);
  transition: transform 0.2s, box-shadow 0.2s;
}

.ai-chat-btn:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.5);
}

.ai-chat-btn.has-unread::after {
  content: '';
  position: absolute;
  top: 2px;
  right: 2px;
  width: 10px;
  height: 10px;
  background: #f56c6c;
  border-radius: 50%;
  border: 2px solid #fff;
}

.ai-chat-panel {
  position: absolute;
  bottom: 64px;
  right: 0;
  width: 380px;
  height: 520px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ai-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.ai-chat-close {
  cursor: pointer;
  font-size: 18px;
  transition: transform 0.2s;
}

.ai-chat-close:hover {
  transform: scale(1.2);
}

.ai-chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: #f5f7fa;
}

.ai-msg {
  display: flex;
  align-items: flex-start;
  margin-bottom: 12px;
  gap: 8px;
}

.ai-msg-user {
  flex-direction: row-reverse;
}

.ai-msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ai-msg-avatar-user {
  background: #67c23a;
}

.ai-msg-bubble {
  max-width: 280px;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}

.ai-msg-assistant .ai-msg-bubble {
  background: #fff;
  color: #303133;
  border-top-left-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.ai-msg-user .ai-msg-bubble {
  background: #409eff;
  color: #fff;
  border-top-right-radius: 4px;
}

.ai-msg-text {
  white-space: pre-wrap;
}

.ai-typing {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}

.ai-typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c0c4cc;
  animation: ai-typing-bounce 1.2s infinite;
}

.ai-typing span:nth-child(2) { animation-delay: 0.2s; }
.ai-typing span:nth-child(3) { animation-delay: 0.4s; }

@keyframes ai-typing-bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-6px); }
}

.ai-searching {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #409eff;
  font-size: 13px;
}

.ai-no-result {
  color: #909399;
  font-size: 13px;
}

.ai-file-card {
  background: #f0f7ff;
  border: 1px solid #d0e3ff;
  border-radius: 8px;
  margin-top: 8px;
  overflow: hidden;
}

.ai-file-card-header {
  background: #409eff;
  color: white;
  padding: 6px 12px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.ai-card-type-tag {
  margin-left: auto;
  background: rgba(255, 255, 255, 0.25);
  border: none;
  color: #fff;
}

.ai-file-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid #e8e8e8;
}

.ai-file-item:last-child {
  border-bottom: none;
}

.ai-file-icon {
  margin-right: 8px;
  flex-shrink: 0;
}

.ai-file-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.ai-file-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ai-file-name {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.ai-file-category-tag {
  flex-shrink: 0;
  font-size: 11px;
  height: 18px;
  padding: 0 4px;
  line-height: 16px;
}

.ai-file-meta {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.ai-chat-input {
  padding: 10px 12px;
  border-top: 1px solid #ebeef5;
  background: #fff;
}

.ai-chat-input :deep(.el-input-group__append) {
  padding: 0 12px;
}

.ai-chat-slide-enter-active,
.ai-chat-slide-leave-active {
  transition: all 0.3s ease;
}

.ai-chat-slide-enter-from,
.ai-chat-slide-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}
</style>
