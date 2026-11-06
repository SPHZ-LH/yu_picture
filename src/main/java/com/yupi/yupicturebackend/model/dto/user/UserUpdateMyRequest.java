package com.yupi.yupicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户自助修改本人资料请求
 *
 * @author SPHZ
 */
@Data
public class UserUpdateMyRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 昵称，trim 后 1-30 字
     */
    private String userName;

    /**
     * 简介，最长 200 字，空串表示清空
     */
    private String userProfile;

    /**
     * 头像 URL，必须 http(s) 开头；空串表示移除头像
     */
    private String userAvatar;

    /**
     * 收藏列表是否公开（公开主页据此展示/隐藏收藏 tab）；null 表示不修改
     */
    private Boolean collectPublic;

    /**
     * 地区，最长 64 字，空串表示清空；null 表示不修改
     */
    private String region;

    /**
     * 个人网站，最长 256 字，空串表示清空；null 表示不修改
     */
    private String website;

    /**
     * 公开主页封面 URL，必须 http(s) 开头；空串表示移除；null 表示不修改
     */
    private String coverUrl;
}
