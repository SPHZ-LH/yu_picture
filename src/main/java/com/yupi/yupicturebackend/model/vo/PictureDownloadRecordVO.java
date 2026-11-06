package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 下载历史条目视图：图片卡片信息 + 下载时间。
 *
 * @author SPHZ
 */
@Data
public class PictureDownloadRecordVO implements Serializable {

    /**
     * 图片视图
     */
    private PictureVO picture;

    /**
     * 下载时间
     */
    private Date downloadTime;

    private static final long serialVersionUID = 1L;
}
