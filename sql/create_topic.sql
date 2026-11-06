-- 创建数据库
CREATE DATABASE IF NOT EXISTS yu_picture;
-- 切换数据库
USE yu_picture;

-- 图片专题表：编辑/策划人维护的作品合集（屿图「专题」板块）。
-- 精选位全站至多一条，由服务层在设置时清掉其他精选保证互斥。
CREATE TABLE IF NOT EXISTS topic
(
    id             bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    name           varchar(64)                             NOT NULL COMMENT '专题名',
    description    varchar(500)                                       COMMENT '专题简介',
    coverUrl       varchar(1024)                                      COMMENT '封面 URL',
    category       varchar(32)                                        COMMENT '专题分类（独立于图片分类词表）',
    curatorId      bigint                                             COMMENT '策划人用户 id',
    updateSchedule varchar(64)                                        COMMENT '更新节奏文案（如：每周五更新）',
    isFeatured     tinyint      DEFAULT 0                  NOT NULL COMMENT '是否本周精选（全站至多一条）',
    userId         bigint                                  NOT NULL COMMENT '创建人 id',
    editTime       datetime                                            COMMENT '编辑时间',
    createTime     datetime     DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '创建时间',
    updateTime     datetime     DEFAULT CURRENT_TIMESTAMP  NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete       tinyint      DEFAULT 0                  NOT NULL COMMENT '0-未删除 1-已删除',
    INDEX idx_category (category),
    INDEX idx_featured (isFeatured)
) COMMENT '图片专题' COLLATE = utf8mb4_unicode_ci;

-- 专题-图片关系表：图片加入专题即插即删（物理删除），入选资格在写入时校验
-- （公共图库过审图），查询时再 JOIN 兜底
CREATE TABLE IF NOT EXISTS topic_picture
(
    id         bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    topicId    bigint                                  NOT NULL COMMENT '专题 id',
    pictureId  bigint                                  NOT NULL COMMENT '图片 id',
    userId     bigint                                  NOT NULL COMMENT '添加人 id',
    createTime datetime     DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '加入时间',
    UNIQUE KEY uk_topicId_pictureId (topicId, pictureId),
    INDEX idx_pictureId (pictureId)
) COMMENT '专题-图片关系' COLLATE = utf8mb4_unicode_ci;
