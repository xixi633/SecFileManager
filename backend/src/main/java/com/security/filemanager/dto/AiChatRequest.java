package com.security.filemanager.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiChatRequest {

    private List<MessageItem> messages;

    @Data
    public static class MessageItem {
        private String role;
        private String content;
    }
}
