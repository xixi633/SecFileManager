package com.security.filemanager.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.security.filemanager.dto.FriendRequestCreateRequest;
import com.security.filemanager.dto.FriendRequestItemResponse;
import com.security.filemanager.dto.FriendUserResponse;
import com.security.filemanager.entity.FriendRelation;
import com.security.filemanager.entity.FriendRequest;
import com.security.filemanager.entity.User;
import com.security.filemanager.exception.BizException;
import com.security.filemanager.mapper.FriendMapper;
import com.security.filemanager.mapper.FriendRequestMapper;
import com.security.filemanager.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class FriendService {

    @Resource
    private FriendRequestMapper friendRequestMapper;

    @Resource
    private FriendMapper friendMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ChatSessionService chatSessionService;

    @Transactional(rollbackFor = Exception.class)
    public void sendRequest(Long currentUserId, FriendRequestCreateRequest request) {
        Long toUserId = request.getToUserId();
        if (toUserId == null) {
            throw BizException.badRequest("FRIEND_TARGET_REQUIRED", "目标用户不能为空");
        }
        if (currentUserId.equals(toUserId)) {
            throw BizException.badRequest("FRIEND_SELF_NOT_ALLOWED", "不能添加自己为好友");
        }

        User targetUser = userMapper.selectById(toUserId);
        if (targetUser == null || targetUser.getStatus() == null || targetUser.getStatus() != 1) {
            throw BizException.badRequest("FRIEND_TARGET_INVALID", "目标用户不存在或不可用");
        }

        if (isFriend(currentUserId, toUserId)) {
            throw BizException.conflict("FRIEND_ALREADY", "你们已经是好友");
        }

        FriendRequest duplicatePending = friendRequestMapper.selectOne(new LambdaQueryWrapper<FriendRequest>()
                .eq(FriendRequest::getFromUserId, currentUserId)
                .eq(FriendRequest::getToUserId, toUserId)
                .eq(FriendRequest::getStatus, 0)
                .last("LIMIT 1"));
        if (duplicatePending != null) {
            throw BizException.conflict("FRIEND_REQUEST_DUPLICATE", "好友申请已发送，请等待对方处理");
        }

        FriendRequest reversePending = friendRequestMapper.selectOne(new LambdaQueryWrapper<FriendRequest>()
                .eq(FriendRequest::getFromUserId, toUserId)
                .eq(FriendRequest::getToUserId, currentUserId)
                .eq(FriendRequest::getStatus, 0)
                .last("LIMIT 1"));
        if (reversePending != null) {
            reversePending.setStatus(1);
            reversePending.setHandledAt(LocalDateTime.now());
            friendRequestMapper.updateById(reversePending);
            createFriendRelationPair(currentUserId, toUserId);
            chatSessionService.getOrCreateSession(currentUserId, toUserId, false);
            return;
        }

        FriendRequest friendRequest = new FriendRequest();
        friendRequest.setFromUserId(currentUserId);
        friendRequest.setToUserId(toUserId);
        friendRequest.setStatus(0);
        friendRequest.setMessage(request.getMessage());
        friendRequest.setCreatedAt(LocalDateTime.now());
        friendRequestMapper.insert(friendRequest);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleRequest(Long currentUserId, Long requestId, boolean accept) {
        FriendRequest friendRequest = friendRequestMapper.selectById(requestId);
        if (friendRequest == null) {
            throw BizException.badRequest("FRIEND_REQUEST_NOT_FOUND", "好友申请不存在");
        }
        if (!currentUserId.equals(friendRequest.getToUserId())) {
            throw BizException.conflict("FRIEND_REQUEST_FORBIDDEN", "你不能处理该好友申请");
        }
        if (friendRequest.getStatus() == null || friendRequest.getStatus() != 0) {
            throw BizException.conflict("FRIEND_REQUEST_HANDLED", "该好友申请已处理");
        }

        friendRequest.setStatus(accept ? 1 : 2);
        friendRequest.setHandledAt(LocalDateTime.now());
        friendRequestMapper.updateById(friendRequest);

        if (accept) {
            createFriendRelationPair(friendRequest.getFromUserId(), friendRequest.getToUserId());
            chatSessionService.getOrCreateSession(friendRequest.getFromUserId(), friendRequest.getToUserId(), false);
        }
    }

    public List<FriendRequestItemResponse> listIncomingRequests(Long currentUserId) {
        List<FriendRequest> requests = friendRequestMapper.selectList(new LambdaQueryWrapper<FriendRequest>()
                .eq(FriendRequest::getToUserId, currentUserId)
                .orderByDesc(FriendRequest::getCreatedAt)
                .last("LIMIT 100"));

        List<FriendRequestItemResponse> result = new ArrayList<>();
        for (FriendRequest request : requests) {
            User fromUser = userMapper.selectById(request.getFromUserId());
            if (fromUser == null) {
                continue;
            }

            FriendRequestItemResponse item = new FriendRequestItemResponse();
            item.setRequestId(request.getId());
            item.setStatus(request.getStatus());
            item.setMessage(request.getMessage());
            item.setCreatedAt(request.getCreatedAt());
            item.setHandledAt(request.getHandledAt());
            item.setFromUser(toFriendUser(fromUser, null));
            result.add(item);
        }
        return result;
    }

    public List<FriendUserResponse> listFriends(Long currentUserId) {
        List<FriendRelation> relations = friendMapper.selectList(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, currentUserId)
                .orderByDesc(FriendRelation::getCreatedAt));

        List<FriendUserResponse> result = new ArrayList<>();
        for (FriendRelation relation : relations) {
            User user = userMapper.selectById(relation.getFriendUserId());
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                continue;
            }
            result.add(toFriendUser(user, relation.getRemark()));
        }
        return result;
    }

    public List<FriendUserResponse> searchUsers(Long currentUserId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String trimmedKeyword = keyword.trim();
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .eq(User::getRole, "user")
                .ne(User::getId, currentUserId)
                .and(w -> w.like(User::getUsername, trimmedKeyword)
                        .or()
                        .like(User::getNickname, trimmedKeyword))
                .last("LIMIT 20"));

        Set<Long> friendIds = new HashSet<>();
        List<FriendRelation> relations = friendMapper.selectList(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, currentUserId));
        for (FriendRelation relation : relations) {
            friendIds.add(relation.getFriendUserId());
        }

        List<FriendUserResponse> result = new ArrayList<>();
        for (User user : users) {
            if (friendIds.contains(user.getId())) {
                continue;
            }
            result.add(toFriendUser(user, null));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRemark(Long currentUserId, Long friendUserId, String remark) {
        FriendRelation relation = friendMapper.selectOne(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, currentUserId)
                .eq(FriendRelation::getFriendUserId, friendUserId)
                .last("LIMIT 1"));
        if (relation == null) {
            throw BizException.conflict("FRIEND_NOT_FOUND", "该用户不是你的好友");
        }

        String normalizedRemark = null;
        if (remark != null) {
            normalizedRemark = remark.trim();
            if (normalizedRemark.isEmpty()) {
                normalizedRemark = null;
            }
        }

        friendMapper.update(null, new LambdaUpdateWrapper<FriendRelation>()
                .eq(FriendRelation::getId, relation.getId())
                .set(FriendRelation::getRemark, normalizedRemark));
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeFriend(Long currentUserId, Long friendUserId) {
        friendMapper.delete(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, currentUserId)
                .eq(FriendRelation::getFriendUserId, friendUserId));
        friendMapper.delete(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, friendUserId)
                .eq(FriendRelation::getFriendUserId, currentUserId));
    }

    public boolean isFriend(Long currentUserId, Long friendUserId) {
        Long count = friendMapper.selectCount(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, currentUserId)
                .eq(FriendRelation::getFriendUserId, friendUserId));
        return count != null && count > 0;
    }

    private FriendUserResponse toFriendUser(User user, String remark) {
        FriendUserResponse response = new FriendUserResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRemark(remark);
        if (user.getAvatarPath() != null && !user.getAvatarPath().trim().isEmpty()) {
            response.setAvatarUrl("/api/user/avatar/" + user.getId());
        } else {
            response.setAvatarUrl(null);
        }
        return response;
    }

    private void createFriendRelationPair(Long userId, Long friendUserId) {
        insertFriendRelation(userId, friendUserId);
        insertFriendRelation(friendUserId, userId);
    }

    private void insertFriendRelation(Long userId, Long friendUserId) {
        FriendRelation relation = new FriendRelation();
        relation.setUserId(userId);
        relation.setFriendUserId(friendUserId);
        relation.setCreatedAt(LocalDateTime.now());
        try {
            friendMapper.insert(relation);
        } catch (DuplicateKeyException ignored) {
            // already inserted
        }
    }
}
