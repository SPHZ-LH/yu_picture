package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.common.PageRequest;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.TopicMapper;
import com.yupi.yupicturebackend.mapper.TopicPictureMapper;
import com.yupi.yupicturebackend.model.dto.topic.TopicAddRequest;
import com.yupi.yupicturebackend.model.dto.topic.TopicPictureOpRequest;
import com.yupi.yupicturebackend.model.dto.topic.TopicUpdateRequest;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Topic;
import com.yupi.yupicturebackend.model.entity.TopicPicture;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.model.vo.TopicVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.TopicService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author SPHZ
 * @description 针对表【topic(图片专题)】的数据库操作Service实现
 * @createDate 2026-09-27
 */
@Slf4j
@Service
public class TopicServiceImpl extends ServiceImpl<TopicMapper, Topic> implements TopicService {

    /** 专题列表每页上限：专题列表不分页，这里只压单次返回量 */
    private static final int MAX_PAGE_SIZE = 20;

    @Resource
    private TopicPictureMapper topicPictureMapper;

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    @Override
    public long addTopic(TopicAddRequest topicAddRequest, User loginUser) {
        ThrowUtils.throwIf(topicAddRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        Topic topic = new Topic();
        applyMutableFields(topic, topicAddRequest.getName(), topicAddRequest.getDescription(),
                topicAddRequest.getCoverUrl(), topicAddRequest.getCategory(),
                topicAddRequest.getCuratorId(), topicAddRequest.getUpdateSchedule());
        topic.setIsFeatured(Boolean.TRUE.equals(topicAddRequest.getIsFeatured()));
        topic.setUserId(loginUser.getId());
        boolean saved = this.save(topic);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "专题创建失败");
        if (Boolean.TRUE.equals(topic.getIsFeatured())) {
            clearFeaturedExclusive(topic.getId());
        }
        return topic.getId();
    }

    @Override
    public boolean updateTopic(TopicUpdateRequest topicUpdateRequest, User loginUser) {
        ThrowUtils.throwIf(topicUpdateRequest == null || topicUpdateRequest.getId() == null
                || topicUpdateRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        Topic old = this.getById(topicUpdateRequest.getId());
        ThrowUtils.throwIf(old == null, ErrorCode.NOT_FOUND_ERROR, "专题不存在");
        Topic topic = new Topic();
        topic.setId(topicUpdateRequest.getId());
        topic.setEditTime(new Date());
        boolean touched = false;
        if (topicUpdateRequest.getName() != null || topicUpdateRequest.getDescription() != null
                || topicUpdateRequest.getCoverUrl() != null || topicUpdateRequest.getCategory() != null
                || topicUpdateRequest.getCuratorId() != null || topicUpdateRequest.getUpdateSchedule() != null) {
            applyMutableFields(topic, topicUpdateRequest.getName(), topicUpdateRequest.getDescription(),
                    topicUpdateRequest.getCoverUrl(), topicUpdateRequest.getCategory(),
                    topicUpdateRequest.getCuratorId(), topicUpdateRequest.getUpdateSchedule());
            touched = true;
        }
        if (topicUpdateRequest.getIsFeatured() != null) {
            topic.setIsFeatured(topicUpdateRequest.getIsFeatured());
            touched = true;
        }
        ThrowUtils.throwIf(!touched, ErrorCode.PARAMS_ERROR, "没有需要修改的内容");
        boolean updated = this.updateById(topic);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "专题更新失败");
        if (Boolean.TRUE.equals(topic.getIsFeatured())) {
            clearFeaturedExclusive(topic.getId());
        }
        return true;
    }

    @Override
    public boolean deleteTopic(long id, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        Topic old = this.getById(id);
        ThrowUtils.throwIf(old == null, ErrorCode.NOT_FOUND_ERROR, "专题不存在");
        return this.removeById(id);
    }

    @Override
    public List<TopicVO> listTopicVOs(String category) {
        return fillCurators(baseMapper.selectTopicVOList(category, false));
    }

    @Override
    public TopicVO getFeaturedTopicVO() {
        List<TopicVO> featured = fillCurators(baseMapper.selectTopicVOList(null, true));
        return CollUtil.isEmpty(featured) ? null : featured.get(0);
    }

    @Override
    public TopicVO getTopicVO(long topicId) {
        ThrowUtils.throwIf(topicId <= 0, ErrorCode.PARAMS_ERROR);
        Topic topic = this.getById(topicId);
        ThrowUtils.throwIf(topic == null, ErrorCode.NOT_FOUND_ERROR, "专题不存在");
        List<TopicVO> matches = fillCurators(baseMapper.selectTopicVOList(null, false)).stream()
                .filter(vo -> vo.getId() != null && vo.getId() == topicId)
                .collect(Collectors.toList());
        return CollUtil.isEmpty(matches) ? null : matches.get(0);
    }

    @Override
    public Page<PictureVO> listTopicPictures(long topicId, PageRequest pageRequest) {
        ThrowUtils.throwIf(pageRequest == null, ErrorCode.PARAMS_ERROR);
        Topic topic = this.getById(topicId);
        ThrowUtils.throwIf(topic == null, ErrorCode.NOT_FOUND_ERROR, "专题不存在");
        long current = Math.max(pageRequest.getCurrent(), 1);
        long pageSize = Math.min(Math.max(pageRequest.getPageSize(), 1), MAX_PAGE_SIZE);
        Page<Picture> picturePage = new Page<>(current, pageSize);
        baseMapper.selectTopicPicturePage(picturePage, topicId);
        return pictureService.getPictureVOPage(picturePage);
    }

    @Override
    public boolean addTopicPicture(TopicPictureOpRequest opRequest, User loginUser) {
        validateOp(opRequest, loginUser);
        Picture picture = pictureService.getById(opRequest.getPictureId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 入选资格：公共图库过审图（专题是站内公共内容，空间图片不入专题）
        ThrowUtils.throwIf(picture.getSpaceId() != null, ErrorCode.PARAMS_ERROR, "空间图片不能加入专题");
        ThrowUtils.throwIf(picture.getReviewStatus() == null
                        || picture.getReviewStatus() != PictureReviewStatusEnum.PASS.getValue(),
                ErrorCode.PARAMS_ERROR, "仅已过审图片可以加入专题");
        TopicPicture topicPicture = new TopicPicture();
        topicPicture.setTopicId(opRequest.getTopicId());
        topicPicture.setPictureId(opRequest.getPictureId());
        topicPicture.setUserId(loginUser.getId());
        try {
            topicPictureMapper.insert(topicPicture);
        } catch (DuplicateKeyException e) {
            // 重复加入按幂等成功处理
        }
        return true;
    }

    @Override
    public boolean removeTopicPicture(TopicPictureOpRequest opRequest, User loginUser) {
        validateOp(opRequest, loginUser);
        return topicPictureMapper.delete(new LambdaQueryWrapper<TopicPicture>()
                .eq(TopicPicture::getTopicId, opRequest.getTopicId())
                .eq(TopicPicture::getPictureId, opRequest.getPictureId())) > 0;
    }

    // ==================== 私有方法 ====================

    private void validateOp(TopicPictureOpRequest opRequest, User loginUser) {
        ThrowUtils.throwIf(opRequest == null || opRequest.getTopicId() == null || opRequest.getTopicId() <= 0
                || opRequest.getPictureId() == null || opRequest.getPictureId() <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        Topic topic = this.getById(opRequest.getTopicId());
        ThrowUtils.throwIf(topic == null, ErrorCode.NOT_FOUND_ERROR, "专题不存在");
    }

    /**
     * 校验并填充可编辑字段（创建与更新共用）：name 必填 1-64 字；
     * coverUrl 必须 http(s) 开头；curatorId 传了必须指向真实用户
     */
    private void applyMutableFields(Topic topic, String name, String description, String coverUrl,
                                    String category, Long curatorId, String updateSchedule) {
        ThrowUtils.throwIf(name == null || name.trim().isEmpty(), ErrorCode.PARAMS_ERROR, "专题名不能为空");
        name = name.trim();
        ThrowUtils.throwIf(name.length() > 64, ErrorCode.PARAMS_ERROR, "专题名过长");
        topic.setName(name);
        if (description != null) {
            ThrowUtils.throwIf(description.length() > 500, ErrorCode.PARAMS_ERROR, "专题简介过长");
            topic.setDescription(description);
        }
        if (coverUrl != null && !coverUrl.isEmpty()) {
            ThrowUtils.throwIf(!(coverUrl.startsWith("http://") || coverUrl.startsWith("https://")),
                    ErrorCode.PARAMS_ERROR, "封面地址格式不正确");
            topic.setCoverUrl(coverUrl);
        }
        if (category != null) {
            topic.setCategory(category.trim().isEmpty() ? null : category.trim());
        }
        if (curatorId != null) {
            if (curatorId > 0) {
                ThrowUtils.throwIf(userService.getById(curatorId) == null,
                        ErrorCode.PARAMS_ERROR, "策划人用户不存在");
                topic.setCuratorId(curatorId);
            } else {
                topic.setCuratorId(null);
            }
        }
        if (updateSchedule != null) {
            topic.setUpdateSchedule(updateSchedule.trim().isEmpty() ? null : updateSchedule.trim());
        }
    }

    /**
     * 精选位互斥：清掉除 keepId 之外所有精选（SELECT 后逐条按 id 更新，
     * 避开 CacheAwareServiceImpl 对 Wrapper 更新的限制——topic 无缓存区，直接用 update 也安全）
     */
    private void clearFeaturedExclusive(Long keepId) {
        this.update(new LambdaUpdateWrapper<Topic>()
                .eq(Topic::getIsFeatured, true)
                .ne(Topic::getId, keepId)
                .set(Topic::getIsFeatured, false));
    }

    /**
     * 批量回填策划人昵称/头像（curatorId 为空的专题不动）
     */
    private List<TopicVO> fillCurators(List<TopicVO> topicVOList) {
        if (CollUtil.isEmpty(topicVOList)) {
            return new ArrayList<>();
        }
        Set<Long> curatorIds = topicVOList.stream()
                .map(TopicVO::getCuratorId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        if (curatorIds.isEmpty()) {
            return topicVOList;
        }
        Map<Long, UserVO> curatorMap = userService.listByIds(curatorIds).stream()
                .map(userService::getUserVO)
                .collect(Collectors.toMap(UserVO::getId, Function.identity()));
        for (TopicVO topicVO : topicVOList) {
            UserVO curator = curatorMap.get(topicVO.getCuratorId());
            if (curator != null) {
                topicVO.setCuratorName(curator.getUserName());
                topicVO.setCuratorAvatar(curator.getUserAvatar());
            }
        }
        return topicVOList;
    }
}
