package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.common.PageRequest;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.PictureMapper;
import com.yupi.yupicturebackend.mapper.SpaceActivityLogMapper;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.SpaceActivityLog;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.model.vo.SpaceActivityVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.SpaceActivityService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author SPHZ
 * @description 空间动态服务实现。动态是图片主流程的旁路数据：记录尽力而为；
 * 列表的图片信息直接走 PictureMapper 批量查询（不依赖 PictureService，
 * 避免与记录方 PictureServiceImpl 形成循环依赖）。
 * @createDate 2026-09-29
 */
@Slf4j
@Service
public class SpaceActivityServiceImpl implements SpaceActivityService {

    /** 动态列表每页上限 */
    private static final int MAX_PAGE_SIZE = 20;

    @Resource
    private SpaceActivityLogMapper spaceActivityLogMapper;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private UserService userService;

    @Resource
    private PictureMapper pictureMapper;

    @Override
    public void recordActivity(Long spaceId, Long userId, String action, Long targetId) {
        if (spaceId == null) {
            // 公共图库没有「空间动态」一说
            return;
        }
        try {
            SpaceActivityLog activityLog = new SpaceActivityLog();
            activityLog.setSpaceId(spaceId);
            activityLog.setUserId(userId);
            activityLog.setAction(action);
            activityLog.setTargetId(targetId);
            spaceActivityLogMapper.insert(activityLog);
        } catch (Exception e) {
            log.warn("空间动态记录失败, spaceId={}, userId={}, action={}, targetId={}",
                    spaceId, userId, action, targetId, e);
        }
    }

    @Override
    public Page<SpaceActivityVO> listSpaceActivities(Long spaceId, PageRequest pageRequest, User loginUser) {
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(pageRequest == null, ErrorCode.PARAMS_ERROR);
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        spaceUserService.checkPermission(space, loginUser, com.yupi.yupicturebackend.model.enums.SpaceRoleEnum.VIEWER,
                "无权限查看该空间的动态");
        long current = Math.max(pageRequest.getCurrent(), 1);
        long pageSize = Math.min(Math.max(pageRequest.getPageSize(), 1), MAX_PAGE_SIZE);

        Page<SpaceActivityLog> logPage = spaceActivityLogMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SpaceActivityLog>()
                        .eq(SpaceActivityLog::getSpaceId, spaceId)
                        .orderByDesc(SpaceActivityLog::getCreateTime)
                        .orderByDesc(SpaceActivityLog::getId));
        Page<SpaceActivityVO> result = new Page<>(current, pageSize, logPage.getTotal());
        List<SpaceActivityLog> logs = logPage.getRecords();
        if (CollUtil.isEmpty(logs)) {
            return result;
        }

        // 批量回填操作人与目标图片（已删图片跳过，动态条目保留）
        Set<Long> userIds = logs.stream().map(SpaceActivityLog::getUserId).collect(Collectors.toSet());
        Map<Long, UserVO> userMap = userService.getUserVOList(userService.listByIds(userIds)).stream()
                .collect(Collectors.toMap(UserVO::getId, Function.identity(), (a, b) -> a));
        Set<Long> pictureIds = logs.stream().map(SpaceActivityLog::getTargetId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, Picture> pictureMap = pictureIds.isEmpty() ? Map.of()
                : pictureMapper.selectBatchIds(pictureIds).stream()
                        .collect(Collectors.toMap(Picture::getId, Function.identity(), (a, b) -> a));

        List<SpaceActivityVO> voList = new ArrayList<>(logs.size());
        for (SpaceActivityLog activityLog : logs) {
            SpaceActivityVO vo = new SpaceActivityVO();
            vo.setId(activityLog.getId());
            vo.setUser(userMap.get(activityLog.getUserId()));
            vo.setAction(activityLog.getAction());
            vo.setTargetId(activityLog.getTargetId());
            vo.setCreateTime(activityLog.getCreateTime());
            Picture picture = pictureMap.get(activityLog.getTargetId());
            if (picture != null) {
                // 动态里的图片只做简要展示（作者即动态上下文，无需回填用户信息）；
                // 不注入 PictureService，避免与记录方 PictureServiceImpl 循环依赖
                vo.setPicture(PictureVO.objToVo(picture));
            }
            voList.add(vo);
        }
        result.setRecords(voList);
        return result;
    }
}
