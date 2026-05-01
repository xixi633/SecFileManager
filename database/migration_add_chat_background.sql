ALTER TABLE t_user
ADD COLUMN chat_background_path VARCHAR(512) COMMENT '聊天背景图存储路径' AFTER avatar_path;
