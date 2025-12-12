package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.model.dto.picture.PictureQueryRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureUploadRequest;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

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
     * @param multipartFile        需要上传的图片
     * @param pictureUploadRequest 图片上传请求
     * @param loginUser            登入用户
     * @return 图片视图
     */
    PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser);

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
}
