-- 创建数据库
CREATE DATABASE IF NOT EXISTS yu_picture;
-- 切换数据库
USE yu_picture;

-- 用户关注关系表：follower 关注 followee。
-- 取关走物理删除：若用逻辑删除，取关后重新关注会撞 uk 唯一键（同 collect 的取舍）。
CREATE TABLE IF NOT EXISTS user_follow
(
    id         bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    followerId bigint                                  NOT NULL COMMENT '关注者 id',
    followeeId bigint                                  NOT NULL COMMENT '被关注者 id',
    createTime datetime     DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '关注时间',
    UNIQUE KEY uk_follower_followee (followerId, followeeId),
    INDEX idx_followee (followeeId)
) COMMENT '用户关注关系' COLLATE = utf8mb4_unicode_ci;
