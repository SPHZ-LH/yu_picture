package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量查询收藏状态请求：一次问一批图片「我收藏了哪几张」。
 * <p>
 * 列表页每张图块都要显示星标状态，逐张调 {@code /picture/collect/status} 会打出
 * 与图块数等量的请求，故合并为一次。
 */
@Data
public class PictureCollectBatchRequest implements Serializable {

    /**
     * 图片 id 列表
     */
    private List<Long> pictureIds;

    private static final long serialVersionUID = 1L;
}
