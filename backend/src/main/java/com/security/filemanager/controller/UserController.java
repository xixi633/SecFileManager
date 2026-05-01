package com.security.filemanager.controller;

import com.security.filemanager.dto.ForgotPasswordRequest;
import com.security.filemanager.dto.LoginRequest;
import com.security.filemanager.dto.LoginResponse;
import com.security.filemanager.dto.RegisterRequest;
import com.security.filemanager.dto.ResetPasswordRequest;
import com.security.filemanager.dto.Result;
import com.security.filemanager.dto.UpdatePasswordRequest;
import com.security.filemanager.dto.UpdateProfileRequest;
import com.security.filemanager.dto.UserProfileResponse;
import com.security.filemanager.dto.VerifyResetCodeRequest;
import com.security.filemanager.interceptor.AuthInterceptor;
import com.security.filemanager.service.PasswordResetService;
import com.security.filemanager.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.annotation.Resource;
import java.net.URLConnection;

/**
 * 用户控制器
 * 
 * @author CourseDesign
 */
@Slf4j
@RestController
@RequestMapping("/user")
@Api(tags = "用户管理")
public class UserController {
    
    @Resource
    private UserService userService;

    @Resource
    private PasswordResetService passwordResetService;
    
    /**
     * 用户注册
     */
    @PostMapping("/register")
    @ApiOperation("用户注册")
    public Result<Void> register(@Validated @RequestBody RegisterRequest request) {
        userService.register(request);
        return Result.success("注册成功", null);
    }
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    @ApiOperation("用户登录")
    public Result<LoginResponse> login(@Validated @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.success("登录成功", response);
    }

    /**
     * 发送找回密码验证码
     */
    @PostMapping("/password/reset/request")
    @ApiOperation("发送找回密码验证码")
    public Result<Void> sendResetCode(@Validated @RequestBody ForgotPasswordRequest request) {
        passwordResetService.sendResetCode(request);
        return Result.success("验证码已发送", null);
    }

    /**
     * 校验找回密码验证码
     */
    @PostMapping("/password/reset/verify")
    @ApiOperation("校验找回密码验证码")
    public Result<Void> verifyResetCode(@Validated @RequestBody VerifyResetCodeRequest request) {
        passwordResetService.verifyCode(request);
        return Result.success("验证码校验通过", null);
    }

    /**
     * 找回密码重置
     */
    @PostMapping("/password/reset/confirm")
    @ApiOperation("找回密码重置")
    public Result<Void> resetPassword(@Validated @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return Result.success("密码已更新", null);
    }
    
    /**
     * 注销账号
     */
    @DeleteMapping("/account")
    @ApiOperation("注销账号")
    public Result<Void> deleteAccount() {
        Long userId = AuthInterceptor.getCurrentUserId();
        userService.deleteAccount(userId);
        log.info("用户 {} 注销账号", userId);
        return Result.success("账号已注销", null);
    }

    /**
     * 获取用户资料
     */
    @GetMapping("/profile")
    @ApiOperation("获取用户资料")
    public Result<UserProfileResponse> getProfile() {
        Long userId = AuthInterceptor.getCurrentUserId();
        UserProfileResponse response = userService.getProfile(userId);
        return Result.success(response);
    }

    /**
     * 更新用户资料
     */
    @PutMapping("/profile")
    @ApiOperation("更新用户资料")
    public Result<Void> updateProfile(@Validated @RequestBody UpdateProfileRequest request) {
        Long userId = AuthInterceptor.getCurrentUserId();
        userService.updateProfile(userId, request);
        return Result.success("更新成功", null);
    }

    /**
     * 更新用户密码
     */
    @PutMapping("/password")
    @ApiOperation("更新用户密码")
    public Result<Void> updatePassword(@Validated @RequestBody UpdatePasswordRequest request) {
        Long userId = AuthInterceptor.getCurrentUserId();
        userService.updatePassword(userId, request);
        return Result.success("密码已更新", null);
    }

    /**
     * 上传用户头像
     */
    @PostMapping("/avatar")
    @ApiOperation("上传用户头像")
    public Result<Void> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = AuthInterceptor.getCurrentUserId();
        userService.updateAvatar(userId, file);
        return Result.success("头像已更新", null);
    }

    /**
     * 获取用户头像
     */
    @GetMapping("/avatar")
    @ApiOperation("获取用户头像")
    public ResponseEntity<byte[]> getAvatar() {
        Long userId = AuthInterceptor.getCurrentUserId();
        byte[] data = userService.getAvatarData(userId);
        String avatarPath = userService.getUserById(userId).getAvatarPath();

        String contentType = URLConnection.guessContentTypeFromName(avatarPath);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(data.length);

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    /**
     * 获取指定用户头像
     */
    @GetMapping("/avatar/{userId}")
    @ApiOperation("获取指定用户头像")
    public ResponseEntity<byte[]> getAvatarByUserId(@PathVariable Long userId) {
        try {
            byte[] data = userService.getAvatarData(userId);
            String avatarPath = userService.getUserById(userId).getAvatarPath();

            String contentType = URLConnection.guessContentTypeFromName(avatarPath);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentLength(data.length);
            return new ResponseEntity<>(data, headers, HttpStatus.OK);
        } catch (RuntimeException ex) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
