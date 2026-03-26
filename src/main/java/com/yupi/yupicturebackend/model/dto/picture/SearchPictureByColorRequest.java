package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * @author SPHZ
 * @version 1.0
 * @createDate 2026-03-11
 * @description 根据颜色查询图片请求
 */
@Data
public class SearchPictureByColorRequest implements Serializable {

    /**
     * 图片主色调
     */
    private String picColor;

    /**
     * 空间 id
     */
    private Long spaceId;

    private static final long serialVersionUID = 1L;
}

