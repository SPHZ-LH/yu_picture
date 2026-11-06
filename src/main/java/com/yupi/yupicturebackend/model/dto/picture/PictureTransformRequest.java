package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片画面编辑请求（裁剪/等比缩放/90°旋转/翻转）。
 * <p>
 * 像素处理不在服务端进行：参数拼成 imageMogr2 规则透传给数据万象云上处理（见 ADR 0001）。
 * 裁剪坐标基于「旋转 + 翻转后」的预览图（即前端所见图像）的像素空间，
 * 因此前端预览 URL 的 imageMogr2 规则段必须与后端第一步处理的规则段逐字一致。
 */
@Data
public class PictureTransformRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long id;

    /**
     * 顺时针旋转角度：0/90/180/270，空或 0 表示不旋转
     */
    private Integer rotate;

    /**
     * 翻转方向：horizontal / vertical，空表示不翻转
     */
    private String flip;

    /**
     * 裁剪起点 x。与 cropY/cropW/cropH 要么都不传（不裁剪），要么都传
     */
    private Integer cropX;

    /**
     * 裁剪起点 y
     */
    private Integer cropY;

    /**
     * 裁剪宽度
     */
    private Integer cropW;

    /**
     * 裁剪高度
     */
    private Integer cropH;

    /**
     * 输出宽度（px），高度等比缩放；空表示不缩放
     */
    private Integer outputWidth;

    /**
     * 亮度：0-200，100 为原图（前端滑杆语义与 CSS filter 一致）；
     * 服务端换算为 imageMogr2 的 [-100,100] 偏移值。空表示不调整
     */
    private Integer brightness;

    /**
     * 对比度：0-200，100 为原图；空表示不调整
     */
    private Integer contrast;

    /**
     * 饱和度：0-200，100 为原图；空表示不调整。
     * 设计稿的滤镜预设（黑白/胶片/暖阳/冷调/青橙）由前端映射为这三组滑杆值，
     * 服务端只认原始参数（imageMogr2 无色相类算子，见 docs/adr/0017）
     */
    private Integer saturation;

    private static final long serialVersionUID = 1L;
}
