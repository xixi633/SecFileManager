package com.security.filemanager.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 文件夹条目响应
 */
@Data
@AllArgsConstructor
@ApiModel("文件夹条目")
public class FileEntryResponse {

    @ApiModelProperty("条目路径")
    private String path;

    @ApiModelProperty("文件名")
    private String name;

    @ApiModelProperty("大小（字节）")
    private Long size;

    @ApiModelProperty("是否目录")
    private Boolean directory;

    @ApiModelProperty("文件类型")
    private String fileType;
}
