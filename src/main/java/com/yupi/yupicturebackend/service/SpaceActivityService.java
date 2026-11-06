package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.common.PageRequest;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.SpaceActivityVO;

/**
 * 空间动态服务：空间内图片上传/编辑/删除事件的记录与查询（团队空间「空间动态」tab）。
 *
 * @author SPHZ
 */
public interface SpaceActivityService {

    /** 动作：图片上传 */
    String ACTION_UPLOAD = "upload";

    /** 动作：图片编辑（信息编辑/画面编辑/扩图保存） */
    String ACTION_EDIT = "edit";

    /** 动作：图片删除 */
    String ACTION_DELETE = "delete";

    /**
     * 记录一条空间动态（尽力而为：写失败只告警，绝不影响图片主流程）。
     * spaceId 为空（公共图库）时不记录。
     *
     * @param spaceId  空间 id，可空
     * @param userId   操作人 id
     * @param action   动作（upload/edit/delete）
     * @param targetId 目标图片 id
     */
    void recordActivity(Long spaceId, Long userId, String action, Long targetId);

    /**
     * 空间动态分页（按时间倒序；仅空间成员可查看）。
     * 图片已被删除的条目保留，picture 字段为 null。
     *
     * @param spaceId    空间 id
     * @param pageRequest 分页请求
     * @param loginUser   登录用户（用于成员权限校验）
     * @return 动态条目分页
     */
    Page<SpaceActivityVO> listSpaceActivities(Long spaceId, PageRequest pageRequest, User loginUser);
}
