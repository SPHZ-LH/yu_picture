package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.common.PageRequest;
import com.yupi.yupicturebackend.model.dto.picture.PicturePublicCollectRequest;
import com.yupi.yupicturebackend.model.entity.Collect;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.PictureCollectStatusVO;
import com.yupi.yupicturebackend.model.vo.PictureVO;

import java.util.List;

/**
 * @author SPHZ
 * @description 针对表【collect(用户收藏图片关系)】的数据库操作Service
 * @createDate 2026-09-13
 */
public interface CollectService extends IService<Collect> {

    /**
     * 收藏图片：写入收藏关系并使图片收藏量 +1（重复收藏幂等，直接返回当前状态）。
     * <p>
     * 图片可见性沿用 {@code PictureService#checkPictureReadable}：
     * 公共图库仅过审图片可收藏，空间图片仅空间所有者或管理员。
     *
     * @param pictureId 图片 id
     * @param loginUser 登录用户
     * @return 收藏状态（含最新收藏量）
     */
    PictureCollectStatusVO doPictureCollect(Long pictureId, User loginUser);

    /**
     * 取消收藏图片：删除收藏关系并使图片收藏量 -1（未收藏时幂等）。
     * 图片已被删除时同样允许取消（清理残留关系，不再回调整计数）。
     *
     * @param pictureId 图片 id
     * @param loginUser 登录用户
     * @return 收藏状态（含最新收藏量）
     */
    PictureCollectStatusVO cancelPictureCollect(Long pictureId, User loginUser);

    /**
     * 查询当前用户对图片的收藏状态（未登录用户 collected=false）。
     *
     * @param pictureId 图片 id
     * @param loginUser 登录用户，可为 null
     * @return 收藏状态（含最新收藏量）
     */
    PictureCollectStatusVO getPictureCollectStatus(Long pictureId, User loginUser);

    /**
     * 分页查询当前用户的收藏图片，按收藏时间倒序（最近收藏的排在前面）。
     * <p>
     * 只返回对该用户而言仍可读的图片：图片被编辑后重回待审核期间不会出现在收藏列表里。
     * 可见性规则与 {@code PictureService#checkPictureReadable} 一致。
     *
     * @param pageRequest 分页请求
     * @param loginUser   登录用户
     * @return 收藏图片视图分页
     */
    Page<PictureVO> listMyCollectPictures(PageRequest pageRequest, User loginUser);

    /**
     * 批量查询「这批图片里我收藏了哪些」，用于列表页一次渲染全部星标。
     * <p>
     * 未登录、未传 id 均返回空集合：星标状态是列表的附属信息，不该让整页请求失败。
     *
     * @param pictureIds 图片 id 列表
     * @param loginUser  登录用户，可为 null
     * @return 其中已被该用户收藏的图片 id 列表
     */
    List<Long> listCollectedPictureIds(List<Long> pictureIds, User loginUser);

    /**
     * 某用户的公开收藏列表（公开主页「收藏」tab）：仅当对方开启收藏公开时返回内容，
     * 且只含公共图库过审图（空间图片无论开关都不公开）。未开启返回空页。
     *
     * @param publicCollectRequest 被查看用户 id + 分页
     * @return 公开收藏图片视图分页
     */
    Page<PictureVO> listPublicCollectPictures(PicturePublicCollectRequest publicCollectRequest);
}
