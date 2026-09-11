package com.yupi.yupicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yupi.yupicturebackend.model.dto.space.SpaceUsageSummary;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.vo.SpaceCategoryAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceSizeAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceTagAnalyzeResponse;
import com.yupi.yupicturebackend.model.vo.SpaceUploadTrendAnalyzeResponse;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
* @author SPHZ
* @description 针对表【picture(图片)】的数据库操作Mapper
* @createDate 2025-12-03 00:51:07
* @Entity com.yupi.yupicturebackend.model.entity.Picture
*/
public interface PictureMapper extends BaseMapper<Picture> {

    /**
     * 空间使用量汇总（实时聚合）
     *
     * @param spaceId  空间 id，为空表示公共图库或全空间
     * @param queryAll true 表示全空间（公共图库 + 所有空间）
     * @return 已用大小与数量汇总
     */
    SpaceUsageSummary selectSpaceUsageSummary(@Param("spaceId") Long spaceId, @Param("queryAll") boolean queryAll);

    /**
     * 分类分析：各分类的图片数量与总大小
     */
    List<SpaceCategoryAnalyzeResponse> selectCategoryAnalyze(@Param("spaceId") Long spaceId, @Param("queryAll") boolean queryAll);

    /**
     * 标签分析：每个标签关联的图片数量（JSON_TABLE 展开标签数组）
     */
    List<SpaceTagAnalyzeResponse> selectTagAnalyze(@Param("spaceId") Long spaceId, @Param("queryAll") boolean queryAll);

    /**
     * 大小分析：按固定分档（&lt;100KB / 100KB-1MB / &gt;1MB）统计
     */
    List<SpaceSizeAnalyzeResponse> selectSizeAnalyze(@Param("spaceId") Long spaceId, @Param("queryAll") boolean queryAll);

    /**
     * 上传行为分析：按时间粒度统计上传数量与总大小
     *
     * @param dateFormat DATE_FORMAT 格式串，由服务层按粒度白名单传入，禁止外部拼接
     */
    List<SpaceUploadTrendAnalyzeResponse> selectUploadTrendAnalyze(@Param("spaceId") Long spaceId,
                                                                   @Param("queryAll") boolean queryAll,
                                                                   @Param("dateFormat") String dateFormat,
                                                                   @Param("startTime") Date startTime,
                                                                   @Param("endTime") Date endTime);
}
