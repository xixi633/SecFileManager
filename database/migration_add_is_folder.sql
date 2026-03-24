-- 添加 is_folder 字段到 t_file 表
USE secure_file_manager;

ALTER TABLE t_file 
ADD COLUMN is_folder TINYINT NOT NULL DEFAULT 0 COMMENT '是否为文件夹：0-文件 1-文件夹（ZIP压缩包）'
AFTER description;
