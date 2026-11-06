package com.yupi.yupicturebackend.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.DeleteRequest;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.video.VideoEditRequest;
import com.yupi.yupicturebackend.model.dto.video.VideoQueryRequest;
import com.yupi.yupicturebackend.model.dto.video.VideoReviewRequest;
import com.yupi.yupicturebackend.model.dto.video.VideoUploadRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.entity.Video;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.vo.VideoVO;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.service.UserService;
import com.yupi.yupicturebackend.service.VideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * 动态壁纸（mp4）管理。
 * <p>
 * 与图片域刻意保持独立：视频不支持裁剪/扩展/以图搜图/色系，也不进多级缓存，
 * 设计取舍见 docs/adr/0011。
 */
@Slf4j
@RestController
@RequestMapping("/video")
public class VideoController {

    @Resource
    private VideoService videoService;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    /**
     * 上传动态壁纸：视频与前端抽好的封面在同一次请求内提交
     *
     * @param multipartFile      视频文件（mp4）
     * @param coverFile          封面文件（jpg）
     * @param videoUploadRequest 元信息（名称、分类、标签、时长、目标空间）
     */
    @PostMapping("/upload")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<VideoVO> uploadVideo(@RequestPart("file") MultipartFile multipartFile,
                                             @RequestPart("coverFile") MultipartFile coverFile,
                                             VideoUploadRequest videoUploadRequest,
                                             HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        VideoVO videoVO = videoService.uploadVideo(multipartFile, coverFile, videoUploadRequest, loginUser);
        return ResultUtils.success(videoVO);
    }

    /**
     * 根据 id 删除视频（上传者本人和管理员）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> deleteVideo(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR, "删除请求发送失败");
        Long id = deleteRequest.getId();
        Video video = videoService.getById(id);
        ThrowUtils.throwIf(video == null, ErrorCode.NOT_FOUND_ERROR);
        User loginUser = userService.getLoginUser(request);
        // 上传者本人、站点管理员，或所在空间的管理员可删。无权者统一回「不存在」，不泄露视频存在性
        if (videoService.hasEditPermission(video, loginUser)) {
            boolean result = videoService.deleteVideo(video);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            return ResultUtils.success(true);
        }
        throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
    }

    /**
     * 编辑视频元信息（名称/简介/分类/标签，不含画面编辑）
     */
    @PostMapping("/edit")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> editVideo(@RequestBody VideoEditRequest videoEditRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(videoEditRequest == null || videoEditRequest.getId() <= 0, ErrorCode.PARAMS_ERROR, "编辑请求发送失败");
        Long videoId = videoEditRequest.getId();
        Video oldVideo = videoService.getById(videoId);
        ThrowUtils.throwIf(oldVideo == null, ErrorCode.NOT_FOUND_ERROR, "视频不存在");
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(!videoService.hasEditPermission(oldVideo, loginUser), ErrorCode.NO_AUTH_ERROR);

        Video video = new Video();
        BeanUtil.copyProperties(videoEditRequest, video);
        video.setTags(JSONUtil.toJsonStr(videoEditRequest.getTags()));
        video.setEditTime(new Date());
        videoService.validVideo(video);
        // 编辑会让公共图库视频重新进入审核流程，与图片口径一致
        videoService.fillReviewParams(video, loginUser);

        boolean result = videoService.updateById(video);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据查询条件查询视频列表（仅管理员）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Video>> listVideoByPage(@RequestBody VideoQueryRequest videoQueryRequest) {
        ThrowUtils.throwIf(videoQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int current = videoQueryRequest.getCurrent();
        int pageSize = videoQueryRequest.getPageSize();
        Page<Video> videoPage = videoService.page(new Page<>(current, pageSize), videoService.getQueryWrapper(videoQueryRequest));
        return ResultUtils.success(videoPage);
    }

    /**
     * 根据查询条件查询视频视图列表。
     * <p>
     * 未指定 spaceId 时仅查询公共图库；指定 spaceId 时仅空间所有者和管理员可查看。
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<VideoVO>> listVideoVOByPage(@RequestBody VideoQueryRequest videoQueryRequest,
                                                        HttpServletRequest request) {
        ThrowUtils.throwIf(videoQueryRequest == null || videoQueryRequest.getPageSize() > 20, ErrorCode.PARAMS_ERROR);
        int current = videoQueryRequest.getCurrent();
        int pageSize = videoQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);

        if (videoQueryRequest.getSpaceId() != null) {
            Space space = spaceService.getById(videoQueryRequest.getSpaceId());
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            User loginUser = userService.getLoginUser(request);
            spaceUserService.checkPermission(space, loginUser, SpaceRoleEnum.VIEWER, "无权限查看该空间的视频");
        } else {
            videoQueryRequest.setNullSpaceFlag(true);
        }
        // 普通用户默认只能查看已过审的数据
        videoQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        LambdaQueryWrapper<Video> queryWrapper = videoService.getQueryWrapper(videoQueryRequest);
        // 列表只查卡片展示所需的列，简介、审核信息等大字段不拖出来
        queryWrapper.select(Video::getId, Video::getUrl, Video::getCoverUrl,
                Video::getName, Video::getCategory, Video::getTags, Video::getVideoSize,
                Video::getVideoWidth, Video::getVideoHeight, Video::getVideoDuration,
                Video::getVideoFormat, Video::getDownloadCount,
                Video::getUserId, Video::getSpaceId);
        Page<Video> videoPage = videoService.page(new Page<>(current, pageSize), queryWrapper);
        Page<VideoVO> videoVOPage = videoService.getVideoVOPage(videoPage);
        return ResultUtils.success(videoVOPage);
    }

    /**
     * 根据 id 获取视频视图（普通用户）。
     * <p>
     * 与下载一致地走可读性校验：公共图库未过审的视频按"不存在"返回，
     * 否则审核闸门只挡住了列表，直链详情仍然能翻出来看。
     */
    @GetMapping("/get/vo")
    public BaseResponse<VideoVO> getVideoVOById(@RequestParam long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Video video = videoService.getById(id);
        User loginUser = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        videoService.checkVideoReadable(video, loginUser);
        return ResultUtils.success(videoService.getVideoVO(video));
    }

    /**
     * 下载视频：计数后 302 到带文件名的限时下载链接。
     * <p>
     * 不强制登录，仅读取登录态用于空间视频鉴权（getLoginUser 未登录会抛异常，故直接取会话属性）。
     */
    @GetMapping("/download/{videoId}")
    public void downloadVideo(@PathVariable("videoId") Long videoId,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        ThrowUtils.throwIf(videoId == null || videoId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        String url = videoService.downloadVideo(videoId, loginUser);
        response.sendRedirect(url);
    }

    /**
     * 根据 id 审核视频（管理员，人工审核）
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doVideoReview(@RequestBody VideoReviewRequest videoReviewRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(videoReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        videoService.doVideoReview(videoReviewRequest, loginUser);
        return ResultUtils.success(true);
    }
}
