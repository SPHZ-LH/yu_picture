package com.yupi.yupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "video")
@Data
public class Video implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 视频 url
     */
    private String url;

    /**
     * 封面 url（上传时由前端抽帧生成）
     */
    private String coverUrl;

    /**
     * 视频名称
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
     * 视频体积
     */
    private Long videoSize;

    /**
     * 视频宽度（取自封面实测：封面由视频帧绘出，宽高与视频一致，且由数据万象返回，客户端无法伪造）
     */
    private Integer videoWidth;

    /**
     * 视频高度
     */
    private Integer videoHeight;

    /**
     * 视频宽高比例
     */
    private Double videoScale;

    /**
     * 视频时长（秒，客户端读 metadata 回传，仅做量级校验）
     */
    private Integer videoDuration;

    /**
     * 视频格式
     */
    private String videoFormat;

    /**
     * 下载量（每次触发下载 +1，不去重）
     */
    private Long downloadCount;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 空间 id（为空表示公共图库）
     */
    private Long spaceId;

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

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
