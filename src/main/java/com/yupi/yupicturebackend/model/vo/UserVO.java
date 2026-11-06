package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class UserVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    private Long id;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    /**
     * 收藏列表是否公开（公开主页据此决定是否展示收藏 tab）
     */
    private Boolean collectPublic;

    /**
     * 地区（如：四川 · 成都）
     */
    private String region;

    /**
     * 个人网站
     */
    private String website;

    /**
     * 公开主页封面 URL
     */
    private String coverUrl;

    /**
     * 是否本周精选创作者（全站至多一位）
     */
    private Boolean isFeaturedCreator;

    /**
     * 创建时间
     */
    private Date createTime;

}
