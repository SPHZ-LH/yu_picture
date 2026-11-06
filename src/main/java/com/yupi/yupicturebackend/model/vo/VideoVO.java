package com.yupi.yupicturebackend.model.vo;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.yupi.yupicturebackend.model.entity.Video;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 视频视图。
 * <p>
 * 与 {@link PictureVO} 一致：只暴露前台展示需要的字段，审核相关字段由管理端
 * 直接读实体（{@code /video/list/page}），不进视图。未填充的字段不序列化。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 视频 url
     */
    private String url;

    /**
     * 封面 url
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
     * 标签
     */
    private List<String> tags;

    /**
     * 分类
     */
    private String category;

    /**
     * 视频体积
     */
    private Long videoSize;

    /**
     * 视频宽度
     */
    private Integer videoWidth;

    /**
     * 视频高度
     */
    private Integer videoHeight;

    /**
     * 视频比例
     */
    private Double videoScale;

    /**
     * 视频时长（秒）
     */
    private Integer videoDuration;

    /**
     * 视频格式
     */
    private String videoFormat;

    /**
     * 下载量
     */
    private Long downloadCount;

    /**
     * 用户 id
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
     * 创建用户信息
     */
    private UserVO user;

    private static final long serialVersionUID = 1L;

    /**
     * 封装类转对象
     */
    public static Video voToObj(VideoVO videoVO) {
        if (videoVO == null) {
            return null;
        }
        Video video = new Video();
        BeanUtils.copyProperties(videoVO, video);
        // 类型不同，需要转换
        video.setTags(JSONUtil.toJsonStr(videoVO.getTags()));
        return video;
    }

    /**
     * 对象转封装类
     */
    public static VideoVO objToVo(Video video) {
        if (video == null) {
            return null;
        }
        VideoVO videoVO = new VideoVO();
        BeanUtils.copyProperties(video, videoVO);
        // 类型不同，需要转换
        videoVO.setTags(JSONUtil.toList(video.getTags(), String.class));
        return videoVO;
    }
}
