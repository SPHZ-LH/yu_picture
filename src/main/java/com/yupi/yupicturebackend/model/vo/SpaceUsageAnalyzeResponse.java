package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间资源使用分析响应
 */
@Data
public class SpaceUsageAnalyzeResponse implements Serializable {

    /**
     * 已用总大小（字节）
     */
    private Long usedSize;

    /**
     * 最大总大小（字节），公共图库 / 全空间无配额时为 null
     */
    private Long maxSize;

    /**
     * 存储使用比例（百分比，保留 2 位小数），无配额时为 null
     */
    private Double usedSizeRatio;

    /**
     * 已用图片数量
     */
    private Long usedCount;

    /**
     * 最大图片数量，公共图库 / 全空间无配额时为 null
     */
    private Long maxCount;

    /**
     * 数量使用比例（百分比，保留 2 位小数），无配额时为 null
     */
    private Double usedCountRatio;

    private static final long serialVersionUID = 1L;
}
