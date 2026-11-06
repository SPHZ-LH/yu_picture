-- 团队空间成员化改造（见根 docs/adr/0002）
-- 已有库执行本脚本；全新建库直接跑 create_space.sql 即可，无需本脚本。
-- 幂等：四步都可重复执行，重复跑不会重复插数据、也不会因为列已存在而中断。

USE yu_picture;

-- ---------------------------------------------------------------------------
-- 1. space 表加成员上限列
--    MySQL 8 的 ADD COLUMN 不支持 IF NOT EXISTS，用 information_schema 判一次再拼 DDL
-- ---------------------------------------------------------------------------
SET @hasColumn = (SELECT COUNT(*)
                  FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME = 'space'
                    AND COLUMN_NAME = 'maxMemberCount');
SET @ddl = IF(@hasColumn = 0,
              'ALTER TABLE space ADD COLUMN maxMemberCount bigint DEFAULT 0 NOT NULL COMMENT ''成员数量上限（含创建者），按空间级别取默认值'' AFTER totalCount',
              'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 2. 按空间级别回填成员上限：普通版 5 / 专业版 20 / 旗舰版 50
--    与 SpaceLevelEnum 的 defMaxMemberCount 一致。这里无条件覆盖是有意的：
--    上限只由级别派生（代码里没有单独改它的入口），所以「按级别重算」永远等于正确值
-- ---------------------------------------------------------------------------
UPDATE space
SET maxMemberCount = CASE spaceLevel
                         WHEN 1 THEN 20
                         WHEN 2 THEN 50
                         ELSE 5
    END
WHERE isDelete = 0;

-- ---------------------------------------------------------------------------
-- 3. 为每个空间的创建者补一条 admin 成员记录
--    成员关系是访问空间的唯一依据，私有空间也要有这条记录，否则改造后谁都进不去。
--    NOT EXISTS 保证幂等；uk_spaceId_userId 是唯一键兜底
-- ---------------------------------------------------------------------------
INSERT INTO space_user (spaceId, userId, spaceRole, createTime, updateTime)
SELECT s.id, s.userId, 'admin', NOW(), NOW()
FROM space s
WHERE s.isDelete = 0
  AND s.userId IS NOT NULL
  AND NOT EXISTS (SELECT 1
                  FROM space_user su
                  WHERE su.spaceId = s.id
                    AND su.userId = s.userId);

-- ---------------------------------------------------------------------------
-- 4. 空间内已存在的待审核内容直接置为通过
--    改造后空间内容免审，但存量那批还卡在待审核：列表接口对所有调用者强制 reviewStatus=PASS，
--    结果是连上传者自己在空间里都看不到（实测有图片自 2026-03-15 卡至今）
-- ---------------------------------------------------------------------------
UPDATE picture
SET reviewStatus = 1
WHERE spaceId IS NOT NULL
  AND reviewStatus = 0
  AND isDelete = 0;

UPDATE video
SET reviewStatus = 1
WHERE spaceId IS NOT NULL
  AND reviewStatus = 0
  AND isDelete = 0;

-- ---------------------------------------------------------------------------
-- 校验：每个未删除的空间都应当恰好有一条创建者的 admin 成员记录
-- ---------------------------------------------------------------------------
SELECT s.id            AS spaceId,
       s.spaceName,
       s.spaceType,
       s.spaceLevel,
       s.maxMemberCount,
       (SELECT COUNT(*) FROM space_user su WHERE su.spaceId = s.id)                       AS memberCount,
       (SELECT COUNT(*)
        FROM space_user su
        WHERE su.spaceId = s.id AND su.userId = s.userId AND su.spaceRole = 'admin')      AS creatorAdminRow
FROM space s
WHERE s.isDelete = 0
ORDER BY s.id;
