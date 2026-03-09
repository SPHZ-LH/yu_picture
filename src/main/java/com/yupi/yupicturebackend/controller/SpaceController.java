package com.yupi.yupicturebackend.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.DeleteRequest;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.space.SpaceAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceEditRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceQueryRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceUpdateRequest;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.SpaceLevel;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceLevelEnum;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author SPHZ
 * @version 1.0
 * @createDate 2026-03-06
 * @description 空间接口
 */
@Slf4j
@RestController
@RequestMapping("/space")
public class SpaceController {

    @Resource
    private SpaceService spaceService;
    @Resource
    private UserService userService;
    @Resource
    private PictureService pictureService;
    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 创建空间
     *
     * @param spaceAddRequest 创建空间请求
     * @param request         前端发送的session
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> addSpace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR, "增加请求发送失败");
        long l = spaceService.addSpace(spaceAddRequest, userService.getLoginUser(request));
        if (l > 0) {
            return ResultUtils.success(true);
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR);
    }

    /**
     * 根据id删除空间（空间创建人和管理员）
     *
     * @param deleteRequest 删除请求
     * @param request       前端传来的 session
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR, "删除请求发送失败");
        // 判断空间是否存在
        Long id = deleteRequest.getId();
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);

        // 判断是否为上传用户或管理员
        User loginUser = userService.getLoginUser(request);
        String userRole = loginUser.getUserRole();
        if (space.getUserId().equals(loginUser.getId()) || UserConstant.ADMIN_ROLE.equals(userRole)) {
            // 1. 查询空间内的所有图片
            List<Picture> pictureList = pictureService.lambdaQuery()
                    .eq(Picture::getSpaceId, id)
                    .list();
            
            // 2. 在事务中执行删除操作，确保数据一致性
            Boolean transactionSuccess = transactionTemplate.execute(status -> {
                // 2.1 删除空间
                boolean result = spaceService.removeById(id);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除空间失败");
                
                // 2.2 删除空间内的所有图片（逻辑删除）
                if (!pictureList.isEmpty()) {
                    List<Long> pictureIds = pictureList.stream()
                            .map(Picture::getId)
                            .collect(Collectors.toList());
                    boolean deletePicturesResult = pictureService.removeByIds(pictureIds);
                    ThrowUtils.throwIf(!deletePicturesResult, ErrorCode.OPERATION_ERROR, "删除空间图片失败");
                }
                return true;
            });
            
            // 3. 只有事务成功后才异步清理图片文件，避免数据不一致
            if (Boolean.TRUE.equals(transactionSuccess) && !pictureList.isEmpty()) {
                pictureService.clearPictureFiles(pictureList);
            }
            
            return ResultUtils.success(true);
        }
        throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
    }

    /**
     * 更新空间信息（仅管理员）
     *
     * @param spaceUpdateRequest 更新请求
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest) {
        if (spaceUpdateRequest == null || spaceUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);
        // 自动填充数据
        spaceService.fillSpaceBySpaceLevel(space);
        // 数据校验
        spaceService.validSpace(space, false);
        // 判断是否存在
        long spaceId = spaceUpdateRequest.getId();
        Space oldSpace = spaceService.getById(spaceId);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 编辑空间
     *
     * @param spaceEditRequest 编辑空间请求
     */
    @PostMapping("/edit")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceEditRequest == null || spaceEditRequest.getId() <= 0, ErrorCode.PARAMS_ERROR, "编辑请求发送失败");
        // 判断空间是否存在
        Long spaceId = spaceEditRequest.getId();
        Space oldSpace = spaceService.getById(spaceId);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        // 判断是否为用户或管理员
        User loginUser = userService.getLoginUser(request);
        Long loginUserId = loginUser.getId();
        Long userId = oldSpace.getUserId();
        ThrowUtils.throwIf(!userId.equals(loginUserId) && !userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);

        // 关联数据
        Space space = new Space();
        BeanUtil.copyProperties(spaceEditRequest, space);
        space.setEditTime(new Date());
        // 自动补充空间数据
        spaceService.fillSpaceBySpaceLevel(space);
        // 空间数据校验
        spaceService.validSpace(space, false);

        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据查询条件查询空间列表（仅管理员）
     *
     * @param spaceQueryRequest 查询请求
     * @return 空间列表
     */
    @PostMapping("/list/space")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int current = spaceQueryRequest.getCurrent();
        int pageSize = spaceQueryRequest.getPageSize();
        Page<Space> spacePage = spaceService.page(new Page<>(current, pageSize), spaceService.getQueryWrapper(spaceQueryRequest));
        return ResultUtils.success(spacePage);
    }

    /**
     * 根据id获取空间（管理员）
     *
     * @param id 空间 id
     * @return 空间
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(@RequestParam long id) {

        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);

        return ResultUtils.success(space);
    }

    /**
     * 空间等级
     */
    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values()) // 获取所有枚举
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }


}
