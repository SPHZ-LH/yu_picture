-- 创建数据库
CREATE DATABASE IF NOT EXISTS yu_picture;
-- 切换数据库
USE yu_picture;

-- 图片点赞关系表（「喜欢」）：与收藏（collect）是两套体系——收藏是个人存档，
-- 点赞是内容认可。取消点赞走物理删除：逻辑删会撞 uk 唯一键导致无法重新点赞。
-- 不在 picture 表冗余 likeCount：设计稿只需要「获赞总数」聚合与本人点赞列表，
-- 有真实需要时再加计数列。
CREATE TABLE IF NOT EXISTS picture_like
(
    id         bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    userId     bigint                                  NOT NULL COMMENT '点赞用户 id',
    pictureId  bigint                                  NOT NULL COMMENT '图片 id',
    createTime datetime     DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '点赞时间',
    UNIQUE KEY uk_userId_pictureId (userId, pictureId),
    INDEX idx_pictureId (pictureId)
) COMMENT '图片点赞关系' COLLATE = utf8mb4_unicode_ci;
