package com.security.filemanager.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendRequestItemResponse {

    private Long requestId;

    private Integer status;

    private String message;

    private LocalDateTime createdAt;

    private LocalDateTime handledAt;

    private FriendUserResponse fromUser;
}
