package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUploadRequest implements Serializable {

    /**
     * 图片 id（用于修改）
     */
    private Long id;

    /**
     * 图片 url
     */
    private String fileUrl;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 空间 id（为空表示上传到公共图库）
     */
    private Long spaceId;

    /**
     * 是否存为草稿（仅公共图库上传生效）：true 时图片落库为 DRAFT 审核态，
     * 不进入审核流、对他人不可见，作者后续经 /picture/draft/submit 提交审核
     */
    private Boolean isDraft;

    private static final long serialVersionUID = 1L;
}


