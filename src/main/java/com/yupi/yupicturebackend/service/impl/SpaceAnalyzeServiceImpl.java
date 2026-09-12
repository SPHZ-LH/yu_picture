package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.PictureMapper;
import com.yupi.yupicturebackend.mapper.SpaceMapper;
import com.yupi.yupicturebackend.model.dto.space.SpaceAnalyzeQueryRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceUsageSummary;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.SpaceCategoryAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceRankAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceSizeAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceTagAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceUploadTrendAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceUsageAnalyzeResponse;
import com.yupi.yupicturebackend.service.SpaceAnalyzeService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 图库分析 Service 实现
 */
@Slf4j
@Service
public class SpaceAnalyzeServiceImpl implements SpaceAnalyzeService {

    @Resource
    private SpaceService spaceService;

    @Resource
    private UserService userService;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private SpaceMapper spaceMapper;

    /**
     * 上传行为分析默认统计最近 30 天
     */
    private static final int DEFAULT_TREND_DAYS = 30;

    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, User loginUser) {
        checkAnalyzeAuth(spaceAnalyzeQueryRequest, loginUser);
        Long querySpaceId = spaceAnalyzeQueryRequest.getQuerySpaceId();
        SpaceUsageAnalyzeResponse response = new SpaceUsageAnalyzeResponse();
        if (querySpaceId != null) {
            // 分析指定空间：直接读空间表维护的使用量与配额
            Space space = spaceService.getById(querySpaceId);
            response.setUsedSize(space.getTotalSize());
            response.setUsedCount(space.getTotalCount());
            response.setMaxSize(space.getMaxSize());
            response.setMaxCount(space.getMaxCount());
            response.setUsedSizeRatio(calcRatio(space.getTotalSize(), space.getMaxSize()));
            response.setUsedCountRatio(calcRatio(space.getTotalCount(), space.getMaxCount()));
            return response;
        }
        // 公共图库 / 全空间：实时聚合，无配额概念
        boolean queryAll = Boolean.TRUE.equals(spaceAnalyzeQueryRequest.getQueryAll());
        SpaceUsageSummary summary = pictureMapper.selectSpaceUsageSummary(null, queryAll);
        response.setUsedSize(summary.getTotalSize());
        response.setUsedCount(summary.getTotalCount());
        return response;
    }

    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, User loginUser) {
        checkAnalyzeAuth(spaceAnalyzeQueryRequest, loginUser);
        return pictureMapper.selectCategoryAnalyze(spaceAnalyzeQueryRequest.getQuerySpaceId(),
                Boolean.TRUE.equals(spaceAnalyzeQueryRequest.getQueryAll()));
    }

    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, User loginUser) {
        checkAnalyzeAuth(spaceAnalyzeQueryRequest, loginUser);
        return pictureMapper.selectTagAnalyze(spaceAnalyzeQueryRequest.getQuerySpaceId(),
                Boolean.TRUE.equals(spaceAnalyzeQueryRequest.getQueryAll()));
    }

    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, User loginUser) {
        checkAnalyzeAuth(spaceAnalyzeQueryRequest, loginUser);
        return pictureMapper.selectSizeAnalyze(spaceAnalyzeQueryRequest.getQuerySpaceId(),
                Boolean.TRUE.equals(spaceAnalyzeQueryRequest.getQueryAll()));
    }

    @Override
    public List<SpaceUploadTrendAnalyzeResponse> getSpaceUploadTrendAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, User loginUser) {
        checkAnalyzeAuth(spaceAnalyzeQueryRequest, loginUser);
        String granularity = StrUtil.blankToDefault(spaceAnalyzeQueryRequest.getGranularity(), "day");
        String dateFormat;
        switch (granularity) {
            case "day":
                dateFormat = "%Y-%m-%d";
                break;
            case "week":
                dateFormat = "%x-W%v";
                break;
            case "month":
                dateFormat = "%Y-%m";
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "时间粒度不合法：仅支持 day / week / month");
        }
        // 默认统计最近 30 天；只传一个时间边界时按传入边界统计
        Date startTime = spaceAnalyzeQueryRequest.getStartTime();
        Date endTime = spaceAnalyzeQueryRequest.getEndTime();
        if (startTime == null && endTime == null) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, -DEFAULT_TREND_DAYS);
            startTime = calendar.getTime();
            endTime = new Date();
        }
        return pictureMapper.selectUploadTrendAnalyze(spaceAnalyzeQueryRequest.getQuerySpaceId(),
                Boolean.TRUE.equals(spaceAnalyzeQueryRequest.getQueryAll()),
                dateFormat, startTime, endTime);
    }

    @Override
    public List<SpaceRankAnalyzeResponse> getSpaceRankAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, User loginUser) {
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权限查看空间排行");
        // 前 N 个空间，默认 10，最大 100
        Integer topNRequest = spaceAnalyzeQueryRequest.getTopN();
        int topN = topNRequest == null ? 10 : topNRequest;
        ThrowUtils.throwIf(topN <= 0 || topN > 100, ErrorCode.PARAMS_ERROR, "topN 仅支持 1-100");
        return spaceMapper.selectSpaceRankAnalyze(topN);
    }

    /**
     * 图库分析权限校验：
     * 1. 分析指定空间：仅空间所有者或管理员
     * 2. 分析公共图库 / 全空间：仅管理员
     */
    private void checkAnalyzeAuth(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        Long querySpaceId = spaceAnalyzeQueryRequest.getQuerySpaceId();
        if (querySpaceId != null) {
            Space space = spaceService.getById(querySpaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            ThrowUtils.throwIf(!space.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser),
                    ErrorCode.NO_AUTH_ERROR, "无权限分析该空间");
        } else {
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "仅管理员可分析公共图库与全空间");
        }
    }

    /**
     * 计算使用比例（百分比，保留 2 位小数），无配额时返回 null
     */
    private Double calcRatio(Long used, Long max) {
        if (used == null || max == null || max <= 0) {
            return null;
        }
        return NumberUtil.round(used * 100.0 / max, 2).doubleValue();
    }
}
