package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

/**
 * @author SPHZ
 * @version 1.0
 * @createDate 2026-01-18
 * @description 批量上传
 */
@Data
public class PictureUploadByBatchRequest {

    /**
     * 名称前缀
     */
    private String namePrefix;


    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 抓取数量
     */
    private Integer count = 10;
}
