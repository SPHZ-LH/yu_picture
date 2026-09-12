package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间图片标签分析响应
 */
@Data
public class SpaceTagAnalyzeResponse implements Serializable {

    /**
     * 标签
     */
    private String tag;

    /**
     * 关联图片数量
     */
    private Long count;

    private static final long serialVersionUID = 1L;
}
