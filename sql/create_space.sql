-- 创建数据库
CREATE DATABASE IF NOT EXISTS yu_picture;
-- 切换数据库
USE yu_picture;

-- 创建空间表
CREATE TABLE IF NOT EXISTS space
(
    id         bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    spaceName  varchar(128)                                DEFAULT NULL COMMENT '空间名称',
    spaceLevel int                                         DEFAULT 0 COMMENT '空间级别：0-普通版 1-专业版 2-旗舰版',
    maxSize    bigint                                      DEFAULT 0 COMMENT '空间图片的最大总大小',
    maxCount   bigint                                      DEFAULT 0 COMMENT '空间图片的最大数量',
    totalSize  bigint                                      DEFAULT 0 COMMENT '当前空间下图片的总大小',
    totalCount bigint                                      DEFAULT 0 COMMENT '当前空间下的图片数量',
    userId     bigint                                      NOT NULL COMMENT '创建用户 id',
    createTime datetime     DEFAULT CURRENT_TIMESTAMP      NOT NULL COMMENT '创建时间',
    editTime   datetime     DEFAULT CURRENT_TIMESTAMP      NOT NULL COMMENT '编辑时间',
    updateTime datetime     DEFAULT CURRENT_TIMESTAMP      NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete   tinyint      DEFAULT 0                      NOT NULL COMMENT '是否删除',
    spaceType  int          DEFAULT 0                      NOT NULL COMMENT '空间类型：0-私有 1-团队',
    INDEX idx_userId (userId),            -- 提升基于用户 id 的查询性能
    INDEX idx_spaceName (spaceName),      -- 提升基于空间名称的查询性能
    INDEX idx_spaceLevel (spaceLevel),    -- 提升基于空间级别的查询性能
    INDEX idx_spaceType (spaceType)       -- 提升基于空间类型的查询性能
) COMMENT '空间' COLLATE = utf8mb4_unicode_ci;

-- 创建空间用户关联表（团队空间成员，本期仅建表预留）
CREATE TABLE IF NOT EXISTS space_user
(
    id         bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    spaceId    bigint                                  NOT NULL COMMENT '空间 id',
    userId     bigint                                  NOT NULL COMMENT '用户 id',
    spaceRole  varchar(128) DEFAULT 'viewer'           NULL COMMENT '空间角色：viewer/editor/admin',
    createTime datetime     DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '创建时间',
    updateTime datetime     DEFAULT CURRENT_TIMESTAMP  NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_spaceId_userId (spaceId, userId), -- 唯一索引，一个用户在一个空间中仅有一条记录
    INDEX idx_spaceId (spaceId),
    INDEX idx_userId (userId)
) COMMENT '空间用户关联' COLLATE = utf8mb4_unicode_ci;

-- 图片表补充空间字段（已建库场景执行）
ALTER TABLE picture
    ADD COLUMN spaceId bigint NULL COMMENT '空间 id（为空表示公共图库）' AFTER reviewTime,
    ADD INDEX idx_spaceId (spaceId);
