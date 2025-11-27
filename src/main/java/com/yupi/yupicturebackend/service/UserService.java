package com.yupi.yupicturebackend.service;

import com.yupi.yupicturebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author SPHZ
 * @description 针对表【user(用户)】的数据库操作Service
 * @createDate 2025-11-23 22:43:10
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账号
     * @param userPassword  用户密码
     * @param checkPassword 确认密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 密码加盐
     *
     * @param userPassword 用户密码
     * @return 加盐后的密码
     */
    String getEncryptPassword(String userPassword);
}
