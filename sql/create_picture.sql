-- 创建数据库
CREATE DATABASE IF NOT EXISTS yu_picture;
-- 切换数据库
USE yu_picture;
-- 创建图库表
CREATE TABLE IF NOT EXISTS picture
(
    id           bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    url          varchar(512)                       NOT NULL COMMENT '图片 url',
    name         varchar(128)                       NOT NULL COMMENT '图片名称',
    introduction varchar(512)                       NULL COMMENT '简介',
    category     varchar(64)                        NULL COMMENT '分类',
    tags         varchar(512)                       NULL COMMENT '标签（JSON 数组）',
    picSize      bigint                             NULL COMMENT '图片体积',
    picWidth     int                                NULL COMMENT '图片宽度',
    picHeight    int                                NULL COMMENT '图片高度',
    picScale     double                             NULL COMMENT '图片宽高比例',
    picFormat    varchar(32)                        NULL COMMENT '图片格式',
    userId       bigint                             NOT NULL COMMENT '创建用户 id',
    createTime   datetime DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    editTime     datetime DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '编辑时间',
    updateTime   datetime DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete     tinyint  DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_name (name),                 -- 提升基于图片名称的查询性能
    INDEX idx_introduction (introduction), -- 用于模糊搜索图片简介
    INDEX idx_category (category),         -- 提升基于分类的查询性能
    INDEX idx_tags (tags),                 -- 提升基于标签的查询性能
    INDEX idx_userId (userId)              -- 提升基于用户 ID 的查询性能
) COMMENT '图片' COLLATE = utf8mb4_unicode_ci;

ALTER TABLE picture
    -- 添加新列
    ADD COLUMN reviewStatus  INT DEFAULT 0 NOT NULL COMMENT '审核状态：0-待审核; 1-通过; 2-拒绝',
    ADD COLUMN reviewMessage VARCHAR(512)  NULL COMMENT '审核信息',
    ADD COLUMN reviewerId    BIGINT        NULL COMMENT '审核人 ID',
    ADD COLUMN reviewTime    DATETIME      NULL COMMENT '审核时间';

-- 创建基于 reviewStatus 列的索引
CREATE INDEX idx_reviewStatus ON picture (reviewStatus);

