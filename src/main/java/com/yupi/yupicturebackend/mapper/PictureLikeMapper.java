package com.yupi.yupicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.PictureLike;
import org.apache.ibatis.annotations.Param;

/**
 * @author SPHZ
 * @description 针对表【picture_like(图片点赞关系)】的数据库操作Mapper
 * @createDate 2026-09-27
 * @Entity com.yupi.yupicturebackend.model.entity.PictureLike
 */
public interface PictureLikeMapper extends BaseMapper<PictureLike> {

    /**
     * 分页查询某用户仍可见的已点赞图片，按点赞时间倒序。
     * <p>
     * 用 JOIN 而不是「先分页查关系、再捞图片后过滤」：图片被编辑后重回待审核会变得
     * 不可见，先分页再过滤会让页长短于 pageSize、total 虚高（同收藏列表的取舍）。
     * 可见性规则与 {@code PictureServiceImpl#checkPictureReadable} 一致。
     *
     * @param page    分页对象
     * @param userId  点赞人 id
     * @param isAdmin 点赞人是否管理员（管理员可读任意空间图片）
     * @return 图片分页
     */
    IPage<Picture> selectLikedPicturePage(IPage<Picture> page,
                                          @Param("userId") Long userId,
                                          @Param("isAdmin") boolean isAdmin);

    /**
     * 统计某用户收到的点赞总数（获赞）：其名下未删除图片被点赞的次数。
     * 口径是「已发生的点赞事实」，不过滤图片审核状态/归属空间。
     *
     * @param userId 图片作者 id
     * @return 获赞总数
     */
    long selectReceivedLikeCount(@Param("userId") Long userId);
}
