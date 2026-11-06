package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 今日一图设置请求（管理员）：全站至多一张，设置新图自动取代旧图
 *
 * @author SPHZ
 */
@Data
public class PictureDailySetRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    private static final long serialVersionUID = 1L;
}
