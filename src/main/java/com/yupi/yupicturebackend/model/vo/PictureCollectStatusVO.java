package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片收藏状态视图：当前用户针对某张图片的收藏状态 + 最新收藏量。
 * <p>
 * 刻意不并入 {@link PictureVO}：PictureVO 详情走多级缓存、所有用户共享，
 * 「我是否已收藏」是会话相关的，混进去会跨用户串状态。
 */
@Data
public class PictureCollectStatusVO implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 是否已被当前用户收藏（未登录恒为 false）
     */
    private Boolean collected;

    /**
     * 收藏量
     */
    private Long collectCount;

    private static final long serialVersionUID = 1L;
}
