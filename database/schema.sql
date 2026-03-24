-- =====================================================
-- 安全文件管理系统 - 数据库表结构
-- =====================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS secure_file_manager 
    DEFAULT CHARACTER SET utf8mb4 
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE secure_file_manager;

-- =====================================================
-- 1. 用户表
-- =====================================================
DROP TABLE IF EXISTS t_file;
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    
    -- 密码安全存储
    password_hash VARCHAR(256) NOT NULL COMMENT '密码哈希值（PBKDF2-HMAC-SHA256）',
    password_salt VARCHAR(64) NOT NULL COMMENT '密码盐值（随机生成，Hex编码）',
    
    -- 用户主密钥（用于加密文件密钥）
    master_key_encrypted VARCHAR(256) NOT NULL COMMENT '用户主密钥（使用系统密钥加密，Base64编码）',
    master_key_iv VARCHAR(64) NOT NULL COMMENT '主密钥加密的IV',
    
    -- 元数据
    email VARCHAR(100) COMMENT '邮箱',
    nickname VARCHAR(50) COMMENT '昵称',
    avatar_path VARCHAR(512) COMMENT '头像存储路径',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    role VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '用户角色：admin-管理员, user-普通用户',
    
    INDEX idx_username (username),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- =====================================================
-- 2. 文件表
-- =====================================================
DROP TABLE IF EXISTS t_file;
CREATE TABLE t_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文件ID',
    
    -- 权限隔离（核心安全字段）
    user_id BIGINT NOT NULL COMMENT '文件所有者ID',
    
    -- 文件基本信息
    original_filename VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_size BIGINT NOT NULL COMMENT '原始文件大小（字节）',
    file_type VARCHAR(100) COMMENT '文件MIME类型',
    
    -- 加密存储相关
    storage_path VARCHAR(512) NOT NULL COMMENT '加密文件存储路径（绝对路径）',
    encrypted_size BIGINT NOT NULL COMMENT '加密后文件大小',
    
    -- 加密参数（AES-256-GCM）
    encrypted_file_key TEXT NOT NULL COMMENT '文件密钥（DEK），使用用户主密钥加密后存储（Base64）',
    iv VARCHAR(64) NOT NULL COMMENT '加密初始化向量（IV，Hex编码，每个文件唯一）',
    auth_tag VARCHAR(64) NOT NULL COMMENT 'GCM认证标签（用于完整性校验）',
    
    -- 完整性校验
    file_hash VARCHAR(64) NOT NULL COMMENT '原始文件SHA-256哈希值（Hex编码）',
    
    -- 元数据
    upload_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    last_download_time DATETIME COMMENT '最后下载时间',
    download_count INT NOT NULL DEFAULT 0 COMMENT '下载次数',
    description TEXT COMMENT '文件描述',
    is_folder TINYINT NOT NULL DEFAULT 0 COMMENT '是否为文件夹：0-文件 1-文件夹（ZIP压缩包）',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    
    -- 索引优化
    INDEX idx_user_id (user_id),
    INDEX idx_upload_time (upload_time),
    INDEX idx_deleted (deleted),
    INDEX idx_user_upload (user_id, upload_time),
    
    -- 外键约束
    FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件表';

-- =====================================================
-- 3. 系统配置表（可选，用于存储系统级密钥）
-- =====================================================
DROP TABLE IF EXISTS t_system_config;
CREATE TABLE t_system_config (
    config_key VARCHAR(100) PRIMARY KEY COMMENT '配置键',
    config_value TEXT NOT NULL COMMENT '配置值（敏感值需加密）',
    description VARCHAR(500) COMMENT '配置说明',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- =====================================================
-- 字段安全说明
-- =====================================================
/*
【用户表安全字段说明】
1. password_hash：存储密码的PBKDF2哈希，防止数据库泄露后密码被破解
2. password_salt：每个用户独立盐值，防止彩虹表攻击
3. master_key_encrypted：用户主密钥（用于解密文件密钥），使用系统密钥加密后存储
   - 防止数据库泄露后攻击者直接获取用户密钥
   - 系统密钥存储在配置文件或环境变量中（不入库）

【文件表安全字段说明】
1. user_id：强制权限隔离，查询时必须带此条件
2. storage_path：使用UUID随机路径，防止路径遍历攻击
3. encrypted_file_key：文件专属密钥，使用用户主密钥加密
   - 实现每文件独立密钥，防止一次泄露影响所有文件
4. iv：每个文件独立IV，保证相同内容加密结果不同
5. auth_tag：GCM模式的认证标签，用于解密时校验完整性
6. file_hash：原始文件哈希，双重完整性校验
*/
