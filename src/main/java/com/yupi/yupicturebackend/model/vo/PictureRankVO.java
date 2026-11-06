package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 榜单条目视图：图片卡片信息 + 窗口内下载量 + 当前名次 + 排名趋势。
 * <p>
 * trend 取值见 {@code PictureRankTrendEnum}：up/down/flat/new；
 * trendDelta 为名次变化绝对值（上升/下降几位，flat 与 new 时为 null）。
 *
 * @author SPHZ
 */
@Data
public class PictureRankVO implements Serializable {

    /**
     * 图片视图
     */
    private PictureVO picture;

    /**
     * 窗口内下载次数
     */
    private Long downloadCount;

    /**
     * 当前名次（从 1 开始）
     */
    private Integer rank;

    /**
     * 排名趋势：up/down/flat/new
     */
    private String trend;

    /**
     * 名次变化位数（上升为正含义，配合 trend 理解；flat/new 为 null）
     */
    private Integer trendDelta;

    private static final long serialVersionUID = 1L;
}
