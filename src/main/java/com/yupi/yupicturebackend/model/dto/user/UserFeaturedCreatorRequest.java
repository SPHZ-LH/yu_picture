package com.yupi.yupicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 本周精选创作者设置请求（管理员）：全站至多一位，设置新人自动取代旧人
 *
 * @author SPHZ
 */
@Data
public class UserFeaturedCreatorRequest implements Serializable {

    /**
     * 用户 id
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}
