package com.yupi.yupicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 空间级别枚举
 */
@Getter
public enum SpaceLevelEnum {

    COMMON("普通版", 0, 100L * 1024 * 1024, 100L, 5L),
    PROFESSIONAL("专业版", 1, 1024L * 1024 * 1024, 1000L, 20L),
    FLAGSHIP("旗舰版", 2, 10L * 1024 * 1024 * 1024, 10000L, 50L);

    private final String text;
    private final int value;
    /**
     * 默认最大总大小（单位：字节）
     */
    private final Long defMaxSize;
    /**
     * 默认最大图片数量
     */
    private final Long defMaxCount;
    /**
     * 默认成员数量上限（含创建者）。只对团队空间有意义，私有空间恒为 1 条成员记录。
     */
    private final Long defMaxMemberCount;

    SpaceLevelEnum(String text, int value, Long defMaxSize, Long defMaxCount, Long defMaxMemberCount) {
        this.text = text;
        this.value = value;
        this.defMaxSize = defMaxSize;
        this.defMaxCount = defMaxCount;
        this.defMaxMemberCount = defMaxMemberCount;
    }

    /**
     * 根据 value 获取枚举
     */
    public static SpaceLevelEnum getEnumByValue(Integer value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (SpaceLevelEnum spaceLevelEnum : SpaceLevelEnum.values()) {
            if (spaceLevelEnum.value == value) {
                return spaceLevelEnum;
            }
        }
        return null;
    }
}
