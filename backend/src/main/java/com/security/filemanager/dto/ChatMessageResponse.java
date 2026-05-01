package com.security.filemanager.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageResponse {

    private Long id;

    private Long sessionId;

    private Long senderId;

    private Long receiverId;

    private String messageType;

    private String content;

    private Long fileId;

    private String fileName;

    private Long fileSize;

    private LocalDateTime sentAt;

    private LocalDateTime readAt;

    private Boolean read;

    private Boolean own;
}
