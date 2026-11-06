-- 切换数据库
USE yu_picture;
-- 创建视频表（动态壁纸，设计与取舍见 docs/adr/0011）
CREATE TABLE IF NOT EXISTS video
(
    id            bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    url           varchar(512)                       NOT NULL COMMENT '视频 url',
    coverUrl      varchar(512)                       NULL COMMENT '封面 url（上传时由前端抽帧生成）',
    name          varchar(128)                       NOT NULL COMMENT '视频名称',
    introduction  varchar(512)                       NULL COMMENT '简介',
    category      varchar(64)                        NULL COMMENT '分类',
    tags          varchar(512)                       NULL COMMENT '标签（JSON 数组）',
    videoSize     bigint                             NULL COMMENT '视频体积',
    videoWidth    int                                NULL COMMENT '视频宽度（取自封面实测，非客户端自报）',
    videoHeight   int                                NULL COMMENT '视频高度（取自封面实测，非客户端自报）',
    videoScale    double                             NULL COMMENT '视频宽高比例',
    videoDuration int                                NULL COMMENT '视频时长（秒，客户端读 metadata 回传）',
    videoFormat   varchar(32)                        NULL COMMENT '视频格式',
    downloadCount bigint   DEFAULT 0                 NOT NULL COMMENT '下载量',
    userId        bigint                             NOT NULL COMMENT '创建用户 id',
    spaceId       bigint                             NULL COMMENT '空间 id（为空表示公共图库）',
    reviewStatus  int        DEFAULT 0               NOT NULL COMMENT '审核状态：0-待审核; 1-通过; 2-拒绝',
    reviewMessage varchar(512)                       NULL COMMENT '审核信息',
    reviewerId    bigint                             NULL COMMENT '审核人 ID',
    reviewTime    datetime                           NULL COMMENT '审核时间',
    createTime    datetime DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    editTime      datetime DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '编辑时间',
    updateTime    datetime DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete      tinyint  DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_name (name),                 -- 提升基于视频名称的查询性能
    INDEX idx_introduction (introduction), -- 用于模糊搜索简介
    INDEX idx_category (category),         -- 提升基于分类的查询性能
    INDEX idx_tags (tags),                 -- 提升基于标签的查询性能
    INDEX idx_userId (userId),             -- 提升基于用户 ID 的查询性能
    INDEX idx_spaceId (spaceId),           -- 提升按空间筛选的查询性能
    INDEX idx_reviewStatus (reviewStatus)  -- 提升审核列表的查询性能
) COMMENT '视频（动态壁纸）' COLLATE = utf8mb4_unicode_ci;
