import { computed, ref } from 'vue';

const MAX_SYSTEM_NOTIFICATIONS = 50;
const DEDUPE_WINDOW_MS = 10 * 1000;

let systemNotificationCounter = 0;

export const chatUnreadCount = ref(0);
export const friendRequestCount = ref(0);
export const systemNotifications = ref([]);

export const unreadSystemNotificationCount = computed(() =>
  systemNotifications.value.filter((item) => !item.read).length
);

export const totalUnreadNotificationCount = computed(() =>
  chatUnreadCount.value + friendRequestCount.value + unreadSystemNotificationCount.value
);

const normalizeCount = (count) => {
  const numeric = Number(count);
  if (!Number.isFinite(numeric) || numeric < 0) {
    return 0;
  }
  return Math.floor(numeric);
};

const buildSystemNotificationId = () => {
  systemNotificationCounter += 1;
  return `sys_msg_${Date.now()}_${systemNotificationCounter}`;
};

const findRecentDuplicate = (dedupeKey, nowTs) => {
  if (!dedupeKey) return null;
  for (const item of systemNotifications.value) {
    if (item.dedupeKey !== dedupeKey) {
      continue;
    }
    if (nowTs - item.timestamp <= DEDUPE_WINDOW_MS) {
      return item;
    }
  }
  return null;
};

const trimSystemNotifications = () => {
  if (systemNotifications.value.length <= MAX_SYSTEM_NOTIFICATIONS) {
    return;
  }
  systemNotifications.value = systemNotifications.value.slice(0, MAX_SYSTEM_NOTIFICATIONS);
};

export const setChatUnreadCount = (count) => {
  chatUnreadCount.value = normalizeCount(count);
};

export const setFriendRequestCount = (count) => {
  friendRequestCount.value = normalizeCount(count);
};

export const addSystemNotification = ({
  type = 'system',
  title = '系统通知',
  message = '操作失败',
  route = '',
  dedupeKey = ''
} = {}) => {
  const nowTs = Date.now();
  const duplicate = findRecentDuplicate(dedupeKey, nowTs);

  if (duplicate) {
    duplicate.timestamp = nowTs;
    duplicate.time = new Date(nowTs).toISOString();
    duplicate.message = message || duplicate.message;
    duplicate.read = false;
    duplicate.count = (duplicate.count || 1) + 1;
    return duplicate.id;
  }

  const notification = {
    id: buildSystemNotificationId(),
    type,
    title,
    message,
    route,
    dedupeKey,
    read: false,
    count: 1,
    timestamp: nowTs,
    time: new Date(nowTs).toISOString()
  };

  systemNotifications.value = [notification, ...systemNotifications.value];
  trimSystemNotifications();
  return notification.id;
};

export const addUploadFailureNotification = ({ fileName = '', reason = '' } = {}) => {
  const displayName = fileName || '未命名文件';
  const detail = reason || '未知错误';
  return addSystemNotification({
    type: 'upload-failed',
    title: '上传失败',
    message: `${displayName}：${detail}`,
    route: '/files',
    dedupeKey: `upload-failed:${displayName}:${detail}`
  });
};

export const addPreviewFailureNotification = ({ fileName = '', message = '' } = {}) => {
  const displayName = fileName || '未知文件';
  const detail = message || '预览失败';
  return addSystemNotification({
    type: 'preview-failed',
    title: '预览失败',
    message: `${displayName}：${detail}`,
    route: '/files',
    dedupeKey: `preview-failed:${displayName}:${detail}`
  });
};

export const markSystemNotificationRead = (id) => {
  const target = systemNotifications.value.find((item) => item.id === id);
  if (target) {
    target.read = true;
  }
};

export const markAllSystemNotificationsRead = () => {
  systemNotifications.value.forEach((item) => {
    item.read = true;
  });
};

export const clearReadSystemNotifications = () => {
  systemNotifications.value = systemNotifications.value.filter((item) => !item.read);
};

export const clearAllSystemNotifications = () => {
  systemNotifications.value = [];
};
