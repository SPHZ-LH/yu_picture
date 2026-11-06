package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 空间动态条目视图：谁在什么时间对哪张图做了什么。
 * 图片已被删除时 picture 为 null（动态本身保留）。
 *
 * @author SPHZ
 */
@Data
public class SpaceActivityVO implements Serializable {

    /**
     * 动态 id
     */
    private Long id;

    /**
     * 操作人用户信息（脱敏）
     */
    private UserVO user;

    /**
     * 动作：upload/edit/delete
     */
    private String action;

    /**
     * 目标图片 id
     */
    private Long targetId;

    /**
     * 目标图片简要视图（名称/缩略图），已删除时为 null
     */
    private PictureVO picture;

    /**
     * 发生时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}
