package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.common.PageRequest;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.PictureDownloadLogMapper;
import com.yupi.yupicturebackend.model.dto.PictureDownloadStat;
import com.yupi.yupicturebackend.model.dto.picture.PictureRankQueryRequest;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.PictureDownloadLog;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureRankTrendEnum;
import com.yupi.yupicturebackend.model.vo.PictureDownloadRecordVO;
import com.yupi.yupicturebackend.model.vo.PictureRankVO;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.service.PictureDownloadLogService;
import com.yupi.yupicturebackend.service.PictureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author SPHZ
 * @description 针对表【picture_download_log(图片下载日志)】的数据库操作Service实现
 * @createDate 2026-09-27
 */
@Slf4j
@Service
public class PictureDownloadLogServiceImpl extends ServiceImpl<PictureDownloadLogMapper, PictureDownloadLog>
        implements PictureDownloadLogService {

    /** 榜单/历史列表每页上限：与收藏列表保持一致 */
    private static final int MAX_PAGE_SIZE = 20;

    /** 趋势基准窗口的取名次范围：上一窗口下载量前 100 名内才算有名次 */
    private static final int TREND_BASE_SIZE = 100;

    @Resource
    private PictureService pictureService;

    @Override
    public void recordDownload(Long pictureId, Long userId) {
        if (pictureId == null || pictureId <= 0) {
            return;
        }
        try {
            PictureDownloadLog record = new PictureDownloadLog();
            record.setPictureId(pictureId);
            record.setUserId(userId);
            this.save(record);
        } catch (Exception e) {
            // 日志是下载链路上最后一步的旁路数据：写失败只告警，不影响已完成的下载重定向
            log.warn("下载日志写入失败, pictureId={}, userId={}", pictureId, userId, e);
        }
    }

    @Override
    public Page<PictureRankVO> listPictureRanking(PictureRankQueryRequest rankQueryRequest) {
        ThrowUtils.throwIf(rankQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long windowMillis = resolveWindowMillis(rankQueryRequest.getPeriod());
        long current = Math.max(rankQueryRequest.getCurrent(), 1);
        long pageSize = Math.min(Math.max(rankQueryRequest.getPageSize(), 1), MAX_PAGE_SIZE);

        // 滚动窗口：当前窗口 [now - window, now)，趋势基准窗口再往前推一个等长窗口
        long now = System.currentTimeMillis();
        Date startTime = new Date(now - windowMillis);
        Date endTime = new Date(now);

        // MyBatis-Plus 把聚合结果填进传入的 Page 实例（mapper 方法返回 IPage）
        Page<PictureDownloadStat> statPage = new Page<>(current, pageSize);
        baseMapper.selectDownloadRankPage(statPage, startTime, endTime, rankQueryRequest.getCategory());
        Page<PictureRankVO> result = new Page<>(current, pageSize, statPage.getTotal());
        List<PictureDownloadStat> stats = statPage.getRecords();
        if (CollUtil.isEmpty(stats)) {
            return result;
        }

        // 聚合行与图片记录并行装配：聚合窗口内已过滤已删图片，这里再兜底跳过两查之间被删的
        Set<Long> pictureIds = stats.stream().map(PictureDownloadStat::getPictureId).collect(Collectors.toSet());
        Map<Long, Picture> pictureMap = pictureService.listByIds(pictureIds).stream()
                .collect(Collectors.toMap(Picture::getId, Function.identity()));
        List<Picture> orderedPictures = new ArrayList<>(stats.size());
        List<PictureDownloadStat> orderedStats = new ArrayList<>(stats.size());
        for (PictureDownloadStat stat : stats) {
            Picture picture = pictureMap.get(stat.getPictureId());
            if (picture != null) {
                orderedPictures.add(picture);
                orderedStats.add(stat);
            }
        }

        Page<Picture> picturePage = new Page<>(current, pageSize, statPage.getTotal());
        picturePage.setRecords(orderedPictures);
        List<PictureVO> pictureVOList = pictureService.getPictureVOPage(picturePage).getRecords();

        Map<Long, Integer> prevRankMap = loadPrevRankMap(startTime, windowMillis);
        long rankOffset = (current - 1) * pageSize;
        List<PictureRankVO> rankVOList = new ArrayList<>(orderedStats.size());
        for (int i = 0; i < orderedStats.size(); i++) {
            PictureDownloadStat stat = orderedStats.get(i);
            PictureRankVO rankVO = new PictureRankVO();
            rankVO.setPicture(pictureVOList.get(i));
            rankVO.setDownloadCount(stat.getDownloadCount());
            int rank = (int) (rankOffset + i + 1);
            rankVO.setRank(rank);
            fillTrend(rankVO, prevRankMap.get(stat.getPictureId()));
            rankVOList.add(rankVO);
        }
        result.setRecords(rankVOList);
        return result;
    }

    @Override
    public Page<PictureDownloadRecordVO> listMyDownloadHistory(PageRequest pageRequest, User loginUser) {
        ThrowUtils.throwIf(pageRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        long current = Math.max(pageRequest.getCurrent(), 1);
        long pageSize = Math.min(Math.max(pageRequest.getPageSize(), 1), MAX_PAGE_SIZE);

        Page<PictureDownloadLog> logPage = this.page(new Page<>(current, pageSize),
                new LambdaQueryWrapper<PictureDownloadLog>()
                        .eq(PictureDownloadLog::getUserId, loginUser.getId())
                        .orderByDesc(PictureDownloadLog::getCreateTime)
                        .orderByDesc(PictureDownloadLog::getId));
        Page<PictureDownloadRecordVO> result = new Page<>(current, pageSize, logPage.getTotal());
        List<PictureDownloadLog> logs = logPage.getRecords();
        if (CollUtil.isEmpty(logs)) {
            return result;
        }

        // 与榜单同样的并行装配：跳过图片已被删除的历史条目
        Set<Long> pictureIds = logs.stream().map(PictureDownloadLog::getPictureId).collect(Collectors.toSet());
        Map<Long, Picture> pictureMap = pictureService.listByIds(pictureIds).stream()
                .collect(Collectors.toMap(Picture::getId, Function.identity()));
        List<Picture> orderedPictures = new ArrayList<>(logs.size());
        List<PictureDownloadLog> orderedLogs = new ArrayList<>(logs.size());
        for (PictureDownloadLog downloadLog : logs) {
            Picture picture = pictureMap.get(downloadLog.getPictureId());
            if (picture != null) {
                orderedPictures.add(picture);
                orderedLogs.add(downloadLog);
            }
        }

        Page<Picture> picturePage = new Page<>(current, pageSize, logPage.getTotal());
        picturePage.setRecords(orderedPictures);
        List<PictureVO> pictureVOList = pictureService.getPictureVOPage(picturePage).getRecords();

        List<PictureDownloadRecordVO> recordList = new ArrayList<>(orderedLogs.size());
        for (int i = 0; i < orderedLogs.size(); i++) {
            PictureDownloadRecordVO recordVO = new PictureDownloadRecordVO();
            recordVO.setPicture(pictureVOList.get(i));
            recordVO.setDownloadTime(orderedLogs.get(i).getCreateTime());
            recordList.add(recordVO);
        }
        result.setRecords(recordList);
        return result;
    }

    /**
     * 榜单周期 → 滚动窗口毫秒数：day=24h、week=7d、month=30d。
     * 未传按日榜处理，非法值报参数错误。
     */
    private long resolveWindowMillis(String period) {
        if (period == null || period.isEmpty()) {
            period = "day";
        }
        long hour = 60L * 60 * 1000;
        switch (period) {
            case "day":
                return 24 * hour;
            case "week":
                return 7 * 24 * hour;
            case "month":
                return 30L * 24 * hour;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法的榜单周期：" + period);
        }
    }

    /**
     * 上一等长窗口的下载量名次表（前 {@value TREND_BASE_SIZE} 名）：pictureId → 名次。
     * 空窗口返回空表，全部条目按「新上榜」处理。
     */
    private Map<Long, Integer> loadPrevRankMap(Date currentWindowStart, long windowMillis) {
        Date prevStart = new Date(currentWindowStart.getTime() - windowMillis);
        Page<PictureDownloadStat> prevPage = new Page<>(1, TREND_BASE_SIZE, false);
        baseMapper.selectDownloadRankPage(prevPage, prevStart, currentWindowStart, null);
        List<PictureDownloadStat> prevStats = prevPage.getRecords();
        if (CollUtil.isEmpty(prevStats)) {
            return Map.of();
        }
        Map<Long, Integer> prevRankMap = new HashMap<>(prevStats.size());
        for (int i = 0; i < prevStats.size(); i++) {
            prevRankMap.put(prevStats.get(i).getPictureId(), i + 1);
        }
        return prevRankMap;
    }

    /**
     * 依据上一窗口名次填充趋势：无名次=新上榜；名次差>0 上升、<0 下降、=0 持平。
     */
    private void fillTrend(PictureRankVO rankVO, Integer prevRank) {
        if (prevRank == null) {
            rankVO.setTrend(PictureRankTrendEnum.NEW.getValue());
            return;
        }
        int delta = prevRank - rankVO.getRank();
        if (delta > 0) {
            rankVO.setTrend(PictureRankTrendEnum.UP.getValue());
            rankVO.setTrendDelta(delta);
        } else if (delta < 0) {
            rankVO.setTrend(PictureRankTrendEnum.DOWN.getValue());
            rankVO.setTrendDelta(-delta);
        } else {
            rankVO.setTrend(PictureRankTrendEnum.FLAT.getValue());
        }
    }
}
