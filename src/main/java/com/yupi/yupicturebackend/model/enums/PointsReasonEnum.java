package com.yupi.yupicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 积分变动原因。
 * <p>
 * register=注册赠送、admin=管理员调整、outpainting=画面扩展扣分、
 * outpainting_refund=扩展任务失败退还。
 *
 * @author SPHZ
 */
public enum PointsReasonEnum {

    REGISTER("注册赠送", "register"),
    ADMIN("管理员调整", "admin"),
    OUTPAINTING("画面扩展扣分", "outpainting"),
    OUTPAINTING_REFUND("画面扩展退还", "outpainting_refund");

    private final String text;

    private final String value;

    PointsReasonEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 获取值
     */
    public String getValue() {
        return value;
    }

    /**
     * 获取文本
     */
    public String getText() {
        return text;
    }

    /**
     * 根据 value 获取枚举，未匹配返回 null
     */
    public static PointsReasonEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (PointsReasonEnum anEnum : PointsReasonEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    /**
     * 获取全部枚举值列表
     */
    public static List<String> getAllValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }
}
