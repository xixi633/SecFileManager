<template>
  <div class="chat-page">
    <aside class="chat-sidebar">
      <div class="sidebar-header">
        <h3>聊天</h3>
        <el-button type="primary" size="small" @click="openAddDialog">添加好友</el-button>
      </div>

      <el-tabs v-model="activeTab" class="chat-tabs">
        <el-tab-pane label="会话" name="sessions">
          <div class="tab-toolbar">
            <el-input
              v-model="sessionKeyword"
              size="small"
              clearable
              placeholder="搜索会话"
            />
            <el-button
              text
              type="primary"
              :disabled="sessions.length === 0"
              @click="markAllSessionAsRead"
            >
              全部已读
            </el-button>
          </div>
          <el-scrollbar height="calc(100vh - 274px)">
            <div
              v-for="item in filteredSessions"
              :key="item.sessionId"
              class="session-item"
              :class="{ active: activeSessionId === item.sessionId }"
              @click="selectSession(item)"
            >
              <el-avatar :size="34" :src="avatarByUserId(item.friendUserId)" />
              <div class="session-main">
                <div class="session-title">{{ displayNameOf(item) }}</div>
                <div class="session-preview">{{ item.lastMessagePreview || '暂无消息' }}</div>
              </div>

              <div class="session-actions">
                <el-tag
                  v-if="isSessionPinned(item.sessionId)"
                  size="small"
                  effect="plain"
                  type="warning"
                >
                  置顶
                </el-tag>
                <el-button
                  link
                  type="primary"
                  class="session-pin-btn"
                  @click.stop="togglePinSession(item.sessionId)"
                >
                  {{ isSessionPinned(item.sessionId) ? '取消置顶' : '置顶' }}
                </el-button>
                <el-badge v-if="item.unreadCount > 0" :value="item.unreadCount" class="session-badge" />
              </div>
            </div>
            <el-empty v-if="filteredSessions.length === 0" description="暂无会话" :image-size="90" />
          </el-scrollbar>
        </el-tab-pane>

        <el-tab-pane label="好友" name="friends">
          <el-scrollbar height="calc(100vh - 230px)">
            <div v-for="friend in friends" :key="friend.userId" class="friend-item">
              <el-avatar :size="34" :src="avatarByUserId(friend.userId)" />
              <div class="friend-meta" @click="openChatByFriend(friend)">
                <div class="friend-name">{{ displayNameOf(friend) }}</div>
                <div class="friend-username">@{{ friend.username }}</div>
              </div>
              <div class="friend-actions">
                <el-button type="primary" link @click="openChatByFriend(friend)">聊天</el-button>
                <el-button type="warning" link @click="openRemarkDialog(friend)">备注</el-button>
                <el-button type="danger" link @click="removeFriendAction(friend)">删除</el-button>
              </div>
            </div>
            <el-empty v-if="friends.length === 0" description="暂无好友" :image-size="90" />
          </el-scrollbar>
        </el-tab-pane>

        <el-tab-pane label="申请" name="requests">
          <div class="tab-toolbar">
            <div class="request-summary">待处理 {{ friendRequestCount }} 条</div>
            <el-button
              text
              type="primary"
              :disabled="processedRequestCount <= 0"
              @click="clearProcessedRequests"
            >
              清理已处理记录
            </el-button>
          </div>
          <el-scrollbar height="calc(100vh - 274px)">
            <div v-for="req in visibleIncomingRequests" :key="req.requestId" class="request-item">
              <el-avatar :size="34" :src="avatarByUserId(req.fromUser?.userId)" />
              <div class="request-text">
                <div class="request-name">{{ displayNameOf(req.fromUser) }}</div>
                <div class="request-message">{{ req.message || '想添加你为好友' }}</div>
              </div>
              <div v-if="req.status === 0" class="request-actions">
                <el-button type="primary" link @click="acceptRequest(req.requestId)">同意</el-button>
                <el-button type="danger" link @click="rejectRequest(req.requestId)">拒绝</el-button>
              </div>
              <div v-else class="request-status">
                {{ req.status === 1 ? '已同意' : '已拒绝' }}
              </div>
            </div>
            <el-empty v-if="visibleIncomingRequests.length === 0" description="暂无申请" :image-size="90" />
          </el-scrollbar>
        </el-tab-pane>
      </el-tabs>
    </aside>

    <main class="chat-main">
      <div v-if="!activeSessionId" class="chat-empty">
        <el-empty description="请选择好友开始聊天" />
      </div>

      <template v-else>
        <div class="chat-header">
          <el-avatar :size="38" :src="activeFriendAvatar" />
          <div class="chat-header-text">
            <div class="chat-title">{{ activeFriendName }}</div>
            <div class="chat-subtitle">实时通道：{{ wsConnected ? '已连接' : '未连接' }}</div>
          </div>
        </div>

        <el-scrollbar ref="messageScrollbarRef" class="message-list">
          <div v-for="msg in messages" :key="msg.id" class="message-row" :class="{ own: msg.own }">
            <el-avatar v-if="!msg.own" :size="32" :src="activeFriendAvatar" class="message-avatar" />
            <div class="message-bubble" :class="{ own: msg.own }">
              <template v-if="msg.messageType === 'file'">
                <div class="file-card">
                  <div class="file-title">{{ msg.fileName || `文件 #${msg.fileId}` }}</div>
                  <div class="file-size">{{ formatFileSize(msg.fileSize) }}</div>
                  <div class="file-actions">
                    <el-button size="small" @click="downloadFile(msg)">下载</el-button>
                    <el-button
                      size="small"
                      type="primary"
                      :loading="isSavingToLibrary(msg.id)"
                      @click="saveChatFileToLibrary(msg)"
                    >
                      保存到文件列表
                    </el-button>
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="message-text">{{ msg.content }}</div>
              </template>
              <div class="message-time">
                {{ formatTime(msg.sentAt) }}
                <span v-if="msg.own">{{ msg.read ? ' 已读' : ' 未读' }}</span>
              </div>
            </div>
            <el-avatar v-if="msg.own" :size="32" :src="avatarUrl" class="message-avatar" />
          </div>
        </el-scrollbar>

        <div class="chat-input-bar">
          <el-input
            v-model="messageInput"
            type="textarea"
            :rows="3"
            placeholder="回车发送，Ctrl+Enter 换行"
            @keydown.enter.exact.prevent="sendText"
          />
          <div class="chat-input-actions">
            <el-button @click="pickFile">上传并发送</el-button>
            <el-button @click="openFileLibraryDialog">从文件列表发送</el-button>
            <el-button type="primary" @click="sendText">发送</el-button>
          </div>
          <input ref="fileInputRef" type="file" style="display: none" @change="onFilePicked" />
        </div>
      </template>
    </main>

    <el-dialog v-model="addDialogVisible" title="添加好友" width="520px">
      <div class="add-friend-panel">
        <el-input v-model="searchKeyword" placeholder="按用户名或昵称搜索" @keyup.enter="runSearchUser">
          <template #append>
            <el-button @click="runSearchUser">搜索</el-button>
          </template>
        </el-input>

        <div class="search-result-list">
          <div v-for="user in searchResult" :key="user.userId" class="search-result-item">
            <div class="search-result-left">
              <el-avatar :size="34" :src="avatarByUserId(user.userId)" />
              <div>
                <div class="name">{{ displayNameOf(user) }}</div>
                <div class="username">@{{ user.username }}</div>
              </div>
            </div>
            <el-button size="small" type="primary" @click="requestAddFriend(user)">添加</el-button>
          </div>
          <el-empty v-if="searchResult.length === 0" description="暂无结果" :image-size="80" />
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="remarkDialogVisible" title="设置备注" width="420px">
      <el-input
        v-model="remarkInput"
        maxlength="100"
        show-word-limit
        placeholder="留空将清除备注"
      />
      <template #footer>
        <el-button @click="remarkDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRemark">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="libraryDialogVisible" title="发送已有文件" width="760px">
      <el-table :data="libraryFiles" v-loading="libraryLoading" size="small" style="width: 100%">
        <el-table-column prop="originalFilename" label="文件名" min-width="260" />
        <el-table-column label="大小" width="140">
          <template #default="scope">{{ formatFileSize(scope.row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="上传时间" width="180">
          <template #default="scope">{{ formatTime(scope.row.uploadTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="scope">
            <el-button type="primary" size="small" @click="sendExistingFile(scope.row)">发送</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="library-pagination">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page="libraryPagination.page"
          :page-size="libraryPagination.size"
          :total="libraryPagination.total"
          @current-change="onLibraryPageChange"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import api from '../api/index.js';
import { useUser } from '../composables/useUser.js';
import {
  acceptFriendRequest,
  downloadChatFile,
  listFriends,
  listIncomingFriendRequests,
  listMessages,
  listSessions,
  markMessagesRead,
  openSession,
  rejectFriendRequest,
  removeFriend,
  searchUsers,
  sendFileMessage,
  sendFriendRequest,
  sendTextMessage,
  updateFriendRemark
} from '../api/chat.js';
import { fetchFileList } from '../api/file.js';
import { createFileUploadTask, enqueueUploadTasks, validateUploadFile } from '../store/uploadQueue.js';
import { setChatUnreadCount, setFriendRequestCount } from '../store/messageCenter.js';

const PINNED_SESSION_STORAGE_KEY = 'chatPinnedSessionIds_v1';
const HIDDEN_REQUEST_STORAGE_KEY = 'chatHiddenRequestIds_v1';

const activeTab = ref('sessions');
const route = useRoute();
const sessionKeyword = ref('');
const pinnedSessionIds = ref([]);
const hiddenRequestIds = ref([]);
const friends = ref([]);
const sessions = ref([]);
const incomingRequests = ref([]);
const messages = ref([]);

const activeSessionId = ref(null);
const activeFriend = ref(null);
const messageInput = ref('');
const messageScrollbarRef = ref(null);
const fileInputRef = ref(null);

const addDialogVisible = ref(false);
const searchKeyword = ref('');
const searchResult = ref([]);

const remarkDialogVisible = ref(false);
const remarkInput = ref('');
const remarkTargetFriend = ref(null);

const libraryDialogVisible = ref(false);
const libraryLoading = ref(false);
const libraryFiles = ref([]);
const libraryPagination = ref({ page: 1, size: 8, total: 0 });

const wsRef = ref(null);
const wsConnected = ref(false);
let wsReconnectTimer = null;
const savingToLibraryMap = ref({});

const { avatarUrl } = useUser('user');
const avatarVersion = String(Date.now());
const pinnedSessionIdSet = computed(() => new Set(pinnedSessionIds.value));
const processedRequestCount = computed(() => incomingRequests.value.filter((item) => Number(item?.status) !== 0).length);

const visibleIncomingRequests = computed(() => {
  const hiddenSet = new Set(hiddenRequestIds.value);
  return incomingRequests.value.filter((item) => Number(item?.status) === 0 || !hiddenSet.has(String(item?.requestId || '')));
});

const sortedSessions = computed(() => {
  const rows = [...sessions.value];
  rows.sort((a, b) => {
    const aPinned = pinnedSessionIdSet.value.has(String(a?.sessionId || ''));
    const bPinned = pinnedSessionIdSet.value.has(String(b?.sessionId || ''));
    if (aPinned !== bPinned) {
      return aPinned ? -1 : 1;
    }
    return getSessionSortTimestamp(b) - getSessionSortTimestamp(a);
  });
  return rows;
});

const filteredSessions = computed(() => {
  const keyword = sessionKeyword.value.trim().toLowerCase();
  if (!keyword) return sortedSessions.value;

  return sortedSessions.value.filter((item) => {
    const text = [
      displayNameOf(item),
      item?.friendUsername || '',
      item?.lastMessagePreview || ''
    ]
      .join(' ')
      .toLowerCase();
    return text.includes(keyword);
  });
});

const activeFriendName = computed(() => displayNameOf(activeFriend.value));
const activeFriendAvatar = computed(() => {
  if (!activeFriend.value?.friendUserId) {
    return '';
  }
  return avatarByUserId(activeFriend.value.friendUserId);
});

onMounted(async () => {
  loadPinnedSessionIds();
  loadHiddenRequestIds();
  await Promise.all([loadFriends(), loadIncomingRequests(), loadSessions()]);
  connectWs();
});

watch(
  () => route.query.tab,
  () => {
    syncActiveTabByRoute();
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  if (wsReconnectTimer) {
    window.clearTimeout(wsReconnectTimer);
    wsReconnectTimer = null;
  }
  if (wsRef.value) {
    wsRef.value.close();
    wsRef.value = null;
  }
});

function displayNameOf(entity) {
  if (!entity) return '聊天';
  return entity.friendRemark || entity.remark || entity.friendNickname || entity.nickname || entity.friendUsername || entity.username || '未知用户';
}

function buildApiBaseUrl() {
  let base = (api.defaults.baseURL || '/api').replace(/\/$/, '');
  if (base.startsWith('/')) {
    base = `${window.location.protocol}//${window.location.host}${base}`;
  }
  return base;
}

function avatarByUserId(userId) {
  if (!userId) return '';
  const token = localStorage.getItem('token') || '';
  const base = buildApiBaseUrl();
  return `${base}/user/avatar/${userId}?token=${encodeURIComponent(token)}&v=${avatarVersion}`;
}

async function loadFriends() {
  const res = await listFriends();
  friends.value = res?.data?.data || [];
}

async function loadIncomingRequests() {
  const res = await listIncomingFriendRequests();
  incomingRequests.value = res?.data?.data || [];
  const pendingCount = incomingRequests.value.filter((item) => Number(item?.status) === 0).length;
  setFriendRequestCount(pendingCount);
  pruneHiddenRequestIds();
}

function getSessionSortTimestamp(session) {
  const rawTime =
    session?.lastMessageTime ||
    session?.lastMessageAt ||
    session?.updateTime ||
    session?.updatedAt;
  if (rawTime) {
    const ts = new Date(rawTime).getTime();
    if (!Number.isNaN(ts)) return ts;
  }
  const fallback = Number(session?.sessionId || 0);
  return Number.isFinite(fallback) ? fallback : 0;
}

function loadPinnedSessionIds() {
  try {
    const raw = localStorage.getItem(PINNED_SESSION_STORAGE_KEY);
    const parsed = JSON.parse(raw || '[]');
    pinnedSessionIds.value = Array.isArray(parsed) ? parsed.map((id) => String(id)) : [];
  } catch (_) {
    pinnedSessionIds.value = [];
  }
}

function savePinnedSessionIds() {
  localStorage.setItem(PINNED_SESSION_STORAGE_KEY, JSON.stringify(pinnedSessionIds.value));
}

function loadHiddenRequestIds() {
  try {
    const raw = localStorage.getItem(HIDDEN_REQUEST_STORAGE_KEY);
    const parsed = JSON.parse(raw || '[]');
    hiddenRequestIds.value = Array.isArray(parsed) ? parsed.map((id) => String(id)) : [];
  } catch (_) {
    hiddenRequestIds.value = [];
  }
}

function saveHiddenRequestIds() {
  localStorage.setItem(HIDDEN_REQUEST_STORAGE_KEY, JSON.stringify(hiddenRequestIds.value));
}

function pruneHiddenRequestIds() {
  const currentIds = new Set(incomingRequests.value.map((item) => String(item?.requestId || '')));
  hiddenRequestIds.value = hiddenRequestIds.value.filter((id) => currentIds.has(id));
  saveHiddenRequestIds();
}

function isSessionPinned(sessionId) {
  return pinnedSessionIdSet.value.has(String(sessionId));
}

function togglePinSession(sessionId) {
  const targetId = String(sessionId || '');
  if (!targetId) return;
  if (pinnedSessionIdSet.value.has(targetId)) {
    pinnedSessionIds.value = pinnedSessionIds.value.filter((id) => id !== targetId);
    ElMessage.success('已取消置顶');
  } else {
    pinnedSessionIds.value = [targetId, ...pinnedSessionIds.value.filter((id) => id !== targetId)];
    ElMessage.success('会话已置顶');
  }
  savePinnedSessionIds();
}

function syncActiveTabByRoute() {
  const tab = String(route.query.tab || '');
  if (tab === 'sessions' || tab === 'friends' || tab === 'requests') {
    activeTab.value = tab;
  }
}

async function loadSessions() {
  const res = await listSessions();
  sessions.value = res?.data?.data || [];
  const totalUnread = sessions.value.reduce((sum, item) => sum + Number(item?.unreadCount || 0), 0);
  setChatUnreadCount(totalUnread);
  if (activeSessionId.value) {
    const current = sessions.value.find((item) => item.sessionId === activeSessionId.value);
    if (current) {
      activeFriend.value = current;
    }
  }
}

async function markAllSessionAsRead() {
  const unreadSessions = sessions.value.filter((item) => Number(item?.unreadCount || 0) > 0);
  if (unreadSessions.length === 0) {
    ElMessage.info('当前没有未读会话');
    return;
  }

  for (const item of unreadSessions) {
    try {
      const res = await listMessages(item.sessionId, 1, 1);
      const latest = res?.data?.data?.records?.[0];
      if (latest?.id) {
        await markMessagesRead({ sessionId: item.sessionId, messageId: latest.id });
      }
    } catch (_) {
      // 单会话失败不影响其他会话已读
    }
  }

  await loadSessions();
  setChatUnreadCount(0);
  ElMessage.success('已将会话标记为已读');
}

function clearProcessedRequests() {
  const processedIds = incomingRequests.value
    .filter((item) => Number(item?.status) !== 0)
    .map((item) => String(item?.requestId || ''));

  if (processedIds.length === 0) {
    ElMessage.info('没有可清理的申请记录');
    return;
  }

  hiddenRequestIds.value = Array.from(new Set([...hiddenRequestIds.value, ...processedIds]));
  saveHiddenRequestIds();
  ElMessage.success(`已清理 ${processedIds.length} 条申请记录`);
}

async function openChatByFriend(friend) {
  const openRes = await openSession(friend.userId);
  const sessionId = openRes?.data?.data?.sessionId;
  if (!sessionId) return;

  activeSessionId.value = sessionId;
  activeFriend.value = {
    friendUserId: friend.userId,
    friendUsername: friend.username,
    friendNickname: friend.nickname,
    friendRemark: friend.remark
  };
  activeTab.value = 'sessions';

  await Promise.all([loadSessions(), loadMessagesForActiveSession()]);
}

async function selectSession(session) {
  activeSessionId.value = session.sessionId;
  activeFriend.value = session;
  await loadMessagesForActiveSession();
}

async function loadMessagesForActiveSession() {
  if (!activeSessionId.value) {
    messages.value = [];
    return;
  }

  const res = await listMessages(activeSessionId.value, 1, 50);
  const records = res?.data?.data?.records || [];
  messages.value = [...records].reverse();

  await nextTick();
  scrollToBottom();

  if (records.length > 0) {
    const latestMessage = records[0];
    await markRead(latestMessage.id, false);
    await loadSessions();
  }
}

async function sendText() {
  if (!activeSessionId.value) {
    ElMessage.warning('请先选择会话');
    return;
  }

  const text = messageInput.value.trim();
  if (!text) return;

  await sendTextMessage({
    sessionId: activeSessionId.value,
    content: text,
    clientMsgId: createClientMsgId('text')
  });

  messageInput.value = '';
  await loadMessagesForActiveSession();
  await loadSessions();
}

function pickFile() {
  if (!activeSessionId.value) {
    ElMessage.warning('请先选择会话');
    return;
  }
  fileInputRef.value?.click();
}

async function onFilePicked(event) {
  const file = event.target.files?.[0];
  if (!file) return;

  try {
    if (!validateUploadFile(file)) {
      return;
    }

    const targetSessionId = activeSessionId.value;
    if (!targetSessionId) {
      ElMessage.warning('请先选择会话');
      return;
    }
    const task = createFileUploadTask(file, 'chat-file', {
      successMessage: '',
      onUploaded: async ({ fileId }) => {
        if (!fileId) {
          throw new Error('上传结果缺少文件ID');
        }

        await sendFileMessage({
          sessionId: targetSessionId,
          fileId,
          clientMsgId: createClientMsgId('upload-file')
        });

        if (activeSessionId.value === targetSessionId) {
          await loadMessagesForActiveSession();
        }
        await loadSessions();
        ElMessage.success('文件已发送');
      }
    });

    enqueueUploadTasks([task]);
    ElMessage.success(`已加入上传列表: ${file.name}`);
  } finally {
    event.target.value = '';
  }
}

function openFileLibraryDialog() {
  if (!activeSessionId.value) {
    ElMessage.warning('请先选择会话');
    return;
  }
  libraryDialogVisible.value = true;
  libraryPagination.value.page = 1;
  loadLibraryFiles();
}

async function loadLibraryFiles(page = libraryPagination.value.page) {
  libraryLoading.value = true;
  try {
    const res = await fetchFileList(page, libraryPagination.value.size, {});
    const data = res?.data?.data;
    const records = data?.records || [];
    libraryFiles.value = records.filter((item) => Number(item.isFolder) !== 1);
    libraryPagination.value.page = Number(data?.current || page);
    libraryPagination.value.total = Number(data?.total || 0);
  } finally {
    libraryLoading.value = false;
  }
}

function onLibraryPageChange(page) {
  libraryPagination.value.page = page;
  loadLibraryFiles(page);
}

async function sendExistingFile(fileRow) {
  if (!fileRow?.id) return;

  await sendFileMessage({
    sessionId: activeSessionId.value,
    fileId: fileRow.id,
    clientMsgId: createClientMsgId('existing-file')
  });

  libraryDialogVisible.value = false;
  ElMessage.success('文件已发送');
  await loadMessagesForActiveSession();
  await loadSessions();
}

async function markRead(messageId, refreshSessions = true) {
  if (!activeSessionId.value || !messageId) return;
  await markMessagesRead({ sessionId: activeSessionId.value, messageId });
  if (refreshSessions) {
    await loadSessions();
  }
}

async function downloadFile(msg) {
  const response = await downloadChatFile(msg.id);
  const blob = new Blob([response.data]);
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  const filename =
    extractFilename(response.headers?.['content-disposition']) || msg.fileName || `chat-file-${msg.fileId}`;
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}

function isSavingToLibrary(messageId) {
  if (!messageId) return false;
  return !!savingToLibraryMap.value[String(messageId)];
}

async function saveChatFileToLibrary(msg) {
  if (!msg?.id) return;
  const key = String(msg.id);
  if (savingToLibraryMap.value[key]) {
    return;
  }

  savingToLibraryMap.value[key] = true;
  try {
    const response = await downloadChatFile(msg.id);
    const filename =
      extractFilename(response.headers?.['content-disposition']) || msg.fileName || `chat-file-${msg.fileId || msg.id}`;
    const blob = new Blob([response.data], { type: response.data?.type || 'application/octet-stream' });
    const file = new File([blob], filename, {
      type: blob.type || 'application/octet-stream',
      lastModified: Date.now()
    });

    if (!validateUploadFile(file)) {
      return;
    }

    const task = createFileUploadTask(file, '聊天文件保存', {
      successMessage: `${filename} 已保存到文件列表`
    });
    enqueueUploadTasks([task]);
    ElMessage.success(`已加入上传列表: ${filename}`);
  } finally {
    delete savingToLibraryMap.value[key];
  }
}

async function acceptRequest(requestId) {
  await acceptFriendRequest(requestId);
  ElMessage.success('已同意好友申请');
  await Promise.all([loadIncomingRequests(), loadFriends(), loadSessions()]);
}

async function rejectRequest(requestId) {
  await rejectFriendRequest(requestId);
  ElMessage.success('已拒绝好友申请');
  await loadIncomingRequests();
}

async function removeFriendAction(friend) {
  try {
    await ElMessageBox.confirm(`确认删除好友「${displayNameOf(friend)}」吗？`, '提示', {
      type: 'warning'
    });
  } catch (_) {
    return;
  }

  await removeFriend(friend.userId);
  ElMessage.success('好友已删除');
  await Promise.all([loadFriends(), loadSessions()]);

  if (activeFriend.value?.friendUserId === friend.userId) {
    activeSessionId.value = null;
    activeFriend.value = null;
    messages.value = [];
  }
}

function openAddDialog() {
  addDialogVisible.value = true;
  searchKeyword.value = '';
  searchResult.value = [];
}

async function runSearchUser() {
  const keyword = searchKeyword.value.trim();
  if (!keyword) {
    ElMessage.warning('请输入搜索关键词');
    return;
  }

  const res = await searchUsers(keyword);
  searchResult.value = res?.data?.data || [];
}

async function requestAddFriend(user) {
  await sendFriendRequest(user.userId, '你好，想加你为好友');
  ElMessage.success('好友申请已发送');
}

function openRemarkDialog(friend) {
  remarkTargetFriend.value = friend;
  remarkInput.value = friend.remark || '';
  remarkDialogVisible.value = true;
}

async function saveRemark() {
  if (!remarkTargetFriend.value?.userId) return;
  await updateFriendRemark(remarkTargetFriend.value.userId, remarkInput.value || '');
  remarkDialogVisible.value = false;
  ElMessage.success('备注已更新');
  await Promise.all([loadFriends(), loadSessions()]);
}

function connectWs() {
  const token = localStorage.getItem('token');
  if (!token) return;

  const baseUrl = buildApiBaseUrl();
  const wsUrl = `${baseUrl.replace(/^http/i, 'ws')}/chat/ws?token=${encodeURIComponent(token)}`;
  const ws = new WebSocket(wsUrl);
  wsRef.value = ws;

  ws.onopen = () => {
    wsConnected.value = true;
  };

  ws.onmessage = async (event) => {
    try {
      const payload = JSON.parse(event.data || '{}');
      const type = payload.type;
      const data = payload.data || {};

      if (type === 'chat:new-message' || type === 'chat:message-sent') {
        const message = data.message;
        if (!message) return;

        if (activeSessionId.value === message.sessionId) {
          const existed = messages.value.some((item) => item.id === message.id);
          if (!existed) {
            messages.value.push(message);
            await nextTick();
            scrollToBottom();
          }
          if (!message.own) {
            await markRead(message.id, false);
          }
        }
        await loadSessions();
        return;
      }

      if (type === 'chat:read-receipt') {
        const messageId = data.messageId;
        messages.value = messages.value.map((item) => {
          if (item.own && item.id <= messageId) {
            return { ...item, read: true };
          }
          return item;
        });
      }
    } catch (_) {
      // Ignore invalid websocket payload.
    }
  };

  ws.onclose = () => {
    wsConnected.value = false;
    wsRef.value = null;
    if (wsReconnectTimer) {
      window.clearTimeout(wsReconnectTimer);
    }
    wsReconnectTimer = window.setTimeout(connectWs, 3000);
  };
}

function scrollToBottom() {
  const scrollbar = messageScrollbarRef.value;
  if (!scrollbar || !scrollbar.wrapRef) return;
  scrollbar.wrapRef.scrollTop = scrollbar.wrapRef.scrollHeight;
}

function createClientMsgId(prefix) {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

function formatTime(time) {
  if (!time) return '';
  const date = new Date(time);
  if (Number.isNaN(date.getTime())) return String(time);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  const hh = String(date.getHours()).padStart(2, '0');
  const mm = String(date.getMinutes()).padStart(2, '0');
  return `${y}-${m}-${d} ${hh}:${mm}`;
}

function formatFileSize(size) {
  const numeric = Number(size || 0);
  if (!numeric || numeric <= 0) return '未知';
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = numeric;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(2)} ${units[index]}`;
}

function extractFilename(contentDisposition) {
  if (!contentDisposition) return '';
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1]);
  }
  const normalMatch = contentDisposition.match(/filename="?([^\";]+)"?/i);
  return normalMatch?.[1] || '';
}
</script>

<style scoped>
.chat-page {
  display: flex;
  height: calc(100vh - 140px);
  border-radius: 10px;
  overflow: hidden;
  background: #ffffff;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
}

.chat-sidebar {
  width: 360px;
  border-right: 1px solid #eef0f3;
  background: #fcfdff;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #eef0f3;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 18px;
}

.chat-tabs {
  padding: 0 12px;
}

.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
  margin-bottom: 8px;
}

.request-summary {
  font-size: 12px;
  color: #7a8599;
}

.session-item,
.friend-item,
.request-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  margin: 6px 2px;
  transition: background 0.2s ease;
}

.session-item {
  cursor: pointer;
}

.session-item:hover,
.friend-item:hover,
.request-item:hover {
  background: #f4f7ff;
}

.session-item.active {
  background: #e8f3ff;
}

.session-main,
.friend-meta,
.request-text {
  flex: 1;
  min-width: 0;
}

.session-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.session-pin-btn {
  padding: 0;
}

.session-title,
.friend-name,
.request-name {
  font-weight: 600;
  color: #1f2d3d;
}

.session-preview,
.friend-username,
.request-message,
.request-status {
  font-size: 12px;
  color: #7a8599;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.friend-actions {
  display: flex;
  gap: 2px;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f9fbff 0%, #f4f7fb 100%);
}

.chat-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-header {
  padding: 14px 18px;
  border-bottom: 1px solid #e7ebf3;
  background: #ffffff;
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-header-text {
  min-width: 0;
}

.chat-title {
  font-size: 17px;
  font-weight: 600;
  color: #1f2d3d;
}

.chat-subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: #7b8796;
}

.message-list {
  flex: 1;
  padding: 16px;
}

.message-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  margin-bottom: 12px;
}

.message-row.own {
  justify-content: flex-end;
}

.message-avatar {
  flex-shrink: 0;
}

.message-bubble {
  max-width: 70%;
  background: #ffffff;
  border-radius: 10px;
  padding: 10px 12px;
  box-shadow: 0 1px 5px rgba(0, 0, 0, 0.08);
}

.message-bubble.own {
  background: #dff3ff;
}

.message-text {
  white-space: pre-wrap;
  line-height: 1.55;
  color: #263447;
}

.message-time {
  margin-top: 6px;
  font-size: 12px;
  color: #7f8a9b;
  text-align: right;
}

.file-card {
  min-width: 220px;
}

.file-title {
  font-weight: 600;
  color: #1f2d3d;
}

.file-size {
  margin: 4px 0 8px;
  font-size: 12px;
  color: #7f8a9b;
}

.file-actions {
  display: inline-flex;
  gap: 8px;
  flex-wrap: wrap;
}

.chat-input-bar {
  background: #ffffff;
  border-top: 1px solid #e7ebf3;
  padding: 14px;
}

.chat-input-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.add-friend-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-result-list {
  max-height: 320px;
  overflow: auto;
}

.search-result-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
  border: 1px solid #edf0f6;
  border-radius: 8px;
  margin-bottom: 8px;
}

.search-result-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search-result-item .name {
  font-weight: 600;
  color: #1f2d3d;
}

.search-result-item .username {
  margin-top: 2px;
  font-size: 12px;
  color: #7f8a9b;
}

.library-pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 980px) {
  .chat-page {
    flex-direction: column;
    height: auto;
    min-height: calc(100vh - 140px);
  }

  .chat-sidebar {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid #eef0f3;
  }

  .chat-main {
    min-height: 60vh;
  }

  .message-bubble {
    max-width: 86%;
  }

  .friend-actions {
    flex-direction: column;
  }
}
</style>
