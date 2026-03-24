package com.security.filemanager.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import javax.validation.constraints.NotBlank;

/**
 * 用户资料更新请求
 */
@Data
@ApiModel("用户资料更新请求")
public class UpdateProfileRequest {

    @ApiModelProperty(value = "用户名")
    @Size(min = 3, max = 20, message = "用户名长度应在3-20个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @ApiModelProperty(value = "昵称")
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;

    @ApiModelProperty(value = "邮箱")
    @Pattern(regexp = "^$|^[\\w-]+(\\.[\\w-]+)*@[\\w-]+(\\.[\\w-]+)+$",
            message = "邮箱格式不正确")
    private String email;

    @ApiModelProperty(value = "确认密码（修改用户名时必填）")
    private String confirmPassword;
}
