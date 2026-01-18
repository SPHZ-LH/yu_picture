package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yupi.yupicturebackend.model.dto.user.UserQueryRequest;
import com.yupi.yupicturebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.vo.LoginUserVO;
import com.yupi.yupicturebackend.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

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
     * @param request      前端发送的 session
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户信息
     *
     * @param request 前端发送的 session
     * @return 当前登入用户信息
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获取用户视图（脱敏）
     *
     * @param user 用户
     * @return 用户视图
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 用户注销
     *
     * @param request 前端发送的 session
     * @return 是否成功
     */
    boolean userLogout(HttpServletRequest request);


    /**
     * 获取用户视图（脱敏）
     *
     * @param user 用户
     * @return 用户视图
     */
    UserVO getUserVO(User user);

    /**
     * 获取用户视图列表（脱敏）
     *
     * @param userList 用户列表
     * @return 用户视图列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 获取用户查询请求条件
     *
     * @param userQueryRequest 用户查询请求
     * @return 用户查询请求条件
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 是否为管理员
     *
     * @param user 登入用户
     */
    boolean isAdmin(User user);
}
