-- 新增用户昵称与头像字段
ALTER TABLE t_user
    ADD COLUMN nickname VARCHAR(50) NULL COMMENT '昵称' AFTER email,
    ADD COLUMN avatar_path VARCHAR(512) NULL COMMENT '头像存储路径' AFTER nickname;
