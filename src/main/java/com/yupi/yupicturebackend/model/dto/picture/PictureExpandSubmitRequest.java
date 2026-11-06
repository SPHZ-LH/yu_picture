package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 画面扩展提交请求
 *
 * @author SPHZ
 */
@Data
public class PictureExpandSubmitRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 目标宽高比，如 16:9
     */
    private String outputRatio;
}
