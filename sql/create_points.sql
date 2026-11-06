-- 创建数据库
CREATE DATABASE IF NOT EXISTS yu_picture;
-- 切换数据库
USE yu_picture;

-- 积分流水表：每笔积分变动一行，余额的权威值在 user.points，本表用于对账与幂等。
-- uk_reason_relatedId 保证同一关联对象（如扩图任务 id）的退还只可能成功一次——
-- 扩图失败退款以此为幂等护栏，MySQL 唯一索引对 NULL relatedId 不去重（注册/管理员行无关联对象）。
CREATE TABLE IF NOT EXISTS points_log
(
    id         bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    userId     bigint                                  NOT NULL COMMENT '用户 id',
    delta      int                                     NOT NULL COMMENT '积分变动（正得负耗）',
    balance    int                                     NOT NULL COMMENT '变动后余额',
    reason     varchar(32)                             NOT NULL COMMENT '变动原因：register/admin/outpainting/outpainting_refund',
    relatedId  varchar(64)                                        COMMENT '关联对象 id（扩图任务 id 等）',
    note       varchar(128)                                       COMMENT '备注（管理员调整时填写）',
    createTime datetime     DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '变动时间',
    INDEX idx_userId_createTime (userId, createTime),
    UNIQUE KEY uk_reason_relatedId (reason, relatedId)
) COMMENT '积分流水' COLLATE = utf8mb4_unicode_ci;

-- user 表扩展：积分余额 + 收藏列表公开开关
ALTER TABLE user
    ADD COLUMN points        bigint  DEFAULT 0 NOT NULL COMMENT '积分余额' AFTER userRole,
    ADD COLUMN collectPublic tinyint DEFAULT 0 NOT NULL COMMENT '收藏列表是否公开' AFTER points;
