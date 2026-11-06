package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.SpaceMapper;
import com.yupi.yupicturebackend.mapper.PictureMapper;
import com.yupi.yupicturebackend.mapper.SpaceUserMapper;
import com.yupi.yupicturebackend.model.dto.space.SpaceUserAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceUserDeleteRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceUserEditRoleRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.dto.SpaceMemberPictureCount;
import com.yupi.yupicturebackend.model.entity.SpaceUser;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.vo.SpaceUserVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author SPHZ
 * @description 针对表【space_user(空间成员关系)】的数据库操作Service实现
 */
@Slf4j
@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser> implements SpaceUserService {

    /** 邀请搜索的最短关键词：一个字符能匹配到半个站，等于放开枚举 */
    private static final int MIN_SEARCH_TEXT_LENGTH = 2;

    /** 邀请搜索单次返回上限：选人只需要前几条，不给全量 */
    private static final int MAX_SEARCH_RESULT = 10;

    /**
     * 走 SpaceMapper 而不是 SpaceService：SpaceService 反过来依赖本 Service（创建空间时要写成员记录），
     * 注入 Service 会形成循环依赖，而 Boot 2.6+ 默认不允许。
     */
    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private UserService userService;

    @Resource
    private PictureMapper pictureMapper;

    @Override
    public SpaceRoleEnum getEffectiveRole(Space space, User loginUser) {
        if (space == null || loginUser == null) {
            return null;
        }
        // 站点管理员视为该空间的管理员：改造前每处校验都是「所有者或站点管理员」，这里不能退化
        if (userService.isAdmin(loginUser)) {
            return SpaceRoleEnum.ADMIN;
        }
        SpaceUser spaceUser = this.getOne(new LambdaQueryWrapper<SpaceUser>()
                .eq(SpaceUser::getSpaceId, space.getId())
                .eq(SpaceUser::getUserId, loginUser.getId())
                .last("LIMIT 1"));
        return spaceUser == null ? null : SpaceRoleEnum.getEnumByValue(spaceUser.getSpaceRole());
    }

    @Override
    public void checkPermission(Space space, User loginUser, SpaceRoleEnum minRole) {
        this.checkPermission(space, loginUser, minRole, "无权限操作该空间");
    }

    @Override
    public void checkPermission(Space space, User loginUser, SpaceRoleEnum minRole, String errorMessage) {
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        SpaceRoleEnum role = this.getEffectiveRole(space, loginUser);
        ThrowUtils.throwIf(role == null || !role.atLeast(minRole), ErrorCode.NO_AUTH_ERROR, errorMessage);
    }

    @Override
    public void checkPermission(Long spaceId, User loginUser, SpaceRoleEnum minRole) {
        this.checkPermission(this.getSpace(spaceId), loginUser, minRole);
    }

    @Override
    public boolean canView(Space space, User loginUser) {
        return this.getEffectiveRole(space, loginUser) != null;
    }

    @Override
    public void saveCreatorAsAdmin(Long spaceId, Long userId) {
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR, "空间 id 不合法");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户 id 不合法");
        boolean exists = this.count(new LambdaQueryWrapper<SpaceUser>()
                .eq(SpaceUser::getSpaceId, spaceId)
                .eq(SpaceUser::getUserId, userId)) > 0;
        if (exists) {
            return;
        }
        SpaceUser spaceUser = new SpaceUser();
        spaceUser.setSpaceId(spaceId);
        spaceUser.setUserId(userId);
        spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
        try {
            this.save(spaceUser);
        } catch (DuplicateKeyException e) {
            // 并发创建被 uk_spaceId_userId 拦下，记录已存在即达到目的
            log.info("创建者成员记录已存在，跳过：spaceId={}, userId={}", spaceId, userId);
        }
    }

    @Override
    public Long addMember(SpaceUserAddRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求发送失败");
        SpaceRoleEnum targetRole = SpaceRoleEnum.getEnumByValue(request.getSpaceRole());
        ThrowUtils.throwIf(targetRole == null, ErrorCode.PARAMS_ERROR, "空间角色不合法");
        Space space = this.getSpace(request.getSpaceId());
        this.checkPermission(space, loginUser, SpaceRoleEnum.ADMIN);

        Long targetUserId = request.getUserId();
        ThrowUtils.throwIf(targetUserId == null || targetUserId <= 0, ErrorCode.PARAMS_ERROR, "用户 id 不合法");
        User targetUser = userService.getById(targetUserId);
        ThrowUtils.throwIf(targetUser == null, ErrorCode.NOT_FOUND_ERROR, "被邀请的用户不存在");

        boolean alreadyMember = this.count(new LambdaQueryWrapper<SpaceUser>()
                .eq(SpaceUser::getSpaceId, space.getId())
                .eq(SpaceUser::getUserId, targetUserId)) > 0;
        ThrowUtils.throwIf(alreadyMember, ErrorCode.OPERATION_ERROR, "该用户已是空间成员");

        // 上限含创建者，所以直接比成员总数。降低上限不清退已有成员，因此这里可能已超额，
        // 超额时任何新增都会被挡下，直到人数降回上限以内
        long memberCount = this.countMembers(space.getId());
        Long maxMemberCount = space.getMaxMemberCount();
        ThrowUtils.throwIf(maxMemberCount != null && maxMemberCount > 0 && memberCount >= maxMemberCount,
                ErrorCode.OPERATION_ERROR, "成员数已达上限（" + maxMemberCount + " 人，含创建者）");

        SpaceUser spaceUser = new SpaceUser();
        spaceUser.setSpaceId(space.getId());
        spaceUser.setUserId(targetUserId);
        spaceUser.setSpaceRole(targetRole.getValue());
        try {
            boolean result = this.save(spaceUser);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "添加成员失败");
        } catch (DuplicateKeyException e) {
            // 上面已查过一次，这里是并发邀请同一个人时唯一键的兜底
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该用户已是空间成员");
        }
        return spaceUser.getId();
    }

    @Override
    public boolean editMemberRole(SpaceUserEditRoleRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求发送失败");
        SpaceRoleEnum newRole = SpaceRoleEnum.getEnumByValue(request.getSpaceRole());
        ThrowUtils.throwIf(newRole == null, ErrorCode.PARAMS_ERROR, "空间角色不合法");
        Space space = this.getSpace(request.getSpaceId());
        this.checkPermission(space, loginUser, SpaceRoleEnum.ADMIN);
        SpaceUser spaceUser = this.getMember(space, request.getUserId());
        // 创建者降级会让团队空间失去唯一管理员，且没有别的途径恢复
        ThrowUtils.throwIf(this.isCreator(space, spaceUser), ErrorCode.OPERATION_ERROR,
                "创建者的角色不可修改");
        ThrowUtils.throwIf(newRole.getValue().equals(spaceUser.getSpaceRole()),
                ErrorCode.OPERATION_ERROR, "该成员已是" + newRole.getText());
        spaceUser.setSpaceRole(newRole.getValue());
        boolean result = this.updateById(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "修改成员角色失败");
        return true;
    }

    @Override
    public boolean removeMember(SpaceUserDeleteRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求发送失败");
        Space space = this.getSpace(request.getSpaceId());
        this.checkPermission(space, loginUser, SpaceRoleEnum.ADMIN);
        SpaceUser spaceUser = this.getMember(space, request.getUserId());
        ThrowUtils.throwIf(this.isCreator(space, spaceUser), ErrorCode.OPERATION_ERROR,
                "创建者不可被移出空间");
        // 物理删除：space_user 没有 isDelete，逻辑删除会让重新邀请撞唯一键
        boolean result = this.removeById(spaceUser.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "移出成员失败");
        return true;
    }

    @Override
    public List<SpaceUserVO> listMembers(Long spaceId, User loginUser) {
        Space space = this.getSpace(spaceId);
        // 任何成员都能看名单（含只读成员），非成员看不到
        this.checkPermission(space, loginUser, SpaceRoleEnum.VIEWER);
        List<SpaceUser> memberList = this.list(new LambdaQueryWrapper<SpaceUser>()
                .eq(SpaceUser::getSpaceId, space.getId())
                .orderByAsc(SpaceUser::getCreateTime)
                .orderByAsc(SpaceUser::getId));
        if (CollUtil.isEmpty(memberList)) {
            return new ArrayList<>();
        }
        // 批量取用户信息，避免逐个成员查一次库
        List<Long> userIds = memberList.stream().map(SpaceUser::getUserId).distinct().collect(Collectors.toList());
        Map<Long, UserVO> userVOMap = userService.getUserVOList(userService.listByIds(userIds)).stream()
                .collect(Collectors.toMap(UserVO::getId, Function.identity(), (a, b) -> a));
        // 成员在本空间的图片数（团队空间侧栏「成员」面板的作品数角标），一次分组查询
        Map<Long, Long> pictureCountMap = pictureMapper.selectSpaceMemberPictureCounts(space.getId()).stream()
                .collect(Collectors.toMap(SpaceMemberPictureCount::getUserId,
                        SpaceMemberPictureCount::getPictureCount, (a, b) -> a));
        return memberList.stream().map(spaceUser -> {
            SpaceUserVO vo = SpaceUserVO.objToVo(spaceUser);
            vo.setCreator(this.isCreator(space, spaceUser));
            vo.setUser(userVOMap.get(spaceUser.getUserId()));
            vo.setPictureCount(pictureCountMap.getOrDefault(spaceUser.getUserId(), 0L));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public long countMembers(Long spaceId) {
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR, "空间 id 不合法");
        return this.count(new LambdaQueryWrapper<SpaceUser>().eq(SpaceUser::getSpaceId, spaceId));
    }

    @Override
    public List<Long> listMySpaceIds(Long userId) {
        if (userId == null || userId <= 0) {
            return new ArrayList<>();
        }
        return this.list(new LambdaQueryWrapper<SpaceUser>()
                        .select(SpaceUser::getSpaceId)
                        .eq(SpaceUser::getUserId, userId))
                .stream()
                .map(SpaceUser::getSpaceId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public Map<Long, String> mapMyRoles(Collection<Long> spaceIds, Long userId) {
        if (CollUtil.isEmpty(spaceIds) || userId == null || userId <= 0) {
            return new HashMap<>();
        }
        return this.list(new LambdaQueryWrapper<SpaceUser>()
                        .select(SpaceUser::getSpaceId, SpaceUser::getSpaceRole)
                        .eq(SpaceUser::getUserId, userId)
                        .in(SpaceUser::getSpaceId, spaceIds))
                .stream()
                .collect(Collectors.toMap(SpaceUser::getSpaceId, SpaceUser::getSpaceRole, (a, b) -> a));
    }

    @Override
    public List<UserVO> searchInvitableUsers(Long spaceId, String searchText, User loginUser) {
        Space space = this.getSpace(spaceId);
        this.checkPermission(space, loginUser, SpaceRoleEnum.ADMIN);
        ThrowUtils.throwIf(StrUtil.isBlank(searchText), ErrorCode.PARAMS_ERROR, "请输入账号或昵称");
        String keyword = searchText.trim();
        ThrowUtils.throwIf(keyword.length() < MIN_SEARCH_TEXT_LENGTH, ErrorCode.PARAMS_ERROR,
                "至少输入 " + MIN_SEARCH_TEXT_LENGTH + " 个字符");

        List<SpaceUser> existingMembers = this.list(new LambdaQueryWrapper<SpaceUser>()
                .select(SpaceUser::getUserId)
                .eq(SpaceUser::getSpaceId, space.getId()));
        List<Long> memberIds = existingMembers.stream().map(SpaceUser::getUserId).collect(Collectors.toList());

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .and(qw -> qw.like(User::getUserAccount, keyword).or().like(User::getUserName, keyword));
        if (CollUtil.isNotEmpty(memberIds)) {
            queryWrapper.notIn(User::getId, memberIds);
        }
        Page<User> userPage = userService.page(new Page<>(1, MAX_SEARCH_RESULT), queryWrapper);
        return userService.getUserVOList(userPage.getRecords());
    }

    /**
     * 取空间，不存在直接抛。成员相关操作全部以「空间存在」为前提，逐处判空只会重复代码
     */
    private Space getSpace(Long spaceId) {
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR, "空间 id 不合法");
        Space space = spaceMapper.selectById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        return space;
    }

    private SpaceUser getMember(Space space, Long userId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户 id 不合法");
        SpaceUser spaceUser = this.getOne(new LambdaQueryWrapper<SpaceUser>()
                .eq(SpaceUser::getSpaceId, space.getId())
                .eq(SpaceUser::getUserId, userId)
                .last("LIMIT 1"));
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR, "该用户不是空间成员");
        return spaceUser;
    }

    private boolean isCreator(Space space, SpaceUser spaceUser) {
        return space.getUserId() != null && space.getUserId().equals(spaceUser.getUserId());
    }
}
