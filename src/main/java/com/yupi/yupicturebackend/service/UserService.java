package com.yupi.yupicturebackend.service;

import com.yupi.yupicturebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.vo.LoginUserVO;

import javax.servlet.http.HttpServletRequest;

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

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request      前端发送的session
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户信息
     *
     * @param request 前端发送的session
     * @return 脱敏后的当前登入用户信息
     */
    LoginUserVO getLoginUser(HttpServletRequest request);

    /**
     * 用户注销
     *
     * @param request 前端发送的session
     * @return 是否成功
     */
    boolean userLogout(HttpServletRequest request);


}
