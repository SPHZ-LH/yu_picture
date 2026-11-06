package com.yupi.yupicturebackend.constant;

/**
 * 积分规则常量。余额权威值在 user.points，流水见 points_log（对账 + 幂等护栏）。
 *
 * @author SPHZ
 */
public interface PointsConstant {

    /**
     * 注册赠送积分
     */
    int REGISTER_GIFT = 100;

    /**
     * 画面扩展（AI 扩图）单次消耗
     */
    int OUTPAINTING_COST = 10;
}
