package com.yupi.yupicturebackend.model.dto.picture;

import com.yupi.yupicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 榜单查询请求（下载排行榜，日/周/月榜共用）。
 *
 * @author SPHZ
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class PictureRankQueryRequest extends PageRequest implements Serializable {

    /**
     * 榜单周期：day-日榜（近 24 小时）/ week-周榜（近 7 天）/ month-月榜（近 30 天）
     */
    private String period;

    /**
     * 分类筛选（picture.category，可空=全部）
     */
    private String category;

    private static final long serialVersionUID = 1L;
}
