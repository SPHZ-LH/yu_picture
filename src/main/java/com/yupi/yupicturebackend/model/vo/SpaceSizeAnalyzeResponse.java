package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间图片大小分析响应
 */
@Data
public class SpaceSizeAnalyzeResponse implements Serializable {

    /**
     * 大小区间：&lt;100KB / 100KB-1MB / &gt;1MB
     */
    private String sizeRange;

    /**
     * 图片数量
     */
    private Long count;

    /**
     * 图片总大小（字节）
     */
    private Long totalSize;

    private static final long serialVersionUID = 1L;
}
