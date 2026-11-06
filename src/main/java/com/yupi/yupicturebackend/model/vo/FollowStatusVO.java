package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 关注状态视图：单个（status 接口）与批量（创作者列表）共用。
 *
 * @author SPHZ
 */
@Data
public class FollowStatusVO implements Serializable {

    /**
     * 被关注者 id
     */
    private Long userId;

    /**
     * 当前用户是否已关注
     */
    private Boolean followed;

    /**
     * 该用户的粉丝数
     */
    private Long followerCount;

    private static final long serialVersionUID = 1L;
}
