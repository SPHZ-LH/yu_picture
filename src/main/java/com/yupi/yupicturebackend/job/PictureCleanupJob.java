package com.yupi.yupicturebackend.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.manager.ImageSearchManager;
import com.yupi.yupicturebackend.mapper.CollectMapper;
import com.yupi.yupicturebackend.mapper.PictureMapper;
import com.yupi.yupicturebackend.model.entity.Collect;
import com.yupi.yupicturebackend.model.entity.Picture;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 逻辑删除图片的落地清理。
 * <p>
 * deletePicture 走逻辑删（isDelete = 1），COS 原图、缩略图等对象不会随之消失，
 * 不清理的话桶里会积累只被已删数据库行引用的孤儿对象。本任务定期把删除时刻
 * （由 MP 自动填充的 updateTime 充当）超过保留期的记录彻底清掉：
 * COS 对象 → 相似图库条目 → collect 关系行 → picture 行。
 * <p>
 * 保留期内的记录仍可从库里人工找回；COS 对象删除失败的记录本轮跳过、下个周期重试，
 * 对象没清干净绝不物理删行——反过来会出现「行没了对象还在」的永久孤儿。
 */
@Slf4j
@Component
public class PictureCleanupJob {

    /** 删除保留期（天） */
    private static final int DEFAULT_GRACE_DAYS = 7;

    /** 单批上限：COS 删除是逐个外呼，一批跑太久会拖住调度线程 */
    private static final int DEFAULT_BATCH_SIZE = 100;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private CollectMapper collectMapper;

    @Resource
    private CosManager cosManager;

    @Resource
    private ImageSearchManager imageSearchManager;

    /** 每天凌晨 3:30 跑一批 */
    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanupScheduled() {
        CleanupSummary summary = cleanupDeletedPictures(DEFAULT_GRACE_DAYS, DEFAULT_BATCH_SIZE);
        if (summary.getScanned() > 0) {
            log.info("逻辑删除图片清理完成: {}", summary.describe());
        }
    }

    /**
     * 清理一批删除时刻早于保留期的逻辑删图片。graceDays 允许传 0（立即清理），
     * maxCount 收敛在 1~500，防止单次外呼量失控。
     */
    public CleanupSummary cleanupDeletedPictures(int graceDays, int maxCount) {
        int safeGraceDays = Math.max(graceDays, 0);
        int safeBatchSize = Math.min(Math.max(maxCount, 1), 500);
        Date before = new Date(System.currentTimeMillis() - safeGraceDays * 24L * 3600_000L);
        List<Picture> deletedPictures = pictureMapper.selectDeletedBefore(before, safeBatchSize);

        CleanupSummary summary = new CleanupSummary();
        summary.setScanned(deletedPictures.size());
        for (Picture picture : deletedPictures) {
            // 原图与缩略图都要清；两个对象相互独立，一个失败也要尝试另一个
            boolean urlCleaned = deleteOwnObject(picture.getUrl(), "原图");
            boolean thumbnailCleaned = deleteOwnObject(picture.getThumbnailUrl(), "缩略图");
            if (!urlCleaned || !thumbnailCleaned) {
                summary.setFailed(summary.getFailed() + 1);
                continue;
            }
            // 相似图库条目是派生数据，软删时的同步可能告警失败，这里兜底重删；失败不阻塞行删除
            try {
                imageSearchManager.deleteImage(picture.getId());
            } catch (Exception e) {
                log.warn("相似图库条目清理失败, pictureId={}", picture.getId(), e);
            }
            try {
                collectMapper.delete(new LambdaQueryWrapper<Collect>().eq(Collect::getPictureId, picture.getId()));
                pictureMapper.deletePhysicallyById(picture.getId());
                summary.setCleaned(summary.getCleaned() + 1);
            } catch (Exception e) {
                log.warn("图片行清理失败, pictureId={}", picture.getId(), e);
                summary.setFailed(summary.getFailed() + 1);
            }
        }
        return summary;
    }

    /** 删除本桶对象；字段为空或指向外部域名（外链图）时无事可做，视为已清 */
    private boolean deleteOwnObject(String url, String label) {
        String objectKey = cosManager.tryExtractOwnObjectKey(url);
        if (objectKey == null || objectKey.isEmpty()) {
            return true;
        }
        try {
            cosManager.deleteObject(objectKey);
            return true;
        } catch (Exception e) {
            log.warn("COS {}对象删除失败, pictureUrl={}, key={}", label, url, objectKey, e);
            return false;
        }
    }

    /** 一轮清理的汇总（供日志与运维接口返回） */
    @Data
    public static class CleanupSummary {
        /** 本轮扫到的待清理记录数 */
        private int scanned;
        /** 成功物理删除的记录数 */
        private int cleaned;
        /** 本轮跳过（待下轮重试）的记录数 */
        private int failed;

        public String describe() {
            return String.format("扫描 %d 条，清理 %d 条，失败 %d 条", scanned, cleaned, failed);
        }
    }
}
