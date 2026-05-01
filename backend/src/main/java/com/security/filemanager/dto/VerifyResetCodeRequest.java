package com.security.filemanager.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 找回密码 - 验证验证码
 */
@Data
@ApiModel("验证码校验请求")
public class VerifyResetCodeRequest {

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
}
