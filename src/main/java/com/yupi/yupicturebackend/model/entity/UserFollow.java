package com.yupi.yupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户关注关系（user_follow 表）：follower 关注 followee。
 * <p>
 * 取关走<b>物理删除</b>：逻辑删后重新关注会撞 uk_follower_followee 唯一键。
 */
@TableName(value = "user_follow")
@Data
public class UserFollow implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关注者 id
     */
    private Long followerId;

    /**
     * 被关注者 id
     */
    private Long followeeId;

    /**
     * 关注时间
     */
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
