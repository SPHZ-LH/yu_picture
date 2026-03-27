package com.yupi.yupicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 任务状态枚举
 * <ul>
 *     <li>PENDING：排队中</li>
 *     <li>RUNNING：处理中</li>
 *     <li>SUSPENDED：挂起</li>
 *     <li>SUCCEEDED：执行成功</li>
 *     <li>FAILED：执行失败</li>
 *     <li>UNKNOWN：任务不存在或状态未知</li>
 * </ul>
 */
@Getter
public enum TaskStatusEnum {
    PENDING("排队中", 0),
    RUNNING("处理中", 1),
    SUSPENDED("挂起", 2),
    SUCCEEDED("执行成功", 3),
    FAILED("执行失败", 4),
    UNKNOWN("任务不存在或状态未知", 5);

    private final String text;
    private final int value;

    TaskStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     */
    public static TaskStatusEnum getEnumByValue(Integer value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (TaskStatusEnum taskStatusEnum : TaskStatusEnum.values()) {
            if (taskStatusEnum.value == value) {
                return taskStatusEnum;
            }
        }
        return null;
    }
}
