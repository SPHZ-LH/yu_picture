package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.mapper.VideoMapper;
import com.yupi.yupicturebackend.model.dto.space.SpaceUsageSummary;
import com.yupi.yupicturebackend.model.dto.video.VideoQueryRequest;
import com.yupi.yupicturebackend.model.dto.video.VideoReviewRequest;
import com.yupi.yupicturebackend.model.dto.video.VideoUploadRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.entity.Video;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.model.vo.VideoVO;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.service.UserService;
import com.yupi.yupicturebackend.service.VideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author SPHZ
 * @description 针对表【video(视频/动态壁纸)】的数据库操作Service实现
 */
@Slf4j
@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    /**
     * 单个视频体积上限。30MB 够放 10~15 秒 1080p H.264，再大就该走前端直传而不是后端中转了
     */
    private static final long MAX_VIDEO_SIZE = 50L * 1024 * 1024;

    /**
     * 封面体积上限。封面按视频原始帧尺寸绘制（宽高要以它的云端实测值为准），
     * 4K 帧的 JPEG 可达数 MB，因此不能沿用头像的 2MB；前端会逐级降质压进这个上限
     */
    private static final long MAX_COVER_SIZE = 5L * 1024 * 1024;

    /**
     * 时长上限（秒）。时长只来自客户端读到的 metadata，这里只做量级校验挡脏数据，
     * 真正的成本闸门是上面的体积上限
     */
    private static final int MAX_VIDEO_DURATION = 600;

    /**
     * 公共图库按用户的视频容量与条数上限。公共图库没有空间配额兜底
     * （PictureServiceImpl#uploadPicture 只在 spaceId 非空时校验），而视频单个体积是图片的十几倍
     */
    private static final long PUBLIC_VIDEO_MAX_SIZE = 200L * 1024 * 1024;
    private static final long PUBLIC_VIDEO_MAX_COUNT = 20;

    /**
     * 下载链接有效期（分钟）：302 一跳即用，不需要更长
     */
    private static final int DOWNLOAD_URL_EXPIRE_MINUTES = 5;

    private static final List<String> ALLOW_VIDEO_FORMAT_LIST = Arrays.asList("mp4");
    private static final List<String> ALLOW_COVER_FORMAT_LIST = Arrays.asList("jpg", "jpeg", "png", "webp");

    /**
     * 排序字段白名单：sortField 来自请求体，直接拼进 ORDER BY 是注入风险点
     */
    private static final Map<String, SFunction<Video, ?>> SORT_FIELD_MAP = new HashMap<>();

    static {
        SORT_FIELD_MAP.put("createTime", Video::getCreateTime);
        SORT_FIELD_MAP.put("editTime", Video::getEditTime);
        SORT_FIELD_MAP.put("name", Video::getName);
        SORT_FIELD_MAP.put("videoSize", Video::getVideoSize);
        SORT_FIELD_MAP.put("videoWidth", Video::getVideoWidth);
        SORT_FIELD_MAP.put("videoHeight", Video::getVideoHeight);
        SORT_FIELD_MAP.put("videoScale", Video::getVideoScale);
        SORT_FIELD_MAP.put("videoDuration", Video::getVideoDuration);
        SORT_FIELD_MAP.put("videoFormat", Video::getVideoFormat);
        SORT_FIELD_MAP.put("downloadCount", Video::getDownloadCount);
    }

    private static final Set<String> ALLOWED_SORT_ORDERS = new HashSet<>(Arrays.asList("ascend", "descend"));

    @Resource
    private CosManager cosManager;
    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoVO uploadVideo(MultipartFile videoFile, MultipartFile coverFile,
                               VideoUploadRequest videoUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        ThrowUtils.throwIf(videoUploadRequest == null, ErrorCode.PARAMS_ERROR);
        validVideoFile(videoFile);
        validCoverFile(coverFile);
        Integer duration = videoUploadRequest.getDuration();
        ThrowUtils.throwIf(duration == null || duration <= 0 || duration > MAX_VIDEO_DURATION,
                ErrorCode.PARAMS_ERROR, "视频时长无效，请重新上传");

        // 校验上传目标空间（为空表示公共图库），与图片的归属规则一致
        Long targetSpaceId = videoUploadRequest.getSpaceId();
        Space targetSpace = null;
        if (targetSpaceId != null) {
            targetSpace = spaceService.getById(targetSpaceId);
            ThrowUtils.throwIf(targetSpace == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 上传需要编辑者及以上；只读成员只能看。角色判定见根 docs/adr/0002
            spaceUserService.checkPermission(targetSpace, loginUser, SpaceRoleEnum.EDITOR,
                    "无权限在该空间上传视频");
        }
        long videoSize = videoFile.getSize();
        checkUploadQuota(targetSpace, loginUser.getId(), videoSize);

        // 目录前缀单独开一个 video/ 顶层：桶级生命周期规则与成本归集都按前缀走
        String uploadPathPrefix = targetSpaceId == null
                ? String.format("video/public/%s", loginUser.getId())
                : String.format("video/space/%s/%s", targetSpaceId, loginUser.getId());
        String uuid = RandomUtil.randomString(16);
        String videoKey = String.format("%s/%s_%s.mp4", uploadPathPrefix, DateUtil.formatDate(new Date()), uuid);
        String coverKey = videoKey.substring(0, videoKey.lastIndexOf('.')) + "_cover.jpg";

        // 先传封面再传视频：封面要拿数据万象的实测宽高，失败时留在桶里的只是一个几十 KB 的孤儿
        ImageInfo coverInfo;
        File coverTemp = null;
        File videoTemp = null;
        try {
            coverTemp = toTempFile(coverFile, "video-cover-");
            coverInfo = cosManager.putImageObjectWithInfo(coverKey, coverTemp);
            videoTemp = toTempFile(videoFile, "video-");
            cosManager.putObject(videoKey, videoTemp);
        } catch (Exception e) {
            log.error("视频上传到对象存储失败, key = {}", videoKey, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            deleteTempFile(coverTemp);
            deleteTempFile(videoTemp);
        }
        Integer coverWidth = coverInfo.getWidth();
        Integer coverHeight = coverInfo.getHeight();
        ThrowUtils.throwIf(coverWidth == null || coverHeight == null || coverWidth <= 0 || coverHeight <= 0,
                ErrorCode.SYSTEM_ERROR, "封面信息获取失败，请重新上传");

        Video video = new Video();
        video.setUrl(cosManager.buildObjectUrl(videoKey));
        video.setCoverUrl(cosManager.buildObjectUrl(coverKey));
        video.setName(StrUtil.isNotBlank(videoUploadRequest.getName())
                ? videoUploadRequest.getName() : FileUtil.mainName(videoFile.getOriginalFilename()));
        video.setIntroduction(videoUploadRequest.getIntroduction());
        video.setCategory(videoUploadRequest.getCategory());
        video.setTags(JSONUtil.toJsonStr(videoUploadRequest.getTags()));
        video.setVideoSize(videoSize);
        // 宽高取自封面的云端实测值：封面由视频帧绘出，尺寸与视频一致，且客户端无法伪造
        video.setVideoWidth(coverWidth);
        video.setVideoHeight(coverHeight);
        video.setVideoScale(NumberUtil.round(coverWidth * 1.0 / coverHeight, 2).doubleValue());
        video.setVideoDuration(duration);
        video.setVideoFormat("mp4");
        video.setUserId(loginUser.getId());
        video.setSpaceId(targetSpaceId);
        // 空间内容不进公共图库、只有成员能看，上传即通过；公共图库仍按用户角色走审核流（见根 docs/adr/0002）
        if (targetSpace != null) {
            video.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        } else {
            fillReviewParams(video, loginUser);
        }
        validVideo(video);

        boolean result = this.save(video);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "视频上传失败");
        if (targetSpaceId != null) {
            spaceService.updateSpaceUsage(targetSpaceId, videoSize, 1);
        }
        return getVideoVO(video);
    }

    /**
     * 视频文件校验：体积 + 后缀 + 魔数。
     * <p>
     * 魔数只认 ftyp box（偏移 4~7）。不进一步校验 brand：ISO BMFF 允许 free/mdat 等 box
     * 排在前面，白名单 brand 会误伤正常文件，而"改名混进对象存储"这一档已经被 ftyp 挡住了。
     */
    private void validVideoFile(MultipartFile videoFile) {
        ThrowUtils.throwIf(videoFile == null || videoFile.isEmpty(), ErrorCode.PARAMS_ERROR, "视频不能为空");
        ThrowUtils.throwIf(videoFile.getSize() > MAX_VIDEO_SIZE, ErrorCode.PARAMS_ERROR, "视频大小不能超过 50M");
        String suffix = FileUtil.getSuffix(videoFile.getOriginalFilename());
        ThrowUtils.throwIf(!ALLOW_VIDEO_FORMAT_LIST.contains(suffix), ErrorCode.PARAMS_ERROR, "仅支持 mp4 视频");
        byte[] header = readHeader(videoFile, 8);
        ThrowUtils.throwIf(!(header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p'),
                ErrorCode.PARAMS_ERROR, "文件内容与 mp4 格式不符");
    }

    /**
     * 封面文件校验：体积 + 后缀 + 魔数（与头像上传同一套口径，见 docs/adr/0009）
     */
    private void validCoverFile(MultipartFile coverFile) {
        ThrowUtils.throwIf(coverFile == null || coverFile.isEmpty(), ErrorCode.PARAMS_ERROR, "封面不能为空");
        ThrowUtils.throwIf(coverFile.getSize() > MAX_COVER_SIZE, ErrorCode.PARAMS_ERROR, "封面大小不能超过 5M");
        String suffix = FileUtil.getSuffix(coverFile.getOriginalFilename());
        ThrowUtils.throwIf(!ALLOW_COVER_FORMAT_LIST.contains(suffix), ErrorCode.PARAMS_ERROR, "封面格式错误");
        byte[] header = readHeader(coverFile, 12);
        boolean matched;
        switch (suffix) {
            case "jpeg":
            case "jpg":
                matched = header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF;
                break;
            case "png":
                matched = header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47;
                break;
            default:
                matched = header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                        && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
        }
        ThrowUtils.throwIf(!matched, ErrorCode.PARAMS_ERROR, "封面内容与扩展名不匹配");
    }

    private byte[] readHeader(MultipartFile multipartFile, int length) {
        byte[] header = new byte[length];
        try (InputStream in = multipartFile.getInputStream()) {
            int read = in.read(header);
            ThrowUtils.throwIf(read < length, ErrorCode.PARAMS_ERROR, "文件过小，无法识别格式");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件读取失败");
        }
        return header;
    }

    /**
     * 上传配额闸门。空间视频复用空间字节配额；公共图库没有配额，按用户单独设限。
     */
    private void checkUploadQuota(Space targetSpace, Long userId, long videoSize) {
        if (targetSpace != null) {
            long usedSize = nullToZero(targetSpace.getTotalSize()) + videoSize;
            long usedCount = nullToZero(targetSpace.getTotalCount()) + 1;
            ThrowUtils.throwIf(targetSpace.getMaxSize() != null && targetSpace.getMaxSize() > 0
                            && usedSize > targetSpace.getMaxSize(),
                    ErrorCode.OPERATION_ERROR, "空间存储空间不足，请清理图片或升级空间");
            ThrowUtils.throwIf(targetSpace.getMaxCount() != null && targetSpace.getMaxCount() > 0
                            && usedCount > targetSpace.getMaxCount(),
                    ErrorCode.OPERATION_ERROR, "空间图片数量已达上限，请清理图片或升级空间");
            return;
        }
        SpaceUsageSummary usage = baseMapper.selectUserPublicUsage(userId);
        long usedSize = usage == null ? 0 : nullToZero(usage.getTotalSize());
        long usedCount = usage == null ? 0 : nullToZero(usage.getTotalCount());
        ThrowUtils.throwIf(usedSize + videoSize > PUBLIC_VIDEO_MAX_SIZE,
                ErrorCode.OPERATION_ERROR, "公共图库视频容量已达上限");
        ThrowUtils.throwIf(usedCount + 1 > PUBLIC_VIDEO_MAX_COUNT,
                ErrorCode.OPERATION_ERROR, "公共图库视频数量已达上限");
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private File toTempFile(MultipartFile multipartFile, String prefix) {
        File file = null;
        try {
            file = File.createTempFile(prefix, ".tmp");
            multipartFile.transferTo(file);
            return file;
        } catch (IOException e) {
            if (file != null && !file.delete()) {
                log.error("临时文件清理失败, filepath = {}", file.getAbsolutePath());
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件处理失败");
        }
    }

    private void deleteTempFile(File file) {
        if (file != null && !file.delete()) {
            log.error("临时文件删除失败, filepath = {}", file.getAbsolutePath());
        }
    }

    @Override
    public boolean deleteVideo(Video oldVideo) {
        ThrowUtils.throwIf(oldVideo == null || oldVideo.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = this.removeById(oldVideo.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "视频删除失败");
        // 与图片口径一致：只逻辑删除记录，对象存储文件保留（见 docs/adr/0001 的"旧文件不删"约定）
        if (oldVideo.getSpaceId() != null) {
            spaceService.updateSpaceUsage(oldVideo.getSpaceId(), -nullToZero(oldVideo.getVideoSize()), -1);
        }
        return true;
    }

    @Override
    public String downloadVideo(Long videoId, User loginUser) {
        ThrowUtils.throwIf(videoId == null || videoId <= 0, ErrorCode.PARAMS_ERROR);
        Video video = this.getById(videoId);
        this.checkVideoReadable(video, loginUser);
        int updated = baseMapper.incrementDownloadCount(videoId);
        ThrowUtils.throwIf(updated <= 0, ErrorCode.OPERATION_ERROR, "下载计数失败");
        String filename = StrUtil.blankToDefault(video.getName(), "wallpaper") + ".mp4";
        return cosManager.buildDownloadUrl(cosManager.extractObjectKey(video.getUrl()),
                filename, DOWNLOAD_URL_EXPIRE_MINUTES);
    }

    @Override
    public void checkVideoReadable(Video video, User loginUser) {
        ThrowUtils.throwIf(video == null, ErrorCode.NOT_FOUND_ERROR, "视频不存在");
        // 公共图库：仅过审视频可读。未过审统一按不存在返回，避免泄露审核中视频的存在性
        if (video.getSpaceId() == null) {
            ThrowUtils.throwIf(video.getReviewStatus() == null
                            || video.getReviewStatus() != PictureReviewStatusEnum.PASS.getValue(),
                    ErrorCode.NOT_FOUND_ERROR, "视频不存在");
            return;
        }
        Space space = spaceService.getById(video.getSpaceId());
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        spaceUserService.checkPermission(space, loginUser, SpaceRoleEnum.VIEWER, "无权限查看该空间的视频");
    }

    @Override
    public boolean hasEditPermission(Video video, User loginUser) {
        if (video == null || loginUser == null) {
            return false;
        }
        if (video.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
            return true;
        }
        if (video.getSpaceId() == null) {
            return false;
        }
        Space space = spaceService.getById(video.getSpaceId());
        return space != null
                && spaceUserService.getEffectiveRole(space, loginUser) == SpaceRoleEnum.ADMIN;
    }

    @Override
    public LambdaQueryWrapper<Video> getQueryWrapper(VideoQueryRequest videoQueryRequest) {
        if (videoQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = videoQueryRequest.getId();
        String name = videoQueryRequest.getName();
        String introduction = videoQueryRequest.getIntroduction();
        String category = videoQueryRequest.getCategory();
        List<String> tags = videoQueryRequest.getTags();
        Long videoSize = videoQueryRequest.getVideoSize();
        Integer videoWidth = videoQueryRequest.getVideoWidth();
        Integer videoHeight = videoQueryRequest.getVideoHeight();
        Double videoScale = videoQueryRequest.getVideoScale();
        String orientation = videoQueryRequest.getOrientation();
        Long minPixels = videoQueryRequest.getMinPixels();
        String videoFormat = videoQueryRequest.getVideoFormat();
        String searchText = videoQueryRequest.getSearchText();
        Long userId = videoQueryRequest.getUserId();
        Integer reviewStatus = videoQueryRequest.getReviewStatus();
        String reviewMessage = videoQueryRequest.getReviewMessage();
        Long reviewerId = videoQueryRequest.getReviewerId();
        Long spaceId = videoQueryRequest.getSpaceId();
        Boolean nullSpaceFlag = videoQueryRequest.getNullSpaceFlag();
        String sortField = videoQueryRequest.getSortField();
        String sortOrder = videoQueryRequest.getSortOrder();

        LambdaQueryWrapper<Video> queryWrapper = new LambdaQueryWrapper<>();
        if (ObjUtil.isNotNull(searchText)) {
            queryWrapper.and(qW -> qW.like(Video::getName, searchText)
                    .or().like(Video::getIntroduction, searchText));
        }
        queryWrapper.eq(ObjUtil.isNotNull(id), Video::getId, id)
                .like(StrUtil.isNotBlank(name), Video::getName, name)
                .like(StrUtil.isNotBlank(videoFormat), Video::getVideoFormat, videoFormat)
                .eq(StrUtil.isNotBlank(category), Video::getCategory, category)
                .eq(ObjUtil.isNotNull(videoSize), Video::getVideoSize, videoSize)
                .eq(ObjUtil.isNotNull(videoWidth), Video::getVideoWidth, videoWidth)
                .eq(ObjUtil.isNotNull(videoHeight), Video::getVideoHeight, videoHeight)
                .eq(ObjUtil.isNotNull(userId), Video::getUserId, userId)
                .eq(ObjUtil.isNotNull(videoScale), Video::getVideoScale, videoScale)
                .eq(ObjUtil.isNotEmpty(reviewStatus), Video::getReviewStatus, reviewStatus)
                .like(StrUtil.isNotBlank(reviewMessage), Video::getReviewMessage, reviewMessage)
                .eq(ObjUtil.isNotEmpty(reviewerId), Video::getReviewerId, reviewerId)
                .eq(ObjUtil.isNotNull(spaceId), Video::getSpaceId, spaceId)
                // 仅查询公共图库（spaceId 为空的视频）
                .isNull(Boolean.TRUE.equals(nullSpaceFlag), Video::getSpaceId);
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like(Video::getTags, "\"" + tag + "\"");
            }
        }
        // 屏幕方向筛选（壁纸场景）：横屏=宽>=高（电脑壁纸），竖屏=宽<高（手机壁纸）
        // 库内列名为驼峰（map-underscore-to-camel-case: false），apply 原生片段需与实际列名一致
        if (StrUtil.isNotBlank(orientation)) {
            if ("landscape".equals(orientation)) {
                queryWrapper.apply("videoWidth >= videoHeight");
            } else if ("portrait".equals(orientation)) {
                queryWrapper.apply("videoWidth < videoHeight");
            } else {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法的屏幕方向");
            }
        }
        // 清晰度档位筛选：总像素下限，横竖视频统一（{0} 预编译占位防注入）
        if (minPixels != null && minPixels > 0) {
            queryWrapper.apply("videoWidth * videoHeight >= {0}", minPixels);
        }
        if (StrUtil.isNotBlank(sortField)) {
            SFunction<Video, ?> column = SORT_FIELD_MAP.get(sortField);
            ThrowUtils.throwIf(column == null, ErrorCode.PARAMS_ERROR, "不支持的排序字段");
            ThrowUtils.throwIf(!ALLOWED_SORT_ORDERS.contains(sortOrder), ErrorCode.PARAMS_ERROR, "不支持的排序方向");
            queryWrapper.orderBy(true, "ascend".equals(sortOrder), column);
        } else {
            queryWrapper.orderByDesc(Video::getCreateTime);
        }
        return queryWrapper;
    }

    @Override
    public VideoVO getVideoVO(Video video) {
        if (video == null) {
            return null;
        }
        VideoVO videoVO = VideoVO.objToVo(video);
        Long userId = video.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            videoVO.setUser(userService.getUserVO(user));
        }
        return videoVO;
    }

    @Override
    public Page<VideoVO> getVideoVOPage(Page<Video> videoPage) {
        List<Video> videoList = videoPage.getRecords();
        Page<VideoVO> videoVOPage = new Page<>(videoPage.getCurrent(), videoPage.getSize(), videoPage.getTotal());
        if (CollUtil.isEmpty(videoList)) {
            return videoVOPage;
        }
        List<VideoVO> videoVOList = videoList.stream().map(VideoVO::objToVo).collect(Collectors.toList());
        // 作者信息一次批量查出（只取 UserVO 需要的列，不拖 userPassword 等无关字段）
        Set<Long> userIdSet = videoList.stream().map(Video::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.list(new LambdaQueryWrapper<User>()
                        .select(User::getId, User::getUserAccount, User::getUserName, User::getUserAvatar,
                                User::getUserProfile, User::getUserRole, User::getCreateTime)
                        .in(User::getId, userIdSet))
                .stream().collect(Collectors.groupingBy(User::getId));
        videoVOList.forEach(videoVO -> {
            UserVO userVO = userService.getUserVO(
                    userIdUserListMap.containsKey(videoVO.getUserId())
                            ? userIdUserListMap.get(videoVO.getUserId()).get(0) : null);
            videoVO.setUser(userVO);
        });
        videoVOPage.setRecords(videoVOList);
        return videoVOPage;
    }

    @Override
    public void validVideo(Video video) {
        ThrowUtils.throwIf(video == null, ErrorCode.PARAMS_ERROR, "视频不存在");
        Long id = video.getId();
        String url = video.getUrl();
        String name = video.getName();
        String introduction = video.getIntroduction();
        // 新增时 id 为空，只在有值时参与校验；url 与名称是入库必填项
        ThrowUtils.throwIf(StrUtil.isNotBlank(url) && url.length() > 512, ErrorCode.PARAMS_ERROR, "url 过长");
        ThrowUtils.throwIf(StrUtil.isBlank(name) || name.length() > 128, ErrorCode.PARAMS_ERROR, "视频名称不合法");
        ThrowUtils.throwIf(StrUtil.isNotBlank(introduction) && introduction.length() > 500, ErrorCode.PARAMS_ERROR, "简介过长");
        ThrowUtils.throwIf(id != null && id <= 0, ErrorCode.PARAMS_ERROR, "视频 id 不合法");
    }

    @Override
    public void doVideoReview(VideoReviewRequest videoReviewRequest, User loginUser) {
        ThrowUtils.throwIf(videoReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = videoReviewRequest.getId();
        Integer reviewStatus = videoReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Video oldVideo = this.getById(id);
        ThrowUtils.throwIf(oldVideo == null, ErrorCode.NOT_FOUND_ERROR);
        if (reviewStatus.equals(oldVideo.getReviewStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        Video updateVideo = new Video();
        updateVideo.setId(id);
        updateVideo.setReviewStatus(reviewStatus);
        updateVideo.setReviewMessage(videoReviewRequest.getReviewMessage());
        updateVideo.setReviewerId(loginUser.getId());
        updateVideo.setReviewTime(new Date());
        boolean result = this.updateById(updateVideo);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public void fillReviewParams(Video video, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            video.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            video.setReviewerId(loginUser.getId());
            video.setReviewMessage("管理员自动过审");
            video.setReviewTime(new Date());
        } else {
            // 非管理员，创建或编辑都要改为待审核
            video.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }
}
