-- 创建数据库
CREATE DATABASE IF NOT EXISTS yu_picture;
-- 切换数据库
USE yu_picture;

-- 团队空间动态表：空间内图片上传/编辑/删除事件的流水（team.html「空间动态」tab）。
-- 纯追加型日志，不设 isDelete；图片删除后动态条目保留（targetId 指向已删图片时 VO 图片信息为空）。
CREATE TABLE IF NOT EXISTS space_activity_log
(
    id         bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    spaceId    bigint                                  NOT NULL COMMENT '空间 id',
    userId     bigint                                  NOT NULL COMMENT '操作人 id',
    action     varchar(32)                             NOT NULL COMMENT '动作：upload/edit/delete',
    targetId   bigint                                             COMMENT '目标图片 id',
    createTime datetime     DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '发生时间',
    INDEX idx_spaceId_createTime (spaceId, createTime)
) COMMENT '空间动态' COLLATE = utf8mb4_unicode_ci;

-- 第三批功能列：主色板 / 编辑精选 / 今日一图 / 用户资料扩展 / 精选创作者
ALTER TABLE picture
    ADD COLUMN palette      varchar(255)                                    COMMENT '主色板 JSON（最多 5 个 hex）' AFTER colorFamily,
    ADD COLUMN isFeatured   tinyint      DEFAULT 0 NOT NULL                 COMMENT '是否编辑精选' AFTER palette,
    ADD COLUMN isDailyPick  tinyint      DEFAULT 0 NOT NULL                 COMMENT '是否今日一图' AFTER isFeatured;

ALTER TABLE user
    ADD COLUMN region             varchar(64)                             COMMENT '地区' AFTER collectPublic,
    ADD COLUMN website            varchar(256)                            COMMENT '个人网站' AFTER region,
    ADD COLUMN coverUrl           varchar(1024)                           COMMENT '公开主页封面 URL' AFTER website,
    ADD COLUMN isFeaturedCreator  tinyint      DEFAULT 0 NOT NULL         COMMENT '是否本周精选创作者' AFTER coverUrl;

-- 色系对齐（屿图 9 色系）：设计稿无「粉」点位，粉/紫同属青紫色段，历史数据并入 purple
UPDATE picture SET colorFamily = 'purple' WHERE colorFamily = 'pink';
