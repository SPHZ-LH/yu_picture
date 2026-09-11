package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户上传行为分析响应
 */
@Data
public class SpaceUploadTrendAnalyzeResponse implements Serializable {

    /**
     * 时间点：按天 yyyy-MM-dd、按周 yyyy-Wxx、按月 yyyy-MM
     */
    private String timePoint;

    /**
     * 上传图片数量
     */
    private Long count;

    /**
     * 上传图片总大小（字节）
     */
    private Long totalSize;

    private static final long serialVersionUID = 1L;
}
