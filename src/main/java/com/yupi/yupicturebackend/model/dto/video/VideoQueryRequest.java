package com.yupi.yupicturebackend.model.dto.video;

import com.yupi.yupicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 视频查询请求。
 * <p>
 * 字段形态与 {@code PictureQueryRequest} 对齐，但去掉了色系筛选：
 * 视频不做色彩提取（见 docs/adr/0011），保留方向与清晰度两项。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class VideoQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 视频名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 视频体积
     */
    private Long videoSize;

    /**
     * 视频宽度
     */
    private Integer videoWidth;

    /**
     * 视频高度
     */
    private Integer videoHeight;

    /**
     * 视频比例
     */
    private Double videoScale;

    /**
     * 屏幕方向筛选：landscape-横屏（宽 >= 高，电脑壁纸）；portrait-竖屏（宽 < 高，手机壁纸）
     */
    private String orientation;

    /**
     * 清晰度档位下限（总像素 videoWidth * videoHeight >= 该值）：1080P=921600 / 2K=2073600 / 4K=8294400
     */
    private Long minPixels;

    /**
     * 视频格式
     */
    private String videoFormat;

    /**
     * 搜索词（同时搜名称、简介等）
     */
    private String searchText;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 状态：0-待审核; 1-通过; 2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;

    /**
     * 审核人 id
     */
    private Long reviewerId;

    /**
     * 空间 id（按空间筛选视频）
     */
    private Long spaceId;

    /**
     * 是否仅查询公共图库（spaceId 为空的视频）
     */
    private Boolean nullSpaceFlag = false;

    private static final long serialVersionUID = 1L;
}
