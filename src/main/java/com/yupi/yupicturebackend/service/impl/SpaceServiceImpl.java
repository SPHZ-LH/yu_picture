package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.SpaceMapper;
import com.yupi.yupicturebackend.model.dto.space.SpaceAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceEditRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceQueryRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceUpdateRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceLevelEnum;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.enums.SpaceTypeEnum;
import com.yupi.yupicturebackend.model.vo.SpaceVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author SPHZ
 * @description 针对表【space(空间)】的数据库操作Service实现
 */
@Slf4j
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceService {

    @Resource
    private UserService userService;

    @Resource
    private SpaceUserService spaceUserService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        // 不传类型按私有处理，兼容只传名称与等级的调用方
        if (space.getSpaceType() == null) {
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        space.setUserId(loginUser.getId());
        // 按空间级别填充默认配额
        validSpace(space, true);
        this.fillSpaceByLevel(space);
        // 每位用户仅能拥有一个私有空间；团队空间不限个数（见根 docs/adr/0002）
        if (SpaceTypeEnum.PRIVATE.getValue() == space.getSpaceType()) {
            long count = this.count(new LambdaQueryWrapper<Space>()
                    .eq(Space::getUserId, loginUser.getId())
                    .eq(Space::getSpaceType, SpaceTypeEnum.PRIVATE.getValue()));
            ThrowUtils.throwIf(count >= 1, ErrorCode.OPERATION_ERROR, "每位用户仅能拥有一个私有空间");
        }
        boolean result = this.save(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "空间创建失败");
        // 创建者写入 admin 成员记录。成员关系是访问空间的唯一依据，私有空间也不例外，
        // 这样两类空间共用同一套鉴权代码。与本方法同一事务：空间建好而没有成员记录会变成一个谁都进不去的空间
        spaceUserService.saveCreatorAsAdmin(space.getId(), loginUser.getId());
        return space.getId();
    }

    @Override
    public boolean updateSpace(SpaceUpdateRequest spaceUpdateRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUpdateRequest == null || spaceUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR, "更新请求发送失败");
        Space oldSpace = this.getById(spaceUpdateRequest.getId());
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);
        validSpace(space, false);
        // 修改空间级别时，按级别重置默认配额
        if (space.getSpaceLevel() != null) {
            this.fillSpaceByLevel(space);
        }
        boolean result = this.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "空间更新失败");
        return true;
    }

    @Override
    public boolean editSpace(SpaceEditRequest spaceEditRequest, User loginUser) {
        ThrowUtils.throwIf(spaceEditRequest == null || spaceEditRequest.getId() <= 0, ErrorCode.PARAMS_ERROR, "编辑请求发送失败");
        Space oldSpace = this.getById(spaceEditRequest.getId());
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        // 改空间设置属于管理员职权，编辑者与只读成员不行
        spaceUserService.checkPermission(oldSpace, loginUser, SpaceRoleEnum.ADMIN);
        Space space = new Space();
        BeanUtils.copyProperties(spaceEditRequest, space);
        boolean result = this.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "空间编辑失败");
        return true;
    }

    @Override
    public void updateSpaceUsage(Long spaceId, long sizeDelta, long countDelta) {
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR, "空间 id 不合法");
        LambdaUpdateWrapper<Space> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Space::getId, spaceId)
                .setSql("totalSize = totalSize + (" + sizeDelta + ")")
                .setSql("totalCount = totalCount + (" + countDelta + ")");
        boolean result = this.update(updateWrapper);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "空间用量更新失败");
    }

    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR, "空间不存在");
        // 有参数则校验
        ThrowUtils.throwIf(StrUtil.isNotBlank(space.getSpaceName()) && space.getSpaceName().length() > 128,
                ErrorCode.PARAMS_ERROR, "空间名称过长");
        if (space.getSpaceLevel() != null) {
            ThrowUtils.throwIf(SpaceLevelEnum.getEnumByValue(space.getSpaceLevel()) == null,
                    ErrorCode.PARAMS_ERROR, "空间级别不合法");
        }
        if (space.getSpaceType() != null) {
            ThrowUtils.throwIf(SpaceTypeEnum.getEnumByValue(space.getSpaceType()) == null,
                    ErrorCode.PARAMS_ERROR, "空间类型不合法");
        }
        if (add) {
            ThrowUtils.throwIf(StrUtil.isBlank(space.getSpaceName()), ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            ThrowUtils.throwIf(space.getSpaceLevel() == null, ErrorCode.PARAMS_ERROR, "空间级别不能为空");
        }
    }

    @Override
    public SpaceVO getSpaceVO(Space space) {
        if (space == null) {
            return null;
        }
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            spaceVO.setUser(userService.getUserVO(user));
        }
        return spaceVO;
    }

    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 只需要查询一次数据库
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVO(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    /**
     * 按空间级别填充默认配额
     */
    private void fillSpaceByLevel(Space space) {
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        ThrowUtils.throwIf(spaceLevelEnum == null, ErrorCode.PARAMS_ERROR, "空间级别不合法");
        space.setMaxSize(spaceLevelEnum.getDefMaxSize());
        space.setMaxCount(spaceLevelEnum.getDefMaxCount());
        space.setMaxMemberCount(spaceLevelEnum.getDefMaxMemberCount());
    }

    /**
     * 构造空间查询条件
     */
    @Override
    public LambdaQueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        LambdaQueryWrapper<Space> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(spaceQueryRequest.getId()), Space::getId, spaceQueryRequest.getId())
                .like(StrUtil.isNotBlank(spaceQueryRequest.getSpaceName()), Space::getSpaceName, spaceQueryRequest.getSpaceName())
                .eq(ObjUtil.isNotNull(spaceQueryRequest.getSpaceLevel()), Space::getSpaceLevel, spaceQueryRequest.getSpaceLevel())
                .eq(ObjUtil.isNotNull(spaceQueryRequest.getSpaceType()), Space::getSpaceType, spaceQueryRequest.getSpaceType())
                .eq(ObjUtil.isNotNull(spaceQueryRequest.getUserId()), Space::getUserId, spaceQueryRequest.getUserId())
                .orderByDesc(Space::getCreateTime);
        return queryWrapper;
    }

    @Override
    public LambdaQueryWrapper<Space> applyVisibility(LambdaQueryWrapper<Space> queryWrapper, User loginUser) {
        // 站点管理员看得到全部空间
        if (loginUser != null && userService.isAdmin(loginUser)) {
            return queryWrapper;
        }
        List<Long> mySpaceIds = loginUser == null
                ? new ArrayList<>()
                : spaceUserService.listMySpaceIds(loginUser.getId());
        // 私有空间的元数据维持公开可读，团队空间的壳只有成员能看到（有意的不对称，见根 docs/adr/0002）
        return queryWrapper.and(qw -> {
            qw.eq(Space::getSpaceType, SpaceTypeEnum.PRIVATE.getValue());
            if (CollUtil.isNotEmpty(mySpaceIds)) {
                qw.or().in(Space::getId, mySpaceIds);
            }
        });
    }

    @Override
    public List<SpaceVO> listMySpaces(User loginUser) {
        // 私有空间也写成员记录，所以「我有成员关系的空间」已经包含「我创建的空间」，不必再并一次 owner 查询
        List<Long> spaceIds = spaceUserService.listMySpaceIds(loginUser.getId());
        if (CollUtil.isEmpty(spaceIds)) {
            return new ArrayList<>();
        }
        List<Space> spaceList = this.lambdaQuery()
                .in(Space::getId, spaceIds)
                .orderByDesc(Space::getCreateTime)
                .list();
        Map<Long, String> myRoleMap = spaceUserService.mapMyRoles(spaceIds, loginUser.getId());
        // myRole 一律是「有效角色」：站点管理员即使成员表里记的是 editor，后端也放行 admin 的操作，
        // 界面若按记录值显示就会藏起其实能用的按钮
        boolean siteAdmin = userService.isAdmin(loginUser);
        return spaceList.stream().map(space -> {
            SpaceVO spaceVO = this.getSpaceVO(space);
            spaceVO.setMyRole(siteAdmin ? SpaceRoleEnum.ADMIN.getValue() : myRoleMap.get(space.getId()));
            spaceVO.setMemberCount(spaceUserService.countMembers(space.getId()));
            return spaceVO;
        }).collect(Collectors.toList());
    }

    @Override
    public SpaceVO getSpaceVO(Space space, User loginUser) {
        SpaceVO spaceVO = this.getSpaceVO(space);
        if (spaceVO == null) {
            return null;
        }
        SpaceRoleEnum myRole = spaceUserService.getEffectiveRole(space, loginUser);
        spaceVO.setMyRole(myRole == null ? null : myRole.getValue());
        spaceVO.setMemberCount(spaceUserService.countMembers(space.getId()));
        return spaceVO;
    }

    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, User loginUser) {
        Page<SpaceVO> spaceVOPage = this.getSpaceVOPage(spacePage);
        if (loginUser == null || CollUtil.isEmpty(spaceVOPage.getRecords())) {
            return spaceVOPage;
        }
        // 一次批量查出本页所有空间里我的角色；成员数列表页用不上，不查
        Set<Long> spaceIds = spaceVOPage.getRecords().stream().map(SpaceVO::getId).collect(Collectors.toSet());
        Map<Long, String> myRoleMap = spaceUserService.mapMyRoles(spaceIds, loginUser.getId());
        // 站点管理员在成员表里没有记录，但 getEffectiveRole 把他视作 admin。
        // 这里补齐，否则同一个 myRole 字段在详情接口是 admin、在列表接口是 null，两套语义
        boolean siteAdmin = userService.isAdmin(loginUser);
        spaceVOPage.getRecords().forEach(spaceVO -> spaceVO.setMyRole(
                siteAdmin ? SpaceRoleEnum.ADMIN.getValue() : myRoleMap.get(spaceVO.getId())));
        return spaceVOPage;
    }
}
