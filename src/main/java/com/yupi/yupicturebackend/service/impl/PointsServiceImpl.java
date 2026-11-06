package com.yupi.yupicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.yupicturebackend.constant.PointsConstant;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.PointsLogMapper;
import com.yupi.yupicturebackend.mapper.UserMapper;
import com.yupi.yupicturebackend.model.entity.PointsLog;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PointsReasonEnum;
import com.yupi.yupicturebackend.model.vo.UserPointsVO;
import com.yupi.yupicturebackend.service.PointsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author SPHZ
 * @description 积分服务实现：变动全部走 user 表原生 SQL 原子增减（WHERE 自带余额守卫），
 * 流水写入失败会回滚变动，保证余额与流水最终一致
 * @createDate 2026-09-27
 */
@Slf4j
@Service
public class PointsServiceImpl implements PointsService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private PointsLogMapper pointsLogMapper;

    @Override
    public UserPointsVO getMyPoints(User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登入");
        User user = userMapper.selectById(loginUser.getId());
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        UserPointsVO vo = new UserPointsVO();
        vo.setPoints(user.getPoints() == null ? 0L : user.getPoints());
        return vo;
    }

    @Override
    public void grantRegisterBonus(Long userId) {
        try {
            int result = doChange(userId, PointsConstant.REGISTER_GIFT, PointsReasonEnum.REGISTER, null, null);
            if (result != CHANGE_OK) {
                log.warn("注册赠送积分失败, userId={}, result={}", userId, result);
            }
        } catch (Exception e) {
            // 注册已成功，赠送失败不回滚注册；流水可由管理员补发
            log.warn("注册赠送积分异常, userId={}", userId, e);
        }
    }

    @Override
    public boolean deductForOutpainting(Long userId, String taskId) {
        int result = doChange(userId, -PointsConstant.OUTPAINTING_COST, PointsReasonEnum.OUTPAINTING, taskId, null);
        if (result == CHANGE_OK) {
            return true;
        }
        // 任务已在百炼侧创建，此时放弃任务的代价大于送出 10 积分——放行并告警
        log.warn("扩图扣分未生效（竞态余额不足或用户不存在），任务放行, userId={}, taskId={}", userId, taskId);
        return false;
    }

    @Override
    public void refundForOutpainting(String taskId) {
        ThrowUtils.throwIf(taskId == null || taskId.isEmpty(), ErrorCode.PARAMS_ERROR);
        // 没有对应扣分流水（如竞态下免费放行）时不退
        PointsLog deduction = pointsLogMapper.selectOne(new LambdaQueryWrapper<PointsLog>()
                .eq(PointsLog::getReason, PointsReasonEnum.OUTPAINTING.getValue())
                .eq(PointsLog::getRelatedId, taskId)
                .last("LIMIT 1"));
        if (deduction == null) {
            log.info("扩图任务无扣分流水，跳过退款, taskId={}", taskId);
            return;
        }
        // 已退过直接幂等返回（正常路径由唯一键拦截，这里省一次无用变动）
        long refunded = pointsLogMapper.selectCount(new LambdaQueryWrapper<PointsLog>()
                .eq(PointsLog::getReason, PointsReasonEnum.OUTPAINTING_REFUND.getValue())
                .eq(PointsLog::getRelatedId, taskId));
        if (refunded > 0) {
            return;
        }
        int result = doChange(deduction.getUserId(), PointsConstant.OUTPAINTING_COST,
                PointsReasonEnum.OUTPAINTING_REFUND, taskId, null);
        if (result == CHANGE_DUPLICATE) {
            log.info("扩图退款已被并发处理, taskId={}", taskId);
        } else if (result != CHANGE_OK) {
            log.warn("扩图退款失败, taskId={}, userId={}, result={}", taskId, deduction.getUserId(), result);
        }
    }

    @Override
    public void adminAdjust(Long userId, int delta, String note) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(delta == 0, ErrorCode.PARAMS_ERROR, "变动值不能为 0");
        ThrowUtils.throwIf(Math.abs(delta) > 1_000_000, ErrorCode.PARAMS_ERROR, "变动值过大");
        if (note != null) {
            ThrowUtils.throwIf(note.length() > 128, ErrorCode.PARAMS_ERROR, "备注过长");
        }
        ThrowUtils.throwIf(userMapper.selectById(userId) == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        int result = doChange(userId, delta, PointsReasonEnum.ADMIN, null, note);
        if (result == CHANGE_REJECTED) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "调整后积分不能为负");
        }
        ThrowUtils.throwIf(result != CHANGE_OK, ErrorCode.OPERATION_ERROR, "积分调整失败");
    }

    // ==================== 变动核心 ====================

    private static final int CHANGE_OK = 0;

    /** 原子更新 0 行：余额不足（调整后为负）或用户不存在 */
    private static final int CHANGE_REJECTED = 1;

    /** 流水唯一键冲突：该关联对象的变动已被处理过，本次变动已回滚 */
    private static final int CHANGE_DUPLICATE = 2;

    /**
     * 变动核心：原子更新余额 → 读回新余额 → 写流水。
     * 流水唯一键（reason+relatedId）冲突时回滚本次变动——这是退款幂等的护栏：
     * 并发退款只有一个能写进流水，另一个连本带利回滚。
     *
     * @return {@link #CHANGE_OK} / {@link #CHANGE_REJECTED} / {@link #CHANGE_DUPLICATE}
     */
    private int doChange(Long userId, int delta, PointsReasonEnum reason, String relatedId, String note) {
        int updated = userMapper.adjustPoints(userId, delta);
        if (updated <= 0) {
            return CHANGE_REJECTED;
        }
        User fresh = userMapper.selectById(userId);
        PointsLog logRow = new PointsLog();
        logRow.setUserId(userId);
        logRow.setDelta(delta);
        logRow.setBalance(fresh == null || fresh.getPoints() == null ? 0 : fresh.getPoints().intValue());
        logRow.setReason(reason.getValue());
        logRow.setRelatedId(relatedId);
        logRow.setNote(note);
        try {
            pointsLogMapper.insert(logRow);
        } catch (DuplicateKeyException e) {
            userMapper.adjustPoints(userId, -delta);
            return CHANGE_DUPLICATE;
        }
        return CHANGE_OK;
    }
}
