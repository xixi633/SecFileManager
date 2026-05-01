package com.security.filemanager.dto;

import lombok.Data;

@Data
public class ChunkUploadRequest {
    // 当前分片序号
    private Integer chunkNumber;
    // 分片大小
    private Long chunkSize;
    // 当前分片大小
    private Long currentChunkSize;
    // 总大小
    private Long totalSize;
    // 文件标识（MD5）
    private String identifier;
    // 文件名
    private String filename;
    // 总分片数
    private Integer totalChunks;
    
    // 用户ID（内部使用）
    private Long userId;
}
