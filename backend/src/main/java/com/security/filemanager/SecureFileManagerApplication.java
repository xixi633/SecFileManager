package com.security.filemanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 安全文件管理系统 - 启动类
 * 
 * @author CourseDesign
 * @date 2026-01-23
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class SecureFileManagerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(SecureFileManagerApplication.class, args);
        System.out.println("=================================");
        System.out.println("耶耶耶！！！\n安全文件管理系统启动成功！");
        System.out.println("Swagger文档: http://localhost:8080/api/doc.html");
        System.out.println("=================================");
    }
}
