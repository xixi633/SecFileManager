package com.security.filemanager.controller;

import com.security.filemanager.dto.FileInfoResponse;
import com.security.filemanager.dto.Result;
import com.security.filemanager.interceptor.AuthInterceptor;
import com.security.filemanager.service.FileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件控制器
 * 
 * @author CourseDesign
 */
@Slf4j
@RestController
@RequestMapping("/file")
@Api(tags = "文件管理")
public class FileController {
    
    @Resource
    private FileService fileService;
    
    // 文件预览缓存 - 避免重复解密（使用LRU缓存，最多缓存50个文件，总大小限制600MB）
    private static final long MAX_CACHE_SIZE_BYTES = 600L * 1024 * 1024; // 600MB
    private static final int MAX_CACHE_ENTRIES = 50;
    private static long currentCacheSize = 0;
    private static final Object cacheLock = new Object();
    
    // 使用单一锁保护的LRU LinkedHashMap，不再包装synchronizedMap避免双重锁问题
    private static final java.util.LinkedHashMap<String, CachedFile> fileCache =
            new java.util.LinkedHashMap<String, CachedFile>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, CachedFile> eldest) {
                    // 由于所有操作都在 synchronized(cacheLock) 内，这里可以安全更新 currentCacheSize
                    boolean shouldRemove = size() > MAX_CACHE_ENTRIES || currentCacheSize > MAX_CACHE_SIZE_BYTES;
                    if (shouldRemove && eldest.getValue() != null) {
                        currentCacheSize -= eldest.getValue().data.length;
                    }
                    return shouldRemove;
                }
            };
    
    // 缓存文件的数据结构
    private static class CachedFile {
        byte[] data;
        String contentType;
        long timestamp;
        
        CachedFile(byte[] data, String contentType) {
            this.data = data;
            this.contentType = contentType;
            this.timestamp = System.currentTimeMillis();
        }
        
        // 缓存是否过期（5分钟）
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 5 * 60 * 1000;
        }
    }
    
    /**
     * 清理过期缓存
     */
    private void cleanExpiredCache() {
        synchronized (cacheLock) {
            fileCache.entrySet().removeIf(entry -> {
                if (entry.getValue().isExpired()) {
                    currentCacheSize -= entry.getValue().data.length;
                    return true;
                }
                return false;
            });
        }
    }
    
    /**
     * 文件上传
     */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<Long> uploadFile(
            @ApiParam(value = "文件", required = true) @RequestParam("file") MultipartFile file,
            @ApiParam("文件描述") @RequestParam(value = "description", required = false) String description,
            @ApiParam("是否为文件夹") @RequestParam(value = "isFolder", required = false, defaultValue = "0") Integer isFolder) {
        
        // 获取当前用户ID
        Long userId = AuthInterceptor.getCurrentUserId();
        
        // 上传文件
        Long fileId = fileService.uploadFile(file, userId, description, isFolder);
        
        return Result.success("上传成功", fileId);
    }
    
    /**
     * 文件下载
     */
    @GetMapping("/download/{fileId}")
    @ApiOperation("文件下载")
    public ResponseEntity<byte[]> downloadFile(
            @ApiParam(value = "文件ID", required = true) @PathVariable Long fileId) {
        
        // 获取当前用户ID
        Long userId = AuthInterceptor.getCurrentUserId();

        com.security.filemanager.entity.FileInfo fileInfo = fileService.getFileInfoForUser(fileId, userId);
        
        // 下载文件
        FileService.FileDownloadResult result = fileService.downloadFile(fileId, userId);
        
        // 构建响应头
        HttpHeaders headers = new HttpHeaders();
        
        // 设置Content-Type
        if (fileInfo.getIsFolder() != null && fileInfo.getIsFolder() == 1) {
            headers.setContentType(MediaType.parseMediaType("application/zip"));
        } else if (result.getContentType() != null) {
            headers.setContentType(MediaType.parseMediaType(result.getContentType()));
        } else {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        
        // 设置Content-Disposition（文件名）
        String downloadName = result.getFilename();
        if (fileInfo.getIsFolder() != null && fileInfo.getIsFolder() == 1 && downloadName != null
                && !downloadName.toLowerCase().endsWith(".zip")) {
            downloadName = downloadName + ".zip";
        }
        String encodedFilename = URLEncoder.encode(downloadName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        headers.setContentDispositionFormData("attachment", encodedFilename);
        
        // 设置Content-Length
        headers.setContentLength(result.getData().length);
        
        return new ResponseEntity<>(result.getData(), headers, HttpStatus.OK);
    }

    /**
     * 文件夹打包下载（ZIP流式返回）
     */
    @GetMapping("/download-folder/{fileId}")
    @ApiOperation("文件夹打包下载")
    public void downloadFolder(
            @ApiParam(value = "文件夹ID", required = true) @PathVariable Long fileId,
            HttpServletResponse response) throws IOException {

        Long userId = AuthInterceptor.getCurrentUserId();
        com.security.filemanager.entity.FileInfo fileInfo = fileService.getFileInfoForUser(fileId, userId);

        String downloadName = fileInfo.getOriginalFilename();
        if (downloadName != null && !downloadName.toLowerCase().endsWith(".zip")) {
            downloadName = downloadName + ".zip";
        }

        String encodedFilename = URLEncoder.encode(downloadName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        response.setContentLengthLong(fileInfo.getFileSize());

        try (OutputStream os = response.getOutputStream()) {
            fileService.writeFolderZipToStream(fileId, userId, os);
        }
    }
    
    /**
     * 文件列表（分页）
     */
    @GetMapping("/list")
    @ApiOperation("文件列表（分页）")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileInfoResponse>> listFiles(
            @ApiParam("页码") @RequestParam(value = "page", defaultValue = "1") Integer page,
            @ApiParam("每页大小") @RequestParam(value = "size", defaultValue = "10") Integer size,
            @ApiParam("文件名") @RequestParam(value = "fileName", required = false) String fileName,
            @ApiParam("文件描述") @RequestParam(value = "description", required = false) String description,
            @ApiParam("关键词") @RequestParam(value = "keyword", required = false) String keyword) {
        // 获取当前用户ID
        Long userId = AuthInterceptor.getCurrentUserId();
        
        // 查询文件列表
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileInfoResponse> fileList = 
                fileService.listFiles(userId, page, size, fileName, description, keyword);
        
        return Result.success(fileList);
    }
    
    /**
     * 删除文件
     */
    @DeleteMapping("/{fileId}")
    @ApiOperation("删除文件")
    public Result<Void> deleteFile(
            @ApiParam(value = "文件ID", required = true) @PathVariable Long fileId) {
        
        // 获取当前用户ID
        Long userId = AuthInterceptor.getCurrentUserId();
        
        // 删除文件
        fileService.deleteFile(fileId, userId);
        
        return Result.success("删除成功", null);
    }

    /**
     * 更新文件描述
     */
    @PutMapping("/description/{fileId}")
    @ApiOperation("更新文件描述")
    public Result<Void> updateFileDescription(
            @ApiParam(value = "文件ID", required = true) @PathVariable Long fileId,
            @ApiParam(value = "文件描述") @RequestParam(value = "description", required = false) String description) {
        Long userId = AuthInterceptor.getCurrentUserId();
        fileService.updateFileDescription(fileId, userId, description);
        return Result.success("更新成功", null);
    }

    /**
     * 回收站列表（分页）
     */
    @GetMapping("/recycle/list")
    @ApiOperation("回收站列表（分页）")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileInfoResponse>> listRecycleFiles(
            @ApiParam("页码") @RequestParam(value = "page", defaultValue = "1") Integer page,
            @ApiParam("每页大小") @RequestParam(value = "size", defaultValue = "10") Integer size) {
        Long userId = AuthInterceptor.getCurrentUserId();
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileInfoResponse> fileList =
                fileService.listDeletedFiles(userId, page, size);
        return Result.success(fileList);
    }

    /**
     * 回收站还原文件
     */
    @PostMapping("/recycle/restore/{fileId}")
    @ApiOperation("回收站还原文件")
    public Result<Void> restoreFile(
            @ApiParam(value = "文件ID", required = true) @PathVariable Long fileId) {
        Long userId = AuthInterceptor.getCurrentUserId();
        fileService.restoreFile(fileId, userId);
        return Result.success("还原成功", null);
    }

    /**
     * 回收站彻底删除文件
     */
    @DeleteMapping("/recycle/{fileId}")
    @ApiOperation("回收站彻底删除文件")
    public Result<Void> deleteFilePermanently(
            @ApiParam(value = "文件ID", required = true) @PathVariable Long fileId) {
        Long userId = AuthInterceptor.getCurrentUserId();
        fileService.deleteFilePermanently(fileId, userId);
        return Result.success("彻底删除成功", null);
    }

    /**
     * 文件夹条目列表
     */
    @GetMapping("/folder/{fileId}/entries")
    @ApiOperation("文件夹条目列表")
    public Result<List<com.security.filemanager.dto.FileEntryResponse>> listFolderEntries(
            @ApiParam(value = "文件夹ID", required = true) @PathVariable Long fileId) {
        Long userId = AuthInterceptor.getCurrentUserId();
        List<com.security.filemanager.dto.FileEntryResponse> entries = fileService.listFolderEntries(fileId, userId);
        return Result.success(entries);
    }

    /**
     * 文件夹内文件预览
     * 
     * 支持Range请求(视频/音频拖动进度条)，使用LRU缓存加速(避免重复解密整个文件夹)
     */
    @GetMapping("/folder/preview/{fileId}")
    @ApiOperation("文件夹内文件预览")
    public void previewFolderEntry(
            @ApiParam(value = "文件夹ID", required = true) @PathVariable Long fileId,
            @RequestParam("path") String path,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        Long userId = AuthInterceptor.getCurrentUserId();
        String entryPath = URLDecoder.decode(path, StandardCharsets.UTF_8.name());

        // 从缓存获取或解密文件夹条目数据
        cleanExpiredCache();
        String cacheKey = userId + "_folder_" + fileId + "_" + entryPath;
        CachedFile cachedFile;
        synchronized (cacheLock) {
            cachedFile = fileCache.get(cacheKey);
        }

        if (cachedFile == null || cachedFile.isExpired()) {
            log.info("文件夹条目缓存未命中，开始解密: fileId={}, path={}", fileId, entryPath);
            byte[] data = fileService.getFolderEntryDataWithLimit(fileId, userId, entryPath, fileService.getLargeFileThreshold());
            String contentType = java.net.URLConnection.guessContentTypeFromName(entryPath);
            if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            cachedFile = new CachedFile(data, contentType);
            synchronized (cacheLock) {
                CachedFile oldCache = fileCache.get(cacheKey);
                if (oldCache != null) {
                    currentCacheSize -= oldCache.data.length;
                }
                fileCache.put(cacheKey, cachedFile);
                currentCacheSize += cachedFile.data.length;
            }
            log.info("文件夹条目已缓存: fileId={}, path={}, size={} bytes", fileId, entryPath, data.length);
        }

        byte[] data = cachedFile.data;
        long totalLength = data.length;
        String contentType = cachedFile.contentType != null ? cachedFile.contentType : "application/octet-stream";

        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            // Range请求 → 206 Partial Content
            String range = rangeHeader.substring(6);
            String[] parts = range.split("-");
            long start = 0, end = totalLength - 1;
            try {
                if (parts.length > 0 && !parts[0].isEmpty()) start = Long.parseLong(parts[0]);
                if (parts.length > 1 && !parts[1].isEmpty()) end = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + totalLength);
                return;
            }

            if (start > end || start < 0 || end >= totalLength) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + totalLength);
                return;
            }

            long contentLength = end - start + 1;
            response.setContentType(contentType);
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + totalLength);
            response.setContentLengthLong(contentLength);
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Access-Control-Expose-Headers", "Content-Length, Content-Type, Content-Range, Accept-Ranges");

            try (OutputStream os = response.getOutputStream()) {
                os.write(data, (int) start, (int) contentLength);
                os.flush();
            }
        } else {
            // 普通请求 → 200 OK + Accept-Ranges 告知浏览器可以Range
            response.setContentType(contentType);
            response.setHeader("Accept-Ranges", "bytes");
            response.setContentLengthLong(totalLength);
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Access-Control-Expose-Headers", "Content-Length, Content-Type, Content-Range, Accept-Ranges");

            try (OutputStream os = response.getOutputStream()) {
                os.write(data);
                os.flush();
            }
        }
    }

    /**
     * 文件夹内文件下载
     */
    @GetMapping("/folder/download/{fileId}")
    @ApiOperation("文件夹内文件下载")
    public ResponseEntity<byte[]> downloadFolderEntry(
            @ApiParam(value = "文件夹ID", required = true) @PathVariable Long fileId,
            @RequestParam("path") String path) throws Exception {
        Long userId = AuthInterceptor.getCurrentUserId();
        String entryPath = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        byte[] data = fileService.getFolderEntryData(fileId, userId, entryPath);

        String filename = entryPath;
        int lastSlash = filename.lastIndexOf('/');
        if (lastSlash >= 0) {
            filename = filename.substring(lastSlash + 1);
        }

        String contentType = java.net.URLConnection.guessContentTypeFromName(filename);
        HttpHeaders headers = new HttpHeaders();
        if (contentType != null) {
            headers.setContentType(MediaType.parseMediaType(contentType));
        } else {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        headers.setContentDispositionFormData("attachment", encodedFilename);
        headers.setContentLength(data.length);

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    /**
     * 文件夹内文件删除
     */
    @DeleteMapping("/folder/entry/{fileId}")
    @ApiOperation("文件夹内文件删除")
    public Result<Void> deleteFolderEntry(
            @ApiParam(value = "文件夹ID", required = true) @PathVariable Long fileId,
            @RequestParam("path") String path) throws Exception {
        Long userId = AuthInterceptor.getCurrentUserId();
        String entryPath = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        fileService.deleteFolderEntry(fileId, userId, entryPath);
        return Result.success("删除成功", null);
    }
    
    /**
     * 浏览文件夹内容
     */
    @GetMapping("/browse/{fileId}")
    @ApiOperation("浏览文件夹内容")
    public Result<List<String>> browseFolderContent(
            @ApiParam(value = "文件夹ID", required = true) @PathVariable Long fileId) {
        
        // 获取当前用户ID
        Long userId = AuthInterceptor.getCurrentUserId();
        
        // 浏览文件夹内容
        List<String> fileList = fileService.browseFolderContent(fileId, userId);
        
        return Result.success(fileList);
    }
    
    /**
     * 文件预览
     * 
     * 【安全流程】
     * 1. 校验用户身份（拦截器已处理）
     * 2. 校验文件权限（user_id匹配）
     * 3. 校验文件完整性（Hash + GCM AuthTag）
     * 4. 解密文件到内存流
     * 5. 设置正确的Content-Type
     * 6. 流式输出到客户端
     * 
     * 【大文件预览策略】
     * - ≤512MB: 解密后缓存到内存，快速响应后续请求
     * - >512MB且≤2GB: 逐块解密流式输出，避免全量加载到内存
     * - >2GB: 不支持在线预览
     */
    // 小文件阈值（512MB），小于等于此值的文件加载到内存并缓存
    private static final long SMALL_FILE_THRESHOLD = 512L * 1024 * 1024;

    @GetMapping("/preview/{fileId}")
    @ApiOperation("文件预览")
    public void previewFile(
            @ApiParam(value = "文件ID", required = true) @PathVariable Long fileId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        
        // 获取当前用户ID
        Long userId = AuthInterceptor.getCurrentUserId();

        com.security.filemanager.entity.FileInfo fileInfo = fileService.getFileInfoForUser(fileId, userId);

        // >2GB: 拒绝预览
        if (fileService.isLargeFile(fileInfo.getFileSize())) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write("文件超过2GB，无法在线预览，请下载后查看");
            return;
        }

        String rangeHeader = request.getHeader("Range");

        // ≤512MB: 缓存方式（加载到内存，LRU缓存加速），Range请求也从缓存切片
        if (fileInfo.getFileSize() <= SMALL_FILE_THRESHOLD) {
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                handleCachedRangePreview(fileId, userId, fileInfo, rangeHeader, response);
            } else {
                handleSmallFilePreview(fileId, userId, fileInfo, response);
            }
            return;
        }

        // >512MB: Range请求走逐块解密
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            handleRangePreview(fileId, userId, fileInfo, rangeHeader, response);
            return;
        }

        // >512MB且≤2GB: 流式输出（逐块解密，不缓存）
        streamLargeFilePreview(fileInfo, userId, response);
    }

    /**
     * 小文件缓存Range请求（≤512MB）
     * 先加载/从缓存获取完整解密数据，再根据Range切片返回。
     * 避免视频 seek 操作每次都重新解密，大幅减少卡顿。
     */
    private void handleCachedRangePreview(Long fileId, Long userId,
            com.security.filemanager.entity.FileInfo fileInfo,
            String rangeHeader, HttpServletResponse response) throws IOException {
        try {
            // 1. 解析Range头
            String range = rangeHeader.substring(6);
            String[] parts = range.split("-");
            Long rangeStart = null;
            Long rangeEnd = null;
            try {
                rangeStart = parts.length > 0 && !parts[0].isEmpty() ? Long.parseLong(parts[0]) : null;
                rangeEnd = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : null;
                if (rangeStart == null && rangeEnd == null) {
                    response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    response.setHeader("Content-Range", "bytes */" + fileInfo.getFileSize());
                    return;
                }
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + fileInfo.getFileSize());
                return;
            }

            long fileLength = fileInfo.getFileSize();
            long start;
            long end;
            if (rangeStart == null && rangeEnd != null) {
                long suffixLength = rangeEnd;
                if (suffixLength <= 0) {
                    response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    response.setHeader("Content-Range", "bytes */" + fileLength);
                    return;
                }
                start = Math.max(fileLength - suffixLength, 0);
                end = fileLength - 1;
            } else {
                start = rangeStart != null ? rangeStart : 0;
                end = rangeEnd != null ? rangeEnd : fileLength - 1;
            }
            if (start > end || start < 0 || end >= fileLength) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + fileLength);
                return;
            }

            // 2. 从缓存获取或解密完整文件
            cleanExpiredCache();
            String cacheKey = userId + "_" + fileId;
            CachedFile cachedFile;
            synchronized (cacheLock) {
                cachedFile = fileCache.get(cacheKey);
            }

            if (cachedFile == null || cachedFile.isExpired()) {
                log.info("Range请求缓存未命中，开始解密文件: {}", fileId);
                FileService.PreviewResult fullResult = fileService.previewFileFull(fileId, userId);
                cachedFile = new CachedFile(fullResult.getData(), fullResult.getContentType());
                synchronized (cacheLock) {
                    CachedFile oldCache = fileCache.get(cacheKey);
                    if (oldCache != null) {
                        currentCacheSize -= oldCache.data.length;
                    }
                    fileCache.put(cacheKey, cachedFile);
                    currentCacheSize += cachedFile.data.length;
                }
                log.info("文件已缓存(Range触发): {}, 大小: {} bytes", fileId, cachedFile.data.length);
            }

            // 3. 从缓存数据中切片返回
            String contentType = cachedFile.contentType != null ?
                    cachedFile.contentType : "application/octet-stream";
            long contentLength = end - start + 1;

            response.setContentType(contentType);
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Access-Control-Expose-Headers", "Content-Length, Content-Type, Content-Range, Accept-Ranges");
            response.setHeader("Accept-Ranges", "bytes");
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            response.setContentLengthLong(contentLength);

            try (OutputStream os = response.getOutputStream()) {
                os.write(cachedFile.data, (int) start, (int) contentLength);
                os.flush();
            }
        } catch (Exception e) {
            log.error("处理缓存Range请求失败", e);
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    /**
     * 处理Range请求（视频/音频流式播放、大文件部分预览）
     * 
     * 对>512MB文件的Range请求，限制单次最大返回5MB，避免一次性加载整个大文件到内存。
     * 浏览器会根据 Content-Range 自动发送后续Range请求获取更多数据。
     */
    private static final long MAX_RANGE_RESPONSE = 5L * 1024 * 1024; // 5MB - 大文件单次Range最大返回量

    private void handleRangePreview(Long fileId, Long userId,
            com.security.filemanager.entity.FileInfo fileInfo,
            String rangeHeader, HttpServletResponse response) throws IOException {
        try {
            String range = rangeHeader.substring(6);
            String[] parts = range.split("-");
            Long rangeStart = null;
            Long rangeEnd = null;
            try {
                rangeStart = parts.length > 0 && !parts[0].isEmpty() ? Long.parseLong(parts[0]) : null;
                rangeEnd = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : null;
                if (rangeStart == null && rangeEnd == null) {
                    response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    response.setHeader("Content-Range", "bytes */" + fileInfo.getFileSize());
                    return;
                }
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + fileInfo.getFileSize());
                return;
            }

            long fileLength = fileInfo.getFileSize();
            long start;
            long end;

            if (rangeStart == null && rangeEnd != null) {
                long suffixLength = rangeEnd;
                if (suffixLength <= 0) {
                    response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    response.setHeader("Content-Range", "bytes */" + fileLength);
                    return;
                }
                start = Math.max(fileLength - suffixLength, 0);
                end = fileLength - 1;
            } else {
                start = rangeStart != null ? rangeStart : 0;
                end = rangeEnd != null ? rangeEnd : fileLength - 1;
            }

            if (start > end || start < 0 || end >= fileLength) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + fileLength);
                return;
            }

            // 对大文件限制单次Range返回量，防止 Range: bytes=0- 导致加载整个文件到内存 OOM
            if ((end - start + 1) > MAX_RANGE_RESPONSE) {
                end = start + MAX_RANGE_RESPONSE - 1;
                if (end >= fileLength) {
                    end = fileLength - 1;
                }
            }

            FileService.PreviewResult rangeResult = fileService.previewFileRange(fileId, userId, start, end);
            String contentType = rangeResult.getContentType() != null ?
                    rangeResult.getContentType() : "application/octet-stream";
            contentType = appendCharsetIfNeeded(contentType);

            long contentLength = end - start + 1;
            response.setContentType(contentType);
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Access-Control-Expose-Headers", "Content-Length, Content-Type, Content-Range, Accept-Ranges");
            response.setHeader("Accept-Ranges", "bytes");
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            response.setContentLengthLong(contentLength);

            try (OutputStream os = response.getOutputStream()) {
                os.write(rangeResult.getData());
                os.flush();
            }
        } catch (Throwable e) {
            log.error("处理Range请求失败", e);
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    /**
     * 大文件流式预览（>512MB且≤2GB）
     * 逐块解密并直接写出到响应流，内存中仅持有一个加密分块
     */
    private void streamLargeFilePreview(com.security.filemanager.entity.FileInfo fileInfo,
            Long userId, HttpServletResponse response) throws IOException {
        String contentType = fileInfo.getFileType() != null ?
                fileInfo.getFileType() : "application/octet-stream";
        contentType = appendCharsetIfNeeded(contentType);

        response.setContentType(contentType);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Access-Control-Expose-Headers", "Content-Length, Content-Type, Content-Range, Accept-Ranges");
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentLengthLong(fileInfo.getFileSize());

        log.info("大文件流式预览开始: fileId={}, size={} bytes", fileInfo.getId(), fileInfo.getFileSize());
        try (OutputStream os = response.getOutputStream()) {
            fileService.streamDecryptedToOutput(fileInfo, userId, os);
        } catch (Exception e) {
            log.error("大文件流式预览失败: fileId={}", fileInfo.getId(), e);
            // 如果响应未提交，设置错误码
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
        log.info("大文件流式预览完成: fileId={}", fileInfo.getId());
    }

    /**
     * 小文件缓存预览（≤512MB）
     * 完整解密后缓存到内存LRU，后续请求直接从缓存读取
     */
    private void handleSmallFilePreview(Long fileId, Long userId,
            com.security.filemanager.entity.FileInfo fileInfo,
            HttpServletResponse response) throws IOException {
        FileService.PreviewResult previewResult;
        cleanExpiredCache();

        String cacheKey = userId + "_" + fileId;
        CachedFile cachedFile;
        synchronized (cacheLock) {
            cachedFile = fileCache.get(cacheKey);
        }

        if (cachedFile == null || cachedFile.isExpired()) {
            log.info("缓存未命中或已过期，开始解密文件: {}", fileId);
            previewResult = fileService.previewFileFull(fileId, userId);
            cachedFile = new CachedFile(previewResult.getData(), previewResult.getContentType());
            synchronized (cacheLock) {
                CachedFile oldCache = fileCache.get(cacheKey);
                if (oldCache != null) {
                    currentCacheSize -= oldCache.data.length;
                }
                fileCache.put(cacheKey, cachedFile);
                currentCacheSize += cachedFile.data.length;
            }
            log.info("文件已缓存: {}, 大小: {} bytes, 总缓存大小: {} bytes", fileId, cachedFile.data.length, currentCacheSize);
        } else {
            log.info("使用缓存文件: {}", fileId);
        }

        previewResult = new FileService.PreviewResult(cachedFile.data, fileInfo.getFileSize(), cachedFile.contentType);

        String contentType = previewResult.getContentType() != null ?
                previewResult.getContentType() : "application/octet-stream";
        contentType = appendCharsetIfNeeded(contentType);

        response.setContentType(contentType);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Access-Control-Expose-Headers", "Content-Length, Content-Type, Content-Range, Accept-Ranges");
        response.setHeader("Accept-Ranges", "bytes");

        byte[] data = previewResult.getData();
        response.setContentLength(data.length);

        try (OutputStream os = response.getOutputStream()) {
            os.write(data);
            os.flush();
        }
    }

    /**
     * 为文本类型追加UTF-8字符集
     */
    private String appendCharsetIfNeeded(String contentType) {
        if (contentType.startsWith("text/") ||
            contentType.equals("application/json") ||
            contentType.equals("application/xml") ||
            contentType.equals("application/javascript")) {
            return contentType + "; charset=UTF-8";
        }
        return contentType;
    }

}
