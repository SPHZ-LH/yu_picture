package com.yupi.yupicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 空间分析查询请求
 *
 * 查询范围：
 * 1. querySpaceId 不为空：分析指定空间（空间所有者或管理员）
 * 2. querySpaceId 为空且 queryAll 为 false：分析公共图库（仅管理员）
 * 3. querySpaceId 为空且 queryAll 为 true：分析全空间，即公共图库 + 所有空间（仅管理员）
 */
@Data
public class SpaceAnalyzeQueryRequest implements Serializable {

    /**
     * 要分析的空间 id（为空表示公共图库或全空间）
     */
    private Long querySpaceId;

    /**
     * 是否分析全空间（公共图库 + 所有空间），仅 querySpaceId 为空时生效，仅管理员可用
     */
    private Boolean queryAll = false;

    /**
     * 上传行为分析：时间粒度 day / week / month（仅上传趋势接口使用）
     */
    private String granularity;

    /**
     * 上传行为分析：开始时间（可选，默认最近 30 天）
     */
    private Date startTime;

    /**
     * 上传行为分析：结束时间（可选，默认当前时间）
     */
    private Date endTime;

    /**
     * 空间使用排行：取前 N 个空间（可选，默认 10）
     */
    private Integer topN;

    private static final long serialVersionUID = 1L;
}
