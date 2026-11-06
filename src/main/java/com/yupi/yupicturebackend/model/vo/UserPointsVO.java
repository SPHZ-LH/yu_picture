package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 积分余额视图
 *
 * @author SPHZ
 */
@Data
public class UserPointsVO implements Serializable {

    /**
     * 积分余额
     */
    private Long points;

    private static final long serialVersionUID = 1L;
}
