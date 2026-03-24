package com.security.filemanager.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 用户注册请求
 */
@Data
@ApiModel("用户注册请求")
public class RegisterRequest {
    
    @ApiModelProperty(value = "用户名", required = true, example = "zhangsan")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度应在3-20个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;
    
    @ApiModelProperty(value = "密码", required = true, example = "Password123!")
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度应在8-32个字符之间")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", 
             message = "密码必须包含大写字母、小写字母和数字")
    private String password;
    
    @ApiModelProperty(value = "邮箱", example = "zhangsan@example.com")
    @Pattern(regexp = "^$|^[\\w-]+(\\.[\\w-]+)*@[\\w-]+(\\.[\\w-]+)+$", 
             message = "邮箱格式不正确")
    private String email;
}
