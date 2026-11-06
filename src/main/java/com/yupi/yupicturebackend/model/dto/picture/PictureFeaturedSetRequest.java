package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 编辑精选标记请求（管理员）：isFeatured=false 取消精选
 *
 * @author SPHZ
 */
@Data
public class PictureFeaturedSetRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 是否编辑精选
     */
    private Boolean isFeatured;

    private static final long serialVersionUID = 1L;
}
