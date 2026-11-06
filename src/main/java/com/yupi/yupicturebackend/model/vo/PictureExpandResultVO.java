package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 画面扩展任务状态视图
 *
 * @author SPHZ
 */
@Data
public class PictureExpandResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务状态：PENDING / RUNNING / SUCCEEDED / FAILED
     */
    private String status;

    /**
     * 结果图地址（仅 SUCCEEDED 时有值，24 小时有效）
     */
    private String imageUrl;

    /**
     * 失败时的中文提示
     */
    private String message;
}
