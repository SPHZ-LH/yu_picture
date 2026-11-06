package com.yupi.yupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片点赞关系（picture_like 表，「喜欢」）。
 * <p>
 * 与收藏（collect）是两套体系：收藏是个人存档，点赞是内容认可。
 * 取消点赞走<b>物理删除</b>（逻辑删会撞 uk_userId_pictureId 导致无法重新点赞）；
 * 不在 picture 表冗余 likeCount（设计只需要获赞总数聚合与本人点赞列表）。
 */
@TableName(value = "picture_like")
@Data
public class PictureLike implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 点赞用户 id
     */
    private Long userId;

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 点赞时间
     */
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
