package com.yupi.yupicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yupi.yupicturebackend.model.entity.Collect;
import com.yupi.yupicturebackend.model.entity.Picture;
import org.apache.ibatis.annotations.Param;

/**
* @author SPHZ
* @description 针对表【collect(用户收藏图片关系)】的数据库操作Mapper
* @createDate 2026-09-13
* @Entity com.yupi.yupicturebackend.model.entity.Collect
*/
public interface CollectMapper extends BaseMapper<Collect> {

    /**
     * 分页查询某用户仍可见的已收藏图片，按收藏时间倒序（最近收藏的排在前面）。
     * <p>
     * 用 JOIN 而不是「先分页查收藏关系、再按 pictureId 捞图片后过滤」：图片被编辑后
     * 会重置为待审核而对他人不可见，先分页再过滤会让页长短于 pageSize、total 虚高。
     * 可见性规则与 {@code PictureServiceImpl#checkPictureReadable} 一致。
     *
     * @param page    分页对象
     * @param userId  收藏人 id
     * @param isAdmin 收藏人是否管理员（管理员可读任意空间图片，不受空间归属限制）
     * @return 图片分页，排序依据是收藏关系的时间而非图片自身时间
     */
    IPage<Picture> selectCollectedPicturePage(IPage<Picture> page,
                                             @Param("userId") Long userId,
                                             @Param("isAdmin") boolean isAdmin);

    /**
     * 分页查询某用户公开收藏里的图片：只含公共图库过审图（空间图片无论开关都不公开），
     * 按收藏时间倒序。调用方先校验 owner.collectPublic 才会走到这里。
     *
     * @param page   分页对象
     * @param userId 收藏者 id
     * @return 图片分页
     */
    IPage<Picture> selectPublicCollectedPicturePage(IPage<Picture> page, @Param("userId") Long userId);
}
