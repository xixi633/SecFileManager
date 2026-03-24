package com.security.filemanager.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体类
 * 
 * 安全设计说明：
 * 1. 密码不直接存储，使用PBKDF2哈希 + 盐值
 * 2. 用户主密钥加密后存储，用于保护文件密钥
 * 3. 每个用户的主密钥独立，实现用户间完全隔离
 */
@Data
@TableName("t_user")
public class User {
    
    /**
     * 用户ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户名（唯一）
     */
    private String username;
    
    /**
     * 密码哈希值
     * 使用 PBKDF2-HMAC-SHA256 算法生成
     * 防止数据库泄露后被暴力破解
     */
    private String passwordHash;
    
    /**
     * 密码盐值（Hex编码）
     * 每个用户独立随机生成
     * 防止彩虹表攻击
     */
    private String passwordSalt;
    
    /**
     * 用户主密钥（加密后存储，Base64编码）
     * 作用：加密/解密该用户的所有文件密钥
     * 加密方式：使用系统主密钥（配置文件中）进行AES-GCM加密
     * 
     * 【为什么加密存储】
     * - 防止数据库泄露后攻击者直接获取密钥
     * - 系统主密钥可独立管理（如存储在HSM中）
     */
    private String masterKeyEncrypted;
    
    /**
     * 主密钥加密时使用的IV（Hex编码）
     */
    private String masterKeyIv;
    
    /**
     * 邮箱
     */
    private String email;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像存储路径
     */
    private String avatarPath;
    
    /**
     * 注册时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    /**
     * 状态：0-禁用 1-正常
     */
    private Integer status;
    
    /**
     * 用户角色：admin-管理员, user-普通用户
     */
    private String role;
}
