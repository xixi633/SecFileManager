import api from './index.js';

export function listFriends() {
  return api.get('/friend/list');
}

export function searchUsers(keyword) {
  return api.get('/friend/search', { params: { keyword } });
}

export function sendFriendRequest(toUserId, message = '') {
  return api.post('/friend/request', { toUserId, message });
}

export function listIncomingFriendRequests() {
  return api.get('/friend/request/incoming');
}

export function acceptFriendRequest(requestId) {
  return api.post(`/friend/request/${requestId}/accept`);
}

export function rejectFriendRequest(requestId) {
  return api.post(`/friend/request/${requestId}/reject`);
}

export function removeFriend(friendUserId) {
  return api.delete(`/friend/${friendUserId}`);
}

export function updateFriendRemark(friendUserId, remark) {
  return api.put(`/friend/${friendUserId}/remark`, { remark });
}

export function openSession(friendUserId) {
  return api.get(`/chat/session/${friendUserId}`);
}

export function listSessions() {
  return api.get('/chat/sessions');
}

export function listMessages(sessionId, page = 1, size = 20) {
  return api.get(`/chat/messages/${sessionId}`, { params: { page, size } });
}

export function sendTextMessage(payload) {
  return api.post('/chat/message/text', payload);
}

export function sendFileMessage(payload) {
  return api.post('/chat/message/file', payload);
}

export function markMessagesRead(payload) {
  return api.post('/chat/message/read', payload);
}

export function unreadCount() {
  return api.get('/chat/unread/count');
}

export function downloadChatFile(messageId) {
  return api.get(`/chat/file/download/${messageId}`, { responseType: 'blob' });
}
