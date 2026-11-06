package com.yupi.yupicturebackend.model.dto.video;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 视频元信息编辑请求（名称/简介/分类/标签）。
 * <p>
 * 不含任何画面编辑：视频不支持裁剪、缩放、扩展（见 docs/adr/0011）。
 */
@Data
public class VideoEditRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 视频名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tags;

    private static final long serialVersionUID = 1L;
}
