package com.security.filemanager.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.security.filemanager.annotation.RequireAdmin;
import com.security.filemanager.dto.Result;
import com.security.filemanager.entity.User;
import com.security.filemanager.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * 管理员权限拦截器
 * 检查用户是否具有管理员权限
 */
@Slf4j
@Component
public class AdminInterceptor implements HandlerInterceptor {
    
    @Resource
    private UserMapper userMapper;
    
    @Resource
    private ObjectMapper objectMapper;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只处理带有@RequireAdmin注解的方法
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequireAdmin requireAdmin = handlerMethod.getMethodAnnotation(RequireAdmin.class);
        
        if (requireAdmin == null) {
            return true; // 不需要管理员权限
        }
        
        try {
            // 获取当前用户ID
            Long userId = AuthInterceptor.getCurrentUserId();
            
            // 查询用户角色
            User user = userMapper.selectById(userId);
            if (user == null || !"admin".equals(user.getRole())) {
                response.setStatus(403);
                response.setContentType("application/json;charset=UTF-8");
                PrintWriter writer = response.getWriter();
                writer.write(objectMapper.writeValueAsString(Result.error("无管理员权限")));
                writer.flush();
                log.warn("用户 {} 尝试访问管理员接口: {}", userId, request.getRequestURI());
                return false;
            }
            
            return true;
        } catch (Exception e) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            PrintWriter writer = response.getWriter();
            writer.write(objectMapper.writeValueAsString(Result.error("未登录或登录已过期")));
            writer.flush();
            log.warn("访问管理员接口失败: {}, 错误: {}", request.getRequestURI(), e.getMessage());
            return false;
        }
    }
}
