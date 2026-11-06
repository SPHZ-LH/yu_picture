package com.yupi.yupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片专题（topic 表）：编辑/策划人维护的作品合集。
 * <p>
 * 精选位（isFeatured）全站至多一条，互斥由服务层保证；
 * 专题与图片的入选关系在 topic_picture，本表不直接挂图片。
 */
@TableName(value = "topic")
@Data
public class Topic implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 专题名
     */
    private String name;

    /**
     * 专题简介
     */
    private String description;

    /**
     * 封面 URL
     */
    private String coverUrl;

    /**
     * 专题分类（独立于图片分类词表）
     */
    private String category;

    /**
     * 策划人用户 id
     */
    private Long curatorId;

    /**
     * 更新节奏文案（如：每周五更新）
     */
    private String updateSchedule;

    /**
     * 是否本周精选（全站至多一条）
     */
    private Boolean isFeatured;

    /**
     * 创建人 id
     */
    private Long userId;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 0-未删除 1-已删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
