package com.yupi.yupicturebackend.controller;

import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.model.dto.space.SpaceAnalyzeQueryRequest;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.SpaceCategoryAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceRankAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceSizeAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceTagAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceUploadTrendAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceUsageAnalyzeResponse;
import com.yupi.yupicturebackend.service.SpaceAnalyzeService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 图库分析接口
 *
 * 查询范围由 SpaceAnalyzeQueryRequest 决定：
 * 1. querySpaceId 不为空：分析指定空间（空间所有者或管理员）
 * 2. querySpaceId 为空且 queryAll 为 false：分析公共图库（仅管理员）
 * 3. querySpaceId 为空且 queryAll 为 true：分析全空间，即公共图库 + 所有空间（仅管理员）
 */
@Slf4j
@RestController
@RequestMapping("/space/analyze")
public class SpaceAnalyzeController {

    @Resource
    private SpaceAnalyzeService spaceAnalyzeService;

    @Resource
    private UserService userService;

    /**
     * 空间资源使用分析
     */
    @GetMapping("/usage")
    public BaseResponse<SpaceUsageAnalyzeResponse> getSpaceUsageAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.getSpaceUsageAnalyze(spaceAnalyzeQueryRequest, loginUser));
    }

    /**
     * 空间图片分类分析
     */
    @GetMapping("/category")
    public BaseResponse<List<SpaceCategoryAnalyzeResponse>> getSpaceCategoryAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.getSpaceCategoryAnalyze(spaceAnalyzeQueryRequest, loginUser));
    }

    /**
     * 空间图片标签分析
     */
    @GetMapping("/tag")
    public BaseResponse<List<SpaceTagAnalyzeResponse>> getSpaceTagAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.getSpaceTagAnalyze(spaceAnalyzeQueryRequest, loginUser));
    }

    /**
     * 空间图片大小分析
     */
    @GetMapping("/size")
    public BaseResponse<List<SpaceSizeAnalyzeResponse>> getSpaceSizeAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.getSpaceSizeAnalyze(spaceAnalyzeQueryRequest, loginUser));
    }

    /**
     * 用户上传行为分析（granularity：day / week / month，默认最近 30 天按天）
     */
    @GetMapping("/upload/trend")
    public BaseResponse<List<SpaceUploadTrendAnalyzeResponse>> getSpaceUploadTrendAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.getSpaceUploadTrendAnalyze(spaceAnalyzeQueryRequest, loginUser));
    }

    /**
     * 空间使用排行分析（按存储使用量取前 N 个空间，仅管理员）
     */
    @GetMapping("/rank")
    public BaseResponse<List<SpaceRankAnalyzeResponse>> getSpaceRankAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.getSpaceRankAnalyze(spaceAnalyzeQueryRequest, loginUser));
    }
}
