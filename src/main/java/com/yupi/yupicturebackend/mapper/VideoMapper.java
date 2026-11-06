package com.yupi.yupicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yupi.yupicturebackend.model.dto.space.SpaceUsageSummary;
import com.yupi.yupicturebackend.model.entity.Video;
import org.apache.ibatis.annotations.Param;

/**
* @author SPHZ
* @description 针对表【video(视频/动态壁纸)】的数据库操作Mapper
* @Entity com.yupi.yupicturebackend.model.entity.Video
*/
public interface VideoMapper extends BaseMapper<Video> {

    /**
     * 下载量原子自增（每次触发下载 +1，不去重）
     *
     * @param videoId 视频 id
     * @return 受影响行数
     */
    int incrementDownloadCount(@Param("videoId") Long videoId);

    /**
     * 汇总某用户在公共图库已占用的视频体积与条数，用于按用户配额校验。
     * <p>
     * 复用 {@link SpaceUsageSummary} 只为承载两个聚合值，与空间无关。
     *
     * @param userId 用户 id
     * @return 已用体积与条数（无数据时为 0）
     */
    SpaceUsageSummary selectUserPublicUsage(@Param("userId") Long userId);
}
