package com.security.filemanager.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class ChatMessageSendFileRequest {

    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    @NotBlank(message = "客户端消息ID不能为空")
    @Size(max = 64, message = "客户端消息ID不能超过64字符")
    private String clientMsgId;
}
