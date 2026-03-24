package com.security.filemanager.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.security.filemanager.annotation.RequireAdmin;
import com.security.filemanager.dto.Result;
import com.security.filemanager.entity.User;
import com.security.filemanager.mapper.UserMapper;
import com.security.filemanager.service.FileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 管理员-用户管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/user")
@Api(tags = "管理员-用户管理")
public class AdminUserController {
    
    @Resource
    private UserMapper userMapper;
    
    @Resource
    private FileService fileService;
    
    /**
     * 获取所有用户列表（分页）
     */
    @GetMapping("/list")
    @RequireAdmin
    @ApiOperation("获取用户列表")
    public Result<Page<User>> getUserList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String username) {
        
        Page<User> pageParam = new Page<>(page, size);
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        
        if (username != null && !username.trim().isEmpty()) {
            wrapper.like("username", username);
        }
        
        wrapper.orderByDesc("created_at");
        Page<User> result = userMapper.selectPage(pageParam, wrapper);
        
        // 隐藏敏感信息
        result.getRecords().forEach(user -> {
            user.setPasswordHash(null);
            user.setPasswordSalt(null);
            user.setMasterKeyEncrypted(null);
            user.setMasterKeyIv(null);
        });
        
        return Result.success(result);
    }
    
    /**
     * 禁用/启用用户
     */
    @PutMapping("/{userId}/status")
    @RequireAdmin
    @ApiOperation("更新用户状态")
    public Result<Void> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam Integer status) {

        if (status == null || (status != 0 && status != 1)) {
            return Result.error("状态参数错误");
        }
        
        // 获取当前管理员ID，禁止禁用自己
        Long currentUserId = com.security.filemanager.interceptor.AuthInterceptor.getCurrentUserId();
        if (userId.equals(currentUserId) && status == 0) {
            return Result.error("不能禁用自己的账号");
        }
        
        // 检查用户是否存在
        User existingUser = userMapper.selectById(userId);
        if (existingUser == null) {
            return Result.error("用户不存在");
        }
        
        User user = new User();
        user.setId(userId);
        user.setStatus(status);
        user.setUpdatedAt(java.time.LocalDateTime.now());
        int updated = userMapper.updateById(user);
        if (updated == 0) {
            return Result.error("更新失败，请重试");
        }
        
        log.info("管理员更新用户状态: userId={}, status={}", userId, status);
        return Result.success();
    }
    
    /**
     * 删除用户
     */
    @DeleteMapping("/{userId}")
    @RequireAdmin
    @ApiOperation("删除用户")
    public Result<Void> deleteUser(@PathVariable Long userId) {
        // 获取当前管理员ID，禁止删除自己
        Long currentUserId = com.security.filemanager.interceptor.AuthInterceptor.getCurrentUserId();
        if (userId.equals(currentUserId)) {
            return Result.error("不能删除自己的账号");
        }
        
        // 检查用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        
        // 删除用户的所有文件
        fileService.deleteAllUserFiles(userId);
        
        // 删除用户
        userMapper.deleteById(userId);
        
        log.info("管理员删除用户: userId={}", userId);
        return Result.success();
    }
}
