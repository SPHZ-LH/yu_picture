package com.yupi.yupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "picture")
@Data
public class Picture implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 图片 url
     */
    private String url;

    /**
     * 缩略图 url（上传时由数据万象生成，历史数据可能为空）
     */
    private String thumbnailUrl;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签（JSON 数组）
     */
    private String tags;

    /**
     * 图片体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private Integer picWidth;

    /**
     * 图片高度
     */
    private Integer picHeight;

    /**
     * 图片宽高比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    /**
     * 下载量（每次触发下载 +1，不去重）
     */
    private Long downloadCount;

    /**
     * 主色 hex（如 #3B82F6），上传/编辑时自动提取；提取失败为空
     */
    private String mainColor;

    /**
     * 色系：red/yellow/green/blue/purple/gray/dark/light/cream（见 ColorExtractor 分桶规则，
     * 原 pink 已并入 purple）
     */
    private String colorFamily;

    /**
     * 主色板 JSON（最多 5 个 hex，如 ["#F2F4F5","#C3CDD3"]），上传/编辑时自动提取
     */
    private String palette;

    /**
     * 是否编辑精选（首页「编辑精选」tab，管理员逐张设置，不互斥）
     */
    private Boolean isFeatured;

    /**
     * 是否今日一图（首页 hero，管理员设置，全站至多一张）
     */
    private Boolean isDailyPick;

    /**
     * 收藏量（由 collect 收藏关系表增减维护，不随编辑等其它写操作变化）
     */
    private Long collectCount;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 状态：0-待审核; 1-通过; 2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;

    /**
     * 审核人 id
     */
    private Long reviewerId;

    /**
     * 审核时间
     */
    private Date reviewTime;

    /**
     * 空间 id（为空表示公共图库）
     */
    private Long spaceId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
