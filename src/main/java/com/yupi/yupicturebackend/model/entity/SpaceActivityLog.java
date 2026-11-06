package com.yupi.yupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 空间动态（space_activity_log 表）：空间内图片上传/编辑/删除事件的追加流水。
 * <p>
 * 纯追加、无 isDelete；图片删除后动态条目保留（展示时图片信息取不到即为空）。
 */
@TableName(value = "space_activity_log")
@Data
public class SpaceActivityLog implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 操作人 id
     */
    private Long userId;

    /**
     * 动作：upload/edit/delete
     */
    private String action;

    /**
     * 目标图片 id
     */
    private Long targetId;

    /**
     * 发生时间
     */
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
