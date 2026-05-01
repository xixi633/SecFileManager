package com.security.filemanager.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ChatMessageReadRequest {

    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    @NotNull(message = "消息ID不能为空")
    private Long messageId;
}
