package com.security.filemanager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_chat_file_share")
public class ChatFileShare {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long messageId;

    private Long fileId;

    private Long ownerUserId;

    private Long receiverUserId;

    private LocalDateTime expiresAt;

    private Integer revoked;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}
