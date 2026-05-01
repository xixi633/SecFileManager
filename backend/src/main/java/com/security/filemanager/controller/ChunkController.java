package com.security.filemanager.controller;

import com.security.filemanager.dto.ChunkUploadRequest;
import com.security.filemanager.dto.Result;
import com.security.filemanager.service.FileChunkService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/file/chunk")
@Api(tags = "大文件分片上传")
public class ChunkController {

    @Resource
    private FileChunkService fileChunkService;

    @ApiOperation("检查分片是否存在")
    @GetMapping("/check")
    public Result<Boolean> checkChunk(ChunkUploadRequest request) {
        boolean exists = fileChunkService.checkChunk(request.getIdentifier(), request.getChunkNumber());
        return Result.success(exists);
    }

    @ApiOperation("查询上传会话状态")
    @GetMapping("/session/status")
    public Result<FileChunkService.UploadStatus> getUploadStatus(@RequestParam("identifier") String identifier) {
        Long userId = com.security.filemanager.interceptor.AuthInterceptor.getCurrentUserId();
        return Result.success(fileChunkService.getUploadStatus(identifier, userId));
    }

    @ApiOperation("重置上传会话（用于重传）")
    @PostMapping("/session/reset")
    public Result<Void> resetUploadSession(@RequestParam("identifier") String identifier) {
        Long userId = com.security.filemanager.interceptor.AuthInterceptor.getCurrentUserId();
        fileChunkService.resetUploadSession(identifier, userId);
        return Result.success("会话已重置", null);
    }

    @ApiOperation("取消上传会话")
    @PostMapping("/session/cancel")
    public Result<Void> cancelUploadSession(@RequestParam("identifier") String identifier) {
        Long userId = com.security.filemanager.interceptor.AuthInterceptor.getCurrentUserId();
        fileChunkService.resetUploadSession(identifier, userId);
        return Result.success("上传已取消", null);
    }

    @ApiOperation("上传分片")
    @PostMapping("/upload")
    public Result<String> uploadChunk(@ModelAttribute ChunkUploadRequest request,
                                      @RequestParam("file") MultipartFile file) {
        Long userId = com.security.filemanager.interceptor.AuthInterceptor.getCurrentUserId();
        request.setUserId(userId);
        fileChunkService.saveChunk(file, request);
        return Result.success("分片上传成功");
    }

    @ApiOperation("合并分片（兼容入口）")
    @PostMapping("/merge")
    public Result<Long> mergeChunks(@RequestBody ChunkUploadRequest request) {
        Long userId = com.security.filemanager.interceptor.AuthInterceptor.getCurrentUserId();
        request.setUserId(userId);
        Long fileId = fileChunkService.mergeChunks(request);
        return Result.success("文件上传已完成", fileId);
    }
}
