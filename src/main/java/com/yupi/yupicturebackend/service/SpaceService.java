package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.dto.space.SpaceAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceEditRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceQueryRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceUpdateRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.SpaceVO;

/**
 * @author SPHZ
 * @description 针对表【space(空间)】的数据库操作Service
 */
public interface SpaceService extends IService<Space> {

    /**
     * 创建空间（每位用户仅能拥有一个私有空间）
     *
     * @param spaceAddRequest 创建请求
     * @param loginUser       登录用户
     * @return 空间 id
     */
    Long saveSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 更新空间（仅管理员，可修改空间级别并按级别重置配额）
     */
    boolean updateSpace(SpaceUpdateRequest spaceUpdateRequest, User loginUser);

    /**
     * 编辑空间（仅本人或管理员，仅可修改空间名称）
     */
    boolean editSpace(SpaceEditRequest spaceEditRequest, User loginUser);

    /**
     * 原子增减空间使用量（上传、删除图片时维护配额计数）
     *
     * @param spaceId    空间 id
     * @param sizeDelta  大小变化量（字节，可为负）
     * @param countDelta 数量变化量（可为负）
     */
    void updateSpaceUsage(Long spaceId, long sizeDelta, long countDelta);

    /**
     * 空间校验
     *
     * @param space 空间
     * @param add   是否为创建时校验
     */
    void validSpace(Space space, boolean add);

    /**
     * 获取空间视图（脱敏，关联用户信息）
     */
    SpaceVO getSpaceVO(Space space);

    /**
     * 分页获取空间视图
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage);

    /**
     * 构造空间查询条件
     */
    LambdaQueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);
}
