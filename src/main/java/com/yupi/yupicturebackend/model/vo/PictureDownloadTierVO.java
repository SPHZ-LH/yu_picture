package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片下载档位视图（详情页「选择分辨率下载」行）：
 * tier 为档位名（8K/4K/2K/1080P），width/height 为该档衍生图的估算尺寸
 * （按总像素等比缩放，实际产物宽高与估算差 ±1px）。
 * 只列出严格小于原图的档位——原图下载即最大档，不放大小图。
 *
 * @author SPHZ
 */
@Data
public class PictureDownloadTierVO implements Serializable {

    /**
     * 档位名：8K/4K/2K/1080P
     */
    private String tier;

    /**
     * 衍生图宽度（等比缩放估算值）
     */
    private Integer width;

    /**
     * 衍生图高度（等比缩放估算值）
     */
    private Integer height;

    private static final long serialVersionUID = 1L;
}
