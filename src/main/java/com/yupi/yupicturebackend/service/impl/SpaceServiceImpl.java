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
import com.yupi.yupicturebackend.model.enums.SpaceTypeEnum;
import com.yupi.yupicturebackend.model.vo.SpaceVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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

    @Override
    public Long saveSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        // 填充参数（本期仅支持私有空间，团队空间为后续功能）
        space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        space.setUserId(loginUser.getId());
        // 按空间级别填充默认配额
        validSpace(space, true);
        this.fillSpaceByLevel(space);
        // 每位用户仅能拥有一个私有空间
        long count = this.count(new LambdaQueryWrapper<Space>()
                .eq(Space::getUserId, loginUser.getId())
                .eq(Space::getSpaceType, SpaceTypeEnum.PRIVATE.getValue()));
        ThrowUtils.throwIf(count >= 1, ErrorCode.OPERATION_ERROR, "每位用户仅能拥有一个私有空间");
        boolean result = this.save(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "空间创建失败");
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
        // 仅本人或管理员可编辑
        ThrowUtils.throwIf(!oldSpace.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser),
                ErrorCode.NO_AUTH_ERROR, "无权限编辑该空间");
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
}
