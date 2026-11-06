package com.yupi.yupicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yupi.yupicturebackend.model.dto.UserFollowCount;
import com.yupi.yupicturebackend.model.entity.UserFollow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author SPHZ
 * @description 针对表【user_follow(用户关注关系)】的数据库操作Mapper
 * @createDate 2026-09-27
 * @Entity com.yupi.yupicturebackend.model.entity.UserFollow
 */
public interface UserFollowMapper extends BaseMapper<UserFollow> {

    /**
     * 批量统计一批用户的粉丝数（关注关系按 followeeId 分组计数）。
     *
     * @param followeeIds 被关注者 id 列表
     * @return 有粉丝记录的用户计数行（无粉丝的用户不在结果里，计数按 0 处理）
     */
    List<UserFollowCount> selectFollowerCounts(@Param("followeeIds") List<Long> followeeIds);
}
