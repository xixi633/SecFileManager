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
 * 鐢ㄦ埛鏈嶅姟
 * 
 * 銆愬畨鍏ㄨ璁°€?
 * 1. 瀵嗙爜浣跨敤PBKDF2鍝堝笇 + 鐩愬€煎瓨鍌?
 * 2. 鐢ㄦ埛涓诲瘑閽ラ殢鏈虹敓鎴愶紝浣跨敤绯荤粺瀵嗛挜鍔犲瘑鍚庡瓨鍌?
 * 3. 鐧诲綍鎴愬姛杩斿洖JWT Token
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
     * 绯荤粺涓诲瘑閽ワ紙鐢ㄤ簬鍔犲瘑鐢ㄦ埛涓诲瘑閽ワ級
     * 浠庨厤缃枃浠惰鍙栵紝鐢熶骇鐜搴旈€氳繃鐜鍙橀噺娉ㄥ叆
     */
    @Value("${secure-file.system-master-key}")
    private String systemMasterKey;

    /**
     * 鏂囦欢瀛樺偍鏍圭洰褰?
     */
    @Value("${secure-file.storage-root}")
    private String storageRoot;

    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024; // 2MB
    private static final long MAX_CHAT_BACKGROUND_SIZE = 5 * 1024 * 1024; // 5MB
    
    /**
     * 鐢ㄦ埛娉ㄥ唽
     * 
     * 銆愬畨鍏ㄦ祦绋嬨€?
     * 1. 鐢熸垚瀵嗙爜鐩愬€?
     * 2. 浣跨敤PBKDF2璁＄畻瀵嗙爜鍝堝笇
     * 3. 鐢熸垚鐢ㄦ埛涓诲瘑閽ワ紙闅忔満256浣嶏級
     * 4. 浣跨敤绯荤粺涓诲瘑閽ュ姞瀵嗙敤鎴蜂富瀵嗛挜
     * 5. 瀛樺叆鏁版嵁搴?
     * 
     * @param request 娉ㄥ唽璇锋眰
     */
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        // 1. 妫€鏌ョ敤鎴峰悕鏄惁宸插瓨鍦紙鍖呮嫭绂佺敤鐨勭敤鎴凤級
        User existUser = userMapper.selectByUsernameIncludeDisabled(request.getUsername());
        if (existUser != null) {
            throw new RuntimeException("用户名已存在，请更换用户名");
        }
        
        // 2. 鐢熸垚瀵嗙爜鐩愬€?
        String passwordSalt = PasswordUtil.generateSalt();
        
        // 3. 璁＄畻瀵嗙爜鍝堝笇
        String passwordHash = PasswordUtil.hashPassword(request.getPassword(), passwordSalt);
        
        // 4. 鐢熸垚鐢ㄦ埛涓诲瘑閽ワ紙鐢ㄤ簬鍔犲瘑璇ョ敤鎴风殑鎵€鏈夋枃浠跺瘑閽ワ級
        String userMasterKey = AESUtil.generateKey();
        
        // 5. 浣跨敤绯荤粺涓诲瘑閽ュ姞瀵嗙敤鎴蜂富瀵嗛挜
        // 銆愪负浠€涔堣鍔犲瘑銆戦槻姝㈡暟鎹簱娉勯湶鍚庣洿鎺ヨ幏鍙栫敤鎴峰瘑閽?
        String masterKeyIv = AESUtil.generateIV();
        AESUtil.EncryptResult encryptResult = AESUtil.encrypt(
                userMasterKey.getBytes(),
                convertSystemKey(systemMasterKey),
                masterKeyIv
        );
        // 瀛樺偍鏍煎紡: Base64(瀵嗘枃):authTag锛屼究浜庡悗缁В瀵嗘椂鏍￠獙瀹屾暣鎬?
        String masterKeyEncrypted = java.util.Base64.getEncoder()
                .encodeToString(encryptResult.getCiphertext())
                + ":" + encryptResult.getAuthTag();
        
        // 6. 鏋勫缓鐢ㄦ埛瀹炰綋
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
        
        // 7. 鎻掑叆鏁版嵁搴?
        userMapper.insert(user);
        
        log.info("鐢ㄦ埛娉ㄥ唽鎴愬姛: {}", request.getUsername());
    }
    
    /**
     * 鐢ㄦ埛鐧诲綍
     * 
     * 銆愬畨鍏ㄦ祦绋嬨€?
     * 1. 鏌ヨ鐢ㄦ埛淇℃伅
     * 2. 浣跨敤鐩稿悓鐩愬€奸噸鏂拌绠楀瘑鐮佸搱甯?
     * 3. 甯搁噺鏃堕棿姣旇緝鍝堝笇鍊硷紙闃叉鏃跺簭鏀诲嚮锛?
     * 4. 妫€鏌ョ敤鎴风姸鎬?
     * 5. 鐢熸垚JWT Token
     * 
     * @param request 鐧诲綍璇锋眰
     * @return 鐧诲綍鍝嶅簲锛堝寘鍚玊oken锛?
     */
    public LoginResponse login(LoginRequest request) {
        // 1. 鏌ヨ鐢ㄦ埛锛堝寘鎷鐢ㄧ姸鎬佺殑鐢ㄦ埛锛?
        User user = userMapper.selectByUsernameIncludeDisabled(request.getUsername());
        if (user == null) {
            throw new RuntimeException("鐢ㄦ埛鍚嶆垨瀵嗙爜閿欒");
        }
        
        // 2. 楠岃瘉瀵嗙爜
        boolean valid = PasswordUtil.verifyPassword(
                request.getPassword(),
                user.getPasswordSalt(),
                user.getPasswordHash()
        );
        
        if (!valid) {
            log.warn("用户 {} 登录失败：密码错误", request.getUsername());
            throw new RuntimeException("鐢ㄦ埛鍚嶆垨瀵嗙爜閿欒");
        }
        
        // 3. 妫€鏌ョ敤鎴风姸鎬?
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("用户 {} 登录失败：账号已被禁用", request.getUsername());
            throw new RuntimeException("账号已被禁用，请联系管理员");
        }
        
        // 4. 鐢熸垚JWT Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        
        log.info("鐢ㄦ埛鐧诲綍鎴愬姛: {}", request.getUsername());
        
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRole());
    }

    /**
     * 鑾峰彇鐢ㄦ埛璧勬枡
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
     * 鏇存柊鐢ㄦ埛璧勬枡锛堟樀绉?閭锛?
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
                    throw new RuntimeException("瀵嗙爜纭澶辫触");
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
            throw new RuntimeException("鏇存柊澶辫触锛岃閲嶈瘯");
        }
    }

    /**
     * 鏇存柊鐢ㄦ埛瀵嗙爜
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
            throw new RuntimeException("瀵嗙爜鏇存柊澶辫触");
        }
    }

    /**
     * 鏇存柊鐢ㄦ埛澶村儚
     */
    public void updateAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("澶村儚鏂囦欢涓嶈兘涓虹┖");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new RuntimeException("澶村儚鏂囦欢涓嶈兘瓒呰繃2MB");
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
                throw new RuntimeException("澶村儚鏇存柊澶辫触");
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("澶村儚淇濆瓨澶辫触", e);
        }
    }

    /**
     * 鑾峰彇鐢ㄦ埛澶村儚鏁版嵁
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
            throw new RuntimeException("璇诲彇澶村儚澶辫触", e);
        }
    }

    /**
     * 鏇存柊鑱婂ぉ鑳屾櫙
     */
    public void updateChatBackground(Long userId, Long sessionId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("鑱婂ぉ鑳屾櫙鏂囦欢涓嶈兘涓虹┖");
        }
        if (file.getSize() > MAX_CHAT_BACKGROUND_SIZE) {
            throw new RuntimeException("鑱婂ぉ鑳屾櫙鍥剧墖涓嶈兘瓒呰繃5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("聊天背景必须是图片文件");
        }

        String originalName = file.getOriginalFilename();
        String ext = getSafeImageExtension(originalName, contentType);

        try {
            java.nio.file.Path backgroundDir = java.nio.file.Paths.get(storageRoot, "chat-backgrounds");
            java.nio.file.Files.createDirectories(backgroundDir);
            String filename = "chat_bg_" + userId + "_s" + sessionId + "_" + System.currentTimeMillis() + ext;
            java.nio.file.Path target = backgroundDir.resolve(filename);
            file.transferTo(target.toFile());

            for (java.nio.file.Path oldPath : listChatBackgroundFiles(userId, sessionId)) {
                if (!oldPath.equals(target)) {
                    java.nio.file.Files.deleteIfExists(oldPath);
                }
            }
            return;

        } catch (java.io.IOException e) {
            throw new RuntimeException("鑱婂ぉ鑳屾櫙淇濆瓨澶辫触", e);
        }
    }

    /**
     * 鑾峰彇鑱婂ぉ鑳屾櫙鏁版嵁
     */
    public byte[] getChatBackgroundData(Long userId, Long sessionId) {
        java.nio.file.Path resolvedPath = resolveChatBackgroundPath(userId, sessionId);
        if (resolvedPath != null) {
            try {
                return java.nio.file.Files.readAllBytes(resolvedPath);
            } catch (java.io.IOException e) {
                throw new RuntimeException("璇诲彇鑱婂ぉ鑳屾櫙澶辫触", e);
            }
        }
        throw new RuntimeException("用户未设置聊天背景");
    }

    /**
     * 鑾峰彇瀹夊叏鐨勫浘鐗囨墿灞曞悕
     */
    public String getChatBackgroundPath(Long userId, Long sessionId) {
        java.nio.file.Path path = resolveChatBackgroundPath(userId, sessionId);
        return path == null ? null : path.toString();
    }

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
     * 鏍规嵁鐢ㄦ埛ID鏌ヨ鐢ㄦ埛
     * 
     * @param userId 鐢ㄦ埛ID
     * @return 鐢ㄦ埛淇℃伅
     */
    private java.nio.file.Path resolveChatBackgroundPath(Long userId, Long sessionId) {
        java.util.List<java.nio.file.Path> files = listChatBackgroundFiles(userId, sessionId);
        if (files.isEmpty()) {
            return null;
        }
        files.sort((a, b) -> {
            try {
                return java.nio.file.Files.getLastModifiedTime(b)
                        .compareTo(java.nio.file.Files.getLastModifiedTime(a));
            } catch (java.io.IOException e) {
                return b.getFileName().toString().compareTo(a.getFileName().toString());
            }
        });
        return files.get(0);
    }

    private java.util.List<java.nio.file.Path> listChatBackgroundFiles(Long userId, Long sessionId) {
        java.util.List<java.nio.file.Path> files = new java.util.ArrayList<>();
        java.nio.file.Path backgroundDir = java.nio.file.Paths.get(storageRoot, "chat-backgrounds");
        if (!java.nio.file.Files.exists(backgroundDir)) {
            return files;
        }
        String prefix = "chat_bg_" + userId + "_s" + sessionId + "_";
        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(backgroundDir)) {
            stream.filter(java.nio.file.Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .forEach(files::add);
        } catch (java.io.IOException e) {
            throw new RuntimeException("璇诲彇鑱婂ぉ鑳屾櫙鐩綍澶辫触", e);
        }
        return files;
    }

    public User getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user;
    }
    
    /**
     * 鑾峰彇鐢ㄦ埛鐨勪富瀵嗛挜锛堣В瀵嗗悗锛?
     * 
     * 銆愮敤閫斻€戠敤浜庡姞瀵?瑙ｅ瘑鏂囦欢瀵嗛挜
     * 
     * 銆愬畨鍏ㄦ祦绋嬨€?
     * 1. 浠嶥N璇诲彇鍔犲瘑鍚庣殑masterKey
     * 2. 浣跨敤绯荤粺涓诲瘑閽ヨВ瀵嗭紝杩樺師鐢ㄦ埛涓诲瘑閽?
     * 3. 瀵逛簬鏃х増鏈湭瀛樺偍authTag鐨勭敤鎴凤紝浣跨敤纭畾鎬у瘑閽ュ吋瀹?
     * 
     * @param userId 鐢ㄦ埛ID
     * @return 鐢ㄦ埛涓诲瘑閽ワ紙Base64缂栫爜锛?
     */
    public String getUserMasterKey(Long userId) {
        User user = getUserById(userId);
        
        String encrypted = user.getMasterKeyEncrypted();
        String iv = user.getMasterKeyIv();
        
        // 鏂扮増鏍煎紡: "Base64(ciphertext):authTag"
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
                log.warn("解密用户主密钥失败 (userId={})，回退到兼容模式", userId, e);
            }
        }
        
        // 鏃х増鍏煎锛氬熀浜庣敤鎴稩D鐢熸垚纭畾鎬у瘑閽?
        log.debug("浣跨敤鍏煎妯″紡瀵嗛挜: userId={}", userId);
        String deterministicKey = String.format("UserMasterKey%020d", userId);
        byte[] keyBytes = new byte[32];
        byte[] sourceBytes = deterministicKey.getBytes();
        System.arraycopy(sourceBytes, 0, keyBytes, 0, Math.min(sourceBytes.length, 32));
        
        return java.util.Base64.getEncoder().encodeToString(keyBytes);
    }
    
    /**
     * 杞崲绯荤粺瀵嗛挜涓築ase64鏍煎紡
     * 
     * 銆愯鏄庛€戦厤缃枃浠朵腑鐨勫瘑閽ユ槸瀛楃涓诧紝闇€瑕佽浆鎹负Base64缂栫爜鐨勫瘑閽?
     */
    private String convertSystemKey(String key) {
        // 纭繚瀵嗛挜鏄?2瀛楄妭锛?56浣嶏級
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
     * 娉ㄩ攢璐﹀彿
     * 鍒犻櫎鐢ㄦ埛鍙婂叾鎵€鏈夋枃浠?
     * 
     * @param userId 鐢ㄦ埛ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long userId) {
        // 1. 鍒犻櫎鐢ㄦ埛鐨勬墍鏈夋枃浠讹紙鐗╃悊鍒犻櫎锛?
        fileService.deleteAllUserFiles(userId);
        
        // 2. 鍒犻櫎鐢ㄦ埛璐﹀彿
        userMapper.deleteById(userId);
        
        log.info("鐢ㄦ埛 {} 鐨勮处鍙峰強鎵€鏈夋枃浠跺凡鍒犻櫎", userId);
    }
}
