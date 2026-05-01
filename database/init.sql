-- =====================================================
-- 安全文件管理系统 - 数据库表结构 (完整初始化脚本)
-- 包含基础表结构及所有后续新增字段合并
-- =====================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS secure_file_manager 
    DEFAULT CHARACTER SET utf8mb4 
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE secure_file_manager;

-- =====================================================
-- 1. 用户表
-- =====================================================
DROP TABLE IF EXISTS t_chat_file_share;
DROP TABLE IF EXISTS t_chat_read_cursor;
DROP TABLE IF EXISTS t_chat_message;
DROP TABLE IF EXISTS t_chat_session;
DROP TABLE IF EXISTS t_friend;
DROP TABLE IF EXISTS t_friend_request;
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
    chat_background_path VARCHAR(512) COMMENT '聊天背景图存储路径', -- 合并自 migration_add_chat_background.sql
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    role VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '用户角色：admin-管理员, user-普通用户',
    
    INDEX idx_username (username),
    INDEX idx_created_at (created_at),
    INDEX idx_role (role)  -- 合并自 migration_add_user_role.sql
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- =====================================================
-- 2. 文件表
-- =====================================================
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
    is_folder TINYINT NOT NULL DEFAULT 0 COMMENT '是否为文件夹：0-文件 1-文件夹（ZIP压缩包）',    -- 合并自 migration_add_is_folder.sql
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',                        -- 合并自 migration_add_deleted.sql
    
    -- 索引优化
    INDEX idx_user_id (user_id),
    INDEX idx_upload_time (upload_time),
    INDEX idx_deleted (deleted),                   -- 合并自 migration_add_deleted.sql
    INDEX idx_user_upload (user_id, upload_time),
    
    -- 外键约束
    FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件表';

-- =====================================================
-- 3. 系统配置表
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
-- 4. 好友申请表
-- =====================================================
CREATE TABLE t_friend_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '申请ID',
    from_user_id BIGINT NOT NULL COMMENT '申请人ID',
    to_user_id BIGINT NOT NULL COMMENT '接收人ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待处理 1-已通过 2-已拒绝',
    message VARCHAR(255) COMMENT '申请留言',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    handled_at DATETIME COMMENT '处理时间',

    INDEX idx_to_status (to_user_id, status, created_at),
    INDEX idx_from_status (from_user_id, status, created_at),

    FOREIGN KEY (from_user_id) REFERENCES t_user(id) ON DELETE CASCADE,
    FOREIGN KEY (to_user_id) REFERENCES t_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友申请表';

-- =====================================================
-- 5. 好友关系表（双向存储）
-- =====================================================
CREATE TABLE t_friend (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关系ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    friend_user_id BIGINT NOT NULL COMMENT '好友用户ID',
    remark VARCHAR(100) COMMENT '好友备注（仅对user_id侧生效）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '成为好友时间',

    UNIQUE KEY uk_user_friend (user_id, friend_user_id),
    INDEX idx_friend_user (friend_user_id),

    FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE,
    FOREIGN KEY (friend_user_id) REFERENCES t_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友关系表';

-- =====================================================
-- 6. 会话表（一期仅支持一对一）
-- =====================================================
CREATE TABLE t_chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '会话ID',
    user_a_id BIGINT NOT NULL COMMENT '较小用户ID（应用层保证 user_a_id < user_b_id）',
    user_b_id BIGINT NOT NULL COMMENT '较大用户ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_message_at DATETIME COMMENT '最后消息时间',

    UNIQUE KEY uk_user_pair (user_a_id, user_b_id),
    INDEX idx_updated_at (updated_at),

    FOREIGN KEY (user_a_id) REFERENCES t_user(id) ON DELETE CASCADE,
    FOREIGN KEY (user_b_id) REFERENCES t_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

-- =====================================================
-- 7. 消息表
-- =====================================================
CREATE TABLE t_chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    receiver_id BIGINT NOT NULL COMMENT '接收者ID',
    client_msg_id VARCHAR(64) NOT NULL COMMENT '客户端幂等消息ID',
    message_type VARCHAR(20) NOT NULL DEFAULT 'text' COMMENT '消息类型：text/file',
    content_ciphertext LONGTEXT COMMENT '文本消息密文（Base64）',
    content_iv VARCHAR(64) COMMENT '文本消息IV（Hex）',
    content_auth_tag VARCHAR(64) COMMENT '文本消息GCM认证标签（Hex）',
    file_id BIGINT COMMENT '文件消息关联文件ID',
    sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    read_at DATETIME COMMENT '已读时间',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',

    UNIQUE KEY uk_session_client_msg (session_id, client_msg_id),
    INDEX idx_session_message (session_id, id),
    INDEX idx_receiver_unread (receiver_id, is_read, id),
    INDEX idx_sender_sent (sender_id, sent_at),

    FOREIGN KEY (session_id) REFERENCES t_chat_session(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES t_user(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES t_user(id) ON DELETE CASCADE,
    FOREIGN KEY (file_id) REFERENCES t_file(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- =====================================================
-- 8. 已读游标表
-- =====================================================
CREATE TABLE t_chat_read_cursor (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '游标ID',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    last_read_message_id BIGINT COMMENT '最后已读消息ID',
    last_read_at DATETIME COMMENT '最后已读时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_session_user (session_id, user_id),
    INDEX idx_user_updated (user_id, updated_at),

    FOREIGN KEY (session_id) REFERENCES t_chat_session(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话已读游标表';

-- =====================================================
-- 9. 聊天文件分享授权表
-- =====================================================
CREATE TABLE t_chat_file_share (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分享授权ID',
    message_id BIGINT NOT NULL COMMENT '关联消息ID',
    file_id BIGINT NOT NULL COMMENT '关联文件ID',
    owner_user_id BIGINT NOT NULL COMMENT '文件所有者ID（发送者）',
    receiver_user_id BIGINT NOT NULL COMMENT '接收者ID',
    expires_at DATETIME COMMENT '过期时间，NULL表示永不过期',
    revoked TINYINT NOT NULL DEFAULT 0 COMMENT '是否撤销：0-有效 1-已撤销',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',

    UNIQUE KEY uk_message_receiver (message_id, receiver_user_id),
    INDEX idx_receiver_valid (receiver_user_id, revoked, expires_at),
    INDEX idx_file_id (file_id),

    FOREIGN KEY (message_id) REFERENCES t_chat_message(id) ON DELETE CASCADE,
    FOREIGN KEY (file_id) REFERENCES t_file(id) ON DELETE CASCADE,
    FOREIGN KEY (owner_user_id) REFERENCES t_user(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_user_id) REFERENCES t_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天文件分享授权表';

-- =====================================================
-- 10. 初始化数据 (可选)
-- =====================================================
-- 预留：如果有默认的 admin 用户插入，可以在这里执行
-- 这里保留原有 migration_add_user_role 中的逻辑，用于在后续插入后更新角色，或者提醒
-- UPDATE t_user SET role = 'admin' WHERE username = 'admin';

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
