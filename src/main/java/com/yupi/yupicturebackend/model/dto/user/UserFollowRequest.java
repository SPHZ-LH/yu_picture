package com.yupi.yupicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 关注/取关请求
 *
 * @author SPHZ
 */
@Data
public class UserFollowRequest implements Serializable {

    /**
     * 被关注者 id
     */
    private Long followeeId;

    private static final long serialVersionUID = 1L;
}
