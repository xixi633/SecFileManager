package com.security.filemanager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.security.filemanager.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;
import java.util.List;

/**
 * 文件Mapper接口
 */
@Mapper
public interface FileMapper extends BaseMapper<FileInfo> {
    
    /**
     * 查询用户的文件列表
     * 【安全】强制带用户ID条件，实现用户隔离
     * 
     * @param userId 用户ID
     * @return 文件列表
     */
    List<FileInfo> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 分页查询用户的文件列表
     * 【安全】强制带用户ID条件，实现用户隔离
     * 
     * @param page 分页对象
     * @param userId 用户ID
     * @return 文件列表
     */
    Page<FileInfo> selectPageByUserId(Page<FileInfo> page, @Param("userId") Long userId);

    /**
     * 分页查询用户文件（支持名称/描述/关键词）
     */
    Page<FileInfo> selectPageByUserIdWithFilters(Page<FileInfo> page,
                                                 @Param("userId") Long userId,
                                                 @Param("fileName") String fileName,
                                                 @Param("description") String description,
                                                 @Param("keyword") String keyword);
    
    /**
     * 查询用户的指定文件
     * 【安全】强制带用户ID条件，防止越权访问
     * 
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 文件信息
     */
    FileInfo selectByIdAndUserId(@Param("fileId") Long fileId, @Param("userId") Long userId);
    
    /**
     * 更新下载信息
     * 
     * @param fileId 文件ID
     */
    void updateDownloadInfo(@Param("fileId") Long fileId);

    /**
     * 获取所有文件总大小
     *
     * @return 总大小（字节）
     */
    Long selectTotalFileSize();

    /**
     * 获取文件类型分布
     *
     * @return 文件类型统计列表
     */
    List<Map<String, Object>> selectFileTypeDistribution();

    /**
     * 查询用户回收站文件列表
     *
     * @param userId 用户ID
     * @return 回收站文件列表
     */
    List<FileInfo> selectDeletedByUserId(@Param("userId") Long userId);

    /**
     * 分页查询用户回收站文件列表
     *
     * @param page 分页对象
     * @param userId 用户ID
     * @return 回收站文件列表
     */
    Page<FileInfo> selectPageDeletedByUserId(Page<FileInfo> page, @Param("userId") Long userId);

    /**
     * 管理员分页查询回收站文件（忽略逻辑删除）
     *
     * @param page 分页对象
     * @param userId 用户ID（可选）
     * @param fileName 文件名（可选）
     * @return 回收站文件列表
     */
    Page<FileInfo> selectPageDeletedForAdmin(Page<FileInfo> page,
                                             @Param("userId") Long userId,
                                             @Param("fileName") String fileName);

    /**
     * 管理员查询回收站中的指定文件（忽略逻辑删除）
     *
     * @param fileId 文件ID
     * @return 文件信息
     */
    FileInfo selectDeletedByIdForAdmin(@Param("fileId") Long fileId);

    /**
     * 查询用户所有文件（包含已删除）
     *
     * @param userId 用户ID
     * @return 文件列表
     */
    List<FileInfo> selectAllByUserId(@Param("userId") Long userId);

    /**
     * 还原文件
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     */
    void restoreByIdAndUserId(@Param("fileId") Long fileId, @Param("userId") Long userId);

    /**
     * 物理删除文件记录
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     */
    void deletePhysicalByIdAndUserId(@Param("fileId") Long fileId, @Param("userId") Long userId);

    /**
     * 查询用户回收站中的指定文件
     * 【安全】强制带用户ID条件，防止越权访问
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 文件信息
     */
    FileInfo selectDeletedByIdAndUserId(@Param("fileId") Long fileId, @Param("userId") Long userId);
}
