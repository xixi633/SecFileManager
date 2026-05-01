package com.security.filemanager.controller;

import com.security.filemanager.dto.FriendRequestCreateRequest;
import com.security.filemanager.dto.FriendRequestItemResponse;
import com.security.filemanager.dto.FriendRemarkUpdateRequest;
import com.security.filemanager.dto.FriendUserResponse;
import com.security.filemanager.dto.Result;
import com.security.filemanager.interceptor.AuthInterceptor;
import com.security.filemanager.service.FriendService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/friend")
@Api(tags = "好友管理")
public class FriendController {

    @Resource
    private FriendService friendService;

    @PostMapping("/request")
    @ApiOperation("发送好友申请")
    public Result<Void> sendFriendRequest(@Validated @RequestBody FriendRequestCreateRequest request) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        friendService.sendRequest(currentUserId, request);
        return Result.success("好友申请已发送", null);
    }

    @GetMapping("/request/incoming")
    @ApiOperation("查询收到的好友申请")
    public Result<List<FriendRequestItemResponse>> listIncomingRequests() {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        return Result.success(friendService.listIncomingRequests(currentUserId));
    }

    @PostMapping("/request/{requestId}/accept")
    @ApiOperation("通过好友申请")
    public Result<Void> acceptRequest(@PathVariable Long requestId) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        friendService.handleRequest(currentUserId, requestId, true);
        return Result.success("已同意好友申请", null);
    }

    @PostMapping("/request/{requestId}/reject")
    @ApiOperation("拒绝好友申请")
    public Result<Void> rejectRequest(@PathVariable Long requestId) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        friendService.handleRequest(currentUserId, requestId, false);
        return Result.success("已拒绝好友申请", null);
    }

    @GetMapping("/list")
    @ApiOperation("查询好友列表")
    public Result<List<FriendUserResponse>> listFriends() {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        return Result.success(friendService.listFriends(currentUserId));
    }

    @GetMapping("/search")
    @ApiOperation("搜索用户（用于加好友）")
    public Result<List<FriendUserResponse>> searchUsers(@RequestParam("keyword") String keyword) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        return Result.success(friendService.searchUsers(currentUserId, keyword));
    }

    @DeleteMapping("/{friendUserId}")
    @ApiOperation("删除好友")
    public Result<Void> removeFriend(@PathVariable Long friendUserId) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        friendService.removeFriend(currentUserId, friendUserId);
        return Result.success("好友已删除", null);
    }

    @PutMapping("/{friendUserId}/remark")
    @ApiOperation("设置好友备注")
    public Result<Void> updateRemark(@PathVariable Long friendUserId,
                                     @Validated @RequestBody FriendRemarkUpdateRequest request) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        friendService.updateRemark(currentUserId, friendUserId, request.getRemark());
        return Result.success("备注已更新", null);
    }
}
