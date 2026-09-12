package com.yupi.yupicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 空间级别枚举
 */
@Getter
public enum SpaceLevelEnum {

    COMMON("普通版", 0, 100L * 1024 * 1024, 100L),
    PROFESSIONAL("专业版", 1, 1024L * 1024 * 1024, 1000L),
    FLAGSHIP("旗舰版", 2, 10L * 1024 * 1024 * 1024, 10000L);

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

    SpaceLevelEnum(String text, int value, Long defMaxSize, Long defMaxCount) {
        this.text = text;
        this.value = value;
        this.defMaxSize = defMaxSize;
        this.defMaxCount = defMaxCount;
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
