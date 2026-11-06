package com.yupi.yupicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 修改空间成员角色请求（仅空间管理员）
 */
@Data
public class SpaceUserEditRoleRequest implements Serializable {

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 目标成员的用户 id
     */
    private Long userId;

    /**
     * 新的空间角色：viewer/editor/admin
     */
    private String spaceRole;

    private static final long serialVersionUID = 1L;
}
