package com.security.filemanager.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.security.filemanager.dto.ChatMessageReadRequest;
import com.security.filemanager.dto.ChatMessageResponse;
import com.security.filemanager.dto.ChatMessageSendFileRequest;
import com.security.filemanager.dto.ChatMessageSendTextRequest;
import com.security.filemanager.entity.ChatMessage;
import com.security.filemanager.entity.ChatReadCursor;
import com.security.filemanager.entity.ChatSession;
import com.security.filemanager.entity.FileInfo;
import com.security.filemanager.exception.BizException;
import com.security.filemanager.mapper.ChatMessageMapper;
import com.security.filemanager.mapper.ChatReadCursorMapper;
import com.security.filemanager.mapper.FileMapper;
import com.security.filemanager.util.AESUtil;
import com.security.filemanager.websocket.ChatWebSocketGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatMessageService {

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private ChatReadCursorMapper chatReadCursorMapper;

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private FriendService friendService;

    @Resource
    private FileMapper fileMapper;

    @Resource
    private ChatFileShareService chatFileShareService;

    @Resource
    private ChatWebSocketGateway chatWebSocketGateway;

    @Value("${secure-file.system-master-key}")
    private String systemMasterKey;

    @Value("${secure-file.chat.message-page-size:20}")
    private long defaultMessagePageSize;

    @Transactional(rollbackFor = Exception.class)
    public ChatMessageResponse sendTextMessage(Long currentUserId, ChatMessageSendTextRequest request) {
        ChatSession session = chatSessionService.getSessionForUser(request.getSessionId(), currentUserId);
        Long peerUserId = chatSessionService.getPeerUserId(session, currentUserId);
        ensureFriend(currentUserId, peerUserId);

        ChatMessage existed = findByClientMsgId(request.getSessionId(), request.getClientMsgId());
        if (existed != null) {
            return toResponse(existed, currentUserId, null);
        }

        ChatMessage message = new ChatMessage();
        message.setSessionId(request.getSessionId());
        message.setSenderId(currentUserId);
        message.setReceiverId(peerUserId);
        message.setClientMsgId(request.getClientMsgId());
        message.setMessageType("text");

        EncryptPayload encrypted = encryptText(request.getContent());
        message.setContentCiphertext(encrypted.ciphertextBase64);
        message.setContentIv(encrypted.ivHex);
        message.setContentAuthTag(encrypted.authTagHex);

        message.setIsRead(0);
        message.setSentAt(LocalDateTime.now());

        ChatMessage persisted = insertMessageWithIdempotency(message);
        chatSessionService.touchSession(session.getId(), persisted.getSentAt());

        ChatMessageResponse senderResponse = toResponse(persisted, currentUserId, null);
        ChatMessageResponse receiverResponse = toResponse(persisted, peerUserId, null);
        notifyNewMessage(currentUserId, peerUserId, senderResponse, receiverResponse);
        return senderResponse;
    }

    @Transactional(rollbackFor = Exception.class)
    public ChatMessageResponse sendFileMessage(Long currentUserId, ChatMessageSendFileRequest request) {
        ChatSession session = chatSessionService.getSessionForUser(request.getSessionId(), currentUserId);
        Long peerUserId = chatSessionService.getPeerUserId(session, currentUserId);
        ensureFriend(currentUserId, peerUserId);

        FileInfo ownedFile = fileMapper.selectByIdAndUserId(request.getFileId(), currentUserId);
        if (ownedFile == null) {
            throw BizException.conflict("CHAT_FILE_NOT_OWNED", "只能发送自己拥有的文件");
        }

        ChatMessage existed = findByClientMsgId(request.getSessionId(), request.getClientMsgId());
        if (existed != null) {
            return toResponse(existed, currentUserId, mapOfFile(ownedFile));
        }

        ChatMessage message = new ChatMessage();
        message.setSessionId(request.getSessionId());
        message.setSenderId(currentUserId);
        message.setReceiverId(peerUserId);
        message.setClientMsgId(request.getClientMsgId());
        message.setMessageType("file");
        message.setFileId(request.getFileId());
        message.setIsRead(0);
        message.setSentAt(LocalDateTime.now());

        ChatMessage persisted = insertMessageWithIdempotency(message);
        chatFileShareService.createShare(persisted.getId(), request.getFileId(), currentUserId, peerUserId);
        chatSessionService.touchSession(session.getId(), persisted.getSentAt());

        Map<Long, FileInfo> fileMap = mapOfFile(ownedFile);
        ChatMessageResponse senderResponse = toResponse(persisted, currentUserId, fileMap);
        ChatMessageResponse receiverResponse = toResponse(persisted, peerUserId, fileMap);
        notifyNewMessage(currentUserId, peerUserId, senderResponse, receiverResponse);
        return senderResponse;
    }

    public Page<ChatMessageResponse> listMessages(Long currentUserId, Long sessionId, Long pageNo, Long pageSize) {
        chatSessionService.getSessionForUser(sessionId, currentUserId);

        long actualPage = pageNo == null || pageNo < 1 ? 1 : pageNo;
        long actualSize = pageSize == null || pageSize < 1 ? defaultMessagePageSize : pageSize;

        Page<ChatMessage> queryPage = new Page<>(actualPage, actualSize);
        Page<ChatMessage> rawPage = chatMessageMapper.selectPage(queryPage,
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getId));

        List<Long> fileIds = rawPage.getRecords().stream()
                .map(ChatMessage::getFileId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, FileInfo> fileInfoMap = new HashMap<>();
        if (!fileIds.isEmpty()) {
            List<FileInfo> files = fileMapper.selectBatchIds(fileIds);
            for (FileInfo file : files) {
                fileInfoMap.put(file.getId(), file);
            }
        }

        List<ChatMessageResponse> responses = new ArrayList<>();
        for (ChatMessage message : rawPage.getRecords()) {
            responses.add(toResponse(message, currentUserId, fileInfoMap));
        }

        Page<ChatMessageResponse> page = new Page<>(rawPage.getCurrent(), rawPage.getSize(), rawPage.getTotal());
        page.setRecords(responses);
        return page;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long currentUserId, ChatMessageReadRequest request) {
        ChatSession session = chatSessionService.getSessionForUser(request.getSessionId(), currentUserId);
        Long peerUserId = chatSessionService.getPeerUserId(session, currentUserId);

        LocalDateTime now = LocalDateTime.now();
        chatMessageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, request.getSessionId())
                .eq(ChatMessage::getReceiverId, currentUserId)
                .eq(ChatMessage::getIsRead, 0)
                .le(ChatMessage::getId, request.getMessageId())
                .set(ChatMessage::getIsRead, 1)
                .set(ChatMessage::getReadAt, now));

        ChatReadCursor cursor = chatReadCursorMapper.selectOne(new LambdaQueryWrapper<ChatReadCursor>()
                .eq(ChatReadCursor::getSessionId, request.getSessionId())
                .eq(ChatReadCursor::getUserId, currentUserId)
                .last("LIMIT 1"));
        if (cursor == null) {
            cursor = new ChatReadCursor();
            cursor.setSessionId(request.getSessionId());
            cursor.setUserId(currentUserId);
            cursor.setLastReadMessageId(request.getMessageId());
            cursor.setLastReadAt(now);
            cursor.setUpdatedAt(now);
            chatReadCursorMapper.insert(cursor);
        } else {
            cursor.setLastReadMessageId(request.getMessageId());
            cursor.setLastReadAt(now);
            cursor.setUpdatedAt(now);
            chatReadCursorMapper.updateById(cursor);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", request.getSessionId());
        payload.put("messageId", request.getMessageId());
        payload.put("readerId", currentUserId);
        chatWebSocketGateway.sendEvent(peerUserId, "chat:read-receipt", payload);
    }

    public Long countUnread(Long currentUserId) {
        Long count = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getReceiverId, currentUserId)
                .eq(ChatMessage::getIsRead, 0));
        return count == null ? 0L : count;
    }

    private ChatMessage insertMessageWithIdempotency(ChatMessage message) {
        try {
            chatMessageMapper.insert(message);
            return message;
        } catch (DuplicateKeyException ex) {
            ChatMessage existed = findByClientMsgId(message.getSessionId(), message.getClientMsgId());
            if (existed != null) {
                return existed;
            }
            throw ex;
        }
    }

    private ChatMessage findByClientMsgId(Long sessionId, String clientMsgId) {
        return chatMessageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getClientMsgId, clientMsgId)
                .last("LIMIT 1"));
    }

    private ChatMessageResponse toResponse(ChatMessage message, Long currentUserId, Map<Long, FileInfo> fileInfoMap) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.setId(message.getId());
        response.setSessionId(message.getSessionId());
        response.setSenderId(message.getSenderId());
        response.setReceiverId(message.getReceiverId());
        response.setMessageType(message.getMessageType());
        response.setSentAt(message.getSentAt());
        response.setReadAt(message.getReadAt());
        response.setRead(message.getIsRead() != null && message.getIsRead() == 1);
        response.setOwn(currentUserId.equals(message.getSenderId()));

        if ("file".equalsIgnoreCase(message.getMessageType())) {
            response.setFileId(message.getFileId());
            if (fileInfoMap != null) {
                FileInfo file = fileInfoMap.get(message.getFileId());
                if (file != null) {
                    response.setFileName(file.getOriginalFilename());
                    response.setFileSize(file.getFileSize());
                }
            }
        } else {
            response.setContent(decryptText(message));
        }
        return response;
    }

    private EncryptPayload encryptText(String plaintext) {
        String key = convertSystemKey(systemMasterKey);
        String iv = AESUtil.generateIV();
        AESUtil.EncryptResult encrypted = AESUtil.encrypt(plaintext.getBytes(StandardCharsets.UTF_8), key, iv);

        EncryptPayload payload = new EncryptPayload();
        payload.ciphertextBase64 = Base64.getEncoder().encodeToString(encrypted.getCiphertext());
        payload.ivHex = iv;
        payload.authTagHex = encrypted.getAuthTag();
        return payload;
    }

    private String decryptText(ChatMessage message) {
        if (message.getContentCiphertext() == null || message.getContentIv() == null || message.getContentAuthTag() == null) {
            return "";
        }
        try {
            String key = convertSystemKey(systemMasterKey);
            byte[] ciphertext = Base64.getDecoder().decode(message.getContentCiphertext());
            byte[] plaintext = AESUtil.decrypt(ciphertext, message.getContentAuthTag(), key, message.getContentIv());
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "[消息解密失败]";
        }
    }

    private String convertSystemKey(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        } else if (keyBytes.length > 32) {
            byte[] trimmed = new byte[32];
            System.arraycopy(keyBytes, 0, trimmed, 0, 32);
            keyBytes = trimmed;
        }
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    private void ensureFriend(Long userId, Long peerUserId) {
        if (!friendService.isFriend(userId, peerUserId)) {
            throw BizException.conflict("CHAT_NOT_FRIEND", "你们还不是好友，不能聊天");
        }
    }

    private void notifyNewMessage(Long senderId, Long receiverId,
                                  ChatMessageResponse senderResponse,
                                  ChatMessageResponse receiverResponse) {
        Map<String, Object> receiverPayload = new HashMap<>();
        receiverPayload.put("sessionId", receiverResponse.getSessionId());
        receiverPayload.put("message", receiverResponse);
        chatWebSocketGateway.sendEvent(receiverId, "chat:new-message", receiverPayload);

        Map<String, Object> senderPayload = new HashMap<>();
        senderPayload.put("sessionId", senderResponse.getSessionId());
        senderPayload.put("message", senderResponse);
        chatWebSocketGateway.sendEvent(senderId, "chat:message-sent", senderPayload);
    }

    private Map<Long, FileInfo> mapOfFile(FileInfo fileInfo) {
        Map<Long, FileInfo> map = new HashMap<>();
        map.put(fileInfo.getId(), fileInfo);
        return map;
    }

    private static class EncryptPayload {
        private String ciphertextBase64;
        private String ivHex;
        private String authTagHex;
    }
}
