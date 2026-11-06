package com.yupi.yupicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yupi.yupicturebackend.model.dto.SpaceMemberPictureCount;
import com.yupi.yupicturebackend.model.entity.SpaceActivityLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author SPHZ
 * @description 针对表【space_activity_log(空间动态)】的数据库操作Mapper
 * @createDate 2026-09-29
 * @Entity com.yupi.yupicturebackend.model.entity.SpaceActivityLog
 */
public interface SpaceActivityLogMapper extends BaseMapper<SpaceActivityLog> {
}
