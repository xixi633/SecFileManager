package com.security.filemanager.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.security.filemanager.dto.ChatMessageReadRequest;
import com.security.filemanager.dto.ChatMessageResponse;
import com.security.filemanager.dto.ChatMessageSendFileRequest;
import com.security.filemanager.dto.ChatMessageSendTextRequest;
import com.security.filemanager.dto.ChatSessionOpenResponse;
import com.security.filemanager.dto.ChatSessionResponse;
import com.security.filemanager.dto.FriendRemarkUpdateRequest;
import com.security.filemanager.dto.Result;
import com.security.filemanager.entity.ChatSession;
import com.security.filemanager.interceptor.AuthInterceptor;
import com.security.filemanager.service.ChatFileShareService;
import com.security.filemanager.service.ChatMessageService;
import com.security.filemanager.service.ChatSessionService;
import com.security.filemanager.service.FileService;
import com.security.filemanager.service.FriendService;
import com.security.filemanager.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.net.URLEncoder;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chat")
@Api(tags = "聊天管理")
public class ChatController {

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private ChatFileShareService chatFileShareService;

    @Resource
    private FriendService friendService;

    @Resource
    private UserService userService;

    @GetMapping("/session/{friendUserId}")
    @ApiOperation("打开或创建会话")
    public Result<ChatSessionOpenResponse> openSession(@PathVariable Long friendUserId) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        ChatSession session = chatSessionService.getOrCreateSession(currentUserId, friendUserId);
        return Result.success(new ChatSessionOpenResponse(session.getId(), friendUserId));
    }

    @PutMapping("/session/{friendUserId}/remark")
    public Result<Void> updateSessionRemark(@PathVariable Long friendUserId,
                                            @Validated @RequestBody FriendRemarkUpdateRequest request) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        friendService.updateRemark(currentUserId, friendUserId, request.getRemark());
        return Result.success("备注已更新", null);
    }

    @PostMapping("/background")
    public Result<Void> uploadChatBackground(@RequestParam("file") MultipartFile file,
                                             @RequestParam("sessionId") Long sessionId) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        userService.updateChatBackground(currentUserId, sessionId, file);
        return Result.success("聊天背景已更新", null);
    }

    @GetMapping("/background")
    public ResponseEntity<byte[]> getChatBackground(@RequestParam("sessionId") Long sessionId) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        byte[] data;
        String backgroundPath;
        try {
            data = userService.getChatBackgroundData(currentUserId, sessionId);
            backgroundPath = userService.getChatBackgroundPath(currentUserId, sessionId);
        } catch (RuntimeException ex) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        String contentType = URLConnection.guessContentTypeFromName(backgroundPath);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(data.length);

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    @GetMapping("/sessions")
    @ApiOperation("获取会话列表")
    public Result<List<ChatSessionResponse>> listSessions() {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        return Result.success(chatSessionService.listSessions(currentUserId));
    }

    @GetMapping("/messages/{sessionId}")
    @ApiOperation("获取消息历史")
    public Result<Page<ChatMessageResponse>> listMessages(@PathVariable Long sessionId,
                                                          @RequestParam(value = "page", required = false) Long page,
                                                          @RequestParam(value = "size", required = false) Long size) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        return Result.success(chatMessageService.listMessages(currentUserId, sessionId, page, size));
    }

    @PostMapping("/message/text")
    @ApiOperation("发送文本消息")
    public Result<ChatMessageResponse> sendTextMessage(@Validated @RequestBody ChatMessageSendTextRequest request) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        ChatMessageResponse response = chatMessageService.sendTextMessage(currentUserId, request);
        return Result.success("发送成功", response);
    }

    @PostMapping("/message/file")
    @ApiOperation("发送文件消息")
    public Result<ChatMessageResponse> sendFileMessage(@Validated @RequestBody ChatMessageSendFileRequest request) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        ChatMessageResponse response = chatMessageService.sendFileMessage(currentUserId, request);
        return Result.success("发送成功", response);
    }

    @PostMapping("/message/read")
    @ApiOperation("标记消息已读")
    public Result<Void> markAsRead(@Validated @RequestBody ChatMessageReadRequest request) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        chatMessageService.markAsRead(currentUserId, request);
        return Result.success("已标记为已读", null);
    }

    @GetMapping("/unread/count")
    @ApiOperation("获取未读消息总数")
    public Result<Map<String, Long>> unreadCount() {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        Long unread = chatMessageService.countUnread(currentUserId);
        Map<String, Long> data = new HashMap<>();
        data.put("unread", unread);
        return Result.success(data);
    }

    @DeleteMapping("/messages/{sessionId}")
    @ApiOperation("删除聊天记录")
    public Result<Void> deleteMessages(@PathVariable Long sessionId) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        chatMessageService.deleteMessagesBySessionId(currentUserId, sessionId);
        return Result.success("聊天记录已删除", null);
    }

    @GetMapping("/messages/{sessionId}/search")
    @ApiOperation("搜索聊天消息")
    public Result<List<ChatMessageResponse>> searchMessages(@PathVariable Long sessionId,
                                                             @RequestParam("keyword") String keyword) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        return Result.success(chatMessageService.searchMessages(currentUserId, sessionId, keyword));
    }

    @GetMapping("/messages/{sessionId}/files")
    @ApiOperation("获取聊天中的文件列表")
    public Result<List<ChatMessageResponse>> listFileMessages(@PathVariable Long sessionId) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        return Result.success(chatMessageService.listFileMessages(currentUserId, sessionId));
    }

    @GetMapping("/file/download/{messageId}")
    @ApiOperation("下载聊天文件")
    public ResponseEntity<byte[]> downloadSharedFile(@PathVariable Long messageId) {
        Long currentUserId = AuthInterceptor.getCurrentUserId();
        FileService.FileDownloadResult result = chatFileShareService.downloadSharedFile(messageId, currentUserId);

        String encodedFilename = URLEncoder.encode(result.getFilename(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        String contentType = result.getContentType();
        if (contentType == null || contentType.trim().isEmpty()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(result.getData().length);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + result.getFilename() + "\"; filename*=UTF-8''" + encodedFilename);

        return new ResponseEntity<>(result.getData(), headers, HttpStatus.OK);
    }
}
