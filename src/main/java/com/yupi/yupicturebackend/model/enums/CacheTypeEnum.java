package com.yupi.yupicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 缓存类型枚举 —— 手动失效接口可以指定的目标缓存区。
 * <p>
 * 每新增一个缓存区，在这里加一个枚举值即可，接口形状不用变。
 *
 * @author SPHZ
 */
@Getter
public enum CacheTypeEnum {

    PICTURE("图片缓存", "picture"),
    USER("用户缓存", "user");

    private final String text;

    private final String value;

    CacheTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的 value
     * @return 枚举值；无法识别时返回 null
     */
    public static CacheTypeEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (CacheTypeEnum anEnum : CacheTypeEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
