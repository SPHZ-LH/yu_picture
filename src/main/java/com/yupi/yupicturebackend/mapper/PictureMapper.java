package com.yupi.yupicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yupi.yupicturebackend.model.dto.space.SpaceUsageSummary;
import com.yupi.yupicturebackend.model.dto.SpaceMemberPictureCount;
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
     * 按成员统计某空间的图片数（团队空间侧栏「成员」面板的作品数角标）
     *
     * @param spaceId 空间 id
     * @return userId → pictureCount（无图片的成员不在结果里，按 0 处理）
     */
    List<SpaceMemberPictureCount> selectSpaceMemberPictureCounts(@Param("spaceId") Long spaceId);

    /**
     * 下载量原子自增（每次触发下载 +1，不去重）。
     * <p>
     * 调用方须在写入成功后自行失效图片缓存（{@code MultiLevelCacheManager.evictPicture}）。
     *
     * @param pictureId 图片 id
     * @return 受影响行数
     */
    int incrementDownloadCount(@Param("pictureId") Long pictureId);

    /**
     * 收藏量原子增减（自减带 GREATEST 兜底，不出现负数）。
     * <p>
     * 调用方须在写入成功后自行失效图片缓存（{@code MultiLevelCacheManager.evictPicture}）。
     *
     * @param pictureId 图片 id
     * @param delta     增量：+1 收藏 / -1 取消收藏
     * @return 受影响行数
     */
    int adjustCollectCount(@Param("pictureId") Long pictureId, @Param("delta") int delta);

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

    /**
     * 清理任务：取逻辑删除超过保留期的图片。
     * <p>
     * Picture 实体带 @TableLogic，常规 Wrapper 查询会被自动追加 isDelete = 0，
     * 已删行只能走原生 SQL。updateTime 在逻辑删除时由 MyBatis-Plus 自动填充，
     * 已删行不会再有任何更新，因此把它当作删除时刻使用。
     *
     * @param before 只取删除时刻早于该时间的记录
     * @param limit  单批上限
     */
    List<Picture> selectDeletedBefore(@Param("before") Date before, @Param("limit") int limit);

    /**
     * 清理任务：物理删除图片行（{@code removeById} 走逻辑删，清完 COS 对象后用这条真正删行）。
     *
     * @param pictureId 图片 id
     * @return 受影响行数
     */
    int deletePhysicallyById(@Param("pictureId") Long pictureId);
}
