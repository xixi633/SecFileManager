-- 为用户表添加角色字段
ALTER TABLE t_user
	ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '用户角色：admin-管理员, user-普通用户' AFTER status;

-- 创建索引
ALTER TABLE t_user ADD INDEX idx_role (role);

-- 设置admin用户为管理员（如果存在）
UPDATE t_user SET role = 'admin' WHERE username = 'admin';
