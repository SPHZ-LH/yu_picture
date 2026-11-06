package com.yupi.yupicturebackend.constant;

import java.util.List;
import java.util.Arrays;

/**
 * 图片信息的封闭词表：分类（单选）与标签（多选）。
 * <p>
 * 这两列是筛选器的取值域，所以只有一处定义：前端选项接口、AI 预填的提示词与结果校验都读这里，
 * 避免"提示里给的选项"和"校验时认的选项"各写一份然后漂移（docs/adr/0012）。
 *
 * @author SPHZ
 */
public interface PictureInfoConstant {

    /**
     * 分类：单选，AI 只能从中选一个
     */
    List<String> CATEGORY_LIST = Arrays.asList("模板", "电商", "表情包", "素材", "海报");

    /**
     * 标签：多选，AI 只能从中挑，不因预填新增取值
     */
    List<String> TAG_LIST = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
}
