package com.security.filemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatSessionOpenResponse {

    private Long sessionId;

    private Long friendUserId;
}
