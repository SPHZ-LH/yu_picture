package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量查询点赞状态请求（列表页一次渲染全部红心）
 *
 * @author SPHZ
 */
@Data
public class PictureLikeBatchRequest implements Serializable {

    /**
     * 图片 id 列表
     */
    private List<Long> pictureIds;

    private static final long serialVersionUID = 1L;
}
