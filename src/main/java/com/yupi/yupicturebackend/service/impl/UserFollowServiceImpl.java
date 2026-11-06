package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.UserFollowMapper;
import com.yupi.yupicturebackend.model.dto.UserFollowCount;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.entity.UserFollow;
import com.yupi.yupicturebackend.model.vo.FollowCountVO;
import com.yupi.yupicturebackend.model.vo.FollowStatusVO;
import com.yupi.yupicturebackend.service.UserFollowService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author SPHZ
 * @description 针对表【user_follow(用户关注关系)】的数据库操作Service实现
 * @createDate 2026-09-27
 */
@Slf4j
@Service
public class UserFollowServiceImpl extends ServiceImpl<UserFollowMapper, UserFollow> implements UserFollowService {

    /** 批量查关注状态的单次 id 上限：与收藏批量口径一致 */
    private static final int MAX_BATCH_SIZE = 30;

    @Resource
    private UserService userService;

    @Override
    public boolean doFollow(Long followeeId, User loginUser) {
        ThrowUtils.throwIf(followeeId == null || followeeId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        ThrowUtils.throwIf(followeeId.equals(loginUser.getId()), ErrorCode.PARAMS_ERROR, "不能关注自己");
        ThrowUtils.throwIf(userService.getById(followeeId) == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        UserFollow follow = new UserFollow();
        follow.setFollowerId(loginUser.getId());
        follow.setFolloweeId(followeeId);
        try {
            this.save(follow);
        } catch (DuplicateKeyException e) {
            // 并发重复关注被 uk_follower_followee 拦下，按已关注幂等处理
        }
        return true;
    }

    @Override
    public boolean cancelFollow(Long followeeId, User loginUser) {
        ThrowUtils.throwIf(followeeId == null || followeeId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        this.remove(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, loginUser.getId())
                .eq(UserFollow::getFolloweeId, followeeId));
        return true;
    }

    @Override
    public FollowStatusVO getFollowStatus(Long followeeId, User loginUser) {
        ThrowUtils.throwIf(followeeId == null || followeeId <= 0, ErrorCode.PARAMS_ERROR);
        FollowStatusVO statusVO = new FollowStatusVO();
        statusVO.setUserId(followeeId);
        statusVO.setFollowed(loginUser != null && this.hasFollow(loginUser.getId(), followeeId));
        statusVO.setFollowerCount(this.getFollowerCount(followeeId));
        return statusVO;
    }

    @Override
    public List<FollowStatusVO> listFollowStatus(List<Long> followeeIds, User loginUser) {
        if (CollUtil.isEmpty(followeeIds)) {
            return new ArrayList<>();
        }
        // 保持传入顺序去重；超限报错而不是截断（截断会让部分按钮静默显示成未关注）
        List<Long> validIds = followeeIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new));
        ThrowUtils.throwIf(validIds.isEmpty(), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(validIds.size() > MAX_BATCH_SIZE, ErrorCode.PARAMS_ERROR,
                "一次最多查询 " + MAX_BATCH_SIZE + " 位用户的关注状态");

        Set<Long> followedSet = loginUser == null ? Collections.emptySet()
                : this.list(new LambdaQueryWrapper<UserFollow>()
                        .select(UserFollow::getFolloweeId)
                        .eq(UserFollow::getFollowerId, loginUser.getId())
                        .in(UserFollow::getFolloweeId, validIds))
                .stream().map(UserFollow::getFolloweeId).collect(Collectors.toSet());
        Map<Long, Long> followerCounts = this.mapFollowerCounts(validIds);

        List<FollowStatusVO> result = new ArrayList<>(validIds.size());
        for (Long followeeId : validIds) {
            FollowStatusVO statusVO = new FollowStatusVO();
            statusVO.setUserId(followeeId);
            statusVO.setFollowed(followedSet.contains(followeeId));
            statusVO.setFollowerCount(followerCounts.getOrDefault(followeeId, 0L));
            result.add(statusVO);
        }
        return result;
    }

    @Override
    public FollowCountVO getFollowCounts(Long userId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR);
        FollowCountVO countVO = new FollowCountVO();
        countVO.setFollowerCount(this.getFollowerCount(userId));
        countVO.setFollowingCount(this.count(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId)));
        return countVO;
    }

    @Override
    public Map<Long, Long> mapFollowerCounts(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return new HashMap<>();
        }
        return baseMapper.selectFollowerCounts(new ArrayList<>(new HashSet<>(userIds))).stream()
                .collect(Collectors.toMap(UserFollowCount::getFolloweeId, UserFollowCount::getFollowerCount));
    }

    private boolean hasFollow(Long followerId, Long followeeId) {
        return this.count(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId)) > 0;
    }

    private long getFollowerCount(Long followeeId) {
        return this.count(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFolloweeId, followeeId));
    }
}
