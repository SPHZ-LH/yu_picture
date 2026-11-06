package com.yupi.yupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户收藏图片关系（collect 表）。
 * <p>
 * 一条记录表示某用户收藏了某张图片；取消收藏走<b>物理删除</b>，
 * 因此不设 isDelete：逻辑删除后重新收藏会撞 uk_userId_pictureId 唯一键。
 */
@TableName(value = "collect")
@Data
public class Collect implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 收藏时间
     */
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
