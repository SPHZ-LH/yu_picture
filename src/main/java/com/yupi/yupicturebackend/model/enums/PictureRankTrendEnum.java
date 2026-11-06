package com.yupi.yupicturebackend.model.enums;

import cn.hutool.core.util.StrUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 榜单排名趋势（当前窗口名次相对上一窗口的变化）。
 * <p>
 * up=上升、down=下降、flat=持平、new=新上榜（上一窗口前 100 名内无记录）。
 *
 * @author SPHZ
 */
public enum PictureRankTrendEnum {

    UP("上升", "up"),
    DOWN("下降", "down"),
    FLAT("持平", "flat"),
    NEW("新上榜", "new");

    private final String text;

    private final String value;

    PictureRankTrendEnum(String text, String value) {
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
    public static PictureRankTrendEnum getEnumByValue(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        for (PictureRankTrendEnum anEnum : PictureRankTrendEnum.values()) {
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
