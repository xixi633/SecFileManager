package com.security.filemanager.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.security.filemanager.entity.ChatFileShare;
import com.security.filemanager.entity.ChatMessage;
import com.security.filemanager.exception.BizException;
import com.security.filemanager.mapper.ChatFileShareMapper;
import com.security.filemanager.mapper.ChatMessageMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class ChatFileShareService {

    @Resource
    private ChatFileShareMapper chatFileShareMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private FileService fileService;

    @Value("${secure-file.chat.file-share-expire-hours:24}")
    private long fileShareExpireHours;

    @Transactional(rollbackFor = Exception.class)
    public void createShare(Long messageId, Long fileId, Long ownerUserId, Long receiverUserId) {
        ChatFileShare share = new ChatFileShare();
        share.setMessageId(messageId);
        share.setFileId(fileId);
        share.setOwnerUserId(ownerUserId);
        share.setReceiverUserId(receiverUserId);
        share.setRevoked(0);
        share.setCreatedAt(LocalDateTime.now());
        share.setExpiresAt(LocalDateTime.now().plusHours(Math.max(1, fileShareExpireHours)));
        try {
            chatFileShareMapper.insert(share);
        } catch (DuplicateKeyException ignored) {
            // idempotent
        }
    }

    public FileService.FileDownloadResult downloadSharedFile(Long messageId, Long currentUserId) {
        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null || !"file".equalsIgnoreCase(message.getMessageType()) || message.getFileId() == null) {
            throw BizException.badRequest("CHAT_FILE_MESSAGE_NOT_FOUND", "文件消息不存在");
        }

        if (currentUserId.equals(message.getSenderId())) {
            return fileService.downloadFile(message.getFileId(), message.getSenderId());
        }

        ChatFileShare share = chatFileShareMapper.selectOne(new LambdaQueryWrapper<ChatFileShare>()
                .eq(ChatFileShare::getMessageId, messageId)
                .eq(ChatFileShare::getReceiverUserId, currentUserId)
                .eq(ChatFileShare::getRevoked, 0)
                .orderByDesc(ChatFileShare::getId)
                .last("LIMIT 1"));

        if (share == null) {
            throw BizException.conflict("CHAT_FILE_FORBIDDEN", "你没有该文件访问权限");
        }

        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw BizException.conflict("CHAT_FILE_SHARE_EXPIRED", "文件分享已过期");
        }

        return fileService.downloadFile(share.getFileId(), share.getOwnerUserId());
    }
}
