package com.yupi.yupicturebackend.controller;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.DeleteRequest;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.user.*;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.CreatorVO;
import com.yupi.yupicturebackend.model.vo.FollowCountVO;
import com.yupi.yupicturebackend.model.vo.FollowStatusVO;
import com.yupi.yupicturebackend.model.vo.LoginUserVO;
import com.yupi.yupicturebackend.model.vo.UserPointsVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.PointsService;
import com.yupi.yupicturebackend.service.UserFollowService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserService userService;
    @Resource
    private UserFollowService userFollowService;
    @Resource
    private PointsService pointsService;

    /**
     * 用户注册
     *
     * @param userRegisterRequest 新用户
     * @return 新用户 Id
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(ObjectUtil.isNull(userRegisterRequest), ErrorCode.PARAMS_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long userId = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(userId);
    }

    /**
     * 用户登入
     *
     * @param userLoginRequest 用户
     * @param request          前端发送的session
     * @return 用户视图（脱敏）
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(ObjectUtil.isNull(userLoginRequest), ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 获取登入用户视图（脱敏）
     *
     * @param request 前端发送的session
     * @return 用户视图
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        LoginUserVO loginUserVO = userService.getLoginUserVO(loginUser);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 用户注销
     *
     * @param request 前端发送的session
     * @return 是否成功
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 添加用户（仅管理员）
     *
     * @param userAddRequest 添加用户请求
     * @return 用户 id
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtils.copyProperties(userAddRequest, user);
        // 默认密码 12345678
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 根据 id 获取用户信息（仅管理员）
     *
     * @param id 查询 id
     * @return 用户信息
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取用户视图（脱敏）。
     * <p>
     * <b>本接口刻意不加 {@code @AuthCheck}（公开访问）</b>：前端用它展示任意用户的公开资料，
     * 返回的是 {@link UserVO}（脱敏，不含密码），公开是可接受的。
     * <p>
     * <b>不要</b>通过调用本类里带 {@code @AuthCheck} 的方法来实现本接口——
     * 同类自调用不经过 Spring AOP 代理，鉴权注解会被**静默绕过**，
     * 造成「看起来需要管理员、实际任何人都能访问」的假象（本接口曾经就是这个样子）。
     * 直接调用 service 层，让代码与实际行为保持一致。
     *
     * @param id 查询 id
     * @return 用户视图
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 删除用户（仅管理员）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户信息（仅管理员）
     *
     * @param userUpdateRequest 更新用户请求
     * @return 是否成功
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        // 密码必须与注册/登录走同一套加盐摘要，明文入库会让该账号永远无法登录
        if (StrUtil.isNotBlank(user.getUserPassword())) {
            ThrowUtils.throwIf(user.getUserPassword().length() < 8, ErrorCode.PARAMS_ERROR, "密码过短");
            user.setUserPassword(userService.getEncryptPassword(user.getUserPassword()));
        }
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 自助修改本人资料（昵称 / 简介 / 头像）
     *
     * @param userUpdateMyRequest 修改请求
     * @param request             前端发送的 session
     * @return 是否成功
     */
    @PostMapping("/update/my")
    public BaseResponse<Boolean> updateMyUser(@RequestBody UserUpdateMyRequest userUpdateMyRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.updateMyProfile(loginUser, userUpdateMyRequest, request));
    }

    /**
     * 自助修改本人密码（校验旧密码，保留当前会话）
     *
     * @param userUpdatePasswordRequest 改密码请求
     * @param request                   前端发送的 session
     * @return 是否成功
     */
    @PostMapping("/update/password")
    public BaseResponse<Boolean> updateMyPassword(@RequestBody UserUpdatePasswordRequest userUpdatePasswordRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.updateMyPassword(loginUser, userUpdatePasswordRequest));
    }

    /**
     * 分页获取用户视图封装列表（仅管理员）
     *
     * @param userQueryRequest 查询请求参数
     * @return 返回用户视图分页
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR, "查询请求发送失败");
        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(new Page<>(current, pageSize), userService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());
        List<User> records = userPage.getRecords();
        log.info(records.toString());
        List<UserVO> userVOList = userService.getUserVOList(records);
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }

    // ==================== 关注 ====================

    /**
     * 关注用户（登录用户；不能关注自己；重复关注幂等）
     *
     * @param userFollowRequest 关注请求
     * @param request           前端传来的 session
     */
    @PostMapping("/follow")
    public BaseResponse<Boolean> doFollow(@RequestBody UserFollowRequest userFollowRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(userFollowRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userFollowService.doFollow(userFollowRequest.getFolloweeId(), loginUser));
    }

    /**
     * 取消关注（未关注时幂等）
     */
    @PostMapping("/follow/cancel")
    public BaseResponse<Boolean> cancelFollow(@RequestBody UserFollowRequest userFollowRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(userFollowRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userFollowService.cancelFollow(userFollowRequest.getFolloweeId(), loginUser));
    }

    /**
     * 查询对某用户的关注状态与对方粉丝数（匿名可访问，未登录 followed=false）
     *
     * @param followeeId 被关注者 id
     */
    @GetMapping("/follow/status")
    public BaseResponse<FollowStatusVO> getFollowStatus(@RequestParam("followeeId") Long followeeId,
            HttpServletRequest request) {
        // 状态查询不强制登录：匿名视为「未关注」（getLoginUser 未登录会抛异常，故直接取会话属性）
        User loginUser = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        return ResultUtils.success(userFollowService.getFollowStatus(followeeId, loginUser));
    }

    /**
     * 批量查询关注状态（创作者列表一次渲染全部关注按钮；匿名可访问）
     *
     * @param userFollowBatchRequest 用户 id 列表
     */
    @PostMapping("/follow/status/batch")
    public BaseResponse<List<FollowStatusVO>> listFollowStatus(
            @RequestBody UserFollowBatchRequest userFollowBatchRequest, HttpServletRequest request) {
        User loginUser = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        return ResultUtils.success(userFollowService.listFollowStatus(
                userFollowBatchRequest == null ? null : userFollowBatchRequest.getUserIds(), loginUser));
    }

    /**
     * 用户关注维度计数（粉丝数 + 关注数，公开主页数据区）
     *
     * @param userId 用户 id
     */
    @GetMapping("/follow/count")
    public BaseResponse<FollowCountVO> getFollowCounts(@RequestParam("userId") Long userId) {
        return ResultUtils.success(userFollowService.getFollowCounts(userId));
    }

    // ==================== 精选创作者 ====================

    /**
     * 设置本周精选创作者（管理员，全站至多一位，设新人自动取代旧人）
     */
    @PostMapping("/featured-creator/set")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> setFeaturedCreator(
            @RequestBody UserFeaturedCreatorRequest featuredCreatorRequest) {
        ThrowUtils.throwIf(featuredCreatorRequest == null || featuredCreatorRequest.getUserId() == null,
                ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(userService.setFeaturedCreator(featuredCreatorRequest.getUserId()));
    }

    /**
     * 本周精选创作者（公开，创作者页 hero）：未设置时 data 为 null
     */
    @GetMapping("/featured-creator")
    public BaseResponse<UserVO> getFeaturedCreator() {
        return ResultUtils.success(userService.getFeaturedCreator());
    }

    /**
     * 创作者目录分页（公开）：有公开图库过审作品的用户，可按人气/作品数/最新加入排序
     *
     * @param current  页码
     * @param pageSize 每页数量
     * @param sortItem 排序：popular/works/newest
     */
    @GetMapping("/creator/list")
    public BaseResponse<Page<CreatorVO>> listCreators(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "12") long pageSize,
            @RequestParam(required = false) String sortItem) {
        return ResultUtils.success(userService.listCreators(current, pageSize, sortItem));
    }

    // ==================== 积分 ====================

    /**
     * 查询我的积分余额（登录用户）
     */
    @GetMapping("/points/my")
    public BaseResponse<UserPointsVO> getMyPoints(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pointsService.getMyPoints(loginUser));
    }

    /**
     * 管理员调整积分（正数发放、负数扣减，调整后余额不能为负）
     */
    @PostMapping("/points/adjust")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> adjustPoints(@RequestBody UserPointsAdjustRequest adjustRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(adjustRequest == null, ErrorCode.PARAMS_ERROR);
        userService.getLoginUser(request);
        pointsService.adminAdjust(adjustRequest.getUserId(),
                adjustRequest.getDelta() == null ? 0 : adjustRequest.getDelta(), adjustRequest.getNote());
        return ResultUtils.success(true);
    }

}
