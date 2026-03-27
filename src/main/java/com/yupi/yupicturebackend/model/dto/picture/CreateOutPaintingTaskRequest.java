package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建图片扩展任务请求
 *
 * @author SPHZ
 * @version 1.0
 * @createDate 2026-03-27
 * @description 用于请求阿里云通义万相图像扩展服务
 */
@Data
public class CreateOutPaintingTaskRequest implements Serializable {

    /**
     * 图片ID
     */
    private Long pictureId;

    /**
     * 模型名称（默认：image-out-painting）
     */
    private String model = "image-out-painting";

    /**
     * 旋转角度（单位：度）
     * 取值范围：0-360
     */
    private Integer angle;

    /**
     * 输出图像比例（格式：宽:高）
     * 例如："16:9", "4:3", "1:1"
     */
    private String outputRatio;

    /**
     * X轴缩放比例
     * 取值范围：1.0-4.0
     */
    private Float xScale;

    /**
     * Y轴缩放比例
     * 取值范围：1.0-4.0
     */
    private Float yScale;

    /**
     * 顶部偏移量（像素）
     */
    private Integer topOffset;

    /**
     * 底部偏移量（像素）
     */
    private Integer bottomOffset;

    /**
     * 左侧偏移量（像素）
     */
    private Integer leftOffset;

    /**
     * 右侧偏移量（像素）
     */
    private Integer rightOffset;

    /**
     * 是否启用最佳质量模式
     */
    private Boolean bestQuality;

    /**
     * 是否限制图像尺寸
     */
    private Boolean limitImageSize;

    /**
     * 是否添加水印
     */
    private Boolean addWatermark;

    private static final long serialVersionUID = 1L;
}
