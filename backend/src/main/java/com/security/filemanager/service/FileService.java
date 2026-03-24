package com.security.filemanager.service;

import com.security.filemanager.dto.FileInfoResponse;
import com.security.filemanager.dto.FileEntryResponse;
import com.security.filemanager.entity.FileInfo;
import com.security.filemanager.mapper.FileMapper;
import com.security.filemanager.util.AESUtil;
import com.security.filemanager.util.CompressionUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.net.URLConnection;

/**
 * 文件服务
 * 
 * 【核心安全功能】
 * 1. 文件上传：AES-256-GCM加密存储
 * 2. 文件下载：权限校验 + 完整性校验 + 解密
 * 3. 文件列表：用户隔离查询
 * 4. 密钥管理：三层密钥架构
 * 
 * @author CourseDesign
 */
@Slf4j
@Service
public class FileService {
    
    @Resource
    private FileMapper fileMapper;
    
    @Resource
    private UserService userService;
    
    /**
     * 文件存储根目录
     */
    @Value("${secure-file.storage-root}")
    private String storageRoot;

    /**
        * 大文件上传阈值（超过该大小启用分块加密）
     */
        @Value("${secure-file.upload.large-file-threshold:52428800}")
        private long uploadLargeFileThreshold;

        /**
        * 大文件预览阈值（超过该大小不允许预览）
        */
        @Value("${secure-file.preview.large-file-threshold:2147483648}")
        private long previewLargeFileThreshold;

    /**
     * 分块大小（字节）
     */
    @Value("${secure-file.preview.chunk-size:4194304}")
    private int previewChunkSize;


    private static final byte[] CHUNK_MAGIC = new byte[]{'S', 'F', 'M', '1'};
    private static final byte CHUNK_VERSION = 1;
    private static final byte FLAG_CHUNKED = 0x01;
    private static final int CHUNK_IV_BASE_LEN = 8;
    private static final int CHUNK_TAG_LEN = 16;
    private static final int CHUNK_HEADER_SIZE = 4 + 1 + 1 + 4 + 8;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ========== 分块解密缓存（大文件视频 seek 优化） ==========
    // 缓存最近访问的解密分块，避免同一分块被反复解密
    private static final int MAX_CHUNK_CACHE_ENTRIES = 6;            // 最多缓存6个分块
    private static final long CHUNK_CACHE_EXPIRY_MS = 5 * 60 * 1000; // 5分钟过期
    private static long currentChunkCacheSize = 0;
    private static final Object chunkCacheLock = new Object();

    private static class CachedChunk {
        final byte[] data;
        final long timestamp;
        CachedChunk(byte[] data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CHUNK_CACHE_EXPIRY_MS;
        }
    }

    private static final LinkedHashMap<String, CachedChunk> chunkCache =
            new LinkedHashMap<String, CachedChunk>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedChunk> eldest) {
                    boolean shouldRemove = size() > MAX_CHUNK_CACHE_ENTRIES;
                    if (shouldRemove && eldest.getValue() != null) {
                        currentChunkCacheSize -= eldest.getValue().data.length;
                    }
                    return shouldRemove;
                }
            };

    private void cleanExpiredChunkCache() {
        synchronized (chunkCacheLock) {
            chunkCache.entrySet().removeIf(entry -> {
                if (entry.getValue().isExpired()) {
                    currentChunkCacheSize -= entry.getValue().data.length;
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * 清除指定文件的分块缓存（文件删除时调用）
     */
    private void evictChunkCache(Long fileId) {
        synchronized (chunkCacheLock) {
            chunkCache.entrySet().removeIf(entry -> {
                if (entry.getKey().contains("_" + fileId + "_")) {
                    currentChunkCacheSize -= entry.getValue().data.length;
                    return true;
                }
                return false;
            });
        }
    }
    
    /**
     * 文件上传
     * 
     * 【安全流程】
     * 1. 读取原始文件内容
     * 2. 计算原始文件SHA-256哈希（用于完整性校验）
     * 3. 压缩文件内容（节省存储空间）
     * 4. 生成随机文件密钥（DEK）
     * 5. 生成随机IV
     * 6. 使用AES-256-GCM加密压缩后的文件内容
     * 7. 使用用户主密钥加密文件密钥
     * 8. 生成随机存储路径（UUID）
     * 9. 将加密文件写入磁盘
     * 10. 将元数据和加密参数存入数据库
     * 
     * @param file 上传的文件
     * @param userId 用户ID
     * @param description 文件描述
     * @return 文件ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long uploadFile(MultipartFile file, Long userId, String description, Integer isFolder) {
        String originalFilename = file.getOriginalFilename();
        long fileSize = file.getSize();
        String contentType = file.getContentType();
        
        // 空文件校验
        if (file.isEmpty() || fileSize == 0) {
            throw new RuntimeException("不能上传空文件");
        }
        
        // 文件名校验
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new RuntimeException("文件名不能为空");
        }
        if (originalFilename.length() > 255) {
            throw new RuntimeException("文件名过长，最大255个字符");
        }
        // 防止路径遍历攻击
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new RuntimeException("文件名包含非法字符");
        }

        log.info("开始上传文件: {}, 大小: {} bytes, 用户: {}, 是否文件夹: {}",
                originalFilename, fileSize, userId, isFolder);

        if (fileSize >= uploadLargeFileThreshold) {
            return uploadLargeFile(file, userId, description, isFolder, originalFilename, fileSize, contentType);
        }

        String storagePath = null;
        try {
            // ========== 第一步：读取原始文件 ==========
            byte[] originalFileData = file.getBytes();

            // ========== 第二步：计算原始文件哈希（完整性基准） ==========
            String fileHash = AESUtil.sha256(originalFileData);
            log.info("原始文件哈希: {}", fileHash);

            // ========== 第三步：压缩文件内容 ==========
            byte[] compressedData = CompressionUtil.compress(originalFileData);
            log.info("文件压缩完成 - 原始: {} bytes, 压缩后: {} bytes, 压缩率: {}%",
                    originalFileData.length,
                    compressedData.length,
                    String.format("%.2f", (1 - (double) compressedData.length / originalFileData.length) * 100));

            // ========== 第四步：生成文件密钥（DEK） ==========
            String fileKey = AESUtil.generateKey();

            // ========== 第五步：生成IV ==========
            String iv = AESUtil.generateIV();

            // ========== 第六步：加密压缩后的文件内容 ==========
            AESUtil.EncryptResult encryptResult = AESUtil.encrypt(
                    compressedData,
                    fileKey,
                    iv
            );
            byte[] encryptedData = encryptResult.getCiphertext();
            String authTag = encryptResult.getAuthTag();

            log.info("文件加密完成，加密后大小: {} bytes", encryptedData.length);

            // ========== 第七步：使用用户主密钥加密文件密钥 ==========
            // 获取用户主密钥（解密后）
            String userMasterKey = userService.getUserMasterKey(userId);

            // 加密文件密钥
            String fileKeyIv = AESUtil.generateIV();
            AESUtil.EncryptResult keyEncryptResult = AESUtil.encrypt(
                    fileKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    userMasterKey,
                    fileKeyIv
            );

            // 将加密后的文件密钥、authTag和IV组合存储
            // 格式: Base64(ciphertext) + ":" + authTag + ":" + iv
            String encryptedFileKey = java.util.Base64.getEncoder()
                    .encodeToString(keyEncryptResult.getCiphertext())
                    + ":" + keyEncryptResult.getAuthTag()
                    + ":" + fileKeyIv;

            log.info("文件密钥加密完成，格式: ciphertext:authTag:iv");

            // ========== 第八步：生成存储路径 ==========
            storagePath = generateStoragePath();

            // ========== 第九步：写入加密文件到磁盘 ==========
            File storageFile = new File(storagePath);
            storageFile.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(storageFile)) {
                fos.write(encryptedData);
            }
            log.info("加密文件已写入: {}", storagePath);

            // ========== 第十步：保存元数据到数据库 ==========
            FileInfo fileInfo = new FileInfo();
            fileInfo.setUserId(userId);
            fileInfo.setOriginalFilename(originalFilename);
            fileInfo.setFileSize(fileSize);
            fileInfo.setFileType(contentType);
            fileInfo.setStoragePath(storagePath);
            fileInfo.setEncryptedSize((long) encryptedData.length);
            fileInfo.setEncryptedFileKey(encryptedFileKey);
            fileInfo.setIv(iv);
            fileInfo.setAuthTag(authTag);
            fileInfo.setFileHash(fileHash);
            fileInfo.setUploadTime(LocalDateTime.now());
            fileInfo.setDownloadCount(0);
            fileInfo.setDescription(description);
            fileInfo.setIsFolder(isFolder != null ? isFolder : 0);

            fileMapper.insert(fileInfo);

            log.info("文件上传成功，文件ID: {}", fileInfo.getId());
            return fileInfo.getId();

        } catch (Exception e) {
            // 上传失败时清理已写入的物理文件
            if (storagePath != null) {
                try {
                    Files.deleteIfExists(Paths.get(storagePath));
                } catch (IOException cleanupEx) {
                    log.warn("清理失败的上传文件时出错: {}", storagePath, cleanupEx);
                }
            }
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 文件下载
     * 
     * 【安全流程】
     * 1. 权限校验：查询时强制带user_id条件
     * 2. 读取加密文件
     * 3. 解密文件密钥
     * 4. 解密文件内容（GCM自动校验authTag）
     * 5. 二次完整性校验（SHA-256）
     * 6. 返回原始文件
     * 
     * @param fileId 文件ID
     * @param userId 当前用户ID
     * @return 文件数据和元信息
     */
    public FileDownloadResult downloadFile(Long fileId, Long userId) {
        try {
            // ========== 第一步：权限校验 ==========
            // 【核心安全】查询时同时带fileId和userId，实现权限隔离
            FileInfo fileInfo = fileMapper.selectByIdAndUserId(fileId, userId);
            if (fileInfo == null) {
                log.warn("用户 {} 尝试访问无权限的文件 {}", userId, fileId);
                throw new RuntimeException("文件不存在或无访问权限");
            }
            
            log.info("开始下载文件: {}, 用户: {}", fileInfo.getOriginalFilename(), userId);
            
            // ========== 第二步：读取加密文件 ==========
            File storageFile = new File(fileInfo.getStoragePath());
            if (!storageFile.exists()) {
                log.error("物理文件不存在: {}", fileInfo.getStoragePath());
                throw new RuntimeException("文件已损坏或被删除");
            }

            if (isChunkedFile(storageFile.toPath())) {
                return downloadChunkedFile(fileInfo, userId, storageFile);
            }
            
            byte[] encryptedData;
            try (FileInputStream fis = new FileInputStream(storageFile)) {
                encryptedData = IOUtils.toByteArray(fis);
            }
            
            // ========== 第三步：解密文件密钥 ==========
            String fileKey = decryptFileKey(fileInfo, userId);
            
            // ========== 第四步：解密文件内容 ==========
            // GCM模式会自动校验authTag，如果数据被篡改会抛出异常
            log.info("加密数据大小: {} bytes", encryptedData.length);
            
            byte[] decryptedData = AESUtil.decrypt(
                    encryptedData,
                    fileInfo.getAuthTag(),
                    fileKey,
                    fileInfo.getIv()
            );
            
            log.info("文件解密成功，解密后大小: {} bytes", decryptedData.length);
            
            // ========== 第五步：解压文件内容 ==========
            log.info("开始解压文件...");
            byte[] decompressedData = CompressionUtil.decompress(decryptedData);
            log.info("文件解压完成 - 压缩: {} bytes, 解压后: {} bytes", 
                    decryptedData.length, decompressedData.length);
            
            // ========== 第六步：完整性校验 ==========
            // 【重要】校验解压后的原始文件哈希，确保整个流程无误
            String computedHash = AESUtil.sha256(decompressedData);
            log.info("文件哈希校验 - 存储: {}, 计算: {}", fileInfo.getFileHash(), computedHash);
            
            if (!computedHash.equals(fileInfo.getFileHash())) {
                log.error("文件哈希校验失败！存储: {}, 计算: {}", 
                        fileInfo.getFileHash(), computedHash);
                throw new RuntimeException("文件完整性校验失败");
            }
            
            log.info("文件完整性校验通过");
            
            // ========== 第七步：更新下载信息 ==========
            fileMapper.updateDownloadInfo(fileId);
            
            log.info("文件下载成功: {}", fileInfo.getOriginalFilename());
            
            return new FileDownloadResult(
                    decompressedData,  // 返回解压后的原始数据
                    fileInfo.getOriginalFilename(),
                    fileInfo.getFileType()
            );
            
        } catch (Exception e) {
            log.error("文件下载失败", e);
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 查询用户的文件列表
     * 
     * 【安全】强制带userId条件，实现用户隔离
     * 
     * @param userId 用户ID
     * @return 文件列表
     */
    public List<FileInfoResponse> listFiles(Long userId) {
        List<FileInfo> fileList = fileMapper.selectByUserId(userId);
        
        return fileList.stream()
                .map(file -> {
                    FileInfoResponse response = new FileInfoResponse();
                    BeanUtils.copyProperties(file, response);
                    return response;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 分页查询用户的文件列表
     * 
     * 【安全】强制带userId条件，实现用户隔离
     * 
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 文件列表（分页）
     */
        public com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileInfoResponse> listFiles(
            Long userId, Integer page, Integer size, String fileName, String description, String keyword) {

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileInfo> pageParam =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileInfo> fileInfoPage =
            fileMapper.selectPageByUserIdWithFilters(pageParam, userId, fileName, description, keyword);

        // 转换为响应对象
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileInfoResponse> responsePage =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>();
        responsePage.setCurrent(fileInfoPage.getCurrent());
        responsePage.setSize(fileInfoPage.getSize());
        responsePage.setTotal(fileInfoPage.getTotal());
        responsePage.setPages(fileInfoPage.getPages());

        List<FileInfoResponse> records = fileInfoPage.getRecords().stream()
            .map(file -> {
                FileInfoResponse response = new FileInfoResponse();
                BeanUtils.copyProperties(file, response);
                return response;
            })
            .collect(Collectors.toList());
        responsePage.setRecords(records);

        return responsePage;
        }

        @Transactional(rollbackFor = Exception.class)
        public void updateFileDescription(Long fileId, Long userId, String description) {
        FileInfo fileInfo = fileMapper.selectByIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new RuntimeException("文件不存在或无访问权限");
        }
        fileInfo.setDescription(description);
        fileMapper.updateById(fileInfo);
        }
    
    /**
     * 删除文件
     * 
     * @param fileId 文件ID
     * @param userId 用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long fileId, Long userId) {
        // 权限校验
        FileInfo fileInfo = fileMapper.selectByIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new RuntimeException("文件不存在或无访问权限");
        }

        // 清除分块缓存
        evictChunkCache(fileId);

        // 逻辑删除（进入回收站）
        fileMapper.deleteById(fileId);
        
        log.info("文件已移入回收站: {}", fileInfo.getOriginalFilename());
    }
    
    /**
     * 删除用户的所有文件
     * 用于账号注销时清理数据
     * 
     * @param userId 用户ID
     */
    public void deleteAllUserFiles(Long userId) {
        // 查询用户的所有文件
        List<FileInfo> files = fileMapper.selectAllByUserId(userId);
        
        for (FileInfo file : files) {
            // 删除物理文件
            File storageFile = new File(file.getStoragePath());
            try {
                boolean deleted = java.nio.file.Files.deleteIfExists(storageFile.toPath());
                if (deleted) {
                    log.info("已删除物理文件: {}", file.getStoragePath());
                } else {
                    log.warn("物理文件不存在，无需删除: {}", file.getStoragePath());
                }
            } catch (IOException e) {
                log.error("删除物理文件失败: {}", file.getStoragePath(), e);
                throw new RuntimeException("删除物理文件失败", e);
            }

            
            // 删除数据库记录
            fileMapper.deletePhysicalByIdAndUserId(file.getId(), userId);
        }
        
        log.info("已删除用户 {} 的所有文件，共 {} 个", userId, files.size());
    }

    /**
     * 分页查询回收站文件
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileInfoResponse> listDeletedFiles(
            Long userId, Integer page, Integer size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileInfo> pageParam =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileInfo> fileInfoPage =
                fileMapper.selectPageDeletedByUserId(pageParam, userId);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileInfoResponse> responsePage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>();
        responsePage.setCurrent(fileInfoPage.getCurrent());
        responsePage.setSize(fileInfoPage.getSize());
        responsePage.setTotal(fileInfoPage.getTotal());
        responsePage.setPages(fileInfoPage.getPages());

        List<FileInfoResponse> records = fileInfoPage.getRecords().stream()
                .map(file -> {
                    FileInfoResponse response = new FileInfoResponse();
                    BeanUtils.copyProperties(file, response);
                    return response;
                })
                .collect(Collectors.toList());
        responsePage.setRecords(records);

        return responsePage;
    }

    /**
     * 还原回收站文件
     */
    @Transactional(rollbackFor = Exception.class)
    public void restoreFile(Long fileId, Long userId) {
        fileMapper.restoreByIdAndUserId(fileId, userId);
        log.info("文件已还原: fileId={}, userId={}", fileId, userId);
    }

    /**
     * 彻底删除文件
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteFilePermanently(Long fileId, Long userId) {
        // 直接查询指定文件（包含已删除）并校验权限
        FileInfo fileInfo = fileMapper.selectDeletedByIdAndUserId(fileId, userId);

        if (fileInfo == null) {
            throw new RuntimeException("文件不存在或无访问权限");
        }

        File storageFile = new File(fileInfo.getStoragePath());
        try {
            boolean deleted = java.nio.file.Files.deleteIfExists(storageFile.toPath());
            if (deleted) {
                log.info("已删除物理文件: {}", fileInfo.getStoragePath());
            } else {
                log.warn("物理文件不存在，无需删除: {}", fileInfo.getStoragePath());
            }
        } catch (IOException e) {
            log.error("删除物理文件失败: {}", fileInfo.getStoragePath(), e);
            throw new RuntimeException("删除物理文件失败", e);
        }


        // 清除分块缓存
        evictChunkCache(fileId);

        fileMapper.deletePhysicalByIdAndUserId(fileId, userId);
        log.info("文件已彻底删除: fileId={}, userId={}", fileId, userId);
    }


    private Long uploadLargeFile(MultipartFile file,
                                 Long userId,
                                 String description,
                                 Integer isFolder,
                                 String originalFilename,
                                 long fileSize,
                                 String contentType) {
        try (InputStream inputStream = file.getInputStream()) {
             return uploadStream(inputStream, originalFilename, fileSize, contentType, userId, description, isFolder);
        } catch (IOException e) {
             throw new RuntimeException("读取上传流失败", e);
        }
    }

    /**
     * 流式上传大文件（支持各种InputStream来源，如分片合并流）
     */
    public Long uploadStream(InputStream inputStream,
                             String originalFilename,
                             long fileSize,
                             String contentType,
                             Long userId,
                             String description,
                             Integer isFolder) {
        String storagePath = null;
        try {
            // ========== 大文件上传：分块加密写入 ==========
            String fileKey = AESUtil.generateKey();

            // 用户主密钥
            String userMasterKey = userService.getUserMasterKey(userId);

            // 加密文件密钥
            String fileKeyIv = AESUtil.generateIV();
            AESUtil.EncryptResult keyEncryptResult = AESUtil.encrypt(
                    fileKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    userMasterKey,
                    fileKeyIv
            );

            String encryptedFileKey = java.util.Base64.getEncoder()
                    .encodeToString(keyEncryptResult.getCiphertext())
                    + ":" + keyEncryptResult.getAuthTag()
                    + ":" + fileKeyIv;

            storagePath = generateStoragePath();
            File storageFile = new File(storagePath);
            storageFile.getParentFile().mkdirs();

            byte[] baseIv = new byte[CHUNK_IV_BASE_LEN];
            SECURE_RANDOM.nextBytes(baseIv);
            String baseIvHex = bytesToHex(baseIv);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteArrayOutputStream tagBuffer = new ByteArrayOutputStream();

            int chunkCount = 0;
            // 注意：这里不要关闭传入的 inputStream，由调用方负责
            try (BufferedInputStream bis = new BufferedInputStream(inputStream);
                 FileOutputStream fos = new FileOutputStream(storageFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                writeChunkedHeader(bos, previewChunkSize, baseIv);

                byte[] buffer = new byte[previewChunkSize];
                int read;
                while ((read = readChunkFully(bis, buffer)) > 0) {
                    byte[] chunkData = Arrays.copyOf(buffer, read);
                    digest.update(chunkData);

                    String chunkIv = buildChunkIvHex(baseIv, chunkCount);
                    AESUtil.EncryptResult encryptResult = AESUtil.encrypt(chunkData, fileKey, chunkIv);
                    bos.write(encryptResult.getCiphertext());
                    tagBuffer.write(hexToBytes(encryptResult.getAuthTag()));
                    chunkCount++;
                }

                bos.write(tagBuffer.toByteArray());
                bos.flush();
            }

            String fileHash = bytesToHex(digest.digest());
            long encryptedSize = CHUNK_HEADER_SIZE + fileSize + (long) chunkCount * CHUNK_TAG_LEN;

            FileInfo fileInfo = new FileInfo();
            fileInfo.setUserId(userId);
            fileInfo.setOriginalFilename(originalFilename);
            fileInfo.setFileSize(fileSize);
            fileInfo.setFileType(contentType);
            fileInfo.setStoragePath(storagePath);
            fileInfo.setEncryptedSize(encryptedSize);
            fileInfo.setEncryptedFileKey(encryptedFileKey);
            fileInfo.setIv(baseIvHex);
            fileInfo.setAuthTag("CHUNKED");
            fileInfo.setFileHash(fileHash);
            fileInfo.setUploadTime(LocalDateTime.now());
            fileInfo.setDownloadCount(0);
            fileInfo.setDescription(description);
            fileInfo.setIsFolder(isFolder != null ? isFolder : 0);

            fileMapper.insert(fileInfo);

            log.info("大文件流式上传成功，文件ID: {}, 分块数: {}", fileInfo.getId(), chunkCount);
            return fileInfo.getId();
        } catch (Exception e) {
            // 上传失败时清理已写入的物理文件，防止孤立文件
            if (storagePath != null) {
                try {
                    Files.deleteIfExists(Paths.get(storagePath));
                } catch (IOException cleanupEx) {
                    log.warn("清理失败的上传文件时出错: {}", storagePath, cleanupEx);
                }
            }
            log.error("大文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    private int readChunkFully(InputStream inputStream, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = inputStream.read(buffer, offset, buffer.length - offset);
            if (read == -1) {
                break;
            }
            offset += read;
            if (read == 0) {
                break;
            }
        }
        return offset;
    }

    private void writeChunkedHeader(OutputStream outputStream, int chunkSize, byte[] baseIv) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(CHUNK_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        buffer.put(CHUNK_MAGIC);
        buffer.put(CHUNK_VERSION);
        buffer.put(FLAG_CHUNKED);
        buffer.putInt(chunkSize);
        buffer.put(baseIv);
        outputStream.write(buffer.array());
    }

    private ChunkedHeader readChunkedHeader(RandomAccessFile raf) throws IOException {
        raf.seek(0);
        byte[] headerBytes = new byte[CHUNK_HEADER_SIZE];
        raf.readFully(headerBytes);

        ByteBuffer buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN);
        byte[] magic = new byte[4];
        buffer.get(magic);
        byte version = buffer.get();
        byte flags = buffer.get();
        int chunkSize = buffer.getInt();
        byte[] baseIv = new byte[CHUNK_IV_BASE_LEN];
        buffer.get(baseIv);

        if (!Arrays.equals(magic, CHUNK_MAGIC) || version != CHUNK_VERSION || (flags & FLAG_CHUNKED) == 0) {
            throw new IOException("非法分块文件头");
        }
        return new ChunkedHeader(chunkSize, baseIv, CHUNK_HEADER_SIZE);
    }

    private boolean isChunkedFile(Path path) {
        if (!Files.exists(path)) {
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            if (raf.length() < CHUNK_HEADER_SIZE) {
                return false;
            }
            byte[] magic = new byte[4];
            raf.readFully(magic);
            return Arrays.equals(magic, CHUNK_MAGIC);
        } catch (IOException e) {
            log.warn("读取分块文件头失败: {}", path, e);
            return false;
        }
    }

    private String buildChunkIvHex(byte[] baseIv, int chunkIndex) {
        ByteBuffer buffer = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
        buffer.put(baseIv);
        buffer.putInt(chunkIndex);
        return bytesToHex(buffer.array());
    }

    private String bytesToHex(byte[] bytes) {
        return AESUtil.bytesToHex(bytes);
    }

    private byte[] hexToBytes(String hex) {
        return AESUtil.hexToBytes(hex);
    }

    private static class ChunkedHeader {
        private final int chunkSize;
        private final byte[] baseIv;
        private final long dataOffset;

        private ChunkedHeader(int chunkSize, byte[] baseIv, long dataOffset) {
            this.chunkSize = chunkSize;
            this.baseIv = baseIv;
            this.dataOffset = dataOffset;
        }
    }

    private static class ChunkLayout {
        private final long dataSize;
        private final long tagOffset;
        private final int chunkCount;

        private ChunkLayout(long dataSize, long tagOffset, int chunkCount) {
            this.dataSize = dataSize;
            this.tagOffset = tagOffset;
            this.chunkCount = chunkCount;
        }
    }

    private ChunkLayout resolveChunkLayout(RandomAccessFile raf, ChunkedHeader header, FileInfo fileInfo) throws IOException {
        long physicalLength = raf.length();
        long payloadLength = physicalLength - header.dataOffset;
        if (payloadLength <= CHUNK_TAG_LEN) {
            throw new RuntimeException("分块文件结构非法");
        }

        long expectedDataSize = fileInfo.getFileSize();
        int expectedChunkCount = (int) ((expectedDataSize + header.chunkSize - 1) / header.chunkSize);
        long expectedTagOffset = header.dataOffset + expectedDataSize;
        long expectedPhysicalLength = expectedTagOffset + (long) expectedChunkCount * CHUNK_TAG_LEN;

        if (expectedChunkCount > 0 && expectedPhysicalLength == physicalLength) {
            return new ChunkLayout(expectedDataSize, expectedTagOffset, expectedChunkCount);
        }

        long base = header.chunkSize + CHUNK_TAG_LEN;
        long approx = payloadLength / base;
        for (long candidate = Math.max(1, approx - 2); candidate <= approx + 2; candidate++) {
            long candidateDataSize = payloadLength - candidate * CHUNK_TAG_LEN;
            if (candidateDataSize <= 0) {
                continue;
            }
            long lower = (candidate - 1) * (long) header.chunkSize;
            long upper = candidate * (long) header.chunkSize;
            if (candidateDataSize > lower && candidateDataSize <= upper) {
                if (candidate > Integer.MAX_VALUE) {
                    throw new RuntimeException("文件过大，无法解密");
                }
                return new ChunkLayout(candidateDataSize, header.dataOffset + candidateDataSize, (int) candidate);
            }
        }

        throw new RuntimeException("无法解析分块文件结构");
    }

    private String decryptFileKey(FileInfo fileInfo, Long userId) {
        String userMasterKey = userService.getUserMasterKey(userId);

        String[] parts = fileInfo.getEncryptedFileKey().split(":");
        if (parts.length != 3) {
            log.error("文件密钥格式错误，期望3部分(ciphertext:authTag:iv)，实际{}部分", parts.length);
            throw new RuntimeException("文件密钥数据格式错误");
        }

        byte[] encryptedFileKeyBytes = java.util.Base64.getDecoder().decode(parts[0]);
        String fileKeyAuthTag = parts[1];
        String fileKeyIv = parts[2];

        byte[] fileKeyBytes = AESUtil.decrypt(
                encryptedFileKeyBytes,
                fileKeyAuthTag,
                userMasterKey,
                fileKeyIv
        );

        return new String(fileKeyBytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private FileDownloadResult downloadChunkedFile(FileInfo fileInfo, Long userId, File storageFile) {
        try (RandomAccessFile raf = new RandomAccessFile(storageFile, "r")) {
            ChunkedHeader header = readChunkedHeader(raf);
            String fileKey = decryptFileKey(fileInfo, userId);

            long fileSize = fileInfo.getFileSize();
            int chunkSize = header.chunkSize;
            long chunkCount = (fileSize + chunkSize - 1) / chunkSize;
            long tagOffset = header.dataOffset + fileSize;

            if (chunkCount > Integer.MAX_VALUE) {
                throw new RuntimeException("文件过大，无法解密");
            }

            byte[] tags = new byte[(int) chunkCount * CHUNK_TAG_LEN];
            raf.seek(tagOffset);
            raf.readFully(tags);

            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.min(fileSize, Integer.MAX_VALUE));
            for (int i = 0; i < chunkCount; i++) {
                long offset = header.dataOffset + (long) i * chunkSize;
                int length = (int) Math.min(chunkSize, fileSize - (long) i * chunkSize);
                byte[] cipher = new byte[length];
                raf.seek(offset);
                raf.readFully(cipher);

                byte[] tag = Arrays.copyOfRange(tags, i * CHUNK_TAG_LEN, (i + 1) * CHUNK_TAG_LEN);
                String authTagHex = bytesToHex(tag);
                String ivHex = buildChunkIvHex(header.baseIv, i);

                byte[] plain = AESUtil.decrypt(cipher, authTagHex, fileKey, ivHex);
                bos.write(plain);
            }

            byte[] data = bos.toByteArray();
            String computedHash = AESUtil.sha256(data);
            if (!computedHash.equals(fileInfo.getFileHash())) {
                log.error("文件哈希校验失败！存储: {}, 计算: {}",
                        fileInfo.getFileHash(), computedHash);
                throw new RuntimeException("文件完整性校验失败");
            }

            fileMapper.updateDownloadInfo(fileInfo.getId());

            return new FileDownloadResult(
                    data,
                    fileInfo.getOriginalFilename(),
                    fileInfo.getFileType()
            );
        } catch (IOException e) {
            log.error("分块文件下载失败", e);
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 全量预览文件（不分区间）
     * 使用与 downloadFile 完全一致的解密逻辑，避免 resolveChunkLayout 偏移兼容问题。
     * 适用于 ≤512MB 的小文件完整预览。
     */
    public PreviewResult previewFileFull(Long fileId, Long userId) {
        FileInfo fileInfo = fileMapper.selectByIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new RuntimeException("文件不存在或无访问权限");
        }

        File storageFile = new File(fileInfo.getStoragePath());
        if (!storageFile.exists()) {
            throw new RuntimeException("文件已损坏或被删除");
        }

        byte[] data;
        if ("CHUNKED".equals(fileInfo.getAuthTag())) {
            // 分块加密文件：使用与 downloadChunkedFile 一致的直接布局解密
            data = decryptChunkedDirect(fileInfo, userId, storageFile);
        } else {
            // 非分块文件
            data = decryptNonChunked(fileInfo, userId, storageFile);
        }

        return new PreviewResult(data, fileInfo.getFileSize(), fileInfo.getFileType());
    }

    /**
     * 直接布局分块解密（与 downloadChunkedFile 完全一致的逻辑）
     * 不使用 resolveChunkLayout，直接根据 fileInfo.getFileSize() 计算偏移
     */
    private byte[] decryptChunkedDirect(FileInfo fileInfo, Long userId, File storageFile) {
        try (RandomAccessFile raf = new RandomAccessFile(storageFile, "r")) {
            ChunkedHeader header = readChunkedHeader(raf);
            String fileKey = decryptFileKey(fileInfo, userId);

            long fileSize = fileInfo.getFileSize();
            int chunkSize = header.chunkSize;
            long chunkCount = (fileSize + chunkSize - 1) / chunkSize;
            long tagOffset = header.dataOffset + fileSize;

            if (chunkCount > Integer.MAX_VALUE) {
                throw new RuntimeException("文件过大，无法解密");
            }

            byte[] tags = new byte[(int) chunkCount * CHUNK_TAG_LEN];
            raf.seek(tagOffset);
            raf.readFully(tags);

            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.min(fileSize, Integer.MAX_VALUE));
            for (int i = 0; i < chunkCount; i++) {
                long offset = header.dataOffset + (long) i * chunkSize;
                int length = (int) Math.min(chunkSize, fileSize - (long) i * chunkSize);
                byte[] cipher = new byte[length];
                raf.seek(offset);
                raf.readFully(cipher);

                byte[] tag = Arrays.copyOfRange(tags, i * CHUNK_TAG_LEN, (i + 1) * CHUNK_TAG_LEN);
                String authTagHex = bytesToHex(tag);
                String ivHex = buildChunkIvHex(header.baseIv, i);

                byte[] plain = AESUtil.decrypt(cipher, authTagHex, fileKey, ivHex);
                bos.write(plain);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("分块文件解密失败", e);
            throw new RuntimeException("文件解密失败: " + e.getMessage(), e);
        }
    }

    public PreviewResult previewFileRange(Long fileId, Long userId, Long rangeStart, Long rangeEnd) {
        FileInfo fileInfo = fileMapper.selectByIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new RuntimeException("文件不存在或无访问权限");
        }

        File storageFile = new File(fileInfo.getStoragePath());
        if (!storageFile.exists()) {
            throw new RuntimeException("文件已损坏或被删除");
        }

        long totalSize = fileInfo.getFileSize();
        long start = rangeStart != null ? rangeStart : 0;
        long end = rangeEnd != null ? rangeEnd : totalSize - 1;

        if (start < 0 || end < start || end >= totalSize) {
            throw new RuntimeException("请求范围无效");
        }

        byte[] data;
        // 使用 authTag 判断分块格式（比检测物理文件头更可靠）
        boolean isChunked = "CHUNKED".equals(fileInfo.getAuthTag());
        if (!isChunked && isChunkedFile(storageFile.toPath())) {
            log.warn("authTag 非 CHUNKED 但物理文件头匹配分块格式，按分块处理: fileId={}", fileId);
            isChunked = true;
        }

        if (isChunked) {
            try {
                data = decryptChunkedRange(fileInfo, userId, storageFile, start, end);
            } catch (RuntimeException ex) {
                log.warn("分块区间预览失败，回退直接布局全量解密: fileId={}, start={}, end={}, totalSize={}",
                        fileId, start, end, totalSize, ex);
                // 使用与 downloadChunkedFile 一致的直接布局解密
                byte[] fullData = decryptChunkedDirect(fileInfo, userId, storageFile);
                data = Arrays.copyOfRange(fullData, (int) start, (int) end + 1);
            }
        } else {
            byte[] fullData = decryptNonChunked(fileInfo, userId, storageFile);
            data = Arrays.copyOfRange(fullData, (int) start, (int) end + 1);
        }

        return new PreviewResult(data, totalSize, fileInfo.getFileType());
    }

    private byte[] decryptNonChunked(FileInfo fileInfo, Long userId, File storageFile) {
        try (FileInputStream fis = new FileInputStream(storageFile)) {
            byte[] encryptedData = IOUtils.toByteArray(fis);
            String fileKey = decryptFileKey(fileInfo, userId);

            byte[] decryptedData = AESUtil.decrypt(
                    encryptedData,
                    fileInfo.getAuthTag(),
                    fileKey,
                    fileInfo.getIv()
            );

            byte[] decompressedData = CompressionUtil.decompress(decryptedData);

            String computedHash = AESUtil.sha256(decompressedData);
            if (!computedHash.equals(fileInfo.getFileHash())) {
                log.error("文件哈希校验失败！存储: {}, 计算: {}",
                        fileInfo.getFileHash(), computedHash);
                throw new RuntimeException("文件完整性校验失败");
            }

            return decompressedData;
        } catch (IOException e) {
            log.error("解密文件失败", e);
            throw new RuntimeException("文件解密失败: " + e.getMessage(), e);
        }
    }

    private byte[] decryptChunkedRange(FileInfo fileInfo, Long userId, File storageFile, long start, long end) {
        cleanExpiredChunkCache();
        try (RandomAccessFile raf = new RandomAccessFile(storageFile, "r")) {
            ChunkedHeader header = readChunkedHeader(raf);

            // 使用直接计算布局（与 downloadChunkedFile 一致）
            long fileSize = fileInfo.getFileSize();
            int chunkSize = header.chunkSize;
            long tagBaseOffset = header.dataOffset + fileSize;

            long chunkStart = start / chunkSize;
            long chunkEnd = end / chunkSize;

            // fileKey 延迟获取，仅在存在缓存未命中的分块时才解密
            String fileKey = null;

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            for (long i = chunkStart; i <= chunkEnd; i++) {
                String cacheKey = userId + "_" + fileInfo.getId() + "_" + i;

                // 1. 尝试从缓存获取
                byte[] plain = null;
                synchronized (chunkCacheLock) {
                    CachedChunk cached = chunkCache.get(cacheKey);
                    if (cached != null && !cached.isExpired()) {
                        plain = cached.data;
                    }
                }

                // 2. 缓存未命中，解密并缓存
                if (plain == null) {
                    if (fileKey == null) {
                        fileKey = decryptFileKey(fileInfo, userId);
                    }

                    long chunkOffset = header.dataOffset + i * chunkSize;
                    int length = (int) Math.min(chunkSize, fileSize - i * chunkSize);
                    byte[] cipher = new byte[length];
                    raf.seek(chunkOffset);
                    raf.readFully(cipher);

                    byte[] tag = new byte[CHUNK_TAG_LEN];
                    raf.seek(tagBaseOffset + i * CHUNK_TAG_LEN);
                    raf.readFully(tag);

                    String authTagHex = bytesToHex(tag);
                    String ivHex = buildChunkIvHex(header.baseIv, (int) i);
                    plain = AESUtil.decrypt(cipher, authTagHex, fileKey, ivHex);

                    // 存入缓存
                    synchronized (chunkCacheLock) {
                        CachedChunk old = chunkCache.get(cacheKey);
                        if (old != null) {
                            currentChunkCacheSize -= old.data.length;
                        }
                        chunkCache.put(cacheKey, new CachedChunk(plain));
                        currentChunkCacheSize += plain.length;
                    }
                    log.debug("分块缓存写入: {}, 大小: {} bytes, 总缓存: {} bytes", cacheKey, plain.length, currentChunkCacheSize);
                }

                // 3. 从解密数据中切片
                int from = (int) (i == chunkStart ? start - i * chunkSize : 0);
                int to = (int) (i == chunkEnd ? end - i * chunkSize + 1 : plain.length);
                bos.write(plain, from, to - from);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("分块文件预览失败", e);
            throw new RuntimeException("文件预览失败: " + e.getMessage(), e);
        }
    }

    private byte[] decryptChunkedAll(FileInfo fileInfo, Long userId, File storageFile) {
        try (RandomAccessFile raf = new RandomAccessFile(storageFile, "r")) {
            ChunkedHeader header = readChunkedHeader(raf);
            String fileKey = decryptFileKey(fileInfo, userId);

            // 优先使用直接计算布局（与 downloadChunkedFile 一致，经过验证的逻辑）
            long fileSize = fileInfo.getFileSize();
            int chunkSize = header.chunkSize;
            long chunkCount = (fileSize + chunkSize - 1) / chunkSize;
            long tagOffset = header.dataOffset + fileSize;

            if (chunkCount > Integer.MAX_VALUE) {
                throw new RuntimeException("文件过大，无法解密");
            }

            // 策略1：直接布局 + 尾部tag（与下载路径完全一致）
            try {
                byte[] data = decryptChunkedAllTailTags(raf, header, fileKey, fileSize, chunkSize, chunkCount, tagOffset);
                return validateChunkedDataWithCompatibility(fileInfo, data);
            } catch (RuntimeException ex) {
                log.warn("直接布局+尾部tag解密失败，尝试resolveChunkLayout: fileId={}", fileInfo.getId(), ex);
            }

            // 策略2：使用resolveChunkLayout重新计算布局 + 尾部tag
            try {
                ChunkLayout layout = resolveChunkLayout(raf, header, fileInfo);
                byte[] data = decryptChunkedAllTailTags(raf, header, fileKey,
                        layout.dataSize, header.chunkSize, layout.chunkCount, layout.tagOffset);
                return validateChunkedDataWithCompatibility(fileInfo, data);
            } catch (RuntimeException ex2) {
                log.warn("resolveChunkLayout+尾部tag解密失败，尝试交错tag兼容: fileId={}", fileInfo.getId(), ex2);
            }

            // 策略3：交错tag格式兼容
            byte[] data = decryptChunkedAllInterleavedTags(raf, header, fileKey, fileSize, chunkSize, chunkCount);
            return validateChunkedDataWithCompatibility(fileInfo, data);
        } catch (IOException e) {
            log.error("分块文件解密失败", e);
            throw new RuntimeException("文件解密失败: " + e.getMessage(), e);
        }
    }

    private byte[] decryptChunkedAllTailTags(RandomAccessFile raf,
                                             ChunkedHeader header,
                                             String fileKey,
                                             long fileSize,
                                             int chunkSize,
                                             long chunkCount,
                                             long tagOffset) throws IOException {
        byte[] tags = new byte[(int) chunkCount * CHUNK_TAG_LEN];
        raf.seek(tagOffset);
        raf.readFully(tags);

        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.min(fileSize, Integer.MAX_VALUE));
        for (int i = 0; i < chunkCount; i++) {
            long offset = header.dataOffset + (long) i * chunkSize;
            int length = (int) Math.min(chunkSize, fileSize - (long) i * chunkSize);
            byte[] cipher = new byte[length];
            raf.seek(offset);
            raf.readFully(cipher);

            byte[] tag = Arrays.copyOfRange(tags, i * CHUNK_TAG_LEN, (i + 1) * CHUNK_TAG_LEN);
            String authTagHex = bytesToHex(tag);
            String ivHex = buildChunkIvHex(header.baseIv, i);

            byte[] plain = AESUtil.decrypt(cipher, authTagHex, fileKey, ivHex);
            bos.write(plain);
        }
        return bos.toByteArray();
    }

    private byte[] decryptChunkedAllInterleavedTags(RandomAccessFile raf,
                                                    ChunkedHeader header,
                                                    String fileKey,
                                                    long fileSize,
                                                    int chunkSize,
                                                    long chunkCount) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.min(fileSize, Integer.MAX_VALUE));
        long cursor = header.dataOffset;
        for (int i = 0; i < chunkCount; i++) {
            int length = (int) Math.min(chunkSize, fileSize - (long) i * chunkSize);
            byte[] cipher = new byte[length];
            raf.seek(cursor);
            raf.readFully(cipher);
            cursor += length;

            byte[] tag = new byte[CHUNK_TAG_LEN];
            raf.seek(cursor);
            raf.readFully(tag);
            cursor += CHUNK_TAG_LEN;

            String authTagHex = bytesToHex(tag);
            String ivHex = buildChunkIvHex(header.baseIv, i);
            byte[] plain = AESUtil.decrypt(cipher, authTagHex, fileKey, ivHex);
            bos.write(plain);
        }
        return bos.toByteArray();
    }

    private byte[] validateChunkedDataWithCompatibility(FileInfo fileInfo, byte[] data) {
        String computedHash = AESUtil.sha256(data);
        if (computedHash.equals(fileInfo.getFileHash())) {
            return data;
        }

        try {
            byte[] decompressedData = CompressionUtil.decompress(data);
            String decompressedHash = AESUtil.sha256(decompressedData);
            if (decompressedHash.equals(fileInfo.getFileHash())) {
                log.info("检测到历史分块压缩格式，已自动兼容解压: fileId={}", fileInfo.getId());
                return decompressedData;
            }
        } catch (Exception ex) {
            log.debug("分块数据按压缩格式解压失败，保持原校验逻辑: fileId={}", fileInfo.getId(), ex);
        }

        log.error("文件哈希校验失败！存储: {}, 计算: {}",
                fileInfo.getFileHash(), computedHash);
        throw new RuntimeException("文件完整性校验失败");
    }

    public FileInfo getFileInfoForUser(Long fileId, Long userId) {
        FileInfo fileInfo = fileMapper.selectByIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new RuntimeException("文件不存在或无访问权限");
        }
        return fileInfo;
    }

    public boolean isLargeFile(long fileSize) {
        return fileSize > previewLargeFileThreshold;
    }

    public long getLargeFileThreshold() {
        return previewLargeFileThreshold;
    }

    /**
     * 流式输出解密后的文件内容到输出流（大文件优化）
     * 对分块加密文件逐块解密写出，每次内存中仅持有一个分块，避免全量加载到内存
     * 适用于 >512MB 的大文件预览场景
     */
    public void streamDecryptedToOutput(FileInfo fileInfo, Long userId, OutputStream outputStream) {
        File storageFile = new File(fileInfo.getStoragePath());
        if (!storageFile.exists()) {
            throw new RuntimeException("文件已损坏或被删除");
        }

        if (isChunkedFile(storageFile.toPath())) {
            try (RandomAccessFile raf = new RandomAccessFile(storageFile, "r")) {
                ChunkedHeader header = readChunkedHeader(raf);
                String fileKey = decryptFileKey(fileInfo, userId);

                // 使用直接计算布局（与 downloadChunkedFile 一致）
                long fileSize = fileInfo.getFileSize();
                int chunkSize = header.chunkSize;
                int chunkCount = (int) ((fileSize + chunkSize - 1) / chunkSize);
                long tagBaseOffset = header.dataOffset + fileSize;

                byte[] tags = new byte[chunkCount * CHUNK_TAG_LEN];
                raf.seek(tagBaseOffset);
                raf.readFully(tags);

                for (int i = 0; i < chunkCount; i++) {
                    long offset = header.dataOffset + (long) i * chunkSize;
                    int length = (int) Math.min(chunkSize, fileSize - (long) i * chunkSize);
                    byte[] cipher = new byte[length];
                    raf.seek(offset);
                    raf.readFully(cipher);

                    byte[] tag = Arrays.copyOfRange(tags, i * CHUNK_TAG_LEN, (i + 1) * CHUNK_TAG_LEN);
                    String authTagHex = bytesToHex(tag);
                    String ivHex = buildChunkIvHex(header.baseIv, i);

                    byte[] plain = AESUtil.decrypt(cipher, authTagHex, fileKey, ivHex);
                    outputStream.write(plain);
                    outputStream.flush();
                }
            } catch (IOException e) {
                log.error("流式预览文件失败", e);
                throw new RuntimeException("文件预览失败: " + e.getMessage(), e);
            }
        } else {
            // 非分块文件始终 <50MB，直接加载到内存解密
            byte[] data = decryptNonChunked(fileInfo, userId, storageFile);
            try {
                outputStream.write(data);
                outputStream.flush();
            } catch (IOException e) {
                throw new RuntimeException("写入输出流失败", e);
            }
        }
    }

    public void writeFolderZipToStream(Long fileId, Long userId, OutputStream outputStream) {
        FileInfo fileInfo = fileMapper.selectByIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new RuntimeException("文件不存在或无访问权限");
        }
        if (fileInfo.getIsFolder() == null || fileInfo.getIsFolder() != 1) {
            throw new RuntimeException("该文件不是文件夹");
        }

        File storageFile = new File(fileInfo.getStoragePath());
        if (!storageFile.exists()) {
            throw new RuntimeException("文件已损坏或被删除");
        }

        if (isChunkedFile(storageFile.toPath())) {
            streamChunkedFile(fileInfo, userId, storageFile, outputStream);
        } else {
            byte[] data = decryptNonChunked(fileInfo, userId, storageFile);
            try {
                outputStream.write(data);
                outputStream.flush();
            } catch (IOException e) {
                throw new RuntimeException("写入输出流失败", e);
            }
        }
    }


    public List<FileEntryResponse> listFolderEntries(Long fileId, Long userId) {
        FileInfo fileInfo = fileMapper.selectByIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new RuntimeException("文件不存在或无访问权限");
        }
        if (fileInfo.getIsFolder() == null || fileInfo.getIsFolder() != 1) {
            throw new RuntimeException("该文件不是文件夹");
        }

        File storageFile = new File(fileInfo.getStoragePath());
        byte[] folderData = isChunkedFile(storageFile.toPath())
                ? decryptChunkedAll(fileInfo, userId, storageFile)
                : decryptNonChunked(fileInfo, userId, storageFile);

        List<FileEntryResponse> entries = new ArrayList<>();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(folderData);
             ZipInputStream zis = new ZipInputStream(bais)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                boolean isDir = entry.isDirectory();
                Long size = 0L;
                if (!isDir) {
                    long count = 0;
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        count += len;
                    }
                    size = count;
                }
                String fileType = isDir ? null : URLConnection.guessContentTypeFromName(entryName);
                String name = entryName;
                if (name.endsWith("/")) {
                    name = name.substring(0, name.length() - 1);
                }
                int lastSlash = name.lastIndexOf('/');
                if (lastSlash >= 0) {
                    name = name.substring(lastSlash + 1);
                }
                entries.add(new FileEntryResponse(entry.getName(), name, size, isDir, fileType));
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException("读取文件夹内容失败", e);
        }

        return entries;
    }

    public byte[] getFolderEntryData(Long fileId, Long userId, String entryPath) {
        return getFolderEntryDataWithLimit(fileId, userId, entryPath, -1);
    }

    public byte[] getFolderEntryDataWithLimit(Long fileId, Long userId, String entryPath, long sizeLimit) {
        if (entryPath == null || entryPath.trim().isEmpty()) {
            throw new RuntimeException("文件路径不能为空");
        }

        FileInfo fileInfo = fileMapper.selectByIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new RuntimeException("文件不存在或无访问权限");
        }
        if (fileInfo.getIsFolder() == null || fileInfo.getIsFolder() != 1) {
            throw new RuntimeException("该文件不是文件夹");
        }

        File storageFile = new File(fileInfo.getStoragePath());
        byte[] folderData = isChunkedFile(storageFile.toPath())
                ? decryptChunkedAll(fileInfo, userId, storageFile)
                : decryptNonChunked(fileInfo, userId, storageFile);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(folderData);
             ZipInputStream zis = new ZipInputStream(bais);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryPath)) {
                    if (sizeLimit > 0 && entry.getSize() > sizeLimit) {
                        throw new RuntimeException("文件超过2GB，无法在线预览，请下载后查看");
                    }
                    byte[] buffer = new byte[8192];
                    int len;
                    long count = 0;
                    while ((len = zis.read(buffer)) > 0) {
                        count += len;
                        if (sizeLimit > 0 && count > sizeLimit) {
                            throw new RuntimeException("文件超过2GB，无法在线预览，请下载后查看");
                        }
                        bos.write(buffer, 0, len);
                    }
                    return bos.toByteArray();
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败", e);
        }

        throw new RuntimeException("文件不存在");
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFolderEntry(Long fileId, Long userId, String entryPath) {
        if (entryPath == null || entryPath.trim().isEmpty()) {
            throw new RuntimeException("文件路径不能为空");
        }

        FileInfo fileInfo = fileMapper.selectByIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new RuntimeException("文件不存在或无访问权限");
        }
        if (fileInfo.getIsFolder() == null || fileInfo.getIsFolder() != 1) {
            throw new RuntimeException("该文件不是文件夹");
        }

        File storageFile = new File(fileInfo.getStoragePath());
        byte[] folderData = isChunkedFile(storageFile.toPath())
                ? decryptChunkedAll(fileInfo, userId, storageFile)
                : decryptNonChunked(fileInfo, userId, storageFile);

        boolean deleted = false;
        boolean deleteDirectory = entryPath.endsWith("/");
        String deletePrefix = deleteDirectory ? entryPath : entryPath;

        byte[] newZipBytes;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(folderData);
             ZipInputStream zis = new ZipInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                boolean shouldDelete = deleteDirectory ? name.startsWith(deletePrefix) : name.equals(entryPath);
                if (shouldDelete) {
                    deleted = true;
                    zis.closeEntry();
                    continue;
                }

                ZipEntry newEntry = new ZipEntry(name);
                if (entry.getTime() > 0) {
                    newEntry.setTime(entry.getTime());
                }
                zos.putNextEntry(newEntry);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                zos.closeEntry();
                zis.closeEntry();
            }
            newZipBytes = baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("删除文件失败", e);
        }

        if (!deleted) {
            throw new RuntimeException("文件不存在");
        }

        saveUpdatedFileContent(fileInfo, userId, newZipBytes, "application/zip");
    }

    private void saveUpdatedFileContent(FileInfo fileInfo, Long userId, byte[] rawData, String contentType) {
        String fileKey = decryptFileKey(fileInfo, userId);
        File storageFile = new File(fileInfo.getStoragePath());
        if (storageFile.getParentFile() != null) {
            storageFile.getParentFile().mkdirs();
        }

        fileInfo.setFileType(contentType);
        fileInfo.setFileSize((long) rawData.length);
        String fileHash = AESUtil.sha256(rawData);
        fileInfo.setFileHash(fileHash);

        if (rawData.length >= uploadLargeFileThreshold) {
            writeChunkedFileFromBytes(rawData, fileKey, storageFile, fileInfo);
        } else {
            byte[] compressedData = CompressionUtil.compress(rawData);
            String iv = AESUtil.generateIV();
            AESUtil.EncryptResult encryptResult = AESUtil.encrypt(compressedData, fileKey, iv);
            byte[] encryptedData = encryptResult.getCiphertext();

            try (FileOutputStream fos = new FileOutputStream(storageFile)) {
                fos.write(encryptedData);
            } catch (IOException e) {
                throw new RuntimeException("写入文件失败", e);
            }

            fileInfo.setEncryptedSize((long) encryptedData.length);
            fileInfo.setIv(iv);
            fileInfo.setAuthTag(encryptResult.getAuthTag());
        }

        fileMapper.updateById(fileInfo);
    }

    private void writeChunkedFileFromBytes(byte[] rawData, String fileKey, File storageFile, FileInfo fileInfo) {
        try (FileOutputStream fos = new FileOutputStream(storageFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            byte[] baseIv = new byte[CHUNK_IV_BASE_LEN];
            SECURE_RANDOM.nextBytes(baseIv);
            String baseIvHex = bytesToHex(baseIv);

            writeChunkedHeader(bos, previewChunkSize, baseIv);

            ByteArrayOutputStream tagBuffer = new ByteArrayOutputStream();

            int chunkCount = 0;
            int offset = 0;
            while (offset < rawData.length) {
                int len = Math.min(previewChunkSize, rawData.length - offset);
                byte[] chunkData = Arrays.copyOfRange(rawData, offset, offset + len);
                String chunkIv = buildChunkIvHex(baseIv, chunkCount);
                AESUtil.EncryptResult encryptResult = AESUtil.encrypt(chunkData, fileKey, chunkIv);
                bos.write(encryptResult.getCiphertext());
                tagBuffer.write(hexToBytes(encryptResult.getAuthTag()));
                offset += len;
                chunkCount++;
            }

            bos.write(tagBuffer.toByteArray());
            bos.flush();

            long encryptedSize = CHUNK_HEADER_SIZE + rawData.length + (long) chunkCount * CHUNK_TAG_LEN;
            fileInfo.setEncryptedSize(encryptedSize);
            fileInfo.setIv(baseIvHex);
            fileInfo.setAuthTag("CHUNKED");
        } catch (IOException e) {
            throw new RuntimeException("写入文件失败", e);
        }
    }

    private void streamChunkedFile(FileInfo fileInfo, Long userId, File storageFile, OutputStream outputStream) {
        try (RandomAccessFile raf = new RandomAccessFile(storageFile, "r")) {
            ChunkedHeader header = readChunkedHeader(raf);
            String fileKey = decryptFileKey(fileInfo, userId);

            long fileSize = fileInfo.getFileSize();
            int chunkSize = header.chunkSize;
            long chunkCount = (fileSize + chunkSize - 1) / chunkSize;
            long tagOffset = header.dataOffset + fileSize;

            if (chunkCount > Integer.MAX_VALUE) {
                throw new RuntimeException("文件过大，无法解密");
            }

            byte[] tags = new byte[(int) chunkCount * CHUNK_TAG_LEN];
            raf.seek(tagOffset);
            raf.readFully(tags);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            for (int i = 0; i < chunkCount; i++) {
                long offset = header.dataOffset + (long) i * chunkSize;
                int length = (int) Math.min(chunkSize, fileSize - (long) i * chunkSize);
                byte[] cipher = new byte[length];
                raf.seek(offset);
                raf.readFully(cipher);

                byte[] tag = Arrays.copyOfRange(tags, i * CHUNK_TAG_LEN, (i + 1) * CHUNK_TAG_LEN);
                String authTagHex = bytesToHex(tag);
                String ivHex = buildChunkIvHex(header.baseIv, i);

                byte[] plain = AESUtil.decrypt(cipher, authTagHex, fileKey, ivHex);
                digest.update(plain);
                outputStream.write(plain);
            }

            outputStream.flush();

            String computedHash = bytesToHex(digest.digest());
            if (!computedHash.equals(fileInfo.getFileHash())) {
                log.error("文件哈希校验失败！存储: {}, 计算: {}",
                        fileInfo.getFileHash(), computedHash);
                throw new RuntimeException("文件完整性校验失败");
            }

            fileMapper.updateDownloadInfo(fileInfo.getId());
        } catch (IOException e) {
            log.error("流式输出文件失败", e);
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }

    public static class PreviewResult {
        private final byte[] data;
        private final long totalSize;
        private final String contentType;

        public PreviewResult(byte[] data, long totalSize, String contentType) {
            this.data = data;
            this.totalSize = totalSize;
            this.contentType = contentType;
        }

        public byte[] getData() {
            return data;
        }

        public long getTotalSize() {
            return totalSize;
        }

        public String getContentType() {
            return contentType;
        }
    }
    
    /**
     * 生成随机存储路径
     * 
     * 【安全】使用UUID防止路径遍历攻击
     * 格式：/storage/YYYY/MM/UUID.enc
     * 
     * @return 存储路径
     */
    private String generateStoragePath() {
        String dateDir = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String filename = UUID.randomUUID().toString() + ".enc";
        
        return Paths.get(storageRoot, dateDir, filename)
                .toString()
                .replace("\\", "/");
    }
    
    /**
     * 浏览文件夹内容
     * 
     * @param fileId 文件夹ID
     * @param userId 用户ID
     * @return 文件夹内的文件列表
     */
    public List<String> browseFolderContent(Long fileId, Long userId) {
        try {
            log.info("开始浏览文件夹内容: 文件ID: {}, 用户: {}", fileId, userId);
            
            // 查询文件信息（权限校验）
            FileInfo fileInfo = fileMapper.selectByIdAndUserId(fileId, userId);
            if (fileInfo == null) {
                throw new RuntimeException("文件不存在或无权访问");
            }
            
            // 检查是否为文件夹
            if (fileInfo.getIsFolder() == null || fileInfo.getIsFolder() != 1) {
                throw new RuntimeException("该文件不是文件夹");
            }
            
            File storageFile = new File(fileInfo.getStoragePath());
            byte[] folderData;
            if (isChunkedFile(storageFile.toPath())) {
                folderData = decryptChunkedAll(fileInfo, userId, storageFile);
            } else {
                folderData = decryptNonChunked(fileInfo, userId, storageFile);
            }
            
            // 解析ZIP文件列表
            List<String> fileList = new java.util.ArrayList<>();
              try (ByteArrayInputStream bais = new ByteArrayInputStream(folderData);
                 ZipInputStream zis = new ZipInputStream(bais)) {
                
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        fileList.add(entry.getName());
                    }
                    zis.closeEntry();
                }
            }
            
            log.info("文件夹浏览成功，包含 {} 个文件", fileList.size());
            return fileList;
            
        } catch (Exception e) {
            log.error("文件夹浏览失败", e);
            throw new RuntimeException("文件夹浏览失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 文件下载结果
     */
    public static class FileDownloadResult {
        private final byte[] data;
        private final String filename;
        private final String contentType;
        
        public FileDownloadResult(byte[] data, String filename, String contentType) {
            this.data = data;
            this.filename = filename;
            this.contentType = contentType;
        }
        
        public byte[] getData() {
            return data;
        }
        
        public String getFilename() {
            return filename;
        }
        
        public String getContentType() {
            return contentType;
        }
    }
}
