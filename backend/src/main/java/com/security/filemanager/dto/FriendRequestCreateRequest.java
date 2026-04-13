package com.security.filemanager.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class FriendRequestCreateRequest {

    @NotNull(message = "目标用户不能为空")
    private Long toUserId;

    @Size(max = 255, message = "留言不能超过255字符")
    private String message;
}
