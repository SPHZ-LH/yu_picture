package com.yupi.yupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 空间成员关系（space_user 表）。
 * <p>
 * 一条记录表示某用户是某空间的成员，成员关系是获得该空间访问权的<b>唯一</b>途径——
 * 私有空间也写这条记录（创建者一条 admin），因此鉴权只需查这一张表，不必按空间类型分支。
 * <p>
 * 移出成员走<b>物理删除</b>，不设 isDelete：逻辑删除后重新邀请会撞 uk_spaceId_userId 唯一键。
 * 空间被删除时成员记录一并物理删除（成员关系脱离空间没有留存价值）。
 *
 * @see com.yupi.yupicturebackend.model.enums.SpaceRoleEnum 角色取值
 */
@TableName(value = "space_user")
@Data
public class SpaceUser implements Serializable {

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
     * 用户 id
     */
    private Long userId;

    /**
     * 空间角色：viewer/editor/admin
     */
    private String spaceRole;

    /**
     * 加入时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
