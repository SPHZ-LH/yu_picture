package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户关注维度计数（公开主页数据区）
 *
 * @author SPHZ
 */
@Data
public class FollowCountVO implements Serializable {

    /**
     * 粉丝数（关注该用户的人数）
     */
    private Long followerCount;

    /**
     * 关注数（该用户关注的人数）
     */
    private Long followingCount;

    private static final long serialVersionUID = 1L;
}
