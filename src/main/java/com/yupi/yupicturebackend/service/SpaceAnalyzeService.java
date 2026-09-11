package com.yupi.yupicturebackend.service;

import com.yupi.yupicturebackend.model.dto.space.SpaceAnalyzeQueryRequest;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.SpaceCategoryAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceRankAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceSizeAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceTagAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceUploadTrendAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceUsageAnalyzeResponse;

import java.util.List;

/**
 * 图库分析 Service
 *
 * 查询范围规则（见 SpaceAnalyzeQueryRequest）：
 * 1. querySpaceId 不为空：分析指定空间（空间所有者或管理员）
 * 2. querySpaceId 为空且 queryAll 为 false：分析公共图库（仅管理员）
 * 3. querySpaceId 为空且 queryAll 为 true：分析全空间（仅管理员）
 */
public interface SpaceAnalyzeService {

    /**
     * 空间资源使用分析：已用大小/总配额、图片数量/最大数量占比
     */
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, User loginUser);

    /**
     * 空间图片分类分析：各分类的图片数量和总大小
     */
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, User loginUser);

    /**
     * 空间图片标签分析：每个标签关联的图片数量
     */
    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, User loginUser);

    /**
     * 空间图片大小分析：按固定分段统计图片数量
     */
    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, User loginUser);

    /**
     * 用户上传行为分析：按日/周/月统计上传数量趋势
     */
    List<SpaceUploadTrendAnalyzeResponse> getSpaceUploadTrendAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, User loginUser);

    /**
     * 空间使用排行分析：按存储使用量取前 N 个空间（仅管理员）
     */
    List<SpaceRankAnalyzeResponse> getSpaceRankAnalyze(SpaceAnalyzeQueryRequest spaceAnalyzeQueryRequest, User loginUser);
}
