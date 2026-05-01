package com.security.filemanager.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionResponse {

    private Long sessionId;

    private Long friendUserId;

    private String friendUsername;

    private String friendNickname;

    private String friendRemark;

    private String friendAvatarUrl;

    private String lastMessagePreview;

    private LocalDateTime lastMessageAt;

    private Long unreadCount;
}
