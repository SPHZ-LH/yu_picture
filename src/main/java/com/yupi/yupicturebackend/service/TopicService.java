package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.common.PageRequest;
import com.yupi.yupicturebackend.model.dto.topic.TopicAddRequest;
import com.yupi.yupicturebackend.model.dto.topic.TopicPictureOpRequest;
import com.yupi.yupicturebackend.model.dto.topic.TopicUpdateRequest;
import com.yupi.yupicturebackend.model.entity.Topic;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.model.vo.TopicVO;

import java.util.List;

/**
 * @author SPHZ
 * @description 针对表【topic(图片专题)】的数据库操作Service
 * @createDate 2026-09-27
 */
public interface TopicService extends IService<Topic> {

    /**
     * 创建专题（管理员）。设置精选时自动清掉其他专题的精选位（全站至多一条）。
     *
     * @param topicAddRequest 创建请求
     * @param loginUser       管理员
     * @return 新专题 id
     */
    long addTopic(TopicAddRequest topicAddRequest, User loginUser);

    /**
     * 更新专题（管理员）：字段传了才更新；isFeatured=true 时自动清掉其他精选位。
     *
     * @param topicUpdateRequest 更新请求
     * @param loginUser          管理员
     * @return 是否成功
     */
    boolean updateTopic(TopicUpdateRequest topicUpdateRequest, User loginUser);

    /**
     * 删除专题（管理员，逻辑删除）。专题-图片关系行保留，查询按专题存活过滤。
     *
     * @param id        专题 id
     * @param loginUser 管理员
     * @return 是否成功
     */
    boolean deleteTopic(long id, User loginUser);

    /**
     * 专题列表（公开）：含实时图片计数（公共图库过审口径）与策划人信息。
     *
     * @param category 分类筛选，空表示全部
     * @return 专题视图列表，精选在前、其余按创建时间倒序
     */
    List<TopicVO> listTopicVOs(String category);

    /**
     * 本周精选专题（公开）：全站至多一条，没有精选时返回 null。
     *
     * @return 精选专题视图
     */
    TopicVO getFeaturedTopicVO();

    /**
     * 专题详情（公开）
     *
     * @param topicId 专题 id
     * @return 专题视图
     */
    TopicVO getTopicVO(long topicId);

    /**
     * 专题内图片分页（公开）：只出公共图库过审图，按加入时间倒序。
     *
     * @param topicId     专题 id
     * @param pageRequest 分页请求
     * @return 图片视图分页
     */
    Page<PictureVO> listTopicPictures(long topicId, PageRequest pageRequest);

    /**
     * 图片加入专题（管理员）：要求图片是公共图库过审图；重复加入幂等。
     *
     * @param opRequest 专题/图片 id
     * @param loginUser 管理员
     * @return 是否成功
     */
    boolean addTopicPicture(TopicPictureOpRequest opRequest, User loginUser);

    /**
     * 图片移出专题（管理员）：未在专题内时幂等。
     *
     * @param opRequest 专题/图片 id
     * @param loginUser 管理员
     * @return 是否成功
     */
    boolean removeTopicPicture(TopicPictureOpRequest opRequest, User loginUser);
}
