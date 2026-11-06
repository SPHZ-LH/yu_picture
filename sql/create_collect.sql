-- 创建数据库
CREATE DATABASE IF NOT EXISTS yu_picture;
-- 切换数据库
USE yu_picture;

-- 用户收藏图片关系表：一条记录表示某用户收藏了某张图片
-- 取消收藏走物理删除：若用逻辑删除，删除后重新收藏会撞 uk_userId_pictureId 唯一键
CREATE TABLE IF NOT EXISTS collect
(
    id         bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    userId     bigint                                  NOT NULL COMMENT '用户 id',
    pictureId  bigint                                  NOT NULL COMMENT '图片 id',
    createTime datetime     DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '收藏时间',
    UNIQUE KEY uk_userId_pictureId (userId, pictureId), -- 唯一索引，同一用户对同一图片仅一条收藏记录
    INDEX idx_pictureId (pictureId)
) COMMENT '用户收藏图片关系' COLLATE = utf8mb4_unicode_ci;
