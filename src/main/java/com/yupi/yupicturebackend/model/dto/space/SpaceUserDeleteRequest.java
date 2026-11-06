package com.yupi.yupicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 移出空间成员请求（仅空间管理员）
 */
@Data
public class SpaceUserDeleteRequest implements Serializable {

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 目标成员的用户 id
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}
