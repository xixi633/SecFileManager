package com.security.filemanager.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文件实体类
 * 
 * 安全设计说明：
 * 1. 文件内容加密存储（AES-256-GCM）
 * 2. 文件密钥使用用户主密钥加密（密钥嵌套保护）
 * 3. 记录完整性校验参数（哈希值、认证标签）
 * 4. 强制用户隔离（user_id字段）
 */
@Data
@TableName("t_file")
public class FileInfo {
    
    /**
     * 文件ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 文件所有者ID
     * 【核心安全字段】所有查询必须带此条件，实现用户间完全隔离
     */
    private Long userId;
    
    /**
     * 原始文件名
     */
    private String originalFilename;
    
    /**
     * 原始文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文件MIME类型
     */
    private String fileType;
    
    /**
     * 加密文件存储路径
     * 使用UUID随机生成，防止路径遍历攻击
     * 示例：/data/secure-files/2024/01/550e8400-e29b-41d4-a716-446655440000.enc
     */
    private String storagePath;
    
    /**
     * 加密后文件大小
     */
    private Long encryptedSize;
    
    /**
     * 文件密钥（DEK - Data Encryption Key）
     * 【加密存储】使用用户主密钥加密后的Base64编码
     * 
     * 【为什么每个文件独立密钥】
     * - 防止一次密钥泄露导致所有文件泄露
     * - 支持密钥轮换（Key Rotation）
     * - 符合最小权限原则
     */
    private String encryptedFileKey;
    
    /**
     * 加密初始化向量（IV，Hex编码）
     * 每个文件随机生成，保证相同文件加密结果不同
     * GCM模式推荐96位（12字节）
     */
    private String iv;
    
    /**
     * GCM认证标签（Hex编码）
     * 用于解密时验证数据完整性和真实性
     * GCM模式自带AEAD（Authenticated Encryption with Associated Data）
     */
    private String authTag;
    
    /**
     * 原始文件SHA-256哈希值（Hex编码）
     * 用于下载后二次校验完整性
     * 
     * 【为什么需要双重校验】
     * - GCM的authTag保证加密数据完整性
     * - SHA-256保证原始文件完整性
     * - 双重保险，防止解密过程中的错误
     */
    private String fileHash;
    
    /**
     * 上传时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime uploadTime;
    
    /**
     * 最后下载时间
     */
    private LocalDateTime lastDownloadTime;
    
    /**
     * 下载次数
     */
    private Integer downloadCount;
    
    /**
     * 文件描述
     */
    private String description;
    
    /**
     * 是否为文件夹（ZIP压缩包）
     * 0-普通文件  1-文件夹（作为ZIP上传）
     */
    private Integer isFolder;

    /**
     * 逻辑删除标记
     * 0-正常 1-已删除
     */
    @TableLogic
    private Integer deleted;
}
