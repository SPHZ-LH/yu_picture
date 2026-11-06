package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.user.UserQueryRequest;
import com.yupi.yupicturebackend.model.dto.user.UserUpdateMyRequest;
import com.yupi.yupicturebackend.model.dto.user.UserUpdatePasswordRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceAddRequest;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceLevelEnum;
import com.yupi.yupicturebackend.model.enums.SpaceTypeEnum;
import com.yupi.yupicturebackend.model.enums.UserRoleEnum;
import com.yupi.yupicturebackend.model.vo.CreatorVO;
import com.yupi.yupicturebackend.model.vo.LoginUserVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.PointsService;
import com.yupi.yupicturebackend.service.UserService;
import com.yupi.yupicturebackend.manager.MultiLevelCacheManager;
import com.yupi.yupicturebackend.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
     * 注册时自动创建的私有空间名。用户之后可以自己改（editSpace）
     */
    private static final String DEFAULT_PRIVATE_SPACE_NAME = "我的私有空间";

    /**
     * SpaceServiceImpl 反过来依赖 UserService（组装 SpaceVO 要查作者、判站点管理员），
     * 直接注入会形成循环依赖，而 Boot 2.6+ 默认不允许。@Lazy 让这里先拿到代理，
     * 真正调用时再解析目标 Bean。
     */
    @Lazy
    @Resource
    private SpaceService spaceService;

    @Resource
    private PointsService pointsService;

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
    @Transactional(rollbackFor = Exception.class)
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
        // 注册即送一个普通版私有空间，与建用户同一事务。
        // 必须同事务：创建入口已改成团队空间专用，私有空间只由注册产生，
        // 一旦这里失败又不回滚，就会留下一个「没有私有空间、也没有任何自助补建入口」的用户
        this.createDefaultPrivateSpace(user);
        // 注册赠送积分（尽力而为，见 docs/adr/0014）：同一事务内写入，失败不回滚注册
        pointsService.grantRegisterBonus(user.getId());
        return user.getId();
    }

    /**
     * 为新用户建默认的私有空间（普通版配额，创建者自动成为 admin 成员）
     */
    private void createDefaultPrivateSpace(User user) {
        SpaceAddRequest spaceAddRequest = new SpaceAddRequest();
        spaceAddRequest.setSpaceName(DEFAULT_PRIVATE_SPACE_NAME);
        spaceAddRequest.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        spaceAddRequest.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        spaceService.saveSpace(spaceAddRequest, user);
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

    @Override
    public boolean updateMyProfile(User loginUser, UserUpdateMyRequest request, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        String userName = request.getUserName() == null ? null : request.getUserName().trim();
        String userProfile = request.getUserProfile() == null ? null : request.getUserProfile().trim();
        String userAvatar = request.getUserAvatar() == null ? null : request.getUserAvatar().trim();
        String region = request.getRegion() == null ? null : request.getRegion().trim();
        String website = request.getWebsite() == null ? null : request.getWebsite().trim();
        String coverUrl = request.getCoverUrl() == null ? null : request.getCoverUrl().trim();
        Boolean collectPublic = request.getCollectPublic();
        ThrowUtils.throwIf(userName == null && userProfile == null && userAvatar == null
                        && collectPublic == null && region == null && website == null && coverUrl == null,
                ErrorCode.PARAMS_ERROR, "没有需要修改的内容");
        if (userName != null) {
            ThrowUtils.throwIf(userName.isEmpty(), ErrorCode.PARAMS_ERROR, "昵称不能为空");
            ThrowUtils.throwIf(userName.length() > 30, ErrorCode.PARAMS_ERROR, "昵称过长");
        }
        if (userProfile != null) {
            ThrowUtils.throwIf(userProfile.length() > 200, ErrorCode.PARAMS_ERROR, "简介过长");
        }
        if (userAvatar != null) {
            ThrowUtils.throwIf(userAvatar.isEmpty(), ErrorCode.PARAMS_ERROR, "头像地址不能为空");
            ThrowUtils.throwIf(userAvatar.length() > 1024, ErrorCode.PARAMS_ERROR, "头像地址过长");
            ThrowUtils.throwIf(!(userAvatar.startsWith("http://") || userAvatar.startsWith("https://")),
                    ErrorCode.PARAMS_ERROR, "头像地址格式不正确");
        }
        if (region != null) {
            ThrowUtils.throwIf(region.length() > 64, ErrorCode.PARAMS_ERROR, "地区过长");
        }
        if (website != null) {
            ThrowUtils.throwIf(website.length() > 256, ErrorCode.PARAMS_ERROR, "网站地址过长");
        }
        if (coverUrl != null) {
            ThrowUtils.throwIf(!coverUrl.isEmpty() && !(coverUrl.startsWith("http://") || coverUrl.startsWith("https://")),
                    ErrorCode.PARAMS_ERROR, "封面地址格式不正确");
            ThrowUtils.throwIf(coverUrl.length() > 1024, ErrorCode.PARAMS_ERROR, "封面地址过长");
        }
        User updateUser = new User();
        updateUser.setId(loginUser.getId());
        updateUser.setUserName(userName);
        updateUser.setUserProfile(userProfile);
        updateUser.setUserAvatar(userAvatar);
        updateUser.setCollectPublic(collectPublic);
        updateUser.setRegion(region);
        updateUser.setWebsite(website);
        updateUser.setCoverUrl(coverUrl);
        boolean result = this.updateById(updateUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // Session 里的登录态是 User 副本，不回写则头部昵称等要等到下次登录才更新
        if (userName != null) {
            loginUser.setUserName(userName);
        }
        if (userProfile != null) {
            loginUser.setUserProfile(userProfile);
        }
        if (userAvatar != null) {
            loginUser.setUserAvatar(userAvatar);
        }
        if (collectPublic != null) {
            loginUser.setCollectPublic(collectPublic);
        }
        if (region != null) {
            loginUser.setRegion(region);
        }
        if (website != null) {
            loginUser.setWebsite(website);
        }
        if (coverUrl != null) {
            loginUser.setCoverUrl(coverUrl);
        }
        httpRequest.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, loginUser);
        return true;
    }

    @Override
    public boolean updateMyPassword(User loginUser, UserUpdatePasswordRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        String oldPassword = request.getOldPassword();
        String newPassword = request.getNewPassword();
        ThrowUtils.throwIf(StrUtil.hasBlank(oldPassword, newPassword), ErrorCode.PARAMS_ERROR, "密码不能为空");
        ThrowUtils.throwIf(newPassword.length() < 8, ErrorCode.PARAMS_ERROR, "密码过短");
        ThrowUtils.throwIf(oldPassword.equals(newPassword), ErrorCode.PARAMS_ERROR, "新密码不能与旧密码相同");
        ThrowUtils.throwIf(!getEncryptPassword(oldPassword).equals(loginUser.getUserPassword()),
                ErrorCode.PARAMS_ERROR, "旧密码不正确");
        User updateUser = new User();
        updateUser.setId(loginUser.getId());
        updateUser.setUserPassword(getEncryptPassword(newPassword));
        boolean result = this.updateById(updateUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setFeaturedCreator(Long userId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR);
        User target = this.getById(userId);
        ThrowUtils.throwIf(target == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        // 逐条 updateById 而不是 update(Wrapper)：缓存失效钩子只挂在按 id 的写入上（docs/adr/0002）
        List<User> currentPicks = this.list(new LambdaQueryWrapper<User>()
                .eq(User::getIsFeaturedCreator, true));
        for (User pick : currentPicks) {
            if (!pick.getId().equals(userId)) {
                User patch = new User();
                patch.setId(pick.getId());
                patch.setIsFeaturedCreator(false);
                this.updateById(patch);
            }
        }
        if (Boolean.TRUE.equals(target.getIsFeaturedCreator())) {
            return true;
        }
        User patch = new User();
        patch.setId(userId);
        patch.setIsFeaturedCreator(true);
        return this.updateById(patch);
    }

    @Override
    public Page<CreatorVO> listCreators(long current, long pageSize, String sortItem) {
        long safeCurrent = Math.max(current, 1);
        long safePageSize = Math.min(Math.max(pageSize, 1), 20);
        Page<CreatorVO> page = new Page<>(safeCurrent, safePageSize);
        // MyBatis-Plus 把聚合结果填进传入的 Page 实例（mapper 方法返回 IPage）
        baseMapper.selectCreatorPage(page, sortItem);
        return page;
    }

    @Override
    public UserVO getFeaturedCreator() {
        User featuredCreator = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getIsFeaturedCreator, true)
                .orderByDesc(User::getUpdateTime)
                .last("LIMIT 1"));
        return featuredCreator == null ? null : this.getUserVO(featuredCreator);
    }
}




