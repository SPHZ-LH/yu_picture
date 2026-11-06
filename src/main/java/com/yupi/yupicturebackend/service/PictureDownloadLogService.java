package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.common.PageRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureRankQueryRequest;
import com.yupi.yupicturebackend.model.entity.PictureDownloadLog;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.PictureDownloadRecordVO;
import com.yupi.yupicturebackend.model.vo.PictureRankVO;

/**
 * @author SPHZ
 * @description 针对表【picture_download_log(图片下载日志)】的数据库操作Service
 * @createDate 2026-09-27
 */
public interface PictureDownloadLogService extends IService<PictureDownloadLog> {

    /**
     * 记录一次下载事件（纯追加，不去重）。尽力而为：写日志失败只告警，
     * 绝不让下载请求本身失败（下载的重定向与计数在此之前已完成）。
     *
     * @param pictureId 图片 id
     * @param userId    下载用户 id，匿名下载传 null
     */
    void recordDownload(Long pictureId, Long userId);

    /**
     * 下载排行榜：按榜单周期统计窗口内下载量倒序，只统计公共图库已过审图片。
     * <p>
     * 周期为滚动窗口：日榜=近 24 小时、周榜=近 7 天、月榜=近 30 天（见 docs/adr/0013）。
     * 排名趋势以「上一等长窗口下载量前 100 名」为基准：前 100 内有名次的按名次差给出
     * up/down/flat，窗口内没有名次的视为新上榜。
     *
     * @param rankQueryRequest 榜单查询请求（period/category/分页）
     * @return 榜单条目分页（图片视图 + 窗口下载量 + 名次 + 趋势）
     */
    Page<PictureRankVO> listPictureRanking(PictureRankQueryRequest rankQueryRequest);

    /**
     * 当前用户的下载历史，按下载时间倒序。
     * <p>
     * 只要求图片记录仍然存在（含空间图片与未过审图片——下载当时对本人可见），
     * 图片被物理或逻辑删除后历史条目随之消失。
     *
     * @param pageRequest 分页请求
     * @param loginUser   登录用户
     * @return 下载历史条目分页（图片视图 + 下载时间）
     */
    Page<PictureDownloadRecordVO> listMyDownloadHistory(PageRequest pageRequest, User loginUser);
}
