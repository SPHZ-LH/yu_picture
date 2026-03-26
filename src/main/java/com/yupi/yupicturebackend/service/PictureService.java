package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.model.dto.picture.*;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author SPHZ
 * @description 针对表【picture(图片)】的数据库操作Service
 * @createDate 2025-12-03 00:51:07
 */
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     * request 请求为 null 则为上传，否则为修改
     *
     * @param inputSoures          输入源
     * @param pictureUploadRequest 图片上传请求
     * @param loginUser            登入用户
     * @return 图片视图
     */
    PictureVO uploadPicture(Object inputSoures, PictureUploadRequest pictureUploadRequest, User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest 批量抓取图片请求
     * @param loginUser                   登入用户
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);

    /**
     * 根据图片id删除图片
     *
     * @param pictureId 图片id
     * @param loginUser 登入用户
     */
    void deletePicture(long pictureId, User loginUser);

    /**
     * 根据图片id列表批量删除图片
     *
     * @param pictureIds 图片id列表
     * @param loginUser  登入用户
     */
    void deletePictureBatch(List<Long> pictureIds, User loginUser);

    /**
     * 根据图片id编辑图片信息
     *
     * @param pictureEditRequest 图片编辑请求
     * @param loginUser          登入用户
     */
    void editPicture(PictureEditRequest pictureEditRequest, User loginUser);

    /**
     * 获取查询条件
     *
     * @param pictureQueryRequest 查询请求
     * @return 查询条件
     */
    LambdaQueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 根据图片获取图片视图
     *
     * @param picture 图片
     * @return 图片视图
     */
    PictureVO getPictureVO(Picture picture);

    /**
     * 根据图片视图获取图片
     *
     * @param pictureVO 图片视图
     * @return 图片
     */
    Picture getPicture(PictureVO pictureVO);

    /**
     * 根据图片分页获取图片视图分页
     *
     * @param picturePage 图片分页
     * @return 图片视图分页
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage);

    /**
     * 图片校验
     *
     * @param picture 图片
     */
    void validPicture(Picture picture);

    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核请求
     * @param loginUser            登入用户
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 根据权限填充图片审核参数
     *
     * @param picture   图片
     * @param loginUser 登入用户
     */
    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 根据查询条件查询图片视图列表（缓存）
     *
     * @param pictureQueryRequest 图片查询请求
     * @return 图片视图列表
     */
    Page<PictureVO> listPictureVOByPageWithCache(PictureQueryRequest pictureQueryRequest);

    /**
     * 根据老图的url地址删除COS对象存储的图片
     *
     * @param oldPicture 老图
     */
    @Async
    void clearPictureFile(Picture oldPicture);

    /**
     * 根据老图URL列表删除COS对象存储的图片
     *
     * @param listPicture 图片列表
     */
    @Async
    void clearPictureFiles(List<Picture> listPicture);

    /**
     * 校验图片权限
     *
     * @param loginUser 登入用户
     * @param picture   图片
     */
    void checkPictureAuth(User loginUser, Picture picture);

    /**
     * 根据颜色搜索图片
     *
     * @param spaceId   空间id
     * @param picColor  图片颜色
     * @param loginUser 登入用户
     * @return 图片视图列表
     */
    List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser);

    /**
     * 批量编辑图片
     *
     * @param pictureEditByBatchRequest 批量编辑图片请求
     * @param loginUser                 登入用户
     */
    @Transactional(rollbackFor = Exception.class)
    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);
}
