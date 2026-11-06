package com.yupi.yupicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量查询关注状态请求（创作者列表一次渲染全部关注按钮）
 *
 * @author SPHZ
 */
@Data
public class UserFollowBatchRequest implements Serializable {

    /**
     * 被关注者 id 列表
     */
    private List<Long> userIds;

    private static final long serialVersionUID = 1L;
}
