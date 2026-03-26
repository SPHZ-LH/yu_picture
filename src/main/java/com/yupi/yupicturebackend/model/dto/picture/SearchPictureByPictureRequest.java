package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * @author SPHZ
 * @version 1.0
 * @createDate 2026-03-11
 * @description 以图搜图请求
 */
@Data
public class SearchPictureByPictureRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    private static final long serialVersionUID = 1L;
}
