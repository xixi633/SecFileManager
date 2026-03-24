package com.security.filemanager.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用户资料响应
 */
@Data
@AllArgsConstructor
@ApiModel("用户资料响应")
public class UserProfileResponse {

    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("用户名")
    private String username;

    @ApiModelProperty("昵称")
    private String nickname;

    @ApiModelProperty("邮箱")
    private String email;

    @ApiModelProperty("角色")
    private String role;

    @ApiModelProperty("状态")
    private Integer status;

    @ApiModelProperty("头像URL")
    private String avatarUrl;
}
