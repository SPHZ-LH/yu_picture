package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.common.PageRequest;
import com.yupi.yupicturebackend.model.entity.PictureLike;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.PictureVO;

import java.util.List;

/**
 * @author SPHZ
 * @description 针对表【picture_like(图片点赞关系)】的数据库操作Service
 * @createDate 2026-09-27
 */
public interface PictureLikeService extends IService<PictureLike> {

    /**
     * 点赞图片（登录用户；图片需当前可读；重复点赞幂等）
     *
     * @param pictureId 图片 id
     * @param loginUser 登录用户
     * @return 是否成功
     */
    boolean doLike(Long pictureId, User loginUser);

    /**
     * 取消点赞（未点赞时幂等）
     *
     * @param pictureId 图片 id
     * @param loginUser 登录用户
     * @return 是否成功
     */
    boolean cancelLike(Long pictureId, User loginUser);

    /**
     * 批量查询「这批图片里我点赞了哪些」，用于列表页一次渲染全部红心。
     * 未登录、未传 id 均返回空集合。
     *
     * @param pictureIds 图片 id 列表
     * @param loginUser  登录用户，可为 null
     * @return 其中已被该用户点赞的图片 id 列表
     */
    List<Long> listLikedPictureIds(List<Long> pictureIds, User loginUser);

    /**
     * 分页查询当前用户已点赞且仍可见的图片，按点赞时间倒序。
     * 可见性规则与 {@code PictureServiceImpl#checkPictureReadable} 一致。
     *
     * @param pageRequest 分页请求
     * @param loginUser   登录用户
     * @return 图片视图分页
     */
    Page<PictureVO> listMyLikedPictures(PageRequest pageRequest, User loginUser);

    /**
     * 统计某用户收到的点赞总数（获赞）：名下未删除图片被点赞的次数。
     *
     * @param userId 图片作者 id
     * @return 获赞总数
     */
    long getReceivedLikeCount(Long userId);
}
