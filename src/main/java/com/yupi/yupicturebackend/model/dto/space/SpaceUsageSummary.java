package com.yupi.yupicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间使用量汇总（分析 SQL 聚合结果）
 */
@Data
public class SpaceUsageSummary implements Serializable {

    /**
     * 已用总大小（字节）
     */
    private Long totalSize;

    /**
     * 图片总数
     */
    private Long totalCount;

    private static final long serialVersionUID = 1L;
}
