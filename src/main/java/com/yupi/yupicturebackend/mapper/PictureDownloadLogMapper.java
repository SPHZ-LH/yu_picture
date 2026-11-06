package com.yupi.yupicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yupi.yupicturebackend.model.dto.PictureDownloadStat;
import com.yupi.yupicturebackend.model.entity.PictureDownloadLog;
import org.apache.ibatis.annotations.Param;

/**
 * @author SPHZ
 * @description 针对表【picture_download_log(图片下载日志)】的数据库操作Mapper
 * @createDate 2026-09-27
 * @Entity com.yupi.yupicturebackend.model.entity.PictureDownloadLog
 */
public interface PictureDownloadLogMapper extends BaseMapper<PictureDownloadLog> {

    /**
     * 分页统计时间窗口内公共图库过审图片的下载量，按下载量倒序（下载榜单）。
     * <p>
     * 只统计公共图库（spaceId IS NULL）且已过审的图片：榜单是站内公共浏览口径，
     * 空间图片不参与排名。被删除的图片（isDelete=1）不进榜单。
     *
     * @param page      分页对象
     * @param startTime 窗口开始（含）
     * @param endTime   窗口结束（不含）
     * @param category  分类筛选，空表示全部
     * @return pictureId + 窗口内下载次数，按下载次数倒序
     */
    IPage<PictureDownloadStat> selectDownloadRankPage(IPage<PictureDownloadStat> page,
                                                      @Param("startTime") java.util.Date startTime,
                                                      @Param("endTime") java.util.Date endTime,
                                                      @Param("category") String category);
}
