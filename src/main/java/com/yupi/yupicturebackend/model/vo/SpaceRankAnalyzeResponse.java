package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间使用排行分析响应（按存储使用量排序）
 */
@Data
public class SpaceRankAnalyzeResponse implements Serializable {

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间类型：0-私有 1-团队
     */
    private Integer spaceType;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 已用总大小（字节）
     */
    private Long totalSize;

    /**
     * 图片数量
     */
    private Long totalCount;

    /**
     * 最大总大小（字节）
     */
    private Long maxSize;

    private static final long serialVersionUID = 1L;
}
