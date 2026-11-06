package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 专题视图：卡片/详情共用。pictureCount 是实时的公共图库过审图计数；
 * 策划人信息由服务层批量回填（curatorId 为空则不填）。
 *
 * @author SPHZ
 */
@Data
public class TopicVO implements Serializable {

    /**
     * id
     */
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
     * 专题分类
     */
    private String category;

    /**
     * 策划人用户 id
     */
    private Long curatorId;

    /**
     * 策划人昵称
     */
    private String curatorName;

    /**
     * 策划人头像
     */
    private String curatorAvatar;

    /**
     * 更新节奏文案
     */
    private String updateSchedule;

    /**
     * 是否本周精选
     */
    private Boolean isFeatured;

    /**
     * 专题内图片数（公共图库过审口径）
     */
    private Long pictureCount;

    /**
     * 创建时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}
