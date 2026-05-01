<template>
  <el-container class="app-wrapper">
    <!-- 左侧侧栏 -->
    <el-aside width="240px" class="sidebar-container">
      <div class="sidebar-logo">
        <svg class="nav-logo-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg" width="24" height="24" style="margin-right: 10px; color: #ffffff;">
          <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
          <line x1="9" y1="14" x2="15" y2="14"></line>
        </svg>
        <transition name="el-fade-in">
          <span class="logo-title">SecFileManager</span>
        </transition>
      </div>
      
      <el-menu
        :default-active="currentRoute"
        class="sidebar-menu"
        background-color="#001529"
        text-color="rgba(255,255,255,0.7)"
        active-text-color="#ffffff"
        router
      >
        <el-menu-item index="/files">
          <el-icon><Document /></el-icon>
          <span>文件列表</span>
        </el-menu-item>
        <el-menu-item index="/recycle">
          <el-icon><Delete /></el-icon>
          <span>回收站</span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatLineRound /></el-icon>
          <el-badge :is-dot="chatUnreadCount > 0" :hidden="chatUnreadCount <= 0" class="chat-menu-badge">
            <span>聊天</span>
          </el-badge>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>个人设置</span>
        </el-menu-item>
        <el-menu-item index="/security">
          <el-icon><Lock /></el-icon>
          <span>安全说明</span>
        </el-menu-item>
        <el-menu-item index="/game">
          <el-icon><Monitor /></el-icon>
          <span>游戏</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    
    <!-- 右侧内容区 -->
    <el-container class="main-container">
      <el-header class="navbar">
        <div class="navbar-left">
          <h2 class="page-title">{{ pageTitle }}</h2>
        </div>
        <div class="navbar-right">
          <el-badge
            :value="totalUnreadNotificationCount"
            :hidden="totalUnreadNotificationCount <= 0"
            :max="99"
            class="message-center-badge"
          >
            <el-button class="message-center-btn" text @click="openMessageCenter">
              <el-icon :size="18"><Bell /></el-icon>
              <span>消息中心</span>
            </el-button>
          </el-badge>

          <el-dropdown trigger="click" @command="handleCommand">
            <div class="avatar-wrapper">
              <el-avatar :size="32" class="user-avatar" :src="avatarUrl" :icon="UserFilled" :style="{ backgroundColor: '#409EFF' }" />
              <span class="user-name">{{ displayName }}</span>
              <el-icon class="el-icon--right"><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout" style="color: #f56c6c;">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      
      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <el-drawer v-model="messageCenterVisible" title="消息中心" size="460px">
      <div class="message-center-summary">
        <el-card shadow="never" class="message-center-card">
          <div class="message-center-card-row">
            <span class="message-center-card-title">聊天未读</span>
            <el-tag type="danger" size="small">{{ chatUnreadCount }}</el-tag>
          </div>
          <div class="message-center-card-desc">未读聊天消息总数</div>
          <el-button type="primary" link @click="openRoute({ path: '/chat', query: { tab: 'sessions' } })">去聊天</el-button>
        </el-card>

        <el-card shadow="never" class="message-center-card">
          <div class="message-center-card-row">
            <span class="message-center-card-title">好友申请</span>
            <el-tag type="warning" size="small">{{ friendRequestCount }}</el-tag>
          </div>
          <div class="message-center-card-desc">待处理的好友申请</div>
          <el-button type="primary" link @click="openRoute({ path: '/chat', query: { tab: 'requests' } })">去处理</el-button>
        </el-card>
      </div>

      <div class="system-message-header">
        <span>系统通知</span>
        <div class="system-message-actions">
          <el-button text type="primary" :disabled="totalUnreadNotificationCount <= 0" @click="markAllCenterRead">
            一键已读
          </el-button>
          <el-button text type="primary" @click="clearReadAndHideBadge">清理已读</el-button>
        </div>
      </div>

      <el-empty v-if="sortedSystemNotifications.length === 0" description="暂无系统通知" />

      <div v-else class="system-message-list">
        <div
          v-for="item in sortedSystemNotifications"
          :key="item.id"
          class="system-message-item"
          :class="{ 'is-unread': !item.read }"
        >
          <div class="system-message-meta">
            <el-tag size="small" :type="getSystemTypeTag(item.type)">
              {{ getSystemTypeText(item.type) }}
            </el-tag>
            <span class="system-message-time">{{ formatNotificationTime(item.timestamp) }}</span>
          </div>
          <div class="system-message-title">{{ item.title }}</div>
          <div class="system-message-content">{{ item.message }}</div>
          <div class="system-message-item-actions">
            <el-button v-if="item.route" text type="primary" @click="handleSystemNotificationClick(item)">查看</el-button>
            <el-button text @click="markSystemNotificationRead(item.id)">标记已读</el-button>
          </div>
        </div>
      </div>
    </el-drawer>
    <AiChat />
  </el-container>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { Document, Setting, Lock, Delete, Files, UserFilled, CaretBottom, ChatLineRound, Bell, Monitor } from '@element-plus/icons-vue';
import { useUser } from '../composables/useUser.js';
import AiChat from '../components/AiChat.vue';
import api from '../api/index.js';
import { listIncomingFriendRequests, listSessions } from '../api/chat.js';
import {
  chatUnreadCount,
  friendRequestCount,
  systemNotifications,
  unreadSystemNotificationCount,
  totalUnreadNotificationCount,
  setChatUnreadCount,
  setFriendRequestCount,
  addSystemNotification,
  markSystemNotificationRead,
  markAllSystemNotificationsRead,
  clearReadSystemNotifications
} from '../store/messageCenter.js';

const router = useRouter();
const route = useRoute();
const currentRoute = computed(() => {
  const p = route.path;
  if (p.startsWith('/game')) return '/game';
  return p;
});
const messageCenterVisible = ref(false);
const chatWsRef = ref(null);
const chatCountInitialized = ref(false);
const friendRequestCountInitialized = ref(false);
const MESSAGE_CENTER_REFRESH_INTERVAL_MS = 10 * 1000;
let chatWsReconnectTimer = null;
let messageCenterRefreshTimer = null;

const { displayName, avatarUrl, logout } = useUser('user');

const sortedSystemNotifications = computed(() => {
  return [...systemNotifications.value].sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
});

// 监听路由变化
watch(() => route.path, () => {
  refreshMessageCenterSummary();
  if (route.path === '/chat') {
    window.setTimeout(() => {
      refreshMessageCenterSummary();
    }, 800);
  }
});

onMounted(() => {
  refreshMessageCenterSummary();
  connectChatNotifyWs();
  messageCenterRefreshTimer = window.setInterval(() => {
    refreshMessageCenterSummary();
  }, MESSAGE_CENTER_REFRESH_INTERVAL_MS);
});

onBeforeUnmount(() => {
  if (messageCenterRefreshTimer) {
    window.clearInterval(messageCenterRefreshTimer);
    messageCenterRefreshTimer = null;
  }
  if (chatWsReconnectTimer) {
    window.clearTimeout(chatWsReconnectTimer);
    chatWsReconnectTimer = null;
  }
  if (chatWsRef.value) {
    chatWsRef.value.close();
    chatWsRef.value = null;
  }
});

function buildApiBaseUrl() {
  let base = (api.defaults.baseURL || '/api').replace(/\/$/, '');
  if (base.startsWith('/')) {
    base = `${window.location.protocol}//${window.location.host}${base}`;
  }
  return base;
}

function countUnreadFromSessions(rows) {
  if (!Array.isArray(rows)) return 0;
  return rows.reduce((sum, item) => sum + Number(item?.unreadCount || 0), 0);
}

async function refreshChatUnread() {
  const token = localStorage.getItem('token');
  if (!token) {
    setChatUnreadCount(0);
    chatCountInitialized.value = false;
    return;
  }
  try {
    const res = await listSessions();
    const sessionRows = res?.data?.data || [];
    const prevCount = chatUnreadCount.value;
    const nextCount = countUnreadFromSessions(sessionRows);
    setChatUnreadCount(nextCount);

    if (chatCountInitialized.value && nextCount > prevCount) {
      addSystemNotification({
        type: 'chat-unread',
        title: '聊天新消息',
        message: `你有 ${nextCount} 条未读聊天消息`,
        route: '/chat?tab=sessions',
        dedupeKey: 'chat-unread'
      });
    }
    chatCountInitialized.value = true;
  } catch (_) {
    // 忽略网络瞬时错误，避免影响主界面交互
  }
}

function countPendingFriendRequests(rows) {
  if (!Array.isArray(rows)) return 0;
  return rows.filter((item) => Number(item?.status) === 0).length;
}

async function refreshFriendRequestPending() {
  const token = localStorage.getItem('token');
  if (!token) {
    setFriendRequestCount(0);
    friendRequestCountInitialized.value = false;
    return;
  }
  try {
    const res = await listIncomingFriendRequests();
    const rows = res?.data?.data || [];
    const prevCount = friendRequestCount.value;
    const pendingCount = countPendingFriendRequests(rows);
    setFriendRequestCount(pendingCount);

    if (friendRequestCountInitialized.value && pendingCount > prevCount) {
      addSystemNotification({
        type: 'friend-request',
        title: '收到新的好友申请',
        message: `当前待处理好友申请 ${pendingCount} 条`,
        route: '/chat?tab=requests',
        dedupeKey: 'friend-request'
      });
    }
    friendRequestCountInitialized.value = true;
  } catch (_) {
    // 忽略网络瞬时错误，避免影响主界面交互
  }
}

async function refreshMessageCenterSummary() {
  await Promise.all([refreshChatUnread(), refreshFriendRequestPending()]);
}

function connectChatNotifyWs() {
  const token = localStorage.getItem('token');
  if (!token) return;

  const baseUrl = buildApiBaseUrl();
  const wsUrl = `${baseUrl.replace(/^http/i, 'ws')}/chat/ws?token=${encodeURIComponent(token)}`;
  const ws = new WebSocket(wsUrl);
  chatWsRef.value = ws;

  ws.onmessage = async (event) => {
    try {
      const payload = JSON.parse(event.data || '{}');
      const type = payload?.type;
      if (type === 'chat:new-message' || type === 'chat:message-sent' || type === 'chat:read-receipt') {
        await refreshChatUnread();
        return;
      }

      if (typeof type === 'string' && type.toLowerCase().includes('friend')) {
        await refreshFriendRequestPending();
      }
    } catch (_) {
      // 忽略无效消息
    }
  };

  ws.onclose = () => {
    chatWsRef.value = null;
    if (chatWsReconnectTimer) {
      window.clearTimeout(chatWsReconnectTimer);
    }
    if (!localStorage.getItem('token')) {
      return;
    }
    chatWsReconnectTimer = window.setTimeout(connectChatNotifyWs, 3000);
  };
}

function openMessageCenter() {
  messageCenterVisible.value = true;
  refreshMessageCenterSummary();
}

function openRoute(target) {
  if (!target) {
    messageCenterVisible.value = false;
    return;
  }

  router.push(target);
  messageCenterVisible.value = false;
}

function markAllCenterRead() {
  markAllSystemNotificationsRead();
  setChatUnreadCount(0);
  setFriendRequestCount(0);
}

function clearReadAndHideBadge() {
  clearReadSystemNotifications();
  setChatUnreadCount(0);
  setFriendRequestCount(0);
}

function handleSystemNotificationClick(item) {
  if (!item) return;
  markSystemNotificationRead(item.id);
  openRoute(item.route);
}

function getSystemTypeText(type) {
  if (type === 'upload-failed') return '上传失败';
  if (type === 'preview-failed') return '预览失败';
  if (type === 'chat-unread') return '聊天消息';
  if (type === 'friend-request') return '好友申请';
  return '系统通知';
}

function getSystemTypeTag(type) {
  if (type === 'upload-failed') return 'danger';
  if (type === 'preview-failed') return 'warning';
  if (type === 'chat-unread') return 'primary';
  if (type === 'friend-request') return 'warning';
  return 'info';
}

function formatNotificationTime(timestamp) {
  if (!timestamp) return '-';
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return '-';
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  const hh = String(date.getHours()).padStart(2, '0');
  const mi = String(date.getMinutes()).padStart(2, '0');
  return `${mm}-${dd} ${hh}:${mi}`;
}

const pageTitle = computed(() => {
  const p = currentRoute.value;
  switch (p) {
    case '/files':
      return '文件列表';
    case '/recycle':
      return '回收站';
    case '/chat':
      return '好友聊天';
    case '/game':
      return '游戏';
    case '/settings':
      return '个人设置';
    case '/security':
      return '安全说明';
    default:
      return '安全文件管理';
  }
});

const handleCommand = (command) => {
  if (command === 'logout') {
    logout();
  } else if (command === 'profile') {
    router.push('/settings');
  }
};
</script>

<style scoped>
.app-wrapper {
  height: 100vh;
  width: 100%;
}

.sidebar-container {
  background-color: #001529;
  height: 100%;
  box-shadow: 2px 0 6px rgba(0,21,41,0.35);
  z-index: 10;
  display: flex;
  flex-direction: column;
}

.sidebar-logo {
  height: 60px;
  line-height: 60px;
  background: #002140;
  text-align: center;
  overflow: hidden;
  color: #fff;
  font-weight: 600;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sidebar-menu {
  border: none;
  flex: 1;
}

.chat-menu-badge {
  display: inline-flex;
  align-items: center;
}

.chat-menu-badge :deep(.el-badge__content.is-fixed) {
  top: 10px;
  right: -6px;
}

/* 覆盖 Element Menu 选中样式 */
.sidebar-menu :deep(.el-menu-item.is-active) {
  background-color: #1890ff !important;
  color: white !important;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background-color: #000c17 !important;
  color: white !important;
}

.main-container {
  background-color: #f0f2f5;
  display: flex;
  flex-direction: column;
}

.navbar {
  height: 60px;
  overflow: hidden;
  position: relative;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,21,41,.08);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 500;
  color: #303133;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.message-center-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #606266;
}

.message-center-badge :deep(.el-badge__content.is-fixed) {
  top: 6px;
  right: 0;
}

.avatar-wrapper {
  margin-top: 5px;
  position: relative;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-size: 14px;
  color: #606266;
}

.app-main {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  scrollbar-width: none;
}
.app-main::-webkit-scrollbar {
  display: none;
}

/* 路由切换动画 */
.fade-transform-leave-active,
.fade-transform-enter-active {
  transition: all 0.3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(30px);
}

.message-center-summary {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  margin-bottom: 14px;
}

.message-center-card {
  border: 1px solid #eef0f3;
}

.message-center-card-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.message-center-card-title {
  font-size: 14px;
  color: #303133;
  font-weight: 600;
}

.message-center-card-desc {
  margin-top: 6px;
  margin-bottom: 4px;
  font-size: 12px;
  color: #909399;
}

.system-message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.system-message-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.system-message-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: calc(100vh - 280px);
  overflow-y: auto;
  padding-right: 4px;
}

.system-message-item {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px;
  background: #fff;
}

.system-message-item.is-unread {
  border-left: 3px solid #409eff;
  background: #f8fbff;
}

.system-message-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.system-message-time {
  font-size: 12px;
  color: #909399;
}

.system-message-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.system-message-content {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
  word-break: break-all;
}

.system-message-item-actions {
  margin-top: 6px;
}
</style>
