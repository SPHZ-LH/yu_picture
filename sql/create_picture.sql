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

-- 缩略图与空间批量导入图 url（历史库已手工添加，此处补充建表脚本保持一致）
ALTER TABLE picture
    ADD COLUMN thumbnailUrl varchar(512) NULL COMMENT '缩略图 url' AFTER url,
    ADD COLUMN pictureUrl   varchar(512) NULL COMMENT '批量导入时的原始图片 url' AFTER thumbnailUrl;

-- 下载/收藏计数（历史库需执行本段增量）
-- 下载量：每次触发下载 +1，不去重；收藏量：由 collect 关系表（见 create_collect.sql）增减维护
ALTER TABLE picture
    ADD COLUMN downloadCount bigint DEFAULT 0 NOT NULL COMMENT '下载量',
    ADD COLUMN collectCount  bigint DEFAULT 0 NOT NULL COMMENT '收藏量';


-- 色系筛选（历史库需执行本段增量）：主色 hex 与色系桶
-- 由后端在上传/编辑时自动提取（见 PictureServiceImpl#fillColorInfo），提取失败留空不参与筛选
ALTER TABLE picture
    ADD COLUMN mainColor   char(7)     NULL COMMENT '主色 hex，如 #3B82F6',
    ADD COLUMN colorFamily varchar(16) NULL COMMENT '色系：blue/green/red/yellow/purple/pink/gray/dark';

-- 查询热路径索引（spaceId 历史库曾手工补加未入库，此处一并补记录保持一致）
-- spaceId：空间页列表查询恒带 spaceId = ?；colorFamily：首页色系筛选
CREATE INDEX idx_spaceId ON picture (spaceId);
CREATE INDEX idx_colorFamily ON picture (colorFamily);
