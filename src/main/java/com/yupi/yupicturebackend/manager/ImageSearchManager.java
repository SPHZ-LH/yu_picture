package com.yupi.yupicturebackend.manager;

import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.tiia.v20190529.TiiaClient;
import com.tencentcloudapi.tiia.v20190529.models.CreateGroupRequest;
import com.tencentcloudapi.tiia.v20190529.models.CreateImageRequest;
import com.tencentcloudapi.tiia.v20190529.models.DeleteImagesRequest;
import com.tencentcloudapi.tiia.v20190529.models.ImageInfo;
import com.tencentcloudapi.tiia.v20190529.models.SearchImageRequest;
import com.tencentcloudapi.tiia.v20190529.models.SearchImageResponse;
import com.yupi.yupicturebackend.config.ImageSearchConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 相似图搜索引擎封装（腾讯云图像搜索 TIIA 图库接口，见 docs/adr/0008）。
 * <p>
 * 图库模型：EntityId = 图片 id（检索结果靠它回查库表），PicName = gallery/{id}.jpg
 * （图库内唯一、长度受限，用短名规避超限）。编辑换图后重调 {@link #addImage} 即可
 * 更新特征（内部先删旧条目，规避同名不可重复入库）。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ImageSearchManager {

    /** GroupType=4：通用图像搜索 1.0 版，相同图与相似图均支持（创建后不可修改） */
    private static final Long GROUP_TYPE_GENERAL = 4L;

    private final TiiaClient tiiaClient;
    private final ImageSearchConfig config;

    /**
     * 建库（幂等）：图库已存在时腾讯云报错，视为开通成功。
     */
    public void ensureGallery() throws TencentCloudSDKException {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setGroupId(config.getGroupId());
        request.setGroupName(config.getGroupName());
        request.setMaxCapacity(config.getMaxCapacity());
        request.setGroupType(GROUP_TYPE_GENERAL);
        request.setMaxQps(10L);
        try {
            tiiaClient.CreateGroup(request);
        } catch (TencentCloudSDKException e) {
            String text = e.getErrorCode() + " " + String.valueOf(e.getMessage());
            if (text.contains("Exist") || text.contains("已存在")) {
                log.info("相似图库已存在，跳过建库: {}", config.getGroupId());
                return;
            }
            throw e;
        }
    }

    /**
     * 图片入库/更新：EntityId 为图片 id，检索结果靠它回查库表。
     * 内部先删同条目再入库，保证编辑换图后图库特征与最新内容一致。
     *
     * @param imageUrl 公网可访问的图片地址（优先 600px JPG 缩略图，省流量且规避格式/分辨率上限）
     */
    public void addImage(Long pictureId, String imageUrl) throws TencentCloudSDKException {
        deleteImage(pictureId);
        CreateImageRequest request = new CreateImageRequest();
        request.setGroupId(config.getGroupId());
        request.setEntityId(String.valueOf(pictureId));
        request.setPicName(galleryPicName(pictureId));
        request.setImageUrl(imageUrl);
        request.setCustomContent(String.valueOf(pictureId));
        tiiaClient.CreateImage(request);
    }

    /**
     * 删除图片条目；条目不存在时腾讯云报错，静默处理。
     */
    public void deleteImage(Long pictureId) {
        DeleteImagesRequest request = new DeleteImagesRequest();
        request.setGroupId(config.getGroupId());
        request.setEntityId(String.valueOf(pictureId));
        request.setPicName(galleryPicName(pictureId));
        try {
            tiiaClient.DeleteImages(request);
        } catch (TencentCloudSDKException e) {
            log.debug("相似图库条目删除跳过, pictureId={}, reason={}", pictureId, e.getMessage());
        }
    }

    /**
     * 相似检索：返回按相似度降序的 entityId（即图片 id）列表。
     * <p>
     * MatchThreshold=1 表示不设阈值（0 会被服务端解释为"使用默认阈值 50"，
     * 会把低分结果过滤掉）；先全量看真实分数分布，后续再决定是否收紧。
     *
     * @param imageUrl 公网可访问的检索图地址
     * @param limit    返回条数上限（最大 100）
     */
    public List<String> searchSimilar(String imageUrl, long limit) throws TencentCloudSDKException {
        SearchImageRequest request = new SearchImageRequest();
        request.setGroupId(config.getGroupId());
        request.setImageUrl(imageUrl);
        request.setLimit(limit);
        request.setMatchThreshold(1L);
        SearchImageResponse response = tiiaClient.SearchImage(request);
        ImageInfo[] infos = response.getImageInfos();
        if (infos == null || infos.length == 0) {
            return Collections.emptyList();
        }
        if (log.isInfoEnabled()) {
            String scores = Arrays.stream(infos)
                    .map(i -> i.getEntityId() + "=" + i.getScore())
                    .collect(Collectors.joining(", "));
            log.info("相似检索结果: {}", scores);
        }
        return Arrays.stream(infos)
                .map(ImageInfo::getEntityId)
                .collect(Collectors.toList());
    }

    /** 图库内条目名：图库内唯一且长度受限，与图片 id 一一对应 */
    public static String galleryPicName(Long pictureId) {
        return "gallery/" + pictureId + ".jpg";
    }
}
