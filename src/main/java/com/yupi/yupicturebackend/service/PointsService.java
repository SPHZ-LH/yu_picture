package com.yupi.yupicturebackend.service;

import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.UserPointsVO;

/**
 * 积分服务：余额权威值在 user.points（原生 SQL 原子增减），流水落 points_log
 * 用于对账与幂等（uk_reason_relatedId 保证同一关联对象的退还只成功一次）。
 *
 * @author SPHZ
 */
public interface PointsService {

    /**
     * 查询当前用户积分余额
     *
     * @param loginUser 登录用户
     * @return 积分余额视图
     */
    UserPointsVO getMyPoints(User loginUser);

    /**
     * 注册赠送积分（尽力而为：注册已成功，赠送失败只告警，可由管理员补发）。
     *
     * @param userId 新用户 id
     */
    void grantRegisterBonus(Long userId);

    /**
     * 画面扩展扣分。必须在扩图任务提交成功<b>之后</b>调用（提交失败不扣分）。
     * 调用方已在提交前校验过余额；这里扣减若因并发花费而失败（返回 false），
     * 说明竞态下余额刚好被花掉——任务已创建，放行并告警，不追回。
     *
     * @param userId 用户 id
     * @param taskId 扩图任务 id（作为流水的关联对象，失败退款以此幂等）
     * @return true=已扣分；false=竞态下扣减失败（任务放行）
     */
    boolean deductForOutpainting(Long userId, String taskId);

    /**
     * 画面扩展任务失败退还。以 points_log 的唯一键幂等：同一 taskId 只可能退还一次。
     * 没有对应的扣分流水（如竞态下免费放行的任务）时不退。
     *
     * @param taskId 扩图任务 id
     */
    void refundForOutpainting(String taskId);

    /**
     * 管理员调整积分（正数发放、负数扣减；调整后余额不能为负）
     *
     * @param userId 用户 id
     * @param delta  变动值
     * @param note   备注（记入流水）
     */
    void adminAdjust(Long userId, int delta, String note);
}
