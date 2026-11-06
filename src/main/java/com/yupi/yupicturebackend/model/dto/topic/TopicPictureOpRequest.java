package com.yupi.yupicturebackend.model.dto.topic;

import lombok.Data;

import java.io.Serializable;

/**
 * 专题加入/移出图片请求（管理员）。加入要求图片是公共图库过审图。
 *
 * @author SPHZ
 */
@Data
public class TopicPictureOpRequest implements Serializable {

    /**
     * 专题 id
     */
    private Long topicId;

    /**
     * 图片 id
     */
    private Long pictureId;

    private static final long serialVersionUID = 1L;
}
