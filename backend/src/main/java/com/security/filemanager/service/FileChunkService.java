package com.security.filemanager.service;

import com.security.filemanager.dto.ChunkUploadRequest;
import com.security.filemanager.entity.FileInfo;
import com.security.filemanager.exception.BizException;
import com.security.filemanager.mapper.FileMapper;
import com.security.filemanager.util.AESUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class FileChunkService {

    @Value("${secure-file.storage-root}")
    private String storageRoot;

    @Value("${secure-file.upload.session-timeout-ms:900000}")
    private long sessionTimeoutMs;

    @Value("${secure-file.upload.chunk-size:16777216}")
    private int defaultChunkSize;

    @Resource
    private UserService userService;
    @Resource
    private FileMapper fileMapper;

    private static final byte[] CHUNK_MAGIC = new byte[]{'S', 'F', 'M', '1'};
    private static final byte CHUNK_VERSION = 1;
    private static final byte FLAG_CHUNKED = 0x01;
    private static final int CHUNK_IV_BASE_LEN = 8;
    private static final int CHUNK_TAG_LEN = 16;
    private static final int CHUNK_HEADER_SIZE = 4 + 1 + 1 + 4 + 8;
    private static final int STORAGE_STREAM_BUFFER_SIZE = 1024 * 1024;
    private static final String CHUNK_TEMP_DIR = "_chunk_tmp";
    private static final String CHUNK_FILE_SUFFIX = ".chunk";
    private static final String CHUNK_TEMP_SUFFIX = ".part";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final Map<String, UploadSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<String, CompletedUploadResult> COMPLETED_UPLOADS = new ConcurrentHashMap<>();
    private static final long COMPLETED_UPLOAD_TTL_MS = 5 * 60 * 1000L;

    public enum UploadStage { INIT, UPLOADING, FINALIZING, DONE, FAILED, EXPIRED }

    public static class UploadStatus {
        private final String identifier;
        private final UploadStage stage;
        private final long uploadedBytes;
        private final long totalBytes;
        private final int nextChunkNumber;
        private final int totalChunks;
        private final long updatedAt;

        public UploadStatus(String identifier, UploadStage stage, long uploadedBytes, long totalBytes, int nextChunkNumber, int totalChunks, long updatedAt) {
            this.identifier = identifier;
            this.stage = stage;
            this.uploadedBytes = uploadedBytes;
            this.totalBytes = totalBytes;
            this.nextChunkNumber = nextChunkNumber;
            this.totalChunks = totalChunks;
            this.updatedAt = updatedAt;
        }

        public String getIdentifier() { return identifier; }
        public UploadStage getStage() { return stage; }
        public long getUploadedBytes() { return uploadedBytes; }
        public long getTotalBytes() { return totalBytes; }
        public int getNextChunkNumber() { return nextChunkNumber; }
        public int getTotalChunks() { return totalChunks; }
        public long getUpdatedAt() { return updatedAt; }
        public int getProgress() { return totalBytes <= 0 ? 0 : (int) Math.min(100, uploadedBytes * 100 / totalBytes); }
    }

    private static class CompletedUploadResult {
        final Long userId;
        final Long fileId;
        final long timestamp;

        CompletedUploadResult(Long userId, Long fileId) {
            this.userId = userId;
            this.fileId = fileId;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static class UploadSession {
        final String identifier;
        final Long userId;
        final String filename;
        final long totalSize;
        final int totalChunks;
        final File storageFile;
        final String fileKey;
        final String encryptedFileKey;
        final byte[] baseIv;
        final String baseIvHex;
        final MessageDigest digest;
        final ByteArrayOutputStream tagBuffer = new ByteArrayOutputStream();
        final Set<Integer> pendingChunkFiles = ConcurrentHashMap.newKeySet();
        final Set<Integer> writingChunks = ConcurrentHashMap.newKeySet();
        final File chunkTempDir;
        final BufferedOutputStream bos;
        int nextChunkNumber = 0;
        long processedSize = 0;
        volatile UploadStage stage = UploadStage.INIT;
        volatile long lastActiveAt = System.currentTimeMillis();

        UploadSession(String identifier, Long userId, String filename, long totalSize, int totalChunks,
                      File storageFile, String fileKey, String encryptedFileKey,
                      byte[] baseIv, String baseIvHex, MessageDigest digest, BufferedOutputStream bos,
                      File chunkTempDir) {
            this.identifier = identifier;
            this.userId = userId;
            this.filename = filename;
            this.totalSize = totalSize;
            this.totalChunks = totalChunks;
            this.storageFile = storageFile;
            this.fileKey = fileKey;
            this.encryptedFileKey = encryptedFileKey;
            this.baseIv = baseIv;
            this.baseIvHex = baseIvHex;
            this.digest = digest;
            this.bos = bos;
            this.chunkTempDir = chunkTempDir;
        }
    }

    private String generateStoragePath() {
        String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String filename = UUID.randomUUID() + ".enc";
        return Paths.get(storageRoot, dateDir, filename).toString().replace("\\", "/");
    }

    private String buildChunkIvHex(byte[] baseIv, int chunkIndex) {
        ByteBuffer buffer = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
        buffer.put(baseIv); buffer.putInt(chunkIndex);
        return AESUtil.bytesToHex(buffer.array());
    }

    private File buildChunkTempDir(String identifier) {
        String safeId = AESUtil.sha256(identifier.getBytes(StandardCharsets.UTF_8));
        return Paths.get(storageRoot, CHUNK_TEMP_DIR, safeId).toFile();
    }

    private File getChunkFile(UploadSession s, int chunkNumber) {
        return new File(s.chunkTempDir, chunkNumber + CHUNK_FILE_SUFFIX);
    }

    private File getChunkTempFile(UploadSession s, int chunkNumber) {
        return new File(s.chunkTempDir, chunkNumber + CHUNK_TEMP_SUFFIX);
    }

    private void persistChunkToTempFile(MultipartFile file, File tempFile) throws IOException {
        if (tempFile.getParentFile() != null && !tempFile.getParentFile().exists()) {
            tempFile.getParentFile().mkdirs();
        }
        try (InputStream input = file.getInputStream();
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile), STORAGE_STREAM_BUFFER_SIZE)) {
            byte[] buffer = new byte[STORAGE_STREAM_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private void moveTempFile(File tempFile, File targetFile) throws IOException {
        try {
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void cleanupChunkTempDir(UploadSession s) {
        if (s == null || s.chunkTempDir == null) return;
        deleteDirectoryQuietly(s.chunkTempDir);
    }

    private void deleteDirectoryQuietly(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] entries = dir.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    deleteDirectoryQuietly(entry);
                } else {
                    try { Files.deleteIfExists(entry.toPath()); } catch (Exception ignored) {}
                }
            }
        }
        try { Files.deleteIfExists(dir.toPath()); } catch (Exception ignored) {}
    }

    private void writeChunkedHeader(BufferedOutputStream os, int chunkSize, byte[] baseIv) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(CHUNK_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        b.put(CHUNK_MAGIC).put(CHUNK_VERSION).put(FLAG_CHUNKED).putInt(chunkSize).put(baseIv);
        os.write(b.array());
    }

    private UploadSession createSession(ChunkUploadRequest r) {
        try {
            File storageFile = new File(generateStoragePath());
            if (storageFile.getParentFile() != null) storageFile.getParentFile().mkdirs();

            File chunkTempDir = buildChunkTempDir(r.getIdentifier());
            if (chunkTempDir.exists()) {
                deleteDirectoryQuietly(chunkTempDir);
            }
            if (!chunkTempDir.mkdirs() && !chunkTempDir.exists()) {
                throw new IOException("无法创建分片临时目录");
            }

            String fileKey = AESUtil.generateKey();
            String userMasterKey = userService.getUserMasterKey(r.getUserId());
            String fileKeyIv = AESUtil.generateIV();
            AESUtil.EncryptResult kr = AESUtil.encrypt(fileKey.getBytes(StandardCharsets.UTF_8), userMasterKey, fileKeyIv);
            String encryptedFileKey = Base64.getEncoder().encodeToString(kr.getCiphertext()) + ":" + kr.getAuthTag() + ":" + fileKeyIv;

            byte[] baseIv = new byte[CHUNK_IV_BASE_LEN];
            SECURE_RANDOM.nextBytes(baseIv);
            String baseIvHex = AESUtil.bytesToHex(baseIv);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(storageFile), STORAGE_STREAM_BUFFER_SIZE);
            int chunkSize = r.getChunkSize() == null ? defaultChunkSize : r.getChunkSize().intValue();
            if (chunkSize <= 0) {
                chunkSize = defaultChunkSize > 0 ? defaultChunkSize : 16 * 1024 * 1024;
            }
            writeChunkedHeader(bos, chunkSize, baseIv);

                UploadSession s = new UploadSession(r.getIdentifier(), r.getUserId(), r.getFilename(), r.getTotalSize(), r.getTotalChunks(),
                    storageFile, fileKey, encryptedFileKey, baseIv, baseIvHex, digest, bos, chunkTempDir);
            s.stage = UploadStage.UPLOADING;
            return s;
        } catch (Exception e) {
            throw BizException.internal("UPLOAD_INIT_FAILED", "初始化上传会话失败");
        }
    }

    private boolean isExpired(UploadSession s) { return System.currentTimeMillis() - s.lastActiveAt > sessionTimeoutMs; }
    private void touch(UploadSession s) { s.lastActiveAt = System.currentTimeMillis(); }

    private int validateChunkRequestLocked(UploadSession s, ChunkUploadRequest r) {
        if (!s.userId.equals(r.getUserId())) {
            throw BizException.conflict("UPLOAD_SESSION_USER_MISMATCH", "上传会话用户不匹配");
        }
        if (isExpired(s)) {
            expireSession(r.getIdentifier(), s);
            throw BizException.conflict("UPLOAD_SESSION_EXPIRED", "上传会话已过期，请重新上传");
        }

        int chunkNumber = r.getChunkNumber();
        if (chunkNumber < 0 || chunkNumber >= s.totalChunks) {
            throw BizException.badRequest("UPLOAD_CHUNK_NUMBER_INVALID", "分片序号非法");
        }

        touch(s);
        s.stage = UploadStage.UPLOADING;
        return chunkNumber;
    }

    public boolean checkChunk(String identifier, Integer chunkNumber) {
        UploadSession s = SESSIONS.get(identifier);
        return s != null && chunkNumber != null && !isExpired(s)
                && (chunkNumber < s.nextChunkNumber
                || s.pendingChunkFiles.contains(chunkNumber)
                || s.writingChunks.contains(chunkNumber)
                || getChunkFile(s, chunkNumber).exists());
    }

    public UploadStatus getUploadStatus(String identifier, Long userId) {
        UploadSession s = SESSIONS.get(identifier);
        if (s == null) return new UploadStatus(identifier, UploadStage.DONE, 0, 0, 0, 0, System.currentTimeMillis());
        synchronized (s) {
            if (!s.userId.equals(userId)) throw BizException.conflict("UPLOAD_SESSION_USER_MISMATCH", "上传会话用户不匹配");
            if (isExpired(s)) {
                expireSession(identifier, s);
                throw BizException.conflict("UPLOAD_SESSION_EXPIRED", "上传会话已过期，请重新上传");
            }
            return new UploadStatus(s.identifier, s.stage, s.processedSize, s.totalSize, s.nextChunkNumber, s.totalChunks, s.lastActiveAt);
        }
    }

    public void saveChunk(MultipartFile file, ChunkUploadRequest r) {
        if (r.getUserId() == null) throw BizException.badRequest("UPLOAD_USER_MISSING", "用户信息缺失");
        if (r.getChunkNumber() == null) throw BizException.badRequest("UPLOAD_CHUNK_NUMBER_MISSING", "分片序号缺失");
        if (r.getIdentifier() == null || r.getIdentifier().isEmpty()) {
            throw BizException.badRequest("UPLOAD_IDENTIFIER_MISSING", "文件标识缺失");
        }

        UploadSession s = SESSIONS.computeIfAbsent(r.getIdentifier(), k -> createSession(r));
        try {
            int chunkNumber;
            synchronized (s) {
                chunkNumber = validateChunkRequestLocked(s, r);
                if (chunkNumber < s.nextChunkNumber) {
                    return;
                }
                if (chunkNumber == s.nextChunkNumber) {
                    try (InputStream input = file.getInputStream()) {
                        processChunkStream(s, chunkNumber, input);
                    }
                    flushPendingChunks(s);
                    finalizeIfCompleted(s);
                    return;
                }
                if (s.pendingChunkFiles.contains(chunkNumber) || s.writingChunks.contains(chunkNumber)) {
                    return;
                }
                s.writingChunks.add(chunkNumber);
            }

            File tempFile = getChunkTempFile(s, chunkNumber);
            File finalFile = getChunkFile(s, chunkNumber);
            try {
                persistChunkToTempFile(file, tempFile);
                moveTempFile(tempFile, finalFile);
            } catch (Exception e) {
                try { Files.deleteIfExists(tempFile.toPath()); } catch (Exception ignored) {}
                throw e;
            }

            synchronized (s) {
                s.writingChunks.remove(chunkNumber);
                if (chunkNumber < s.nextChunkNumber) {
                    try { Files.deleteIfExists(finalFile.toPath()); } catch (Exception ignored) {}
                    return;
                }
                s.pendingChunkFiles.add(chunkNumber);
                flushPendingChunks(s);
                finalizeIfCompleted(s);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            synchronized (s) {
                if (SESSIONS.get(s.identifier) == s && s.stage != UploadStage.DONE) {
                    failSession(s, e);
                }
            }
            throw BizException.internal("UPLOAD_CHUNK_PROCESS_FAILED", "分片处理失败");
        }
    }

    private void flushPendingChunks(UploadSession s) throws Exception {
        while (true) {
            int chunkNumber = s.nextChunkNumber;
            File chunkFile = getChunkFile(s, chunkNumber);
            if (!chunkFile.exists()) {
                break;
            }
            try (InputStream input = new BufferedInputStream(new FileInputStream(chunkFile), STORAGE_STREAM_BUFFER_SIZE)) {
                processChunkStream(s, chunkNumber, input);
            }
            s.pendingChunkFiles.remove(chunkNumber);
            try { Files.deleteIfExists(chunkFile.toPath()); } catch (Exception ignored) {}
        }
    }

    private void processChunkStream(UploadSession s, int chunkNumber, InputStream chunkStream) throws Exception {
        DigestInputStream digestStream = new DigestInputStream(chunkStream, s.digest);
        String iv = buildChunkIvHex(s.baseIv, chunkNumber);
        AESUtil.EncryptStreamResult result = AESUtil.encryptStreamToStream(digestStream, s.fileKey, iv, s.bos);
        s.tagBuffer.write(AESUtil.hexToBytes(result.getAuthTag()));
        s.processedSize += result.getBytesProcessed();
        s.nextChunkNumber++;
    }

    private void processChunkBytes(UploadSession s, int chunkNumber, byte[] chunkData) throws Exception {
        s.digest.update(chunkData);
        String iv = buildChunkIvHex(s.baseIv, chunkNumber);
        String authTag = AESUtil.encryptToStream(chunkData, s.fileKey, iv, s.bos);
        s.tagBuffer.write(AESUtil.hexToBytes(authTag));
        s.processedSize += chunkData.length;
        s.nextChunkNumber++;
    }

    private void finalizeIfCompleted(UploadSession s) throws IOException {
        if (s.nextChunkNumber == s.totalChunks) {
            s.stage = UploadStage.FINALIZING;
            Long fileId = finalizeSession(s);
            s.stage = UploadStage.DONE;
            SESSIONS.remove(s.identifier);
            COMPLETED_UPLOADS.put(s.identifier, new CompletedUploadResult(s.userId, fileId));
            cleanupChunkTempDir(s);
        }
    }

    private Long finalizeSession(UploadSession s) throws IOException {
        s.bos.write(s.tagBuffer.toByteArray());
        s.bos.flush();
        s.bos.close();

        String fileHash = AESUtil.bytesToHex(s.digest.digest());
        long encryptedSize = CHUNK_HEADER_SIZE + s.totalSize + (long) s.totalChunks * CHUNK_TAG_LEN;

        FileInfo fi = new FileInfo();
        fi.setUserId(s.userId);
        fi.setOriginalFilename(s.filename);
        fi.setFileSize(s.totalSize);
        String ct = URLConnection.guessContentTypeFromName(s.filename);
        fi.setFileType(ct == null ? "application/octet-stream" : ct);
        fi.setStoragePath(s.storageFile.getPath().replace("\\", "/"));
        fi.setEncryptedSize(encryptedSize);
        fi.setEncryptedFileKey(s.encryptedFileKey);
        fi.setIv(s.baseIvHex);
        fi.setAuthTag("CHUNKED");
        fi.setFileHash(fileHash);
        fi.setUploadTime(LocalDateTime.now());
        fi.setDownloadCount(0);
        fi.setDescription(s.filename);
        fi.setIsFolder(0);
        fileMapper.insert(fi);
        log.info("分片增量加密上传完成: {}, fileId={}", s.identifier, fi.getId());
        return fi.getId();
    }

    private void expireSession(String id, UploadSession s) {
        s.stage = UploadStage.EXPIRED;
        safeCloseAndDelete(s);
        SESSIONS.remove(id);
        log.info("上传会话过期并清理: {}", id);
    }

    private void failSession(UploadSession s, Exception e) {
        s.stage = UploadStage.FAILED;
        safeCloseAndDelete(s);
        SESSIONS.remove(s.identifier);
        log.error("分片会话失败: {}", s.identifier, e);
    }

    private void safeCloseAndDelete(UploadSession s) {
        try { s.bos.close(); } catch (Exception ignored) {}
        try { Files.deleteIfExists(s.storageFile.toPath()); } catch (Exception ignored) {}
        cleanupChunkTempDir(s);
    }

    @Scheduled(fixedDelayString = "${secure-file.upload.session-cleanup-interval-ms:60000}")
    public void cleanupExpiredSessions() {
        SESSIONS.forEach((id, s) -> {
            synchronized (s) {
                if (isExpired(s)) expireSession(id, s);
            }
        });
        long now = System.currentTimeMillis();
        COMPLETED_UPLOADS.entrySet().removeIf(e -> now - e.getValue().timestamp > COMPLETED_UPLOAD_TTL_MS);
    }

    public void resetUploadSession(String identifier, Long userId) {
        UploadSession s = SESSIONS.get(identifier);
        if (s == null) {
            throw BizException.conflict("UPLOAD_SESSION_NOT_FOUND", "上传会话不存在或已完成");
        }
        synchronized (s) {
            if (!s.userId.equals(userId)) {
                throw BizException.conflict("UPLOAD_SESSION_USER_MISMATCH", "上传会话用户不匹配");
            }
            s.stage = UploadStage.FAILED;
            safeCloseAndDelete(s);
            SESSIONS.remove(identifier);
        }
    }

    public Long mergeChunks(ChunkUploadRequest request) {
        UploadSession s = SESSIONS.get(request.getIdentifier());
        if (s != null && s.stage != UploadStage.DONE) {
            throw BizException.conflict("UPLOAD_NOT_FINISHED", "分片尚未上传完成");
        }
        CompletedUploadResult result = COMPLETED_UPLOADS.get(request.getIdentifier());
        if (result != null && result.userId.equals(request.getUserId())) {
            return result.fileId;
        }
        return null;
    }
}
