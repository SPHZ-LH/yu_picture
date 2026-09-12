-- 空间使用量校准脚本：以 picture 表真实数据为准，重算每个空间的 totalSize / totalCount
-- 历史数据中可能存在计数漂移（如删除图片未同步扣减），执行一次即可
USE yu_picture;

UPDATE space s
SET s.totalSize  = IFNULL((SELECT SUM(p.picSize) FROM picture p WHERE p.spaceId = s.id AND p.isDelete = 0), 0),
    s.totalCount = IFNULL((SELECT COUNT(*) FROM picture p WHERE p.spaceId = s.id AND p.isDelete = 0), 0)
WHERE s.isDelete = 0;
