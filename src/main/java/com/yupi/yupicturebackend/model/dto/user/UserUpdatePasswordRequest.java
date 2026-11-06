package com.yupi.yupicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户自助修改本人密码请求
 *
 * @author SPHZ
 */
@Data
public class UserUpdatePasswordRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 旧密码，用于校验身份
     */
    private String oldPassword;

    /**
     * 新密码，至少 8 位且不得与旧密码相同
     */
    private String newPassword;
}
