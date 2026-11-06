package com.yupi.yupicturebackend.model.dto.picture;

import com.yupi.yupicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class PictureQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 图片名称
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
     * 文件体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private Integer picWidth;

    /**
     * 图片高度
     */
    private Integer picHeight;

    /**
     * 图片比例
     */
    private Double picScale;

    /**
     * 屏幕方向筛选：landscape-横屏（宽 >= 高，电脑壁纸）；portrait-竖屏（宽 < 高，手机壁纸）
     */
    private String orientation;

    /**
     * 清晰度档位下限（总像素 picWidth * picHeight >= 该值）：
     * 1080P=921600 / 2K=2073600 / 4K=8294400 / 8K=33177600
     */
    private Long minPixels;

    /**
     * 色系筛选：red/yellow/green/blue/purple/gray/dark/light/cream（pink 已并入 purple）
     */
    private String colorFamily;

    /**
     * 是否编辑精选（首页「编辑精选」tab）；空表示不筛
     */
    private Boolean isFeatured;

    /**
     * 图片格式
     */
    private String picFormat;

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
     * 空间 id（按空间筛选图片）
     */
    private Long spaceId;

    /**
     * 是否仅查询公共图库（spaceId 为空的图片）
     */
    private Boolean nullSpaceFlag = false;

    private static final long serialVersionUID = 1L;
}

