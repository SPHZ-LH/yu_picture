package com.yupi.yupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 积分流水（points_log 表）：每笔积分变动一行。
 * <p>
 * 余额的权威值在 user.points，本表用于对账与幂等——
 * uk_reason_relatedId 唯一键保证同一关联对象（如扩图任务）的退还只可能成功一次；
 * MySQL 唯一索引对 NULL relatedId 不去重，注册赠送与管理员调整行（无关联对象）不受影响。
 */
@TableName(value = "points_log")
@Data
public class PointsLog implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 积分变动（正得负耗）
     */
    private Integer delta;

    /**
     * 变动后余额
     */
    private Integer balance;

    /**
     * 变动原因：register/admin/outpainting/outpainting_refund
     */
    private String reason;

    /**
     * 关联对象 id（扩图任务 id 等）
     */
    private String relatedId;

    /**
     * 备注（管理员调整时填写）
     */
    private String note;

    /**
     * 变动时间
     */
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
