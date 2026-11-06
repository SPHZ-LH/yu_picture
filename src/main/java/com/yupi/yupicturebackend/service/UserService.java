package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.model.dto.user.UserQueryRequest;
import com.yupi.yupicturebackend.model.dto.user.UserUpdateMyRequest;
import com.yupi.yupicturebackend.model.dto.user.UserUpdatePasswordRequest;
import com.yupi.yupicturebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.vo.CreatorVO;
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
     * 根据 id 获取用户视图（脱敏），走多级缓存。
     * <p>
     * 读路径：L1（本地 Caffeine）-> L2（Redis）-> 数据库，逐层回源并逐层回填。
     *
     * @param id 用户 id
     * @return 用户视图；不存在时返回 null
     */
    UserVO getUserVOById(Long id);

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

    /**
     * 自助修改本人资料（昵称 / 简介 / 头像），成功后回写 Session 登录态。
     * 账号与角色不在可改范围内（DTO 中不存在这两个字段）。
     *
     * @param loginUser 当前登录用户
     * @param request   修改请求
     * @param httpRequest 前端发送的 session
     * @return 是否成功
     */
    boolean updateMyProfile(User loginUser, UserUpdateMyRequest request, HttpServletRequest httpRequest);

    /**
     * 自助修改本人密码：校验旧密码后写入加盐摘要，保留当前会话。
     *
     * @param loginUser 当前登录用户
     * @param request   改密码请求
     * @return 是否成功
     */
    boolean updateMyPassword(User loginUser, UserUpdatePasswordRequest request);

    /**
     * 设置本周精选创作者（管理员，全站至多一位，设新人自动取代旧人）
     *
     * @param userId 创作者用户 id
     * @return 是否成功
     */
    boolean setFeaturedCreator(Long userId);

    /**
     * 本周精选创作者（公开）：未设置时返回 null
     *
     * @return 创作者视图
     */
    UserVO getFeaturedCreator();

    /**
     * 创作者目录分页（公开）：有公开图库过审作品的用户，按人气/作品数/最新加入排序。
     *
     * @param current  页码
     * @param pageSize 每页数量（上限 20）
     * @param sortItem 排序：popular/works/newest，空与非法按 popular
     * @return 创作者聚合视图分页
     */
    Page<CreatorVO> listCreators(long current, long pageSize, String sortItem);
}
