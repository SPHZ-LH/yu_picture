package com.yupi.yupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 专题-图片关系（topic_picture 表）。
 * <p>
 * 图片移出专题走<b>物理删除</b>（重新加入会撞 uk_topicId_pictureId 唯一键）。
 * 入选资格（公共图库过审图）在写入时校验，查询时 JOIN picture 兜底。
 */
@TableName(value = "topic_picture")
@Data
public class TopicPicture implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 专题 id
     */
    private Long topicId;

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 添加人 id
     */
    private Long userId;

    /**
     * 加入时间
     */
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
