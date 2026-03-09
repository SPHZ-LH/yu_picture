package com.yupi.yupicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * @author SPHZ
 * @version 1.0
 * @createDate 2026-03-09
 * @description 管理员创建空间请求
 */
@Data
public class SpaceAdminAddRequest implements Serializable {

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    /**
     * 用户 id（管理员可为指定用户创建空间）
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}
