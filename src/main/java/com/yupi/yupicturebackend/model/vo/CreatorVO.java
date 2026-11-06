package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 创作者目录条目视图（创作者页网格）：公开图库作品聚合 + 粉丝数。
 *
 * @author SPHZ
 */
@Data
public class CreatorVO implements Serializable {

    /**
     * 用户 id
     */
    private Long id;

    /**
     * 昵称
     */
    private String userName;

    /**
     * 头像
     */
    private String userAvatar;

    /**
     * 简介
     */
    private String userProfile;

    /**
     * 加入时间
     */
    private Date createTime;

    /**
     * 公开图库过审作品数
     */
    private Long pictureCount;

    /**
     * 作品总下载量
     */
    private Long downloadCount;

    /**
     * 粉丝数
     */
    private Long followerCount;

    private static final long serialVersionUID = 1L;
}
