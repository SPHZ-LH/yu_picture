package com.yupi.yupicturebackend.model.dto;

import lombok.Data;

/**
 * 榜单窗口内单张图片的下载计数（picture_download_log 聚合查询结果行）。
 * <p>
 * 只承载 pictureId 与窗口内下载量，图片详情由服务层按 id 批量回填。
 *
 * @author SPHZ
 */
@Data
public class PictureDownloadStat {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 窗口内下载次数
     */
    private Long downloadCount;
}
