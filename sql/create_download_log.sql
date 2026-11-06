-- 创建数据库
CREATE DATABASE IF NOT EXISTS yu_picture;
-- 切换数据库
USE yu_picture;

-- 图片下载日志表：每次下载事件一行（不去重、不回改），是日/周/月榜单、排名趋势、
-- 用户下载历史与创作者下载聚合的唯一数据源。匿名下载 userId 为 NULL。
-- 纯追加型日志，不设 isDelete；当前量级不做清理，量起来后再加归档策略。
CREATE TABLE IF NOT EXISTS picture_download_log
(
    id         bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    pictureId  bigint                                  NOT NULL COMMENT '图片 id',
    userId     bigint                                             COMMENT '下载用户 id（匿名下载为 NULL）',
    createTime datetime     DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '下载时间',
    INDEX idx_pictureId_createTime (pictureId, createTime), -- 单图窗口计数（排名趋势）
    INDEX idx_userId_createTime (userId, createTime),         -- 下载历史分页
    INDEX idx_createTime (createTime)                          -- 榜单窗口扫描
) COMMENT '图片下载日志' COLLATE = utf8mb4_unicode_ci;
