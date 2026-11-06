package com.yupi.yupicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Topic;
import com.yupi.yupicturebackend.model.vo.TopicVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author SPHZ
 * @description 针对表【topic(图片专题)】的数据库操作Mapper
 * @createDate 2026-09-27
 * @Entity com.yupi.yupicturebackend.model.entity.Topic
 */
public interface TopicMapper extends BaseMapper<Topic> {

    /**
     * 查询专题视图列表（含实时图片计数），策划人信息由服务层批量回填。
     *
     * @param category     分类筛选，空表示全部
     * @param featuredOnly true 时只取精选专题
     * @return 专题视图列表，精选在前、其余按创建时间倒序
     */
    List<TopicVO> selectTopicVOList(@Param("category") String category,
                                    @Param("featuredOnly") boolean featuredOnly);

    /**
     * 分页查询专题内图片：只出公共图库过审图（入选资格的查询时兜底），
     * 按加入专题的时间倒序。列与收藏列表一致（卡片所需）。
     *
     * @param page    分页对象
     * @param topicId 专题 id
     * @return 图片分页
     */
    IPage<Picture> selectTopicPicturePage(IPage<Picture> page, @Param("topicId") Long topicId);
}
