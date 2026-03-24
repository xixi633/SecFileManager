package com.security.filemanager.config;

import com.security.filemanager.interceptor.AdminInterceptor;
import com.security.filemanager.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * Web MVC 配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Resource
    private AuthInterceptor authInterceptor;
    
    @Resource
    private AdminInterceptor adminInterceptor;
    
    /**
     * 配置拦截器
     * 
     * 【注意】由于 application.yml 中配置了 context-path: /api
     * 所以这里的路径不需要 /api 前缀
     * 实际访问路径：/api/user/login
     * 拦截器匹配路径：/user/login, /file/**
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 认证拦截器
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/register",
                        "/user/login",
                        "/user/password/reset/request",
                        "/user/password/reset/verify",
                        "/user/password/reset/confirm",
                        "/doc.html",
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/v2/api-docs"
                );
        
        // 管理员权限拦截器（在认证拦截器之后）
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/**");
    }
    
    /**
     * 配置CORS跨域
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
    
    /**
     * 配置Swagger资源处理
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
