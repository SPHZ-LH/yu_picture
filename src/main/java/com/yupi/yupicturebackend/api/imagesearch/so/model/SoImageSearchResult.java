package com.yupi.yupicturebackend.api.imagesearch.so.model;

/**
 * @author SPHZ
 * @version 1.0
 * @createDate 2026-03-26
 * @description 360搜图图片搜索结果
 */

import lombok.Data;


@Data
public class SoImageSearchResult {

    /**
     * 图片地址
     */
    private String imgUrl;

    /**
     * 标题
     */
    private String title;

    /**
     * 图片key
     */
    private String imgkey;

    /**
     * HTTP
     */
    private String http;

    /**
     * HTTPS
     */
    private String https;
}

