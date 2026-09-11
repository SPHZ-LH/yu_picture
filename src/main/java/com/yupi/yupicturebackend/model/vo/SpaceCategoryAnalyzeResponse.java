package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间图片分类分析响应
 */
@Data
public class SpaceCategoryAnalyzeResponse implements Serializable {

    /**
     * 分类
     */
    private String category;

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
