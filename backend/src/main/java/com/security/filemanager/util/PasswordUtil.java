package com.security.filemanager.util;

import lombok.extern.slf4j.Slf4j;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * 密码安全工具类
 * 
 * 【密码存储方案】
 * - 算法：PBKDF2-HMAC-SHA256
 * - 迭代次数：210,000（OWASP 2023推荐）
 * - 盐值长度：16字节（128位）
 * - 输出长度：32字节（256位）
 * 
 * 【为什么使用PBKDF2】
 * 1. 密钥派生函数，专为密码存储设计
 * 2. 高迭代次数增加暴力破解成本
 * 3. Java标准库原生支持，无需第三方库
 * 4. OWASP推荐，符合安全标准
 * 
 * 【为什么需要盐值】
 * - 防止彩虹表攻击
 * - 相同密码产生不同哈希值
 * - 每个用户独立盐值
 * 
 * @author CourseDesign
 */
@Slf4j
public class PasswordUtil {
    
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210000; // OWASP 2023推荐
    private static final int KEY_LENGTH = 256;    // 输出256位
    private static final int SALT_LENGTH = 16;    // 盐值16字节
    
    private static final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * 生成随机盐值
     * 
     * @return 盐值（Hex编码）
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        return bytesToHex(salt);
    }
    
    /**
     * 生成密码哈希值
     * 
     * 【使用场景】用户注册时
     * 
     * @param password 明文密码
     * @param saltHex 盐值（Hex编码）
     * @return 密码哈希值（Base64编码）
     */
    public static String hashPassword(String password, String saltHex) {
        try {
            byte[] salt = hexToBytes(saltHex);
            
            // 配置PBKDF2参数
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    salt,
                    ITERATIONS,
                    KEY_LENGTH
            );
            
            // 生成哈希
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            
            // 清除密码（安全实践）
            spec.clearPassword();
            
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.error("密码哈希生成失败", e);
            throw new RuntimeException("密码处理失败", e);
        }
    }
    
    /**
     * 验证密码
     * 
     * 【使用场景】用户登录时
     * 
     * @param password 用户输入的明文密码
     * @param saltHex 数据库中存储的盐值
     * @param storedHashBase64 数据库中存储的密码哈希
     * @return 密码是否正确
     */
    public static boolean verifyPassword(String password, String saltHex, String storedHashBase64) {
        try {
            // 使用相同盐值重新计算哈希
            String computedHash = hashPassword(password, saltHex);
            
            // 【重要】使用常量时间比较，防止时序攻击
            return constantTimeEquals(computedHash, storedHashBase64);
            
        } catch (Exception e) {
            log.error("密码验证失败", e);
            return false;
        }
    }
    
    /**
     * 常量时间字符串比较
     * 
     * 【为什么需要常量时间比较】
     * - 防止时序攻击（Timing Attack）
     * - 普通的 equals() 遇到第一个不同字符就返回，泄露信息
     * - 常量时间比较始终比较所有字符，不泄露任何信息
     * 
     * @param a 字符串A
     * @param b 字符串B
     * @return 是否相等
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        
        byte[] aBytes = a.getBytes();
        byte[] bBytes = b.getBytes();
        
        // 长度不同时仍然执行完整比较，防止时序泄露
        int maxLen = Math.max(aBytes.length, bBytes.length);
        int result = aBytes.length ^ bBytes.length;  // 长度不同则结果非零
        for (int i = 0; i < maxLen; i++) {
            int aByte = i < aBytes.length ? aBytes[i] : 0;
            int bByte = i < bBytes.length ? bBytes[i] : 0;
            result |= aByte ^ bByte;
        }
        
        return result == 0;
    }
    
    /**
     * 字节数组转Hex字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Hex字符串转字节数组
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
