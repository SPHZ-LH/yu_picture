package com.yupi.yupicturebackend.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author SPHZ
 * @version 1.0
 * @createDate 2026-03-09
 * @description 空间等级
 */
@Data
@AllArgsConstructor
public class SpaceLevel {

    private int value;

    private String text;

    private long maxCount;

    private long maxSize;
}
