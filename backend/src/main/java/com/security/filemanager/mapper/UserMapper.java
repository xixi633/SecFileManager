package com.security.filemanager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.security.filemanager.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    /**
     * 根据用户名查询用户（仅正常状态）
     * 
     * @param username 用户名
     * @return 用户实体
     */
    User selectByUsername(@Param("username") String username);
    
    /**
     * 根据用户名查询用户（包括禁用状态）
     * 用于登录时检查用户状态
     * 
     * @param username 用户名
     * @return 用户实体
     */
    User selectByUsernameIncludeDisabled(@Param("username") String username);
}
