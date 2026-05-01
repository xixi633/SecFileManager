package com.security.filemanager.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 找回密码 - 重置密码
 */
@Data
@ApiModel("重置密码请求")
public class ResetPasswordRequest {

    @ApiModelProperty(value = "用户名", required = true, example = "zhangsan")
    @NotBlank(message = "用户名不能为空")
    private String username;

    @ApiModelProperty(value = "邮箱", required = true, example = "zhangsan@example.com")
    @NotBlank(message = "邮箱不能为空")
    @Pattern(regexp = "^[\\w-]+(\\.[\\w-]+)*@[\\w-]+(\\.[\\w-]+)+$",
            message = "邮箱格式不正确")
    private String email;

    @ApiModelProperty(value = "验证码", required = true, example = "123456")
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
    private String code;

    @ApiModelProperty(value = "新密码", required = true, example = "Password123")
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度应在8-32个字符之间")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = "密码必须包含大写字母、小写字母和数字")
    private String newPassword;
}
