package com.yupi.yupicturebackend.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.DeleteRequest;
import com.yupi.yupicturebackend.common.PageRequest;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.constant.PictureInfoConstant;
import com.yupi.yupicturebackend.constant.PictureSearchConstant;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.job.PictureCleanupJob;
import com.yupi.yupicturebackend.model.dto.picture.*;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.yupicturebackend.model.entity.PictureTagCategory;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.vo.PictureAiPrefillVO;
import com.yupi.yupicturebackend.model.vo.PictureCollectStatusVO;
import com.yupi.yupicturebackend.model.vo.PictureDownloadRecordVO;
import com.yupi.yupicturebackend.model.vo.PictureDownloadTierVO;
import com.yupi.yupicturebackend.model.vo.PictureExpandResultVO;
import com.yupi.yupicturebackend.model.vo.PictureRankVO;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.service.CollectService;
import com.yupi.yupicturebackend.service.PictureDownloadLogService;
import com.yupi.yupicturebackend.service.PictureLikeService;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceActivityService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {
    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private SpaceUserService spaceUserService;
    @Resource
    private CollectService collectService;
    @Resource
    private PictureLikeService pictureLikeService;
    @Resource
    private PictureDownloadLogService pictureDownloadLogService;
    @Resource
    private SpaceActivityService spaceActivityService;
    @Resource
    private PictureCleanupJob pictureCleanupJob;


    /**
     * 文件上传图片（可重新上传）
     */
    @PostMapping("/upload")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<PictureVO> uploadPicture(@RequestPart("file") MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * Url上传图片（可重新上传）
     */
    @PostMapping("/upload/url")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<PictureVO> uploadPictureByUrl(@RequestBody PictureUploadRequest pictureUploadRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }


    /**
     * 根据id删除图片（上传用户和管理员）
     *
     * @param deleteRequest 删除请求
     * @param request       前端传来的 session
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR, "删除请求发送失败");
        // 判断图片是否存在
        Long id = deleteRequest.getId();
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);

        // 上传者本人、站点管理员，或所在空间的管理员可删。无权者统一回「不存在」，不泄露图片存在性
        User loginUser = userService.getLoginUser(request);
        if (pictureService.hasEditPermission(picture, loginUser)) {
            boolean result = pictureService.deletePicture(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 空间动态：删除事件（公共图库不记录）
            spaceActivityService.recordActivity(picture.getSpaceId(), loginUser.getId(),
                    SpaceActivityService.ACTION_DELETE, picture.getId());
            return ResultUtils.success(true);
        }
        throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
    }

    /**
     * 更新图片（仅管理员）
     *
     * @param pictureUpdateRequest 跟新请求
     * @param request              前端发送的 session
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0, ErrorCode.PARAMS_ERROR, "更新请求发送失败");
        // 判断图片是否存在
        Long pictureID = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(pictureID);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 数据关联
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureUpdateRequest, picture);
        String jsonStr = JSONUtil.toJsonStr(pictureUpdateRequest.getTags());
        picture.setTags(jsonStr);
        // 图片校验
        pictureService.validPicture(picture);
        // 补充审核参数
        User loginUser = userService.getLoginUser(request);
        pictureService.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 空间动态：信息编辑事件（公共图库不记录）
        spaceActivityService.recordActivity(oldPicture.getSpaceId(), loginUser.getId(),
                SpaceActivityService.ACTION_EDIT, oldPicture.getId());
        // 编辑可能改变审核状态等可见性属性，同步以图搜图图库
        pictureService.syncPictureGalleryById(pictureID);
        return ResultUtils.success(true);
    }


    /**
     * 编辑图片
     *
     * @param pictureEditRequest 编辑图片请求
     * @param request            前端出来的 session
     */
    @PostMapping("/edit")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditRequest == null || pictureEditRequest.getId() <= 0, ErrorCode.PARAMS_ERROR, "编辑请求发送失败");
        // 判断图片是否存在
        Long pictureId = pictureEditRequest.getId();
        Picture oldPicture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 判断是否为上传图片用户或管理员
        User loginUser = userService.getLoginUser(request);
        Long loginUserId = loginUser.getId();
        Long userId = oldPicture.getUserId();
        ThrowUtils.throwIf(!userId.equals(loginUserId) && !userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);

        // 关联数据
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureEditRequest, picture);
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        picture.setEditTime(new Date());

        // 图片校验
        pictureService.validPicture(picture);

        // 补充审核参数
        pictureService.fillReviewParams(picture, loginUser);

        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 编辑会触发重新审核等可见性变化，同步以图搜图图库
        pictureService.syncPictureGalleryById(pictureId);
        return ResultUtils.success(true);
    }

    /**
     * 画面编辑（裁剪/等比缩放/90°旋转/翻转，上传者、空间所有者或管理员）
     * <p>
     * 像素处理走数据万象云上处理（ADR 0001）：产物另存为新对象并原地更新图片记录，
     * 旧文件保留作回滚；公共图库图片编辑后重新进入审核流程。
     *
     * @param pictureTransformRequest 编辑参数（裁剪坐标基于旋转+翻转后的预览图）
     * @param request                 前端传来的 session
     * @return 更新后的图片视图
     */
    @PostMapping("/transform")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<PictureVO> transformPicture(@RequestBody PictureTransformRequest pictureTransformRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureTransformRequest == null || pictureTransformRequest.getId() == null
                || pictureTransformRequest.getId() <= 0, ErrorCode.PARAMS_ERROR, "编辑请求发送失败");
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.transformPicture(pictureTransformRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 提交画面扩展任务（作者 / 空间所有者 / 管理员）
     *
     * @param pictureExpandSubmitRequest 图片 id 与目标宽高比
     * @param request                    前端传来的 session
     * @return 任务 id
     */
    @PostMapping("/expand/submit")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<String> submitExpand(@RequestBody PictureExpandSubmitRequest pictureExpandSubmitRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureService.submitExpand(pictureExpandSubmitRequest, loginUser));
    }

    /**
     * 查询画面扩展任务状态（taskId 不可猜测，故不再附加图片权限校验）
     *
     * @param taskId 任务 id
     * @return 任务状态与结果图地址
     */
    @GetMapping("/expand/result")
    public BaseResponse<PictureExpandResultVO> getExpandResult(String taskId) {
        return ResultUtils.success(pictureService.queryExpandResult(taskId));
    }

    /**
     * 保存画面扩展结果为该图片当前展示内容（作者 / 空间所有者 / 管理员）
     *
     * @param pictureExpandSaveRequest 图片 id 与任务 id
     * @param request                  前端传来的 session
     * @return 更新后的图片视图
     */
    @PostMapping("/expand/save")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<PictureVO> saveExpand(@RequestBody PictureExpandSaveRequest pictureExpandSaveRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureService.saveExpand(pictureExpandSaveRequest, loginUser));
    }

    /**
     * 下载图片：下载量原子自增（不去重）后 302 重定向到 COS 地址，浏览器直接从 COS 下载。
     * tier 为空下载原图；指定 8k/4k/2k/1080p 且原图大于该档时重定向到等比缩小的衍生档
     * （imageMogr2 实时处理，见 docs/adr/0017）。允许匿名下载公共图库过审图片；
     * 空间图片仅空间所有者或管理员。每次成功下载追加一条下载日志。
     *
     * @param pictureId 图片 id
     * @param tier      分辨率档位，可空（原图）
     */
    @GetMapping("/download/{pictureId}")
    public void downloadPicture(@PathVariable("pictureId") Long pictureId,
                                @RequestParam(value = "tier", required = false) String tier,
                                HttpServletRequest request,
                                HttpServletResponse response) throws IOException {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        // 下载不强制登录，仅读取登录态用于空间图片鉴权（getLoginUser 未登录会抛异常，故直接取会话属性）
        User loginUser = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        String url = pictureService.downloadPicture(pictureId, loginUser, tier);
        pictureDownloadLogService.recordDownload(pictureId, loginUser == null ? null : loginUser.getId());
        response.sendRedirect(url);
    }

    /**
     * 图片可用的下载档位列表（详情页「选择分辨率下载」行）：
     * 只含严格小于原图的档位，匿名可访问（与公共浏览口径一致）。
     *
     * @param pictureId 图片 id
     */
    @GetMapping("/download/tiers")
    public BaseResponse<List<PictureDownloadTierVO>> listDownloadTiers(
            @RequestParam("pictureId") Long pictureId) {
        return ResultUtils.success(pictureService.listDownloadTiers(pictureId));
    }

    /**
     * 设置/取消编辑精选（管理员，逐张设置不互斥；首页「编辑精选」tab 数据源）
     *
     * @param featuredSetRequest 图片 id 与是否精选
     */
    @PostMapping("/featured/set")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> setFeatured(@RequestBody PictureFeaturedSetRequest featuredSetRequest) {
        ThrowUtils.throwIf(featuredSetRequest == null || featuredSetRequest.getPictureId() == null
                || featuredSetRequest.getIsFeatured() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(pictureService.setFeatured(
                featuredSetRequest.getPictureId(), featuredSetRequest.getIsFeatured()));
    }

    /**
     * 设置今日一图（管理员，全站至多一张，设新图自动取代旧图）
     */
    @PostMapping("/daily/set")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> setDailyPick(@RequestBody PictureDailySetRequest dailySetRequest) {
        ThrowUtils.throwIf(dailySetRequest == null || dailySetRequest.getPictureId() == null,
                ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(pictureService.setDailyPick(dailySetRequest.getPictureId()));
    }

    /**
     * 今日一图（公开，首页 hero 右侧）：未设置时 data 为 null
     */
    @GetMapping("/daily")
    public BaseResponse<PictureVO> getDailyPickPicture() {
        return ResultUtils.success(pictureService.getDailyPickPicture());
    }

    /**
     * 提交草稿进入审核流（作者本人或管理员）：仅 DRAFT 态图片可提交
     *
     * @param submitRequest 图片 id（复用 DeleteRequest 的 id 字段）
     * @param request       前端传来的 session
     */
    @PostMapping("/draft/submit")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> submitDraft(@RequestBody DeleteRequest submitRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(submitRequest == null || submitRequest.getId() == null || submitRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureService.submitDraft(submitRequest.getId(), loginUser));
    }

    /**
     * 下载排行榜（公共图库口径）：按榜单周期统计窗口内下载量倒序，可按分类筛选。
     * period=day/week/month（滚动窗口，见 docs/adr/0013），匿名可访问。
     *
     * @param pictureRankQueryRequest 榜单查询请求（周期/分类/分页）
     */
    @PostMapping("/ranking")
    public BaseResponse<Page<PictureRankVO>> listPictureRanking(
            @RequestBody PictureRankQueryRequest pictureRankQueryRequest) {
        return ResultUtils.success(pictureDownloadLogService.listPictureRanking(pictureRankQueryRequest));
    }

    /**
     * 我的下载历史：按下载时间倒序，图片已删除的条目自动消失（登录用户）。
     *
     * @param pageRequest 分页请求
     * @param request     前端传来的 session
     */
    @PostMapping("/download/history/my")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Page<PictureDownloadRecordVO>> listMyDownloadHistory(
            @RequestBody PageRequest pageRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureDownloadLogService.listMyDownloadHistory(pageRequest, loginUser));
    }

    // ==================== 点赞（喜欢） ====================

    /**
     * 点赞图片（登录用户；图片需当前可读；重复点赞幂等）
     */
    @PostMapping("/like")
    public BaseResponse<Boolean> doLike(@RequestBody PictureLikeRequest pictureLikeRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(pictureLikeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureLikeService.doLike(pictureLikeRequest.getPictureId(), loginUser));
    }

    /**
     * 取消点赞（未点赞时幂等）
     */
    @PostMapping("/like/cancel")
    public BaseResponse<Boolean> cancelLike(@RequestBody PictureLikeRequest pictureLikeRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(pictureLikeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureLikeService.cancelLike(pictureLikeRequest.getPictureId(), loginUser));
    }

    /**
     * 批量查询「这批图片里我点赞了哪些」（列表页一次渲染全部红心；匿名返回空集合）
     */
    @PostMapping("/like/status/batch")
    public BaseResponse<List<Long>> listLikedPictureIds(
            @RequestBody PictureLikeBatchRequest pictureLikeBatchRequest, HttpServletRequest request) {
        User loginUser = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        return ResultUtils.success(pictureLikeService.listLikedPictureIds(
                pictureLikeBatchRequest == null ? null : pictureLikeBatchRequest.getPictureIds(), loginUser));
    }

    /**
     * 我的点赞列表（按点赞时间倒序，只含仍可见的图片；登录用户）
     */
    @PostMapping("/like/list/my")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Page<PictureVO>> listMyLikedPictures(@RequestBody PageRequest pageRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureLikeService.listMyLikedPictures(pageRequest, loginUser));
    }

    /**
     * 某用户收到的点赞总数（获赞，公开主页数据区）
     *
     * @param userId 图片作者 id
     */
    @GetMapping("/like/received/count")
    public BaseResponse<Long> getReceivedLikeCount(@RequestParam("userId") Long userId) {
        return ResultUtils.success(pictureLikeService.getReceivedLikeCount(userId));
    }

    // ==================== 公开收藏 ====================

    /**
     * 某用户的公开收藏列表：仅当对方开启「收藏公开」时返回内容，
     * 且只含公共图库过审图（未开启返回空页，前端按 UserVO.collectPublic 决定是否展示 tab）
     *
     * @param publicCollectRequest 被查看用户 id + 分页
     */
    @PostMapping("/collect/list/public")
    public BaseResponse<Page<PictureVO>> listPublicCollectPictures(
            @RequestBody PicturePublicCollectRequest publicCollectRequest) {
        ThrowUtils.throwIf(publicCollectRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(collectService.listPublicCollectPictures(publicCollectRequest));
    }

    /**
     * 色系回填：为 colorFamily 为空的图片补提取主色/色系（管理员运维接口）。
     * 上传与编辑已自动提取，本接口用于历史数据。
     */
    @PostMapping("/color/backfill")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> backfillColor() {
        return ResultUtils.success(pictureService.backfillColorInfo());
    }

    /**
     * 清理逻辑删除图片（管理员运维接口）：把删除时刻超过保留期的记录连 COS 原图/缩略图
     * 对象、相似图库条目、收藏关系一并清掉。定时任务每天凌晨自动跑，本接口用于手动触发；
     * graceDays 传 0 可立即清理刚删除的记录（仅建议在测试环境）。
     *
     * @param graceDays 保留期（天），负数按 0 处理
     * @param maxCount  单批上限，1~500
     */
    @PostMapping("/cleanup/deleted")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<String> cleanupDeletedPictures(
            @RequestParam(defaultValue = "7") int graceDays,
            @RequestParam(defaultValue = "100") int maxCount) {
        PictureCleanupJob.CleanupSummary summary = pictureCleanupJob.cleanupDeletedPictures(graceDays, maxCount);
        return ResultUtils.success(summary.describe());
    }

    /**
     * 收藏图片（登录用户；重复收藏幂等，返回最新收藏状态）
     *
     * @param pictureCollectRequest 收藏请求
     * @param request               前端传来的 session
     */
    @PostMapping("/collect")
    public BaseResponse<PictureCollectStatusVO> doPictureCollect(@RequestBody PictureCollectRequest pictureCollectRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(pictureCollectRequest == null || pictureCollectRequest.getPictureId() == null
                || pictureCollectRequest.getPictureId() <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        PictureCollectStatusVO statusVO = collectService.doPictureCollect(pictureCollectRequest.getPictureId(), loginUser);
        return ResultUtils.success(statusVO);
    }

    /**
     * 取消收藏图片（登录用户；未收藏时幂等，返回最新收藏状态）
     *
     * @param pictureCollectRequest 取消收藏请求
     * @param request               前端传来的 session
     */
    @PostMapping("/collect/cancel")
    public BaseResponse<PictureCollectStatusVO> cancelPictureCollect(@RequestBody PictureCollectRequest pictureCollectRequest,
                                                                     HttpServletRequest request) {
        ThrowUtils.throwIf(pictureCollectRequest == null || pictureCollectRequest.getPictureId() == null
                || pictureCollectRequest.getPictureId() <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        PictureCollectStatusVO statusVO = collectService.cancelPictureCollect(pictureCollectRequest.getPictureId(), loginUser);
        return ResultUtils.success(statusVO);
    }

    /**
     * 查询收藏状态：当前用户是否已收藏 + 最新收藏量（未登录用户 collected=false）
     *
     * @param pictureId 图片 id
     * @param request   前端传来的 session
     */
    @GetMapping("/collect/status")
    public BaseResponse<PictureCollectStatusVO> getPictureCollectStatus(@RequestParam("pictureId") long pictureId,
                                                                        HttpServletRequest request) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        PictureCollectStatusVO statusVO = collectService.getPictureCollectStatus(pictureId, loginUser);
        return ResultUtils.success(statusVO);
    }

    /**
     * 批量查询收藏状态：这批图片里「我收藏了哪些」，用于列表页一次性渲染全部星标，
     * 免去每张图块各发一次请求。未登录返回空集合（星标状态是列表的附属信息，不因此报错）。
     *
     * @param pictureCollectBatchRequest 图片 id 列表
     * @param request                    前端传来的 session
     * @return 其中已被当前用户收藏的图片 id 列表
     */
    @PostMapping("/collect/status/batch")
    public BaseResponse<List<Long>> listCollectedPictureIds(@RequestBody PictureCollectBatchRequest pictureCollectBatchRequest,
                                                            HttpServletRequest request) {
        ThrowUtils.throwIf(pictureCollectBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        return ResultUtils.success(
                collectService.listCollectedPictureIds(pictureCollectBatchRequest.getPictureIds(), loginUser));
    }

    /**
     * 分页查询我的收藏（登录用户，按收藏时间倒序，最近收藏的排在前面）
     *
     * @param pageRequest 分页请求
     * @param request     前端传来的 session
     */
    @PostMapping("/collect/list/page/vo")
    public BaseResponse<Page<PictureVO>> listMyCollectPictureVOByPage(@RequestBody PageRequest pageRequest,
                                                                     HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(collectService.listMyCollectPictures(pageRequest, loginUser));
    }

    /**
     * 根据查询条件查询图片列表（仅管理员）
     *
     * @param pictureQueryRequest 查询请求
     * @return 图片列表
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        ThrowUtils.throwIf(pictureQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 根据查询条件查询图片视图列表
     *
     * 未指定 spaceId 时仅查询公共图库；指定 spaceId 时仅空间所有者和管理员可查看
     *
     * @param pictureQueryRequest 查询请求
     * @return 图片视图列表
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureQueryRequest == null || pictureQueryRequest.getPageSize() > 20, ErrorCode.PARAMS_ERROR);
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);

        // 空间图片仅空间成员和站点管理员可查看；未指定空间时默认查询公共图库
        if (pictureQueryRequest.getSpaceId() != null) {
            Space space = spaceService.getById(pictureQueryRequest.getSpaceId());
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            User loginUser = userService.getLoginUser(request);
            spaceUserService.checkPermission(space, loginUser, SpaceRoleEnum.VIEWER, "无权限查看该空间的图片");
        } else {
            pictureQueryRequest.setNullSpaceFlag(true);
        }

        // 普通用户默认只能查看已过审的数据；查自己的作品（个人中心「作品」tab）时放开
        // 审核态过滤，否则自己的草稿/待审/被拒作品永远不可见（docs/adr/0017）
        User listViewer = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        boolean querySelf = listViewer != null && pictureQueryRequest.getUserId() != null
                && pictureQueryRequest.getUserId().equals(listViewer.getId());
        if (!querySelf) {
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        }
        LambdaQueryWrapper<Picture> queryWrapper = pictureService.getQueryWrapper(pictureQueryRequest);
        // 列表只查卡片展示所需的列：简介、审核信息等大字段在列表场景用不到，拖出来纯属浪费 IO 和带宽
        queryWrapper.select(Picture::getId, Picture::getUrl, Picture::getThumbnailUrl,
                Picture::getName, Picture::getCategory, Picture::getTags, Picture::getPicSize,
                Picture::getPicWidth, Picture::getPicHeight, Picture::getPicFormat,
                Picture::getDownloadCount, Picture::getCollectCount,
                Picture::getMainColor, Picture::getColorFamily,
                Picture::getUserId, Picture::getSpaceId);
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), queryWrapper);
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage);
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 根据id获取图片视图（普通用户）
     *
     * @param id 图片 id
     * @return 图片视图
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(@RequestParam long id, HttpServletRequest request) {

        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 可选登录：公共图库图片匿名可读，空间图片要求成员身份。
        // getLoginUser 未登录会抛异常，所以直接取会话属性
        User loginUser = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        // 走多级缓存：L1（本地 Caffeine）-> L2（Redis）-> 数据库
        PictureVO pictureVO = pictureService.getPictureVOById(id, loginUser);
        ThrowUtils.throwIf(pictureVO == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(pictureVO);

    }

    /**
     * 根据id获取图片（管理员）
     *
     * @param id 图片 id
     * @return 图片
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(@RequestParam long id) {

        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);

        return ResultUtils.success(picture);
    }

    /**
     * 根据id审核图片（管理员）
     *
     * @param pictureReviewRequest 图片审核请求
     * @param request              前端发送的 session
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }


    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        // 词表唯一定义在 PictureInfoConstant：AI 预填的提示词与结果校验读同一份，避免两处漂移
        pictureTagCategory.setTagList(PictureInfoConstant.TAG_LIST);
        pictureTagCategory.setCategoryList(PictureInfoConstant.CATEGORY_LIST);
        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * AI 读图预填图片信息（作者 / 空间所有者 / 管理员）
     * <p>
     * 只返回建议、不落库：前端填进表单，人确认后仍走 /picture/edit（docs/adr/0012）。
     * 读不出可用内容时 data 为空，前端跳过即可，不视为错误。
     *
     * @param pictureAiPrefillRequest 图片 id
     * @param request                 前端传来的 session
     * @return 预填建议（可能为空）
     */
    @PostMapping("/ai/prefill")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<PictureAiPrefillVO> aiPrefillPictureInfo(
            @RequestBody PictureAiPrefillRequest pictureAiPrefillRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(
                pictureService.aiPrefillPictureInfo(pictureAiPrefillRequest.getPictureId(), loginUser));
    }

    /**
     * 以图搜图：上传本地图片、粘贴图片链接或指定库内图片 id 作为检索输入，返回按相似度降序的图片列表。
     * 查询图只是检索输入，不会进入图库。
     * <p>
     * 图片链接由服务端抓取，故对匿名请求同样只允许公网 http/https 地址（防 SSRF）。
     *
     * @param file      本地图片文件（与 pictureId / imageUrl 三选一）
     * @param pictureId 库内图片 id（与 file / imageUrl 三选一）
     * @param imageUrl  图片的网络地址（与 file / pictureId 三选一）
     * @return 按相似度降序排列的图片视图列表
     */
    @PostMapping("/search/picture_by_image")
    public BaseResponse<List<PictureVO>> searchPictureByImage(
            @RequestParam(value = "file", required = false) MultipartFile multipartFile,
            @RequestParam(value = "pictureId", required = false) Long pictureId,
            @RequestParam(value = "imageUrl", required = false) String imageUrl) {
        ThrowUtils.throwIf(multipartFile == null && pictureId == null && StrUtil.isBlank(imageUrl),
                ErrorCode.PARAMS_ERROR, "请上传图片、粘贴图片链接或指定图片 id");
        ThrowUtils.throwIf(multipartFile != null
                        && multipartFile.getSize() > PictureSearchConstant.MAX_QUERY_IMAGE_BYTES,
                ErrorCode.PARAMS_ERROR, PictureSearchConstant.QUERY_IMAGE_OVER_SIZE_MESSAGE);
        List<PictureVO> result = pictureService.searchPictureByImage(multipartFile, pictureId, imageUrl, 30);
        return ResultUtils.success(result);
    }

    /**
     * 以图搜图图库全量同步（管理员）：存量图片或入库失败的图片重新入库，
     * 首次调用会自动尝试开通图库。日常上传/删除已有增量同步，无需手动调用。
     */
    @PostMapping("/gallery/sync")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> syncPictureGallery() {
        return ResultUtils.success(pictureService.syncAllPicturesToGallery());
    }
}
