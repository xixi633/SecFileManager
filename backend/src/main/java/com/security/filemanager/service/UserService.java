package com.security.filemanager.service;

import com.security.filemanager.dto.LoginRequest;
import com.security.filemanager.dto.LoginResponse;
import com.security.filemanager.dto.RegisterRequest;
import com.security.filemanager.dto.UpdatePasswordRequest;
import com.security.filemanager.dto.UpdateProfileRequest;
import com.security.filemanager.dto.UserProfileResponse;
import com.security.filemanager.entity.User;
import com.security.filemanager.mapper.UserMapper;
import com.security.filemanager.util.AESUtil;
import com.security.filemanager.util.JwtUtil;
import com.security.filemanager.util.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 用户服务
 * 
 * 【安全设计】
 * 1. 密码使用PBKDF2哈希 + 盐值存储
 * 2. 用户主密钥随机生成，使用系统密钥加密后存储
 * 3. 登录成功返回JWT Token
 * 
 * @author CourseDesign
 */
@Slf4j
@Service
public class UserService {
    
    @Resource
    private UserMapper userMapper;
    
    @Resource
    private JwtUtil jwtUtil;
    
    @Resource
    @org.springframework.context.annotation.Lazy
    private FileService fileService;
    
    /**
     * 系统主密钥（用于加密用户主密钥）
     * 从配置文件读取，生产环境应通过环境变量注入
     */
    @Value("${secure-file.system-master-key}")
    private String systemMasterKey;

    /**
     * 文件存储根目录
     */
    @Value("${secure-file.storage-root}")
    private String storageRoot;

    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024; // 2MB
    
    /**
     * 用户注册
     * 
     * 【安全流程】
     * 1. 生成密码盐值
     * 2. 使用PBKDF2计算密码哈希
     * 3. 生成用户主密钥（随机256位）
     * 4. 使用系统主密钥加密用户主密钥
     * 5. 存入数据库
     * 
     * @param request 注册请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        // 1. 检查用户名是否已存在（包括禁用的用户）
        User existUser = userMapper.selectByUsernameIncludeDisabled(request.getUsername());
        if (existUser != null) {
            throw new RuntimeException("用户名已存在，请更换用户名");
        }
        
        // 2. 生成密码盐值
        String passwordSalt = PasswordUtil.generateSalt();
        
        // 3. 计算密码哈希
        String passwordHash = PasswordUtil.hashPassword(request.getPassword(), passwordSalt);
        
        // 4. 生成用户主密钥（用于加密该用户的所有文件密钥）
        String userMasterKey = AESUtil.generateKey();
        
        // 5. 使用系统主密钥加密用户主密钥
        // 【为什么要加密】防止数据库泄露后直接获取用户密钥
        String masterKeyIv = AESUtil.generateIV();
        AESUtil.EncryptResult encryptResult = AESUtil.encrypt(
                userMasterKey.getBytes(),
                convertSystemKey(systemMasterKey),
                masterKeyIv
        );
        // 存储格式: Base64(密文):authTag，便于后续解密时校验完整性
        String masterKeyEncrypted = java.util.Base64.getEncoder()
                .encodeToString(encryptResult.getCiphertext())
                + ":" + encryptResult.getAuthTag();
        
        // 6. 构建用户实体
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordHash);
        user.setPasswordSalt(passwordSalt);
        user.setMasterKeyEncrypted(masterKeyEncrypted);
        user.setMasterKeyIv(masterKeyIv);
        user.setEmail(request.getEmail());
        user.setNickname(request.getUsername());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setStatus(1);
        user.setRole("user");
        
        // 7. 插入数据库
        userMapper.insert(user);
        
        log.info("用户注册成功: {}", request.getUsername());
    }
    
    /**
     * 用户登录
     * 
     * 【安全流程】
     * 1. 查询用户信息
     * 2. 使用相同盐值重新计算密码哈希
     * 3. 常量时间比较哈希值（防止时序攻击）
     * 4. 检查用户状态
     * 5. 生成JWT Token
     * 
     * @param request 登录请求
     * @return 登录响应（包含Token）
     */
    public LoginResponse login(LoginRequest request) {
        // 1. 查询用户（包括禁用状态的用户）
        User user = userMapper.selectByUsernameIncludeDisabled(request.getUsername());
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 2. 验证密码
        boolean valid = PasswordUtil.verifyPassword(
                request.getPassword(),
                user.getPasswordSalt(),
                user.getPasswordHash()
        );
        
        if (!valid) {
            log.warn("用户 {} 登录失败：密码错误", request.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 3. 检查用户状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("用户 {} 登录失败：账号已被禁用", request.getUsername());
            throw new RuntimeException("账号已被禁用，请联系管理员");
        }
        
        // 4. 生成JWT Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        
        log.info("用户登录成功: {}", request.getUsername());
        
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRole());
    }

    /**
     * 获取用户资料
     */
    public UserProfileResponse getProfile(Long userId) {
        User user = getUserById(userId);
        String avatarUrl = user.getAvatarPath() != null ? "/api/user/avatar" : null;
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                avatarUrl
        );
    }

    /**
     * 更新用户资料（昵称/邮箱）
     */
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUserById(userId);
        String username = request.getUsername();
        String nickname = request.getNickname();
        String email = request.getEmail();

        boolean hasUpdate = false;
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId);

        if (username != null) {
            String newUsername = username.trim();
            if (newUsername.isEmpty()) {
                throw new RuntimeException("用户名不能为空");
            }
            if (!newUsername.equals(user.getUsername())) {
                String confirmPassword = request.getConfirmPassword();
                if (confirmPassword == null || confirmPassword.trim().isEmpty()) {
                    throw new RuntimeException("修改用户名需要确认密码");
                }
                boolean valid = PasswordUtil.verifyPassword(
                        confirmPassword,
                        user.getPasswordSalt(),
                        user.getPasswordHash()
                );
                if (!valid) {
                    throw new RuntimeException("密码确认失败");
                }
                User existUser = userMapper.selectByUsernameIncludeDisabled(newUsername);
                if (existUser != null && !existUser.getId().equals(userId)) {
                    throw new RuntimeException("用户名已存在，请更换用户名");
                }
                wrapper.set("username", newUsername);
                hasUpdate = true;
            }
        }

        if (nickname != null) {
            nickname = nickname.trim();
            wrapper.set("nickname", nickname.isEmpty() ? null : nickname);
            hasUpdate = true;
        }
        if (email != null) {
            email = email.trim();
            wrapper.set("email", email.isEmpty() ? null : email);
            hasUpdate = true;
        }

        if (!hasUpdate) {
            throw new RuntimeException("未提供更新内容");
        }

        wrapper.set("updated_at", LocalDateTime.now());
        int updated = userMapper.update(null, wrapper);
        if (updated == 0) {
            throw new RuntimeException("更新失败，请重试");
        }
    }

    /**
     * 更新用户密码
     */
    public void updatePassword(Long userId, UpdatePasswordRequest request) {
        User user = getUserById(userId);
        boolean valid = PasswordUtil.verifyPassword(
                request.getOldPassword(),
                user.getPasswordSalt(),
                user.getPasswordHash()
        );
        if (!valid) {
            throw new RuntimeException("原密码错误");
        }

        if (PasswordUtil.verifyPassword(request.getNewPassword(), user.getPasswordSalt(), user.getPasswordHash())) {
            throw new RuntimeException("新密码不能与原密码相同");
        }

        String newSalt = PasswordUtil.generateSalt();
        String newHash = PasswordUtil.hashPassword(request.getNewPassword(), newSalt);

        User update = new User();
        update.setId(userId);
        update.setPasswordSalt(newSalt);
        update.setPasswordHash(newHash);
        update.setUpdatedAt(LocalDateTime.now());
        int updated = userMapper.updateById(update);
        if (updated == 0) {
            throw new RuntimeException("密码更新失败");
        }
    }

    /**
     * 更新用户头像
     */
    public void updateAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("头像文件不能为空");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new RuntimeException("头像文件不能超过2MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("头像必须是图片文件");
        }

        String originalName = file.getOriginalFilename();
        String ext = getSafeImageExtension(originalName, contentType);

        try {
            java.nio.file.Path avatarDir = java.nio.file.Paths.get(storageRoot, "avatars");
            java.nio.file.Files.createDirectories(avatarDir);
            String filename = "avatar_" + userId + "_" + System.currentTimeMillis() + ext;
            java.nio.file.Path target = avatarDir.resolve(filename);
            file.transferTo(target.toFile());

            User user = getUserById(userId);
            if (user.getAvatarPath() != null) {
                java.nio.file.Path oldPath = java.nio.file.Paths.get(user.getAvatarPath());
                java.nio.file.Files.deleteIfExists(oldPath);
            }

            User update = new User();
            update.setId(userId);
            update.setAvatarPath(target.toString());
            update.setUpdatedAt(LocalDateTime.now());
            int updated = userMapper.updateById(update);
            if (updated == 0) {
                throw new RuntimeException("头像更新失败");
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("头像保存失败", e);
        }
    }

    /**
     * 获取用户头像数据
     */
    public byte[] getAvatarData(Long userId) {
        User user = getUserById(userId);
        if (user.getAvatarPath() == null || user.getAvatarPath().trim().isEmpty()) {
            throw new RuntimeException("用户未设置头像");
        }
        java.nio.file.Path path = java.nio.file.Paths.get(user.getAvatarPath());
        if (!java.nio.file.Files.exists(path)) {
            throw new RuntimeException("头像文件不存在");
        }
        try {
            return java.nio.file.Files.readAllBytes(path);
        } catch (java.io.IOException e) {
            throw new RuntimeException("读取头像失败", e);
        }
    }

    /**
     * 获取安全的图片扩展名
     */
    private String getSafeImageExtension(String filename, String contentType) {
        String ext = null;
        if (filename != null) {
            int idx = filename.lastIndexOf('.');
            if (idx >= 0 && idx < filename.length() - 1) {
                ext = filename.substring(idx + 1).toLowerCase();
            }
        }

        java.util.Set<String> allowed = new java.util.HashSet<>();
        allowed.add("png");
        allowed.add("jpg");
        allowed.add("jpeg");
        allowed.add("gif");
        allowed.add("webp");

        if (ext != null && allowed.contains(ext)) {
            return "." + ext;
        }

        if (contentType != null) {
            if (contentType.contains("png")) return ".png";
            if (contentType.contains("jpeg") || contentType.contains("jpg")) return ".jpg";
            if (contentType.contains("gif")) return ".gif";
            if (contentType.contains("webp")) return ".webp";
        }
        return ".png";
    }
    
    /**
     * 根据用户ID查询用户
     * 
     * @param userId 用户ID
     * @return 用户信息
     */
    public User getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user;
    }
    
    /**
     * 获取用户的主密钥（解密后）
     * 
     * 【用途】用于加密/解密文件密钥
     * 
     * 【安全流程】
     * 1. 从DN读取加密后的masterKey
     * 2. 使用系统主密钥解密，还原用户主密钥
     * 3. 对于旧版本未存储authTag的用户，使用确定性密钥兼容
     * 
     * @param userId 用户ID
     * @return 用户主密钥（Base64编码）
     */
    public String getUserMasterKey(Long userId) {
        User user = getUserById(userId);
        
        String encrypted = user.getMasterKeyEncrypted();
        String iv = user.getMasterKeyIv();
        
        // 新版格式: "Base64(ciphertext):authTag"
        if (encrypted != null && encrypted.contains(":")) {
            try {
                String[] parts = encrypted.split(":");
                if (parts.length == 2) {
                    byte[] ciphertext = java.util.Base64.getDecoder().decode(parts[0]);
                    String authTag = parts[1];
                    String systemKey = convertSystemKey(systemMasterKey);
                    byte[] masterKeyBytes = AESUtil.decrypt(ciphertext, authTag, systemKey, iv);
                    return new String(masterKeyBytes, java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                log.warn("解密用户主密钥失败(userId={}):，回退到兼容模式", userId, e);
            }
        }
        
        // 旧版兼容：基于用户ID生成确定性密钥
        log.debug("使用兼容模式密钥: userId={}", userId);
        String deterministicKey = String.format("UserMasterKey%020d", userId);
        byte[] keyBytes = new byte[32];
        byte[] sourceBytes = deterministicKey.getBytes();
        System.arraycopy(sourceBytes, 0, keyBytes, 0, Math.min(sourceBytes.length, 32));
        
        return java.util.Base64.getEncoder().encodeToString(keyBytes);
    }
    
    /**
     * 转换系统密钥为Base64格式
     * 
     * 【说明】配置文件中的密钥是字符串，需要转换为Base64编码的密钥
     */
    private String convertSystemKey(String key) {
        // 确保密钥是32字节（256位）
        byte[] keyBytes = key.getBytes();
        if (keyBytes.length < 32) {
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, keyBytes.length);
            keyBytes = paddedKey;
        } else if (keyBytes.length > 32) {
            byte[] trimmedKey = new byte[32];
            System.arraycopy(keyBytes, 0, trimmedKey, 0, 32);
            keyBytes = trimmedKey;
        }
        return java.util.Base64.getEncoder().encodeToString(keyBytes);
    }
    
    /**
     * 注销账号
     * 删除用户及其所有文件
     * 
     * @param userId 用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long userId) {
        // 1. 删除用户的所有文件（物理删除）
        fileService.deleteAllUserFiles(userId);
        
        // 2. 删除用户账号
        userMapper.deleteById(userId);
        
        log.info("用户 {} 的账号及所有文件已删除", userId);
    }
}
