package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.model.dto.space.SpaceAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceAdminAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceQueryRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.SpaceVO;

/**
 * @author SPHZ
 * @description 针对表【space(空间)】的数据库操作Service
 * @createDate 2026-03-03 16:26:36
 */
public interface SpaceService extends IService<Space> {

    /**
     * 创建空间
     *
     * @param spaceAddRequest 创建空间请求
     * @param loginUser       登入用户
     * @return 空间id
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 管理员创建空间（可为指定用户创建）
     *
     * @param spaceAdminAddRequest 管理员创建空间请求
     * @param loginUser            登入用户
     * @return 空间id
     */
    long adminAddSpace(SpaceAdminAddRequest spaceAdminAddRequest, User loginUser);

    /**
     * 获取查询条件
     *
     * @param spaceQueryRequest 查询请求
     * @return 查询条件
     */
    LambdaQueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 根据空间获取空间视图
     *
     * @param space 空间
     * @return 空间视图
     */
    SpaceVO getSpaceVO(Space space);

    /**
     * 根据空间视图获取空间
     *
     * @param spaceVO 空间视图
     * @return 空间
     */
    Space getSpace(SpaceVO spaceVO);


    /**
     * 根据空间分页获取空间视图分页
     *
     * @param spacePage 空间分页
     * @return 空间视图分页
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage);


    /**
     * 空间校验
     *
     * @param space 空间
     * @param add   新增判断
     */
    void validSpace(Space space, boolean add);

    /**
     * 根据空间级别填充限额数据
     *
     * @param space 空间
     */
    void fillSpaceBySpaceLevel(Space space);
}
