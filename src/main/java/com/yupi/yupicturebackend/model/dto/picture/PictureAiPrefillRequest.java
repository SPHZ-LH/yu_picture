package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 预填图片信息的请求。
 * <p>
 * 只收图片 id：图片地址由后端按 id 取，不接受客户端传 URL（防伪造外链，与扩图保存同一条纪律）。
 *
 * @author SPHZ
 */
@Data
public class PictureAiPrefillRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    private static final long serialVersionUID = 1L;
}
