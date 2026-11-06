package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.entity.UserFollow;
import com.yupi.yupicturebackend.model.vo.FollowCountVO;
import com.yupi.yupicturebackend.model.vo.FollowStatusVO;

import java.util.List;
import java.util.Map;

/**
 * @author SPHZ
 * @description 针对表【user_follow(用户关注关系)】的数据库操作Service
 * @createDate 2026-09-27
 */
public interface UserFollowService extends IService<UserFollow> {

    /**
     * 关注用户（登录用户；不能关注自己；重复关注幂等）
     *
     * @param followeeId 被关注者 id
     * @param loginUser  登录用户
     * @return 是否成功
     */
    boolean doFollow(Long followeeId, User loginUser);

    /**
     * 取消关注（未关注时幂等）
     *
     * @param followeeId 被关注者 id
     * @param loginUser  登录用户
     * @return 是否成功
     */
    boolean cancelFollow(Long followeeId, User loginUser);

    /**
     * 查询当前用户对某用户的关注状态与对方粉丝数（未登录时 followed=false）
     *
     * @param followeeId 被关注者 id
     * @param loginUser  登录用户，可为 null
     * @return 关注状态
     */
    FollowStatusVO getFollowStatus(Long followeeId, User loginUser);

    /**
     * 批量查询关注状态（创作者列表一次渲染全部关注按钮）。
     * 未登录、未传 id 均返回空集合（关注状态是列表附属信息，不该让整页请求失败）。
     *
     * @param followeeIds 被关注者 id 列表
     * @param loginUser   登录用户，可为 null
     * @return 关注状态列表（保持传入顺序，去重）
     */
    List<FollowStatusVO> listFollowStatus(List<Long> followeeIds, User loginUser);

    /**
     * 用户关注维度计数（粉丝数 + 关注数）
     *
     * @param userId 用户 id
     * @return 计数
     */
    FollowCountVO getFollowCounts(Long userId);

    /**
     * 批量统计粉丝数（创作者目录用），无粉丝的用户计 0
     *
     * @param userIds 用户 id 列表
     * @return userId → 粉丝数
     */
    Map<Long, Long> mapFollowerCounts(List<Long> userIds);
}
