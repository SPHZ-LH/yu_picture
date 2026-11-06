package com.yupi.yupicturebackend.model.dto.topic;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新专题请求（管理员）：字段全部可选，传了才更新；isFeatured=true 时
 * 自动清掉其他专题的精选位
 *
 * @author SPHZ
 */
@Data
public class TopicUpdateRequest implements Serializable {

    /**
     * 专题 id
     */
    private Long id;

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
     * 策划人用户 id
     */
    private Long curatorId;

    /**
     * 更新节奏文案
     */
    private String updateSchedule;

    /**
     * 是否本周精选
     */
    private Boolean isFeatured;

    private static final long serialVersionUID = 1L;
}
