package com.yupi.yupicturebackend.model.vo;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.yupi.yupicturebackend.model.entity.Picture;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 图片视图。
 * <p>
 * 列表等场景只填充部分字段，未填充的字段统一不序列化，
 * 避免响应里出现大量无意义的 null 键。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PictureVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 图片 url
     */
    private String url;

    /**
     * 缩略图 url（列表页展示用，历史数据可能为空，为空时前端回退原图）
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
     * 标签
     */
    private List<String> tags;

    /**
     * 分类
     */
    private String category;

    /**
     * 文件体积
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
     * 图片比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    /**
     * 下载量
     */
    private Long downloadCount;

    /**
     * 主色 hex（如 #3B82F6）
     */
    private String mainColor;

    /**
     * 色系：red/yellow/green/blue/purple/gray/dark/light/cream（pink 已并入 purple）
     */
    private String colorFamily;

    /**
     * 主色板 JSON（最多 5 个 hex，详情页「画面色彩」色点）
     */
    private String palette;

    /**
     * 是否编辑精选（首页「编辑精选」tab）
     */
    private Boolean isFeatured;

    /**
     * 是否今日一图（首页 hero）
     */
    private Boolean isDailyPick;

    /**
     * 收藏量
     */
    private Long collectCount;

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
    public static Picture voToObj(PictureVO pictureVO) {
        if (pictureVO == null) {
            return null;
        }
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureVO, picture);
        // 类型不同，需要转换
        picture.setTags(JSONUtil.toJsonStr(pictureVO.getTags()));
        return picture;
    }

    /**
     * 对象转封装类
     */
    public static PictureVO objToVo(Picture picture) {
        if (picture == null) {
            return null;
        }
        PictureVO pictureVO = new PictureVO();
        BeanUtils.copyProperties(picture, pictureVO);
        // 类型不同，需要转换
        pictureVO.setTags(JSONUtil.toList(picture.getTags(), String.class));
        return pictureVO;
    }
}

