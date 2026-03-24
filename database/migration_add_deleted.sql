ALTER TABLE t_file
  ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除' AFTER is_folder;

CREATE INDEX idx_deleted ON t_file (deleted);
