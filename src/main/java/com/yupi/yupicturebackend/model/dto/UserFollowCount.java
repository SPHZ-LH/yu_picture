package com.yupi.yupicturebackend.model.dto;

import lombok.Data;

/**
 * 用户粉丝数聚合行（user_follow 按 followeeId 分组计数结果）
 *
 * @author SPHZ
 */
@Data
public class UserFollowCount {

    /**
     * 被关注者 id
     */
    private Long followeeId;

    /**
     * 粉丝数
     */
    private Long followerCount;
}
