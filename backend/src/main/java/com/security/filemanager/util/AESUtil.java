package com.security.filemanager.util;

import lombok.extern.slf4j.Slf4j;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES加密工具类
 * 
 * 【加密方案】
 * - 算法：AES-256-GCM
 * - 密钥长度：256位（32字节）
 * - IV长度：96位（12字节，GCM推荐）
 * - 认证标签长度：128位（16字节）
 * 
 * 【为什么选择GCM模式】
 * 1. AEAD（认证加密）：同时提供机密性和完整性
 * 2. 无需padding，避免padding oracle攻击
 * 3. 并行计算，性能优于CBC模式
 * 4. 业界标准，TLS 1.3默认使用
 * 
 * @author CourseDesign
 */
@Slf4j
public class AESUtil {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256; // 256位密钥
    private static final int IV_LENGTH = 12;  // GCM推荐12字节IV
    private static final int TAG_LENGTH = 128; // 128位认证标签
    
    private static final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * 生成随机AES密钥
     * 
     * @return 256位密钥（Base64编码）
     */
    public static String generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE, secureRandom);
            SecretKey secretKey = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            log.error("生成AES密钥失败", e);
            throw new RuntimeException("密钥生成失败", e);
        }
    }
    
    /**
     * 生成随机IV
     * 
     * @return 12字节IV（Hex编码）
     */
    public static String generateIV() {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        return bytesToHex(iv);
    }
    
    /**
     * AES-GCM 加密
     * 
     * @param plaintext 明文数据
     * @param keyBase64 AES密钥（Base64编码）
     * @param ivHex IV（Hex编码）
     * @return 加密结果，包含密文和认证标签
     */
    public static EncryptResult encrypt(byte[] plaintext, String keyBase64, String ivHex) {
        try {
            // 解码密钥和IV
            byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
            byte[] iv = hexToBytes(ivHex);
            
            // 创建密钥对象
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            
            // 初始化Cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            
            // 加密（GCM模式会自动附加认证标签）
            byte[] ciphertext = cipher.doFinal(plaintext);
            
            // GCM模式下，doFinal返回的数据 = 密文 + 认证标签（最后16字节）
            // 分离密文和认证标签
            int ciphertextLength = ciphertext.length - (TAG_LENGTH / 8);
            byte[] actualCiphertext = new byte[ciphertextLength];
            byte[] authTag = new byte[TAG_LENGTH / 8];
            
            System.arraycopy(ciphertext, 0, actualCiphertext, 0, ciphertextLength);
            System.arraycopy(ciphertext, ciphertextLength, authTag, 0, TAG_LENGTH / 8);
            
            return new EncryptResult(actualCiphertext, bytesToHex(authTag));
            
        } catch (Exception e) {
            log.error("AES加密失败", e);
            throw new RuntimeException("加密失败", e);
        }
    }
    
    /**
     * AES-GCM 解密
     * 
     * @param ciphertext 密文数据
     * @param authTagHex 认证标签（Hex编码）
     * @param keyBase64 AES密钥（Base64编码）
     * @param ivHex IV（Hex编码）
     * @return 明文数据
     * @throws RuntimeException 如果认证标签校验失败（数据被篡改）
     */
    public static byte[] decrypt(byte[] ciphertext, String authTagHex, String keyBase64, String ivHex) {
        try {
            // 解码密钥、IV和认证标签
            byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
            byte[] iv = hexToBytes(ivHex);
            byte[] authTag = hexToBytes(authTagHex);
            
            // 创建密钥对象
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            
            // 初始化Cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
            
            // 重新组合密文和认证标签
            byte[] combined = new byte[ciphertext.length + authTag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(authTag, 0, combined, ciphertext.length, authTag.length);
            
            // 解密（会自动校验认证标签）
            // 【重要】如果认证标签不匹配，会抛出AEADBadTagException
            return cipher.doFinal(combined);
            
        } catch (javax.crypto.AEADBadTagException e) {
            log.error("认证标签校验失败，文件可能被篡改！");
            throw new RuntimeException("文件完整性校验失败，数据已被篡改", e);
        } catch (Exception e) {
            log.error("AES解密失败", e);
            throw new RuntimeException("解密失败", e);
        }
    }
    
    /**
     * 计算数据的SHA-256哈希值
     * 
     * @param data 数据
     * @return 哈希值（Hex编码）
     */
    public static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (Exception e) {
            log.error("SHA-256计算失败", e);
            throw new RuntimeException("哈希计算失败", e);
        }
    }
    
    /**
     * 字节数组转Hex字符串
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Hex字符串转字节数组
     */
    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
    
    /**
     * 加密结果封装
     */
    public static class EncryptResult {
        private final byte[] ciphertext;
        private final String authTag;
        
        public EncryptResult(byte[] ciphertext, String authTag) {
            this.ciphertext = ciphertext;
            this.authTag = authTag;
        }
        
        public byte[] getCiphertext() {
            return ciphertext;
        }
        
        public String getAuthTag() {
            return authTag;
        }
    }
}
