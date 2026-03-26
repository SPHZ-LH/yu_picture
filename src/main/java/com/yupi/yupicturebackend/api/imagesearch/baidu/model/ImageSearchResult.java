package com.yupi.yupicturebackend.api.imagesearch.baidu.model;

import lombok.Data;

/**
 * @author SPHZ
 * @version 1.0
 * @createDate 2026-03-11
 * @description 百度图片搜索结果类
 */
@Data
public class ImageSearchResult {

    /**
     * 缩略图地址
     */
    private String thumbUrl;

    /**
     * 来源地址
     */
    private String fromUrl;
}

