package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.user.UserQueryRequest;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.UserRoleEnum;
import com.yupi.yupicturebackend.model.vo.LoginUserVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.UserService;
import com.yupi.yupicturebackend.manager.MultiLevelCacheManager;
import com.yupi.yupicturebackend.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author SPHZ
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2025-11-23 22:43:10
 */
@Slf4j
@Service
public class UserServiceImpl extends CacheAwareServiceImpl<UserMapper, User> implements UserService {

    /**
     * 允许排序的字段白名单（sortField 来自请求体，直接进 ORDER BY 是 SQL 注入风险点）
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = new HashSet<>(Arrays.asList(
            "id", "userAccount", "userName", "userProfile", "userRole",
            "createTime", "updateTime", "editTime"));

    /**
     * 允许的排序方向
     */
    private static final Set<String> ALLOWED_SORT_ORDERS = new HashSet<>(Arrays.asList("ascend", "descend"));

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1.参数校验
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码为空");
        }
        if (userAccount.length() <= 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号过短");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码不一致");
        }
        // 2.账号重复
        QueryWrapper<User> wrapper = new QueryWrapper<User>().eq("userAccount", userAccount);
        User queryUser = this.baseMapper.selectOne(wrapper);
        if (ObjectUtil.isNotNull(queryUser)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已存在");
        }
        // 3.加密
        String encryptPassword = this.getEncryptPassword(userPassword);
        // 4.保存数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库异常");
        }
        return user.getId();
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "yupi";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4 || userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }
        // 2. 加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (ObjectUtil.isNull(user)) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 3. 记录用户的登录态
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (ObjectUtil.isNull(currentUser)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登入");
        }

        return currentUser;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (ObjectUtil.isNull(userObj)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登入");
        }
        // 移除登录态
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {

        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空");

        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();


        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        // 排序字段与方向必须走白名单（sortField 来自请求体，直接进 ORDER BY 是注入风险点）；
        // 这里同时修掉了原写法中 sortOrder 为 null 时的 NPE
        if (StrUtil.isNotEmpty(sortField)) {
            ThrowUtils.throwIf(!ALLOWED_SORT_FIELDS.contains(sortField),
                    ErrorCode.PARAMS_ERROR, "非法的排序字段");
            String order = StrUtil.isNotBlank(sortOrder) ? sortOrder : "descend";
            ThrowUtils.throwIf(!ALLOWED_SORT_ORDERS.contains(order),
                    ErrorCode.PARAMS_ERROR, "非法的排序方式");
            queryWrapper.orderBy(true, "ascend".equals(order), sortField);
        }
        return queryWrapper;
    }


    public LoginUserVO getLoginUserVO(User user) {
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVOById(Long id) {
        if (id == null || id <= 0) {
            return null;
        }
        // 缓存只存在于本方法内；回源函数由本类提供，
        // 避免 MultiLevelCacheManager 反向依赖 Service 造成循环依赖
        return multiLevelCacheManager.getUser(id, this::loadUserVOFromDb);
    }

    /**
     * 从数据库回源用户视图。
     * <p>
     * 注意：**不要缓存本方法，也不要缓存 getById**。getById 的结果会被用于鉴权判断
     * （见 PictureServiceImpl.uploadPicture 的「是否图片作者」校验），
     * 把缓存埋在那层会让鉴权变成缓存依赖。详见 docs/adr/0002。
     */
    private UserVO loadUserVOFromDb(Long id) {
        User user = this.getById(id);
        return this.getUserVO(user);
    }

    // ==================== 接入缓存基类 ====================
    // 全部写入的失效钩子都在基类 CacheAwareServiceImpl 里。这里只需告诉基类两件事：
    // 缓存 key 是什么、失效时调哪个缓存区。
    // 注意：本类此前**漏了 saveOrUpdate 的覆盖**（与 PictureServiceImpl 不一致），
    // 收敛到基类后这个漂移被消除了。约定与理由见 docs/adr/0002、docs/adr/0007。

    @Override
    protected Long keyOf(User entity) {
        return entity == null ? null : entity.getId();
    }

    @Override
    protected void evict(Long key) {
        multiLevelCacheManager.evictUser(key);
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

}




