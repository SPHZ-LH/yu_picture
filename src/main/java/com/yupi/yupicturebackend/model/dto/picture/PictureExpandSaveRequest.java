package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 画面扩展保存请求（只收 taskId，结果 URL 由后端重查任务获取，杜绝伪造外链）
 *
 * @author SPHZ
 */
@Data
public class PictureExpandSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 扩图任务 id
     */
    private String taskId;
}
