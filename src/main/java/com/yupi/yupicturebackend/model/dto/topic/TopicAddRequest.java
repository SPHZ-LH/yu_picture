package com.yupi.yupicturebackend.model.dto.topic;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建专题请求（管理员）
 *
 * @author SPHZ
 */
@Data
public class TopicAddRequest implements Serializable {

    /**
     * 专题名，trim 后 1-64 字
     */
    private String name;

    /**
     * 专题简介，最长 500 字
     */
    private String description;

    /**
     * 封面 URL，必须 http(s) 开头
     */
    private String coverUrl;

    /**
     * 专题分类
     */
    private String category;

    /**
     * 策划人用户 id，可空
     */
    private Long curatorId;

    /**
     * 更新节奏文案（如：每周五更新）
     */
    private String updateSchedule;

    /**
     * 是否设为本周精选（全站至多一条，设置时自动清掉其他精选）
     */
    private Boolean isFeatured;

    private static final long serialVersionUID = 1L;
}
