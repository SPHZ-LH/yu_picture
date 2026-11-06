package com.yupi.yupicturebackend.model.dto.video;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 视频上传请求。
 * <p>
 * 与图片上传不同：视频与封面在同一次 multipart 请求里提交，元信息也一并带上，
 * 目的是让"传对象 + 落库"只有一个往返，中途失败不会留下已入库但残缺的记录。
 */
@Data
public class VideoUploadRequest implements Serializable {

    /**
     * 视频 id（为空表示新增；当前版本不支持重新上传覆盖）
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

    /**
     * 空间 id（为空表示上传到公共图库）
     */
    private Long spaceId;

    /**
     * 视频时长（秒），由前端读 video 元素 metadata 回传
     */
    private Integer duration;

    private static final long serialVersionUID = 1L;
}
