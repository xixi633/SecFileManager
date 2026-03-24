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
import javax.servlet.http.HttpServletResponse;

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

    @ApiOperation("上传分片")
    @PostMapping("/upload")
    public Result<String> uploadChunk(@ModelAttribute ChunkUploadRequest request,
                                      @RequestParam("file") MultipartFile file) {
        fileChunkService.saveChunk(file, request);
        return Result.success("分片上传成功");
    }

    @ApiOperation("合并分片")
    @PostMapping("/merge")
    public Result<String> mergeChunks(@RequestBody ChunkUploadRequest request) {
        // 获取当前用户ID并设置到Request中，以便异步线程使用
        Long userId = com.security.filemanager.interceptor.AuthInterceptor.getCurrentUserId();
        request.setUserId(userId);
        
        // 异步合并和处理
        fileChunkService.mergeChunks(request);
        return Result.success("文件合并中，请稍后在文件列表查看");
    }
}
