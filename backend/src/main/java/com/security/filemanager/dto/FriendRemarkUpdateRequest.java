package com.security.filemanager.dto;

import lombok.Data;

import javax.validation.constraints.Size;

@Data
public class FriendRemarkUpdateRequest {

    @Size(max = 100, message = "备注长度不能超过100字符")
    private String remark;
}
