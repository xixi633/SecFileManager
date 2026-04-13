package com.security.filemanager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.security.filemanager.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
