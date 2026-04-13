package com.security.filemanager.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.security.filemanager.dto.ChatSessionResponse;
import com.security.filemanager.entity.FriendRelation;
import com.security.filemanager.entity.ChatMessage;
import com.security.filemanager.entity.ChatSession;
import com.security.filemanager.entity.User;
import com.security.filemanager.exception.BizException;
import com.security.filemanager.mapper.ChatMessageMapper;
import com.security.filemanager.mapper.ChatSessionMapper;
import com.security.filemanager.mapper.FriendMapper;
import com.security.filemanager.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChatSessionService {

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private FriendMapper friendMapper;

    public ChatSession getOrCreateSession(Long userId, Long friendUserId) {
        return getOrCreateSession(userId, friendUserId, true);
    }

    public ChatSession getOrCreateSession(Long userId, Long friendUserId, boolean requireFriend) {
        if (userId == null || friendUserId == null) {
            throw BizException.badRequest("CHAT_USER_REQUIRED", "会话用户不能为空");
        }
        if (userId.equals(friendUserId)) {
            throw BizException.badRequest("CHAT_SELF_NOT_ALLOWED", "不能和自己创建会话");
        }

        User friend = userMapper.selectById(friendUserId);
        if (friend == null || friend.getStatus() == null || friend.getStatus() != 1) {
            throw BizException.badRequest("CHAT_TARGET_USER_INVALID", "目标用户不存在或不可用");
        }

        if (requireFriend && !isFriend(userId, friendUserId)) {
            throw BizException.conflict("CHAT_NOT_FRIEND", "你们还不是好友，无法开始聊天");
        }

        Long userA = Math.min(userId, friendUserId);
        Long userB = Math.max(userId, friendUserId);

        ChatSession session = findByPair(userA, userB);
        if (session != null) {
            return session;
        }

        ChatSession create = new ChatSession();
        create.setUserAId(userA);
        create.setUserBId(userB);
        create.setCreatedAt(LocalDateTime.now());
        create.setUpdatedAt(LocalDateTime.now());

        try {
            chatSessionMapper.insert(create);
            return create;
        } catch (DuplicateKeyException ex) {
            return findByPair(userA, userB);
        }
    }

    public ChatSession getSessionForUser(Long sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw BizException.badRequest("CHAT_SESSION_NOT_FOUND", "会话不存在");
        }
        if (!userId.equals(session.getUserAId()) && !userId.equals(session.getUserBId())) {
            throw BizException.conflict("CHAT_SESSION_FORBIDDEN", "你不在该会话中");
        }
        return session;
    }

    public Long getPeerUserId(ChatSession session, Long currentUserId) {
        if (currentUserId.equals(session.getUserAId())) {
            return session.getUserBId();
        }
        if (currentUserId.equals(session.getUserBId())) {
            return session.getUserAId();
        }
        throw BizException.conflict("CHAT_SESSION_FORBIDDEN", "你不在该会话中");
    }

    public List<ChatSessionResponse> listSessions(Long userId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .and(w -> w.eq(ChatSession::getUserAId, userId).or().eq(ChatSession::getUserBId, userId))
                .orderByDesc(ChatSession::getUpdatedAt);

        List<ChatSession> sessions = chatSessionMapper.selectList(wrapper);
        List<ChatSessionResponse> result = new ArrayList<>();
        for (ChatSession session : sessions) {
            Long peerId = getPeerUserId(session, userId);
            User peer = userMapper.selectById(peerId);
            if (peer == null || peer.getStatus() == null || peer.getStatus() != 1) {
                continue;
            }

            ChatSessionResponse item = new ChatSessionResponse();
            item.setSessionId(session.getId());
            item.setFriendUserId(peerId);
            item.setFriendUsername(peer.getUsername());
            item.setFriendNickname(peer.getNickname());
            FriendRelation relation = friendMapper.selectOne(new LambdaQueryWrapper<FriendRelation>()
                    .eq(FriendRelation::getUserId, userId)
                    .eq(FriendRelation::getFriendUserId, peerId)
                    .last("LIMIT 1"));
            if (relation != null) {
                item.setFriendRemark(relation.getRemark());
            }
            if (peer.getAvatarPath() != null && !peer.getAvatarPath().trim().isEmpty()) {
                item.setFriendAvatarUrl("/api/user/avatar/" + peerId);
            } else {
                item.setFriendAvatarUrl(null);
            }

            ChatMessage lastMessage = chatMessageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getSessionId, session.getId())
                    .orderByDesc(ChatMessage::getId)
                    .last("LIMIT 1"));
            if (lastMessage != null) {
                item.setLastMessageAt(lastMessage.getSentAt());
                if ("file".equalsIgnoreCase(lastMessage.getMessageType())) {
                    item.setLastMessagePreview("[文件]");
                } else {
                    item.setLastMessagePreview("[文本消息]");
                }
            }

            Long unreadCount = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getSessionId, session.getId())
                    .eq(ChatMessage::getReceiverId, userId)
                    .eq(ChatMessage::getIsRead, 0));
            item.setUnreadCount(unreadCount == null ? 0L : unreadCount);

            result.add(item);
        }
        return result;
    }

    public void touchSession(Long sessionId, LocalDateTime messageTime) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ChatSession> update = new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, sessionId)
                .set(ChatSession::getUpdatedAt, now)
                .set(ChatSession::getLastMessageAt, messageTime == null ? now : messageTime);
        chatSessionMapper.update(null, update);
    }

    private boolean isFriend(Long userId, Long friendUserId) {
        Long count = friendMapper.selectCount(new LambdaQueryWrapper<com.security.filemanager.entity.FriendRelation>()
                .eq(com.security.filemanager.entity.FriendRelation::getUserId, userId)
                .eq(com.security.filemanager.entity.FriendRelation::getFriendUserId, friendUserId));
        return count != null && count > 0;
    }

    private ChatSession findByPair(Long userA, Long userB) {
        return chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserAId, userA)
                .eq(ChatSession::getUserBId, userB)
                .last("LIMIT 1"));
    }
}
