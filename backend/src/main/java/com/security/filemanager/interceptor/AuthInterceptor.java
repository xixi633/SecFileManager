package com.security.filemanager.interceptor;

import com.security.filemanager.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 身份认证拦截器
 * 
 * 【功能】
 * 1. 从请求头中提取JWT Token
 * 2. 验证Token有效性
 * 3. 提取用户ID并存入ThreadLocal
 * 4. 供Service层获取当前用户ID
 * 
 * @author CourseDesign
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    @Resource
    private JwtUtil jwtUtil;
    
    @Value("${secure-file.jwt.header}")
    private String tokenHeader;
    
    @Value("${secure-file.jwt.prefix}")
    private String tokenPrefix;
    
    /**
     * ThreadLocal存储当前用户ID
     */
    private static final ThreadLocal<Long> USER_CONTEXT = new ThreadLocal<>();
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 处理OPTIONS预检请求
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        
        // 获取Token - 优先从Header获取，其次在预览接口从URL参数获取
        String token = null;
        String authHeader = request.getHeader(tokenHeader);
        
        if (authHeader != null && authHeader.startsWith(tokenPrefix)) {
            token = authHeader.substring(tokenPrefix.length());
        } else {
            // 仅允许预览/头像接口通过URL参数携带token（用于 <video> <img> 等标签的预览）
            String requestUri = request.getRequestURI();
            if (requestUri != null && (requestUri.contains("/file/preview/")
                    || requestUri.contains("/file/folder/preview/")
                    || requestUri.contains("/file/folder/preview-safe/")
                    || requestUri.contains("/user/avatar"))) {
                token = request.getParameter("token");
            }
        }
        
        if (token == null || token.trim().isEmpty()) {
            response.setStatus(401);
            log.warn("请求未携带有效Token: {}", request.getRequestURI());
            return false;
        }
        
        // 验证Token
        if (!jwtUtil.validateToken(token)) {
            response.setStatus(401);
            log.warn("Token验证失败: {}", request.getRequestURI());
            return false;
        }
        
        // 提取用户ID并存入ThreadLocal
        Long userId = jwtUtil.getUserIdFromToken(token);
        USER_CONTEXT.set(userId);
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                 Object handler, Exception ex) {
        // 清理ThreadLocal，防止内存泄漏
        USER_CONTEXT.remove();
    }
    
    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId() {
        Long userId = USER_CONTEXT.get();
        if (userId == null) {
            throw new RuntimeException("未登录");
        }
        return userId;
    }
}
