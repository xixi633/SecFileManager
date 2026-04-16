package com.security.filemanager.controller;

import com.security.filemanager.dto.AiChatRequest;
import com.security.filemanager.interceptor.AuthInterceptor;
import com.security.filemanager.service.AiChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/ai")
public class AiChatController {

    @Resource
    private AiChatService aiChatService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);
        Long userId = AuthInterceptor.getCurrentUserId();
        List<AiChatRequest.MessageItem> messages = request.getMessages();
        aiChatService.streamChat(messages, userId, emitter);
        return emitter;
    }
}
