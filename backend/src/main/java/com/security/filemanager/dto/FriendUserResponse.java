package com.security.filemanager.dto;

import lombok.Data;

@Data
public class FriendUserResponse {

    private Long userId;

    private String username;

    private String nickname;

    private String remark;

    private String avatarUrl;
}
