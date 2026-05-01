package com.security.filemanager.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文件信息响应
 */
@Data
@ApiModel("文件信息")
public class FileInfoResponse {
    
    @ApiModelProperty("文件ID")
    private Long id;
    
    @ApiModelProperty("原始文件名")
    private String originalFilename;
    
    @ApiModelProperty("文件大小（字节）")
    private Long fileSize;
    
    @ApiModelProperty("文件类型")
    private String fileType;
    
    @ApiModelProperty("上传时间")
    private LocalDateTime uploadTime;
    
    @ApiModelProperty("下载次数")
    private Integer downloadCount;
    
    @ApiModelProperty("最后下载时间")
    private LocalDateTime lastDownloadTime;
    
    @ApiModelProperty("文件描述")
    private String description;
    
    @ApiModelProperty("文件哈希值")
    private String fileHash;
    
    @ApiModelProperty("是否为文件夹：0-文件 1-文件夹")
    private Integer isFolder;
}
