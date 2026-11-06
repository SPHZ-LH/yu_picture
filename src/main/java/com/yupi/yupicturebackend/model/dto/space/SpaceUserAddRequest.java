package com.yupi.yupicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 添加空间成员请求（仅空间管理员）
 */
@Data
public class SpaceUserAddRequest implements Serializable {

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 被邀请的用户 id
     */
    private Long userId;

    /**
     * 空间角色：viewer/editor/admin
     */
    private String spaceRole;

    private static final long serialVersionUID = 1L;
}
