package com.security.filemanager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.security.filemanager.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
