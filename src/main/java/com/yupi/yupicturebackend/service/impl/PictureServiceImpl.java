package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.ImageSearchManager;
import com.yupi.yupicturebackend.manager.MultiLevelCacheManager;
import com.yupi.yupicturebackend.manager.OutPaintingManager;
import com.yupi.yupicturebackend.manager.PictureInfoAiManager;
import com.yupi.yupicturebackend.manager.RemoteImageManager;
import com.yupi.yupicturebackend.manager.upload.FilePictureUpload;
import com.yupi.yupicturebackend.manager.upload.PictureUploadTemplate;
import com.yupi.yupicturebackend.manager.upload.UrlPictureUpload;
import com.yupi.yupicturebackend.manager.CosManager;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIUploadResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import com.yupi.yupicturebackend.model.dto.picture.PictureExpandSaveRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureExpandSubmitRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureQueryRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureReviewRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureTransformRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureUploadByBatchRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureUploadRequest;
import com.yupi.yupicturebackend.model.entity.Collect;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.vo.PictureAiPrefillVO;
import com.yupi.yupicturebackend.model.vo.PictureDownloadTierVO;
import com.yupi.yupicturebackend.model.vo.PictureExpandResultVO;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.constant.PointsConstant;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.PointsService;
import com.yupi.yupicturebackend.mapper.CollectMapper;
import com.yupi.yupicturebackend.mapper.PictureMapper;
import com.yupi.yupicturebackend.service.SpaceActivityService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.util.ColorExtractor;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author SPHZ
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-12-03 00:51:07
 */
@Slf4j
@Service
public class PictureServiceImpl extends CacheAwareServiceImpl<PictureMapper, Picture> implements PictureService {

    @Resource
    private UserService userService;

    @Resource
    private PointsService pointsService;

    @Resource
    private SpaceActivityService spaceActivityService;

    @Resource
    private OutPaintingManager outPaintingManager;

    @Resource
    private PictureInfoAiManager pictureInfoAiManager;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private CosManager cosManager;

    @Resource
    private ImageSearchManager imageSearchManager;

    @Resource
    private RemoteImageManager remoteImageManager;

    @Resource
    private CollectMapper collectMapper;

    /**
     * 允许排序的字段白名单。
     * <p>
     * sortField / sortOrder 来自请求体，直接拼进 ORDER BY 是 SQL 注入风险点，
     * 所以必须走白名单。取值与前端 {@code yu-picture-frontend/src/constant/picture.ts}
     * 的 PIC_SORT_FIELD_OPTIONS 保持一致。
     * <p>
     * 注意 {@link LambdaQueryWrapper} 的 {@code orderBy} 只接受 lambda 列引用、不接受字符串，
     * 所以这里存的是「请求字段名 -> SFunction 列引用」的映射，而不是简单字符串集合。
     */
    private static final Map<String, SFunction<Picture, ?>> SORT_FIELD_MAP = new HashMap<>();

    static {
        SORT_FIELD_MAP.put("createTime", Picture::getCreateTime);
        SORT_FIELD_MAP.put("editTime", Picture::getEditTime);
        SORT_FIELD_MAP.put("name", Picture::getName);
        SORT_FIELD_MAP.put("picSize", Picture::getPicSize);
        SORT_FIELD_MAP.put("picWidth", Picture::getPicWidth);
        SORT_FIELD_MAP.put("picHeight", Picture::getPicHeight);
        SORT_FIELD_MAP.put("picScale", Picture::getPicScale);
        SORT_FIELD_MAP.put("picFormat", Picture::getPicFormat);
        SORT_FIELD_MAP.put("downloadCount", Picture::getDownloadCount);
        SORT_FIELD_MAP.put("collectCount", Picture::getCollectCount);
    }

    /**
     * 允许的排序方向
     */
    private static final Set<String> ALLOWED_SORT_ORDERS = new HashSet<>(Arrays.asList("ascend", "descend"));

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PictureVO uploadPicture(Object inputSoures, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        // 用于判断是新增还是更新图片
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新图片，需要校验图片是否存在
        Picture oldPicture = null;
        if (pictureId != null) {
            oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人或管理员可编辑
            ThrowUtils.throwIf(!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        }

        // 校验上传目标空间（为空表示公共图库）
        Long targetSpaceId = pictureUploadRequest != null ? pictureUploadRequest.getSpaceId() : null;
        Space targetSpace = null;
        if (targetSpaceId != null) {
            targetSpace = spaceService.getById(targetSpaceId);
            ThrowUtils.throwIf(targetSpace == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 上传需要编辑者及以上；只读成员只能看。角色判定见根 docs/adr/0002
            spaceUserService.checkPermission(targetSpace, loginUser, SpaceRoleEnum.EDITOR,
                    "无权限在该空间上传图片");
        }

        // 上传图片，得到信息
        // 空间上传按空间划分目录，公共图库按照用户 id 划分目录
        String uploadPathPrefix = targetSpaceId == null
                ? String.format("public/%s", loginUser.getId())
                : String.format("space/%s/%s", targetSpaceId, loginUser.getId());

        // 根据输入源类型选择不同的上传模板
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSoures instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSoures, uploadPathPrefix);

        // 构造要入库的图片信息
        Picture picture = new Picture();
        BeanUtil.copyProperties(uploadPictureResult, picture);
        picture.setUserId(loginUser.getId());
        picture.setSpaceId(targetSpaceId);
        // 色系提取：增强能力，失败留空不阻断上传
        fillColorInfo(picture, uploadPictureResult.getThumbnailUrl(), uploadPictureResult.getUrl());
        // 判断上传请求是否有图片名称
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        // 审核参数：空间内容不进公共图库、只有成员能看，上传即通过；公共图库仍按用户角色走审核流。
        // 与「空间图片编辑免审」同一条规则，见根 docs/adr/0002
        if (targetSpace != null) {
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        } else if (pictureUploadRequest != null && Boolean.TRUE.equals(pictureUploadRequest.getIsDraft())) {
            // 草稿：落库为 DRAFT 态，不进审核流、对他人不可见，作者经 /picture/draft/submit 提交（docs/adr/0017）
            picture.setReviewStatus(PictureReviewStatusEnum.DRAFT.getValue());
        } else {
            this.fillReviewParams(picture, loginUser);
        }
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 空间配额校验：同一空间重新上传时，旧图占用会被释放，按净增量校验
        if (targetSpace != null) {
            boolean sameSpaceReUpload = oldPicture != null && targetSpaceId.equals(oldPicture.getSpaceId());
            long oldSize = sameSpaceReUpload && oldPicture.getPicSize() != null ? oldPicture.getPicSize() : 0L;
            long usedSizeAfter = targetSpace.getTotalSize() - oldSize + uploadPictureResult.getPicSize();
            long usedCountAfter = targetSpace.getTotalCount() - (sameSpaceReUpload ? 1 : 0) + 1;
            ThrowUtils.throwIf(targetSpace.getMaxSize() != null && targetSpace.getMaxSize() > 0 && usedSizeAfter > targetSpace.getMaxSize(),
                    ErrorCode.OPERATION_ERROR, "空间存储空间不足，请清理图片或升级空间");
            ThrowUtils.throwIf(targetSpace.getMaxCount() != null && targetSpace.getMaxCount() > 0 && usedCountAfter > targetSpace.getMaxCount(),
                    ErrorCode.OPERATION_ERROR, "空间图片数量已达上限，请清理图片或升级空间");
        }
        boolean result = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
        // 维护空间用量：旧图从原空间释放，新图计入目标空间
        if (oldPicture != null && oldPicture.getSpaceId() != null) {
            spaceService.updateSpaceUsage(oldPicture.getSpaceId(), -oldPicture.getPicSize(), -1);
        }
        if (targetSpaceId != null) {
            spaceService.updateSpaceUsage(targetSpaceId, uploadPictureResult.getPicSize(), 1);
        }
        // 空间动态：上传事件（公共图库不记录，见 docs/adr/0017）
        spaceActivityService.recordActivity(targetSpaceId, loginUser.getId(),
                SpaceActivityService.ACTION_UPLOAD, picture.getId());
        // 以图搜图图库同步：图库是派生数据，按可见性规则对齐（失败只告警不阻断上传）
        syncPictureGallery(picture);
        return this.getPictureVO(picture);
    }

    /**
     * 提取并填充图片主色/色系。拉取 COS 缩略图（优先）做像素分桶；
     * 仅允许本 COS 域名下的 url（防 SSRF），任何失败都静默留空，不阻断主流程。
     */
    /**
     * 色调参数归一化：滑杆值 0-200（100 = 原图），换算为 imageMogr2 的 [-100,100] 偏移。
     * 未传（null）与 100 都返回 0（不调整）；越界报参数错误。
     */
    private int normalizeTonalParam(Integer value) {
        if (value == null || value == 100) {
            return 0;
        }
        ThrowUtils.throwIf(value < 0 || value > 200, ErrorCode.PARAMS_ERROR, "色调参数需在 0-200 之间");
        return value - 100;
    }

    private void fillColorInfo(Picture picture, String thumbnailUrl, String url) {
        String target = StrUtil.blankToDefault(thumbnailUrl, url);
        try {
            if (StrUtil.isBlank(target)) {
                return;
            }
            String key = cosManager.extractObjectKey(target);
            String safeUrl = cosManager.buildObjectUrl(key);
            if (!safeUrl.equals(target)) {
                // 非本 COS 域名（如批量导入的外链），不抓取
                return;
            }
            byte[] imageBytes = HttpUtil.downloadBytes(safeUrl);
            String[] color = ColorExtractor.extract(imageBytes);
            if (color != null) {
                picture.setMainColor(color[0]);
                picture.setColorFamily(color[1]);
            } else {
                log.warn("色系提取失败（无法解析的图片）, url={}", safeUrl);
            }
            // 主色板（详情页「画面色彩」色点）：与主色同一次下载内提取，失败静默
            List<String> palette = ColorExtractor.extractPalette(imageBytes, COLOR_PALETTE_SIZE);
            if (!palette.isEmpty()) {
                picture.setPalette(JSONUtil.toJsonStr(palette));
            }
        } catch (Exception e) {
            log.warn("色系提取失败, url={}", target, e);
        }
    }

    @Override
    public int backfillColorInfo() {
        final int maxRounds = 500;
        int updated = 0;
        // 每轮只取 colorFamily 或 palette 为空的前 20 条；回填后这些行离开结果集，故固定查第一页
        for (int round = 0; round < maxRounds; round++) {
            Page<Picture> page = this.page(new Page<>(1, 20, false),
                    new LambdaQueryWrapper<Picture>().and(w -> w.isNull(Picture::getColorFamily)
                            .or().isNull(Picture::getPalette)));
            if (CollUtil.isEmpty(page.getRecords())) {
                return updated;
            }
            for (Picture picture : page.getRecords()) {
                Picture patch = new Picture();
                patch.setId(picture.getId());
                fillColorInfo(patch, picture.getThumbnailUrl(), picture.getUrl());
                if (patch.getColorFamily() == null) {
                    // 解析失败也要留痕：标记为 unknown 避免死循环（下次回填不再选中）
                    patch.setColorFamily("unknown");
                }
                if (patch.getPalette() == null) {
                    // 主色板同理：空数组留痕，让该行离开回填结果集
                    patch.setPalette(COLOR_PALETTE_EMPTY);
                }
                if (this.updateById(patch)) {
                    updated++;
                }
            }
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePicture(Picture oldPicture) {
        ThrowUtils.throwIf(oldPicture == null || oldPicture.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = this.removeById(oldPicture.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片删除失败");
        // 同步扣减空间用量
        if (oldPicture.getSpaceId() != null) {
            spaceService.updateSpaceUsage(oldPicture.getSpaceId(), -oldPicture.getPicSize(), -1);
        }
        // 相似图库同步移除该条目（派生数据，失败只告警）
        try {
            imageSearchManager.deleteImage(oldPicture.getId());
        } catch (Exception e) {
            log.warn("相似图库条目删除失败, pictureId={}", oldPicture.getId(), e);
        }
        // 收藏关系随图片删除一并清理（收藏量记录在被删图片上，随之失效）
        collectMapper.delete(new LambdaQueryWrapper<Collect>().eq(Collect::getPictureId, oldPicture.getId()));
        return true;
    }

    /**
     * 图库可见性规则：只有「公共图库（无空间）且已过审」的图片才允许进入相似图库。
     * 私有空间、待审核、已拒绝的图片一律不入库。
     */
    private boolean eligibleForGallery(Picture picture) {
        return picture.getSpaceId() == null
                && picture.getReviewStatus() != null
                && picture.getReviewStatus() == PictureReviewStatusEnum.PASS.getValue();
    }

    /**
     * 使某张图片在相似图库中的条目与可见性规则一致：符合条件则入库，否则移除。
     * <p>
     * 图库是派生数据，任何失败只告警不影响主流程。
     */
    private void syncPictureGallery(Picture picture) {
        try {
            if (eligibleForGallery(picture)) {
                String objectKey = resolveGalleryObjectKey(picture);
                if (isUnsupportedGalleryFormat(objectKey)) {
                    return;
                }
                imageSearchManager.addImage(picture.getId(), cosManager.buildObjectUrl(objectKey));
            } else {
                imageSearchManager.deleteImage(picture.getId());
            }
        } catch (Exception e) {
            log.warn("相似图库同步失败, pictureId={}", picture.getId(), e);
        }
    }

    @Override
    public void syncPictureGalleryById(Long pictureId) {
        if (pictureId == null) {
            return;
        }
        Picture picture = this.getById(pictureId);
        if (picture != null) {
            syncPictureGallery(picture);
        }
    }

    /** 相似图库支持的输入格式有限（PNG/JPG/JPEG/BMP）：无缩略图且原图为 GIF/WebP 时无法入库 */
    private boolean isUnsupportedGalleryFormat(String objectKey) {
        String lower = objectKey.toLowerCase();
        return lower.endsWith(".gif") || lower.endsWith(".webp");
    }

    /** 以图搜图查询图允许的格式 */
    private static final Set<String> ALLOWED_QUERY_FORMATS = Set.of("jpg", "jpeg", "png", "webp");

    /**
     * 计算图片用于图库（入库/检索）的对象 key：优先缩略图。
     * <p>
     * 图库对图片分辨率有上限（超限报 ImageResolutionExceed），而相似检索的
     * 特征提取与分辨率无关，宽 600 的缩略图完全够用，还能省检索流量。
     */
    private String resolveGalleryObjectKey(Picture picture) {
        if (StrUtil.isNotBlank(picture.getThumbnailUrl())) {
            return cosManager.extractObjectKey(picture.getThumbnailUrl());
        }
        return cosManager.extractObjectKey(picture.getUrl());
    }

    @Override
    public List<PictureVO> searchPictureByImage(MultipartFile file, Long pictureId, String imageUrl, long limit) {
        String objectKey;
        boolean isTempQueryObject = false;
        if (pictureId != null) {
            Picture picture = this.getById(pictureId);
            ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            objectKey = resolveGalleryObjectKey(picture);
            ThrowUtils.throwIf(isUnsupportedGalleryFormat(objectKey), ErrorCode.PARAMS_ERROR, "该图片格式暂不支持相似检索");
        } else {
            // 本地文件与远程地址两种输入都先落成本地临时文件，再走同一条路：
            // 临时上传到 search/ 目录（不入图库）→ 用缩略图检索 → 用完即删。
            // 必须先过一遍对象存储：检索服务要能拉到查询图，且查询图分辨率常超图库上限，
            // 与库内图片一致地用宽 600 的缩略图做检索输入
            ThrowUtils.throwIf(file == null && StrUtil.isBlank(imageUrl), ErrorCode.PARAMS_ERROR,
                    "请上传图片、粘贴图片链接或指定图片 id");
            File tempFile = null;
            try {
                String suffix;
                if (file != null) {
                    suffix = StrUtil.nullToEmpty(cn.hutool.core.io.FileUtil.getSuffix(file.getOriginalFilename()))
                            .toLowerCase();
                    ThrowUtils.throwIf(!ALLOWED_QUERY_FORMATS.contains(suffix), ErrorCode.PARAMS_ERROR, "仅支持 jpg/png/webp 图片");
                    tempFile = File.createTempFile("picture-search-", "." + suffix);
                    file.transferTo(tempFile);
                } else {
                    tempFile = File.createTempFile("picture-search-", null);
                    // 后缀由下载到的文件头判定，不信任远程地址声明的 Content-Type
                    suffix = remoteImageManager.downloadToTempFile(imageUrl, tempFile);
                }
                objectKey = String.format("search/%s.%s", RandomUtil.randomString(16), suffix);
                // putPictureObject 会同步生成缩略图，检索用缩略图、原图与缩略图用后即删
                cosManager.putPictureObject(objectKey, tempFile);
                isTempQueryObject = true;
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询图片处理失败");
            } finally {
                if (tempFile != null && !tempFile.delete()) {
                    tempFile.deleteOnExit();
                }
            }
        }

        String searchImageUrl = cosManager.buildObjectUrl(
                isTempQueryObject ? CosManager.getThumbnailPath(objectKey) : objectKey);
        List<String> entityIds;
        try {
            entityIds = imageSearchManager.searchSimilar(searchImageUrl, limit);
        } catch (TencentCloudSDKException e) {
            log.error("相似图检索失败, imageUrl={}", searchImageUrl, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "相似图搜索服务暂不可用，请稍后重试");
        } finally {
            if (isTempQueryObject) {
                try {
                    cosManager.deleteObject(objectKey);
                    cosManager.deleteObject(CosManager.getThumbnailPath(objectKey));
                } catch (Exception e) {
                    log.warn("查询图清理失败, objectKey={}", objectKey, e);
                }
            }
        }

        if (CollUtil.isEmpty(entityIds)) {
            return new ArrayList<>();
        }
        // 检索结果本身按相似度降序；entityId 回查库表，图库中已删除的图片直接跳过
        List<Long> pictureIds = entityIds.stream()
                .filter(StrUtil::isNotBlank)
                .map(Long::valueOf)
                .collect(Collectors.toList());
        if (pictureIds.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Picture> pictureMap = this.listByIds(pictureIds).stream()
                .collect(Collectors.toMap(Picture::getId, p -> p));
        List<PictureVO> result = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        for (String entityId : entityIds) {
            Long id = Long.valueOf(entityId);
            // 按图片 id 去重，防异常数据中的重复条目
            if (!seenIds.add(id)) {
                continue;
            }
            Picture picture = pictureMap.get(id);
            // 兜底过滤：图库万一有漏网的私有/未审条目，结果侧同样不放行
            if (picture == null || !eligibleForGallery(picture)) {
                continue;
            }
            PictureVO pictureVO = PictureVO.objToVo(picture);
            pictureVO.setUser(userService.getUserVOById(picture.getUserId()));
            result.add(pictureVO);
        }
        return result;
    }

    @Override
    public int syncAllPicturesToGallery() {
        // 首次调用先建库（已存在时 ensureGallery 内静默跳过；建库失败则整体无意义，直接返回）
        try {
            imageSearchManager.ensureGallery();
        } catch (TencentCloudSDKException e) {
            log.error("相似图库建库失败, 全量同步中止: {}", e.getMessage());
            return 0;
        }
        List<Picture> pictures = this.list(new LambdaQueryWrapper<Picture>()
                .select(Picture::getId, Picture::getUrl, Picture::getThumbnailUrl,
                        Picture::getSpaceId, Picture::getReviewStatus)
                .eq(Picture::getIsDelete, 0));
        int successCount = 0;
        for (Picture picture : pictures) {
            if (StrUtil.isBlank(picture.getUrl())) {
                continue;
            }
            try {
                if (!eligibleForGallery(picture)) {
                    // 私有空间、未过审的图片不允许被相似检索到，只清不入
                    imageSearchManager.deleteImage(picture.getId());
                    continue;
                }
                String objectKey = resolveGalleryObjectKey(picture);
                if (isUnsupportedGalleryFormat(objectKey)) {
                    continue;
                }
                imageSearchManager.addImage(picture.getId(), cosManager.buildObjectUrl(objectKey));
                successCount++;
            } catch (Exception e) {
                log.warn("存量图片相似图库入库失败, pictureId={}", picture.getId(), e);
            }
        }
        return successCount;
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        String searchText = pictureUploadByBatchRequest.getSearchText();
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        // 格式化数量
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        // 要抓取的地址
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        /*Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }*/
        Elements imgElementList = document.select("img.mimg");
        if (ObjUtil.isNull(imgElementList)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过: {}", fileUrl);
                continue;
            }
            // 处理图片上传地址，防止出现转义问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setPicName(namePrefix + "_" + (uploadCount + 1));
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功, id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    @Override
    public LambdaQueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String orientation = pictureQueryRequest.getOrientation();
        Long minPixels = pictureQueryRequest.getMinPixels();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        Boolean nullSpaceFlag = pictureQueryRequest.getNullSpaceFlag();

        LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<>();
        if (ObjectUtil.isNotNull(searchText)) {
            queryWrapper.and(qW -> qW.like(Picture::getName, searchText)
                    .or().like(ObjectUtil.isNotNull(introduction), Picture::getIntroduction, searchText));
        }
        queryWrapper.eq(ObjectUtil.isNotNull(id), Picture::getId, id)
                .like(ObjectUtil.isNotNull(name), Picture::getName, name)
                .like(ObjectUtil.isNotNull(picFormat), Picture::getPicFormat, picFormat)
                .eq(ObjectUtil.isNotNull(category), Picture::getCategory, category)
                .eq(ObjectUtil.isNotNull(picSize), Picture::getPicSize, picSize)
                .eq(ObjectUtil.isNotNull(picWidth), Picture::getPicWidth, picWidth)
                .eq(ObjectUtil.isNotNull(picHeight), Picture::getPicHeight, picHeight)
                .eq(ObjectUtil.isNotNull(userId), Picture::getUserId, userId)
                .eq(ObjectUtil.isNotNull(picScale), Picture::getPicScale, picScale)
                .eq(ObjUtil.isNotEmpty(reviewStatus), Picture::getReviewStatus, reviewStatus)
                .like(StrUtil.isNotBlank(reviewMessage), Picture::getReviewMessage, reviewMessage)
                .eq(ObjUtil.isNotEmpty(reviewerId), Picture::getReviewerId, reviewerId)
                .eq(ObjectUtil.isNotNull(spaceId), Picture::getSpaceId, spaceId)
                // 仅查询公共图库（spaceId 为空的图片）
                .isNull(Boolean.TRUE.equals(nullSpaceFlag), Picture::getSpaceId);
        if (ObjectUtil.isNotNull(tags)) {
            for (String tag : tags) {
                queryWrapper.like(Picture::getTags, "\"" + tag + "\"");
            }
        }
        // 屏幕方向筛选（壁纸场景）：横屏=宽>=高（电脑壁纸），竖屏=宽<高（手机壁纸）
        // 库内列名为驼峰（picWidth/picHeight），apply 原生片段需与实际列名一致
        if (StrUtil.isNotBlank(orientation)) {
            if ("landscape".equals(orientation)) {
                queryWrapper.apply("picWidth >= picHeight");
            } else if ("portrait".equals(orientation)) {
                queryWrapper.apply("picWidth < picHeight");
            } else {
                ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "非法的屏幕方向");
            }
        }
        // 色系筛选（等值匹配，参数化无注入面）
        String colorFamily = pictureQueryRequest.getColorFamily();
        queryWrapper.eq(StrUtil.isNotBlank(colorFamily), Picture::getColorFamily, colorFamily);
        // 编辑精选筛选（首页「编辑精选」tab）
        queryWrapper.eq(ObjectUtil.isNotNull(pictureQueryRequest.getIsFeatured()),
                Picture::getIsFeatured, pictureQueryRequest.getIsFeatured());
        // 清晰度档位筛选：总像素（宽*高）下限，横竖图统一（{0} 预编译占位防注入）
        if (minPixels != null && minPixels > 0) {
            queryWrapper.apply("picWidth * picHeight >= {0}", minPixels);
        }
        // 排序字段与方向必须走白名单：它们来自请求体，直接拼进 ORDER BY 是 SQL 注入风险点
        if (ObjectUtil.isNotNull(sortField)) {
            SFunction<Picture, ?> column = SORT_FIELD_MAP.get(sortField);
            ThrowUtils.throwIf(column == null, ErrorCode.PARAMS_ERROR, "非法的排序字段");
            String order = StrUtil.isNotBlank(sortOrder) ? sortOrder : "descend";
            ThrowUtils.throwIf(!ALLOWED_SORT_ORDERS.contains(order),
                    ErrorCode.PARAMS_ERROR, "非法的排序方式");
            queryWrapper.orderBy(true, "ascend".equals(order), column);
        }
        return queryWrapper;
    }

    @Override
    public PictureVO getPictureVO(Picture picture) {
        if (picture == null) {
            return null;
        }
        // 封装图片视图
        PictureVO pictureVO = new PictureVO();
        BeanUtil.copyProperties(picture, pictureVO);
        pictureVO.setTags(JSONUtil.toList(picture.getTags(), String.class));

        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    @Override
    public PictureVO getPictureVOById(Long id, User loginUser) {
        if (id == null || id <= 0) {
            return null;
        }
        Picture picture = multiLevelCacheManager.getPicture(id, this::loadPictureFromDb);
        if (picture == null) {
            return null;
        }
        // 空间图片必须是成员才能看。可见性取决于调用者、缓存的却是图片本身，
        // 所以校验只能放在缓存读之后，用缓存里的实体判，不能再回源查一次库。
        //
        // 这里刻意只做空间这一支，不套用完整的 checkPictureReadable：那条规则要求公共图片
        // 必须已过审，而公共图片一经编辑就会重置为待审核——套用之后作者自己将打不开刚编辑的图。
        // 公共图片的过审校验留在下载与收藏路径（checkPictureReadable）上。
        if (picture.getSpaceId() != null) {
            Space space = spaceService.getById(picture.getSpaceId());
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            spaceUserService.checkPermission(space, loginUser, SpaceRoleEnum.VIEWER, "无权限查看该空间的图片");
        }
        return this.buildPictureVO(picture);
    }

    /**
     * 实体转视图并补作者信息（作者同样走多级缓存）
     */
    private PictureVO buildPictureVO(Picture picture) {
        PictureVO pictureVO = PictureVO.objToVo(picture);
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            // 跨 Bean 调用 userService，其缓存才会生效；
            // 若写成同类内部自调用（this.xxx），会被代理绕过而静默失效
            UserVO userVO = userService.getUserVOById(userId);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 从数据库回源图片。
     * <p>
     * 注意：**不要缓存本方法，也不要缓存 getById**。getById 的结果在本类
     * {@link #uploadPicture} 中被用于鉴权判断（「是否图片作者」），
     * 把缓存埋在那层会让鉴权变成缓存依赖，且会污染 MyBatis-Plus 内部的调用语义。
     * 详见 docs/adr/0002。
     */
    private Picture loadPictureFromDb(Long id) {
        return this.getById(id);
    }

    @Override
    public Picture getPicture(PictureVO pictureVO) {
        if (pictureVO == null) {
            return null;
        }
        // 封装图片
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureVO, picture);
        picture.setTags(JSONUtil.toJsonStr(picture.getTags()));
        return picture;
    }

    /**
     * 分页获取图片视图
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        // 只需要查询一次数据库
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息（只取 UserVO 需要的列，不拖 userPassword 等无关字段）
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.list(new LambdaQueryWrapper<User>()
                        .select(User::getId, User::getUserAccount, User::getUserName, User::getUserAvatar,
                                User::getUserProfile, User::getUserRole, User::getCreateTime)
                        .in(User::getId, userIdSet))
                .stream().collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }


    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR, "图片不存在");
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "图片id不存在");
        ThrowUtils.throwIf(StrUtil.isNotBlank(url) && url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        ThrowUtils.throwIf(StrUtil.isNotBlank(introduction) && introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
    }

    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 已是该状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 更新审核状态
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 审核结果改变图库可见性：过审且公共才可被以图搜图检索到，拒绝/回退则移出图库
        this.syncPictureGalleryById(id);
    }

    // ==================== 画面编辑（数据万象云上处理） ====================

    /**
     * imageMogr2 组合多个操作时的实际执行顺序与参数书写顺序无关（实测 cut 裁剪总是最先执行），
     * 所以编辑链路把 flip/rotate/cut/thumbnail 拆成单操作逐步链式执行：
     * 每步一次云上处理调用、顺序完全由代码控制，不依赖其内部顺序。
     * 前端预览（组件内旋转 + 本地镜像）与该链路的最终产物一致由坐标空间约定保证。
     */
    private static final Set<String> ALLOWED_FLIP_VALUES = Set.of("horizontal", "vertical");

    /** 主色板取色数量（详情页「画面色彩」色点数） */
    private static final int COLOR_PALETTE_SIZE = 5;

    /** 主色板提取失败时的留痕标记（与 colorFamily 的 unknown 同一防死循环思路） */
    private static final String COLOR_PALETTE_EMPTY = "[]";

    /**
     * 分辨率档位 → 总像素预算（与清晰度档位阈值一致），key 全小写；
     * 迭代顺序即档位从高到低（8K→4K→2K→1080P）
     */
    private static final Map<String, Long> TIER_PIXEL_BUDGETS = new LinkedHashMap<>() {{
        put("8k", 33177600L);
        put("4k", 8294400L);
        put("2k", 2073600L);
        put("1080p", 921600L);
    }};

    /** 档位展示名（key 全小写） */
    private static final Map<String, String> TIER_DISPLAY_NAMES = Map.of(
            "8k", "8K", "4k", "4K", "2k", "2K", "1080p", "1080P");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PictureVO transformPicture(PictureTransformRequest transformRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        ThrowUtils.throwIf(transformRequest == null || transformRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        Picture oldPicture = this.getById(transformRequest.getId());
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        ThrowUtils.throwIf(StrUtil.isBlank(oldPicture.getUrl()), ErrorCode.PARAMS_ERROR, "图片缺少存储地址");
        checkEditPermission(oldPicture, loginUser);

        // ---- 规范化编辑参数并拼 imageMogr2 规则段 ----
        int rotate = transformRequest.getRotate() == null ? 0 : transformRequest.getRotate();
        ThrowUtils.throwIf(rotate < 0 || rotate > 270 || rotate % 90 != 0,
                ErrorCode.PARAMS_ERROR, "仅支持 90° 步进旋转");
        String flip = transformRequest.getFlip();
        ThrowUtils.throwIf(StrUtil.isNotBlank(flip) && !ALLOWED_FLIP_VALUES.contains(flip),
                ErrorCode.PARAMS_ERROR, "非法的翻转方向");
        String transformRule = (rotate > 0 ? "/rotate/" + rotate : "")
                + (StrUtil.isNotBlank(flip) ? "/flip/" + flip : "");

        Integer outputWidth = transformRequest.getOutputWidth();
        ThrowUtils.throwIf(outputWidth != null && (outputWidth < 1 || outputWidth > 10000),
                ErrorCode.PARAMS_ERROR, "输出宽度需在 1-10000 之间");
        boolean hasCrop = transformRequest.getCropX() != null || transformRequest.getCropY() != null
                || transformRequest.getCropW() != null || transformRequest.getCropH() != null;
        int cropX = 0;
        int cropY = 0;
        int cropW = 0;
        int cropH = 0;
        if (hasCrop) {
            ThrowUtils.throwIf(transformRequest.getCropX() == null || transformRequest.getCropY() == null
                            || transformRequest.getCropW() == null || transformRequest.getCropH() == null,
                    ErrorCode.PARAMS_ERROR, "裁剪参数不完整");
            cropX = transformRequest.getCropX();
            cropY = transformRequest.getCropY();
            cropW = transformRequest.getCropW();
            cropH = transformRequest.getCropH();
            // 裁剪坐标基于「旋转+翻转后」的图像：90°/270° 旋转时可视宽高互换
            Integer oldW = oldPicture.getPicWidth();
            Integer oldH = oldPicture.getPicHeight();
            ThrowUtils.throwIf(oldW == null || oldH == null || oldW <= 0 || oldH <= 0,
                    ErrorCode.PARAMS_ERROR, "图片缺少尺寸信息，无法裁剪");
            int viewW = (rotate == 90 || rotate == 270) ? oldH : oldW;
            int viewH = (rotate == 90 || rotate == 270) ? oldW : oldH;
            cropX = Math.max(0, Math.min(cropX, viewW - 1));
            cropY = Math.max(0, Math.min(cropY, viewH - 1));
            cropW = Math.max(1, Math.min(cropW, viewW - cropX));
            cropH = Math.max(1, Math.min(cropH, viewH - cropY));
        }
        // 输出宽度与裁剪后宽度相同视为不缩放
        boolean hasResize = outputWidth != null && (!hasCrop || outputWidth != cropW);
        // 色调调整：0-200 滑杆语义（100 原图），换算为 imageMogr2 的 [-100,100] 偏移
        int brightness = normalizeTonalParam(transformRequest.getBrightness());
        int contrast = normalizeTonalParam(transformRequest.getContrast());
        int saturation = normalizeTonalParam(transformRequest.getSaturation());
        boolean hasTonal = brightness != 0 || contrast != 0 || saturation != 0;
        ThrowUtils.throwIf(transformRule.isEmpty() && !hasCrop && !hasResize && !hasTonal,
                ErrorCode.PARAMS_ERROR, "未提交任何编辑内容");

        String objectKey = cosManager.extractObjectKey(oldPicture.getUrl());
        String timestamp = String.valueOf(System.currentTimeMillis());
        String finalKey = CosManager.getEditedPath(objectKey, timestamp);
        try {
            // 单操作链：flip → rotate → cut → thumbnail → 色调，顺序即语义
            List<String> steps = new ArrayList<>();
            if (StrUtil.isNotBlank(flip)) {
                steps.add("/flip/" + flip);
            }
            if (rotate > 0) {
                steps.add("/rotate/" + rotate);
            }
            if (hasCrop) {
                steps.add("/cut/" + cropW + "x" + cropH + "x" + cropX + "x" + cropY);
            }
            if (hasResize) {
                steps.add("/thumbnail/" + outputWidth + "x");
            }
            if (hasTonal) {
                StringBuilder tonal = new StringBuilder();
                if (brightness != 0) {
                    tonal.append("/brightness/").append(brightness);
                }
                if (contrast != 0) {
                    tonal.append("/contrast/").append(contrast);
                }
                if (saturation != 0) {
                    tonal.append("/saturation/").append(saturation);
                }
                steps.add(tonal.toString());
            }
            String sourceKey = objectKey;
            String prevTempKey = null;
            for (int i = 0; i < steps.size(); i++) {
                boolean last = i == steps.size() - 1;
                String targetKey = last ? finalKey : CosManager.getEditedPath(objectKey, timestamp + "s" + i);
                cosManager.transformPictureObject(sourceKey, targetKey, steps.get(i));
                if (prevTempKey != null) {
                    cosManager.deleteObject(prevTempKey);
                }
                prevTempKey = last ? null : targetKey;
                sourceKey = targetKey;
            }
            // 缩略图沿用上传时的规则从编辑产物重新生成，返回的 OriginalInfo 即产物尺寸信息
            String thumbnailKey = CosManager.getThumbnailPath(finalKey);
            CIUploadResult thumbnailResult = cosManager.transformPictureObject(
                    finalKey, thumbnailKey, "/thumbnail/600x/format/jpg");
            ImageInfo finalInfo = thumbnailResult.getOriginalInfo().getImageInfo();

            // ---- 原地更新图片记录（旧文件按 ADR 0001 保留作回滚，不删除） ----
            Picture picture = new Picture();
            picture.setId(oldPicture.getId());
            picture.setUrl(cosManager.buildObjectUrl(finalKey));
            picture.setThumbnailUrl(cosManager.buildObjectUrl(thumbnailKey));
            // 画面变了，主色与色系重新提取（失败留空，不阻断编辑保存）
            fillColorInfo(picture, picture.getThumbnailUrl(), picture.getUrl());
            int finalW = finalInfo.getWidth();
            int finalH = finalInfo.getHeight();
            picture.setPicWidth(finalW);
            picture.setPicHeight(finalH);
            picture.setPicScale(NumberUtil.round(finalW * 1.0 / finalH, 2).doubleValue());
            picture.setPicFormat(finalInfo.getFormat());
            picture.setPicSize(cosManager.getObjectSize(finalKey));
            picture.setEditTime(new Date());
            // 审核规则：空间图片免审保持可见；公共图库按用户角色回填（管理员自动过审，普通用户重新待审核）
            if (oldPicture.getSpaceId() != null) {
                picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            } else {
                this.fillReviewParams(picture, loginUser);
            }
            boolean result = this.updateById(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "编辑结果保存失败");
            // 空间用量：编辑只改变体积，不改变数量
            if (oldPicture.getSpaceId() != null && picture.getPicSize() != null && oldPicture.getPicSize() != null) {
                spaceService.updateSpaceUsage(oldPicture.getSpaceId(),
                        picture.getPicSize() - oldPicture.getPicSize(), 0);
            }
            // 空间动态：画面编辑事件
            spaceActivityService.recordActivity(oldPicture.getSpaceId(), loginUser.getId(),
                    SpaceActivityService.ACTION_EDIT, oldPicture.getId());
            // 图库是派生数据：内容或可见性变化后重新对齐（失败只告警不阻断）
            Picture updated = this.getById(oldPicture.getId());
            syncPictureGallery(updated);
            return this.getPictureVO(updated);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("图片画面编辑失败, pictureId={}", oldPicture.getId(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片处理失败，请稍后重试");
        }
    }

    // ==================== 下载与收藏 ====================

    @Override
    public String downloadPicture(Long pictureId, User loginUser, String tier) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = this.getById(pictureId);
        this.checkPictureReadable(picture, loginUser);
        int updated = baseMapper.incrementDownloadCount(pictureId);
        ThrowUtils.throwIf(updated <= 0, ErrorCode.OPERATION_ERROR, "下载计数失败");
        // 原生 SQL 写入不经 CacheAwareServiceImpl 的失效钩子，按 docs/adr/0002 自行失效
        multiLevelCacheManager.evictPicture(pictureId);
        String url = picture.getUrl();
        if (StrUtil.isBlank(tier)) {
            return url;
        }
        // 分辨率档位：只对小于原图的档位生成衍生地址（imageMogr2 实时缩放，见 docs/adr/0017）；
        // 非 COS 域名（外链图）或档位不合法时回退原图
        Long budget = TIER_PIXEL_BUDGETS.get(tier.toLowerCase());
        Integer width = picture.getPicWidth();
        Integer height = picture.getPicHeight();
        if (budget == null || width == null || height == null
                || (long) width * height <= budget
                || cosManager.extractObjectKey(url) == null) {
            return url;
        }
        int scaledWidth = Math.max(1, (int) Math.round(width * Math.sqrt((double) budget / ((long) width * height))));
        return url + "?imageMogr2/thumbnail/" + scaledWidth + "x";
    }

    @Override
    public boolean setFeatured(Long pictureId, boolean isFeatured) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        Picture picture = new Picture();
        picture.setId(pictureId);
        picture.setIsFeatured(isFeatured);
        return this.updateById(picture);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setDailyPick(Long pictureId) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture target = this.getById(pictureId);
        ThrowUtils.throwIf(target == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 逐条 updateById 而不是 update(Wrapper)：缓存失效钩子只挂在按 id 的写入上（docs/adr/0002）
        List<Picture> currentPicks = this.list(new LambdaQueryWrapper<Picture>()
                .eq(Picture::getIsDailyPick, true));
        for (Picture pick : currentPicks) {
            if (!pick.getId().equals(pictureId)) {
                Picture patch = new Picture();
                patch.setId(pick.getId());
                patch.setIsDailyPick(false);
                this.updateById(patch);
            }
        }
        if (Boolean.TRUE.equals(target.getIsDailyPick())) {
            return true;
        }
        Picture patch = new Picture();
        patch.setId(pictureId);
        patch.setIsDailyPick(true);
        return this.updateById(patch);
    }

    @Override
    public boolean submitDraft(Long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        ThrowUtils.throwIf(!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser),
                ErrorCode.NO_AUTH_ERROR, "无权限提交该草稿");
        ThrowUtils.throwIf(oldPicture.getReviewStatus() == null
                        || oldPicture.getReviewStatus() != PictureReviewStatusEnum.DRAFT.getValue(),
                ErrorCode.PARAMS_ERROR, "该图片不是草稿");
        Picture picture = new Picture();
        picture.setId(pictureId);
        this.fillReviewParams(picture, loginUser);
        return this.updateById(picture);
    }

    @Override
    public PictureVO getDailyPickPicture() {
        Picture dailyPick = this.getOne(new LambdaQueryWrapper<Picture>()
                .eq(Picture::getIsDailyPick, true)
                .isNull(Picture::getSpaceId)
                .eq(Picture::getReviewStatus, PictureReviewStatusEnum.PASS.getValue())
                .orderByDesc(Picture::getEditTime)
                .last("LIMIT 1"));
        return dailyPick == null ? null : this.getPictureVO(dailyPick);
    }

    @Override
    public List<PictureDownloadTierVO> listDownloadTiers(Long pictureId) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = this.getById(pictureId);
        // 档位列表与匿名可见口径一致：空间图片不提供档位下载
        this.checkPictureReadable(picture, null);
        List<PictureDownloadTierVO> tiers = new ArrayList<>();
        Integer width = picture.getPicWidth();
        Integer height = picture.getPicHeight();
        if (width == null || height == null || width <= 0 || height <= 0) {
            return tiers;
        }
        long origPixels = (long) width * height;
        for (Map.Entry<String, Long> entry : TIER_PIXEL_BUDGETS.entrySet()) {
            long budget = entry.getValue();
            // 1.1 倍余量：原图只比档位略大时衍生档与原图肉眼无差，不值得列出
            if (origPixels < budget * 11 / 10) {
                continue;
            }
            double scale = Math.sqrt((double) budget / (double) origPixels);
            PictureDownloadTierVO tierVO = new PictureDownloadTierVO();
            tierVO.setTier(TIER_DISPLAY_NAMES.get(entry.getKey()));
            tierVO.setWidth((int) Math.max(1, Math.round(width * scale)));
            tierVO.setHeight((int) Math.max(1, Math.round(height * scale)));
            tiers.add(tierVO);
        }
        return tiers;
    }

    @Override
    public void adjustCollectCount(Long pictureId, int delta) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(delta != 1 && delta != -1, ErrorCode.PARAMS_ERROR);
        int updated = baseMapper.adjustCollectCount(pictureId, delta);
        ThrowUtils.throwIf(updated <= 0, ErrorCode.OPERATION_ERROR, "收藏计数更新失败");
        // 原生 SQL 写入不经 CacheAwareServiceImpl 的失效钩子，按 docs/adr/0002 自行失效
        multiLevelCacheManager.evictPicture(pictureId);
    }

    @Override
    public void checkPictureReadable(Picture picture, User loginUser) {
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 公共图库：仅过审图片可读。未过审统一按不存在返回，避免泄露审核中图片的存在性
        if (picture.getSpaceId() == null) {
            ThrowUtils.throwIf(picture.getReviewStatus() == null
                            || picture.getReviewStatus() != PictureReviewStatusEnum.PASS.getValue(),
                    ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            return;
        }
        // 空间图片：仅成员或站点管理员（与列表页空间可见性规则一致）
        Space space = spaceService.getById(picture.getSpaceId());
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        spaceUserService.checkPermission(space, loginUser, SpaceRoleEnum.VIEWER, "无权限查看该空间的图片");
    }

    // ==================== 接入缓存基类 ====================
    // 全部写入的失效钩子都在基类 CacheAwareServiceImpl 里（覆盖单条与批量写入，
    // 并对无法按 id 失效的 wrapper 写法快速失败）。这里只需告诉基类两件事：
    // 缓存 key 是什么、失效时调哪个缓存区。约定与理由见 docs/adr/0002、docs/adr/0007。

    @Override
    protected Long keyOf(Picture entity) {
        return entity == null ? null : entity.getId();
    }

    @Override
    protected void evict(Long key) {
        multiLevelCacheManager.evictPicture(key);
    }

    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，创建或编辑都要改为待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    // ==================== 画面扩展（百炼异步，无状态代理，见 docs/adr/0010） ====================

    /**
     * 比例串格式：两边都是 1-4 位整数、可选两位小数，如 16:9、16:10、1:2.37。
     * 只挡注入与明显非法的串；能不能扩得出来由 OutPaintingManager 按图片尺寸判断
     */
    private static final Pattern EXPAND_RATIO_PATTERN =
            Pattern.compile("[1-9]\\d{0,3}(\\.\\d{1,2})?:[1-9]\\d{0,3}(\\.\\d{1,2})?");

    /**
     * taskId 会拼进查询 URL，白名单字符防注入
     */
    private static final Pattern TASK_ID_PATTERN = Pattern.compile("[A-Za-z0-9\\-]{8,64}");

    @Override
    public String submitExpand(PictureExpandSubmitRequest submitRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        ThrowUtils.throwIf(submitRequest == null || submitRequest.getPictureId() == null, ErrorCode.PARAMS_ERROR);
        String outputRatio = submitRequest.getOutputRatio();
        ThrowUtils.throwIf(StrUtil.isBlank(outputRatio) || !EXPAND_RATIO_PATTERN.matcher(outputRatio).matches(),
                ErrorCode.PARAMS_ERROR, "不支持的宽高比");
        Picture picture = this.getById(submitRequest.getPictureId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        ThrowUtils.throwIf(StrUtil.isBlank(picture.getUrl()), ErrorCode.PARAMS_ERROR, "图片缺少存储地址");
        checkEditPermission(picture, loginUser);
        // 积分门槛：先查余额再提交（提交失败不扣分；扣减 SQL 自带余额守卫，见 docs/adr/0014）
        User fresh = userService.getById(loginUser.getId());
        ThrowUtils.throwIf(fresh == null || fresh.getPoints() == null
                        || fresh.getPoints() < PointsConstant.OUTPAINTING_COST,
                ErrorCode.OPERATION_ERROR, "积分不足，无法使用画面扩展（每次消耗 " + PointsConstant.OUTPAINTING_COST + " 积分）");
        // 目标比例不在服务端白名单内时，manager 会改用像素偏移实现（见 docs/adr/0010）
        String taskId = outPaintingManager.submitExpandTask(picture.getUrl(), outputRatio,
                picture.getPicWidth(), picture.getPicHeight());
        pointsService.deductForOutpainting(loginUser.getId(), taskId);
        return taskId;
    }

    @Override
    public PictureExpandResultVO queryExpandResult(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId) || !TASK_ID_PATTERN.matcher(taskId).matches(),
                ErrorCode.PARAMS_ERROR, "任务 id 不合法");
        PictureExpandResultVO result = outPaintingManager.queryExpandTask(taskId);
        if ("FAILED".equals(result.getStatus())) {
            // 失败退还（幂等，同一任务只退一次，见 docs/adr/0014）；退款失败不影响查询结果
            try {
                pointsService.refundForOutpainting(taskId);
            } catch (Exception e) {
                log.warn("扩图任务失败退还异常, taskId={}", taskId, e);
            }
        }
        return result;
    }

    @Override
    public PictureVO saveExpand(PictureExpandSaveRequest saveRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        ThrowUtils.throwIf(saveRequest == null || saveRequest.getPictureId() == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(
                StrUtil.isBlank(saveRequest.getTaskId()) || !TASK_ID_PATTERN.matcher(saveRequest.getTaskId()).matches(),
                ErrorCode.PARAMS_ERROR, "任务 id 不合法");
        Picture oldPicture = this.getById(saveRequest.getPictureId());
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        checkEditPermission(oldPicture, loginUser);
        // 只信后端重查任务拿到的 URL，不接受客户端传入的图片地址
        PictureExpandResultVO task = outPaintingManager.queryExpandTask(saveRequest.getTaskId());
        ThrowUtils.throwIf(!"SUCCEEDED".equals(task.getStatus()) || StrUtil.isBlank(task.getImageUrl()),
                ErrorCode.OPERATION_ERROR, "扩图任务尚未成功，无法保存");
        File tempFile = null;
        try {
            tempFile = File.createTempFile("expand-", ".img");
            HttpUtil.downloadFile(task.getImageUrl(), tempFile);
            ThrowUtils.throwIf(tempFile.length() == 0, ErrorCode.OPERATION_ERROR, "扩图结果获取失败");
            String objectKey = cosManager.extractObjectKey(oldPicture.getUrl());
            String timestamp = String.valueOf(System.currentTimeMillis());
            String finalKey = CosManager.getEditedPath(objectKey, timestamp);
            // 上传时沿用上传规则顺带生成缩略图，返回的 OriginalInfo 即产物尺寸信息
            PutObjectResult putObjectResult = cosManager.putPictureObject(finalKey, tempFile);
            ImageInfo finalInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            String thumbnailKey = CosManager.getThumbnailPath(finalKey);

            // ---- 原地更新图片记录（旧文件按 ADR 0001 保留作回滚，不删除） ----
            Picture picture = new Picture();
            picture.setId(oldPicture.getId());
            picture.setUrl(cosManager.buildObjectUrl(finalKey));
            picture.setThumbnailUrl(cosManager.buildObjectUrl(thumbnailKey));
            // 画面变了，主色与色系重新提取（失败留空，不阻断保存）
            fillColorInfo(picture, picture.getThumbnailUrl(), picture.getUrl());
            int finalW = finalInfo.getWidth();
            int finalH = finalInfo.getHeight();
            picture.setPicWidth(finalW);
            picture.setPicHeight(finalH);
            picture.setPicScale(NumberUtil.round(finalW * 1.0 / finalH, 2).doubleValue());
            picture.setPicFormat(finalInfo.getFormat());
            picture.setPicSize(cosManager.getObjectSize(finalKey));
            picture.setEditTime(new Date());
            // 审核规则与画面编辑一致：空间图片免审；公共图库按用户角色回填
            if (oldPicture.getSpaceId() != null) {
                picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            } else {
                this.fillReviewParams(picture, loginUser);
            }
            boolean result = this.updateById(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "扩图结果保存失败");
            // 空间用量：只改变体积，不改变数量
            if (oldPicture.getSpaceId() != null && picture.getPicSize() != null && oldPicture.getPicSize() != null) {
                spaceService.updateSpaceUsage(oldPicture.getSpaceId(),
                        picture.getPicSize() - oldPicture.getPicSize(), 0);
            }
            // 空间动态：画面扩展保存事件
            spaceActivityService.recordActivity(oldPicture.getSpaceId(), loginUser.getId(),
                    SpaceActivityService.ACTION_EDIT, oldPicture.getId());
            // 图库是派生数据：内容或可见性变化后重新对齐（失败只告警不阻断）
            Picture updated = this.getById(oldPicture.getId());
            syncPictureGallery(updated);
            return this.getPictureVO(updated);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("图片画面扩展保存失败, pictureId={}", oldPicture.getId(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "扩图结果保存失败，请稍后重试");
        } finally {
            if (tempFile != null && !tempFile.delete()) {
                tempFile.deleteOnExit();
            }
        }
    }

    // ==================== AI 预填图片信息（读图模型，见 docs/adr/0012） ====================

    @Override
    public PictureAiPrefillVO aiPrefillPictureInfo(Long pictureId, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = this.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        ThrowUtils.throwIf(StrUtil.isBlank(picture.getUrl()), ErrorCode.PARAMS_ERROR, "图片缺少存储地址");
        checkEditPermission(picture, loginUser);
        // 只产出建议：填进表单由人确认，落库仍走编辑保存
        return pictureInfoAiManager.readPictureInfo(picture.getUrl());
    }

    /**
     * 编辑权限：仅上传者、站点管理员，或所在空间的管理员（编辑者只能动自己上传的，已被上一条覆盖）
     */
    private void checkEditPermission(Picture picture, User loginUser) {
        ThrowUtils.throwIf(!hasEditPermission(picture, loginUser), ErrorCode.NO_AUTH_ERROR);
    }

    @Override
    public boolean hasEditPermission(Picture picture, User loginUser) {
        if (picture == null || loginUser == null) {
            return false;
        }
        if (picture.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
            return true;
        }
        if (picture.getSpaceId() == null) {
            return false;
        }
        Space space = spaceService.getById(picture.getSpaceId());
        return space != null
                && spaceUserService.getEffectiveRole(space, loginUser) == SpaceRoleEnum.ADMIN;
    }
}




