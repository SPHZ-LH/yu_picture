package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 预填图片信息的结果（名称/简介/分类/标签）。
 * <p>
 * 字段为空表示"模型没给出这一项"，调用方跳过该字段即可；整个对象为空表示本次读图没有产出可用内容。
 * 这是**建议**，不是图片的当前值——落库仍走 {@code /picture/edit}（docs/adr/0012）。
 *
 * @author SPHZ
 */
@Data
public class PictureAiPrefillVO implements Serializable {

    /**
     * 建议名称
     */
    private String name;

    /**
     * 建议简介
     */
    private String introduction;

    /**
     * 建议分类（必属既有分类词表）
     */
    private String category;

    /**
     * 建议标签（必属既有标签词表，最多 5 个）
     */
    private List<String> tags;

    private static final long serialVersionUID = 1L;
}
