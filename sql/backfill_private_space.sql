-- 给存量用户补建私有空间（见根 docs/adr/0002 与「注册即建私有空间」这条决定）
-- 新用户由 UserServiceImpl.userRegister 在同一事务内自动创建，本脚本只负责改造前就存在的账号。
-- 幂等：重复执行不会重复建空间、也不会重复插成员记录。

USE yu_picture;

-- ---------------------------------------------------------------------------
-- 1. 为每个还没有私有空间的未删除用户建一个普通版「我的私有空间」
--    配额三件套是 SpaceLevelEnum.COMMON 的值，写死在这里是纯 SQL 回填的无奈：
--    改 COMMON 的默认配额时，这里要跟着改，否则新老用户的私有空间配额会不一致。
--    id 不指定，走表的 AUTO_INCREMENT（与 MyBatis-Plus 的 IdType.AUTO 一致）。
-- ---------------------------------------------------------------------------
INSERT INTO space (spaceName, spaceLevel, maxSize, maxCount, totalSize, totalCount,
                   maxMemberCount, userId, spaceType, createTime, editTime, updateTime, isDelete)
SELECT '我的私有空间',
       0,
       100 * 1024 * 1024,
       100,
       0,
       0,
       5,
       u.id,
       0,
       NOW(),
       NOW(),
       NOW(),
       0
FROM user u
WHERE u.isDelete = 0
  AND NOT EXISTS (SELECT 1
                  FROM space s
                  WHERE s.userId = u.id
                    AND s.spaceType = 0
                    AND s.isDelete = 0);

-- ---------------------------------------------------------------------------
-- 2. 补齐创建者的 admin 成员记录
--    成员关系是访问空间的唯一依据，缺这条记录的空间谁都进不去。
--    覆盖上一步新建的空间，也兜住任何历史上漏写的空间。
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
-- 校验：未删除用户数应当等于「有私有空间的用户数」，missing 必须为 0
-- ---------------------------------------------------------------------------
SELECT (SELECT COUNT(*) FROM user WHERE isDelete = 0)                                        AS users,
       (SELECT COUNT(*)
        FROM user u
        WHERE u.isDelete = 0
          AND EXISTS (SELECT 1
                      FROM space s
                      WHERE s.userId = u.id AND s.spaceType = 0 AND s.isDelete = 0))          AS users_with_private_space,
       (SELECT COUNT(*)
        FROM user u
        WHERE u.isDelete = 0
          AND NOT EXISTS (SELECT 1
                          FROM space s
                          WHERE s.userId = u.id AND s.spaceType = 0 AND s.isDelete = 0))      AS missing;

SELECT s.id, s.spaceName, s.spaceType, s.spaceLevel, s.maxSize, s.maxCount, s.maxMemberCount,
       u.userAccount,
       (SELECT COUNT(*) FROM space_user su WHERE su.spaceId = s.id) AS memberCount
FROM space s
         JOIN user u ON u.id = s.userId
WHERE s.isDelete = 0
ORDER BY s.spaceType, s.id;
