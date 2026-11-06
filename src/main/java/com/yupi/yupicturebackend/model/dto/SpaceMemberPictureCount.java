package com.yupi.yupicturebackend.model.dto;

import lombok.Data;

/**
 * 空间成员作品数聚合行（picture 按 spaceId+userId 分组计数结果）
 *
 * @author SPHZ
 */
@Data
public class SpaceMemberPictureCount {

    /**
     * 成员用户 id
     */
    private Long userId;

    /**
     * 该成员在本空间的图片数
     */
    private Long pictureCount;
}
