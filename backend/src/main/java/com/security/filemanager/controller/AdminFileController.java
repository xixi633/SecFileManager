package com.security.filemanager.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.security.filemanager.annotation.RequireAdmin;
import com.security.filemanager.dto.Result;
import com.security.filemanager.entity.FileInfo;
import com.security.filemanager.mapper.FileMapper;
import com.security.filemanager.service.FileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员-文件管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/file")
@Api(tags = "管理员-文件管理")
public class AdminFileController {
    
    @Resource
    private FileMapper fileMapper;
    
    @Resource
    private FileService fileService;
    
    /**
     * 获取所有文件列表（分页）
     */
    @GetMapping("/list")
    @RequireAdmin
    @ApiOperation("获取文件列表")
    public Result<Page<FileInfo>> getFileList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String typeCategory) {

        Page<FileInfo> pageParam = new Page<>(page, size);
        Page<FileInfo> result = fileMapper.selectPageForAdmin(pageParam, userId, fileName, typeCategory);
        
        return Result.success(result);
    }
    
    /**
     * 获取文件统计信息
     */
    @GetMapping("/statistics")
    @RequireAdmin
    @ApiOperation("获取文件统计")
    public Result<Map<String, Object>> getStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        
        // 总文件数（排除已删除文件）
        QueryWrapper<FileInfo> countWrapper = new QueryWrapper<>();
        countWrapper.eq("deleted", 0);
        Long totalCount = fileMapper.selectCount(countWrapper);
        statistics.put("totalCount", totalCount);
        
        // 总文件大小
        Long totalSize = fileMapper.selectTotalFileSize();
        statistics.put("totalSize", totalSize != null ? totalSize : 0L);
        
        // 文件类型分布
        List<Map<String, Object>> typeList = fileMapper.selectFileTypeDistribution();
        Map<String, Long> typeDistribution = new HashMap<>();
        if (typeList != null) {
            for (Map<String, Object> row : typeList) {
                String fileType = row.get("fileType") != null ? row.get("fileType").toString() : "unknown";
                Number count = row.get("count") instanceof Number ? (Number) row.get("count") : 0;
                typeDistribution.put(fileType, count.longValue());
            }
        }
        statistics.put("typeDistribution", typeDistribution);
        
        return Result.success(statistics);
    }
    
    /**
     * 删除文件
     */
    @DeleteMapping("/{fileId}")
    @RequireAdmin
    @ApiOperation("删除文件")
    public Result<Void> deleteFile(@PathVariable Long fileId) {
        // 管理员删除文件，先查询文件获取userId（使用selectById会绕过逻辑删除查询条件）
        QueryWrapper<FileInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("id", fileId);
        wrapper.eq("deleted", 0);  // 只查询未删除的文件
        FileInfo file = fileMapper.selectOne(wrapper);
        if (file == null) {
            return Result.error("文件不存在或已在回收站");
        }
        
        fileService.deleteFile(fileId, file.getUserId());
        
        log.info("管理员删除文件: fileId={}, userId={}", fileId, file.getUserId());
        return Result.success();
    }

    /**
     * 获取回收站文件列表（分页）
     */
    @GetMapping("/recycle/list")
    @RequireAdmin
    @ApiOperation("获取回收站文件列表")
    public Result<Page<FileInfo>> getRecycleFileList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String typeCategory) {

        Page<FileInfo> pageParam = new Page<>(page, size);
        Page<FileInfo> result = fileMapper.selectPageDeletedForAdmin(pageParam, userId, fileName, typeCategory);

        return Result.success(result);
    }

    /**
     * 回收站还原文件
     */
    @PostMapping("/recycle/restore/{fileId}")
    @RequireAdmin
    @ApiOperation("回收站还原文件")
    public Result<Void> restoreFile(@PathVariable Long fileId) {
        FileInfo file = fileMapper.selectDeletedByIdForAdmin(fileId);
        if (file == null) {
            return Result.error("文件不存在或不在回收站");
        }

        fileService.restoreFile(fileId, file.getUserId());
        log.info("管理员还原文件: fileId={}, userId={}", fileId, file.getUserId());
        return Result.success("还原成功", null);
    }

    /**
     * 回收站彻底删除文件
     */
    @DeleteMapping("/recycle/{fileId}")
    @RequireAdmin
    @ApiOperation("回收站彻底删除文件")
    public Result<Void> deleteFilePermanently(@PathVariable Long fileId) {
        FileInfo file = fileMapper.selectDeletedByIdForAdmin(fileId);
        if (file == null) {
            return Result.error("文件不存在或不在回收站");
        }

        fileService.deleteFilePermanently(fileId, file.getUserId());
        log.info("管理员彻底删除文件: fileId={}, userId={}", fileId, file.getUserId());
        return Result.success("彻底删除成功", null);
    }
}
