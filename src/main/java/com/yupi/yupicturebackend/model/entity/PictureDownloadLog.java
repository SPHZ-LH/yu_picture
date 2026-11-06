package com.yupi.yupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片下载日志（picture_download_log 表）。
 * <p>
 * 每次下载事件追加一行：不去重、不回改、不设 isDelete（纯追加型日志）。
 * 是日/周/月榜单、排名趋势、用户下载历史与创作者下载聚合的唯一数据源；
 * 匿名下载 userId 为 NULL。
 */
@TableName(value = "picture_download_log")
@Data
public class PictureDownloadLog implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 下载用户 id（匿名下载为 null）
     */
    private Long userId;

    /**
     * 下载时间
     */
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
