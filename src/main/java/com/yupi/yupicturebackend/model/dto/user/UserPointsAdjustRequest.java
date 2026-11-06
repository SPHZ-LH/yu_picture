package com.yupi.yupicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员调整积分请求
 *
 * @author SPHZ
 */
@Data
public class UserPointsAdjustRequest implements Serializable {

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 变动值，正数发放、负数扣减，调整后余额不能为负
     */
    private Integer delta;

    /**
     * 备注（记入流水）
     */
    private String note;

    private static final long serialVersionUID = 1L;
}
