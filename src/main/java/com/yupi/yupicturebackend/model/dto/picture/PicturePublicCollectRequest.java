package com.yupi.yupicturebackend.model.dto.picture;

import com.yupi.yupicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 公开收藏列表请求：查看某用户已公开的收藏（对方 collectPublic=false 时返回空页）
 *
 * @author SPHZ
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class PicturePublicCollectRequest extends PageRequest implements Serializable {

    /**
     * 收藏者（被查看）用户 id
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}
