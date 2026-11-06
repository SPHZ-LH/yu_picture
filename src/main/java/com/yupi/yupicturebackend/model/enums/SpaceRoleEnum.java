package com.yupi.yupicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Getter;

/**
 * 空间角色（{@code space_user.spaceRole}）。
 * <p>
 * 存库的是英文小写值，中文只用于展示。level 用来做「最低角色」比较：
 * 数值大的角色天然包含数值小的角色的权限。
 * <p>
 * 注意别和站点级的 {@code user.userRole}（user/admin）混为一谈：空间角色按空间分别授予，
 * 同一个人在不同空间里可以是不同角色。也别和后端 CONTEXT.md 的「访问者 (Viewer)」混淆——
 * 那是指任何发起查询的人（含匿名），这里的 viewer 是一种被授予的身份，中文一律写「只读成员」。
 */
@Getter
public enum SpaceRoleEnum {

    ADMIN("管理员", "admin", 3),
    EDITOR("编辑者", "editor", 2),
    VIEWER("只读成员", "viewer", 1);

    private final String text;
    private final String value;
    private final int level;

    SpaceRoleEnum(String text, String value, int level) {
        this.text = text;
        this.value = value;
        this.level = level;
    }

    public static SpaceRoleEnum getEnumByValue(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        for (SpaceRoleEnum spaceRoleEnum : SpaceRoleEnum.values()) {
            if (spaceRoleEnum.value.equals(value)) {
                return spaceRoleEnum;
            }
        }
        return null;
    }

    /**
     * 当前角色是否达到 min 这一档（含）以上
     */
    public boolean atLeast(SpaceRoleEnum min) {
        return min == null || this.level >= min.level;
    }

    /**
     * 取角色的中文名，未识别的取值原样返回，避免展示成空白
     */
    public static String getTextByValue(String value) {
        SpaceRoleEnum spaceRoleEnum = getEnumByValue(value);
        return spaceRoleEnum == null ? value : spaceRoleEnum.getText();
    }

    /**
     * 判断取值是否合法
     */
    public static boolean isValid(String value) {
        return ObjUtil.isNotNull(getEnumByValue(value));
    }
}
