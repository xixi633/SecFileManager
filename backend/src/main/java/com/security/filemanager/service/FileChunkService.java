package com.security.filemanager.service;

import com.security.filemanager.dto.ChunkUploadRequest;
import com.security.filemanager.entity.FileInfo;
import com.security.filemanager.entity.User;
import com.security.filemanager.mapper.FileMapper;
import com.security.filemanager.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class FileChunkService {

    @Value("${secure-file.storage-root}")
    private String storageRoot;
    
    @Resource
    private FileService fileService;

    @Resource
    private UserMapper userMapper;

    private String getChunkPath(String identifier) {
        return storageRoot + File.separator + "temp" + File.separator + identifier;
    }

    public boolean checkChunk(String identifier, Integer chunkNumber) {
        String path = getChunkPath(identifier) + File.separator + chunkNumber;
        return new File(path).exists();
    }

    public void saveChunk(MultipartFile file, ChunkUploadRequest request) {
        String path = getChunkPath(request.getIdentifier());
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            File chunkFile = new File(path + File.separator + request.getChunkNumber());
            file.transferTo(chunkFile);
        } catch (IOException e) {
            log.error("分片保存失败", e);
            throw new RuntimeException("分片保存失败");
        }
    }

    //@Async("taskExecutor") // 移除异步注解，改为同步调用以便前端等待
    public void mergeChunks(ChunkUploadRequest request) {
        String chunkDir = getChunkPath(request.getIdentifier());
        // String mergedFilePath = chunkDir + File.separator + request.getFilename(); // 不需要临时合并文件了
        
        try {
            // 1. 确认所有分片都已上传
            File dir = new File(chunkDir);
            File[] chunks = dir.listFiles(f -> f.getName().matches("\\d+"));
            if (chunks == null || chunks.length == 0) {
                 log.error("没有找到分片文件: {}", request.getIdentifier());
                 throw new RuntimeException("合并失败：分片不存在");
            }
            
            // 简单校验数量（更严谨可以校验所有序号）
            if (chunks.length != request.getTotalChunks()) {
                log.warn("分片数量不一致，期望: {}, 实际: {}", request.getTotalChunks(), chunks.length);
                 // 此时前端可能重试，这里可以不做强制失败，或者抛异常
            }

            // 2. 准备流式合并（直接通过 SequenceInputStream 读取所有分片流，传给 fileService）
            List<File> sortedChunks = Arrays.stream(chunks)
                    .sorted(Comparator.comparingInt(o -> Integer.parseInt(o.getName())))
                    .collect(Collectors.toList());

            // 注意：一次性打开所有分片的文件流可能会占用较多文件句柄
            // 默认系统限制通常是 1024+，对于 20MB 分片，2GB 文件约 100 个句柄，是安全的
            // 我们的前端已调整为 20MB 分片，所以这里安全
            java.util.Vector<InputStream> inputStreams = new java.util.Vector<>();
            try {
                for (File chunk : sortedChunks) {
                    inputStreams.add(new FileInputStream(chunk));
                }
            } catch (IOException e) {
                // 创建流失败时，关闭已打开的流，避免资源泄漏
                for (InputStream is : inputStreams) {
                    try { is.close(); } catch (Exception ignored) {}
                }
                throw new RuntimeException("打开分片文件失败: " + e.getMessage(), e);
            }
            
            try (SequenceInputStream sequenceInputStream = new SequenceInputStream(inputStreams.elements())) {
                
                Long userId = request.getUserId();
                String contentType = "application/octet-stream"; // 默认流类型，也可从 filename 推断
                
                // 3. 调用流式上传接口（一边读取分片流，一边加密写入目标文件）
                fileService.uploadStream(
                    sequenceInputStream, 
                    request.getFilename(), 
                    request.getTotalSize(), 
                    contentType, 
                    userId, 
                    request.getFilename(), // description 默认用文件名
                    0 // isFolder 默认为 0
                );
                
                log.info("分片流式合并并上传成功: {}", request.getIdentifier());
            } finally {
                // SequenceInputStream.close() 会关闭所有流，但为确保万无一失，显式关闭
                for (InputStream is : inputStreams) {
                    try { is.close(); } catch (Exception ignored) {}
                }
            }

            // 4. 删除分片和目录
            for (File chunk : chunks) {
                chunk.delete();
            }
            dir.delete();

        } catch (Exception e) {
            log.error("文件合并处理失败", e);
            throw new RuntimeException("文件合并失败: " + e.getMessage());
        }
    }
}
