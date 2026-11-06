package com.yupi.yupicturebackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.DeleteRequest;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.space.SpaceAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceEditRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceQueryRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceUpdateRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.SpaceUser;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceTypeEnum;
import com.yupi.yupicturebackend.model.vo.SpaceVO;
import com.yupi.yupicturebackend.common.PageRequest;
import com.yupi.yupicturebackend.model.vo.SpaceActivityVO;
import com.yupi.yupicturebackend.service.SpaceActivityService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 空间接口
 */
@Slf4j
@RestController
@RequestMapping("/space")
public class SpaceController {

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private SpaceActivityService spaceActivityService;

    @Resource
    private UserService userService;

    /**
     * 创建空间
     */
    @PostMapping("/add")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR, "创建请求发送失败");
        User loginUser = userService.getLoginUser(request);
        long spaceId = spaceService.saveSpace(spaceAddRequest, loginUser);
        return ResultUtils.success(spaceId);
    }

    /**
     * 删除空间（仅管理员）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR, "删除请求发送失败");
        boolean result = spaceService.removeById(deleteRequest.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "空间删除失败");
        // 成员关系脱离空间没有留存价值，随空间物理删除（space_user 没有 isDelete，见根 docs/adr/0002）
        spaceUserService.remove(new LambdaQueryWrapper<SpaceUser>()
                .eq(SpaceUser::getSpaceId, deleteRequest.getId()));
        return ResultUtils.success(true);
    }

    /**
     * 更新空间（仅管理员，可修改空间级别并按级别重置配额）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean result = spaceService.updateSpace(spaceUpdateRequest, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 编辑空间（仅本人或管理员，仅可修改空间名称）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean result = spaceService.editSpace(spaceEditRequest, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取空间（仅管理员，原始实体）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        return ResultUtils.success(space);
    }

    /**
     * 根据 id 获取空间视图（脱敏）
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        // 可选登录：私有空间的元数据维持公开可读（匿名也能拿到壳），团队空间连壳都不露
        User loginUser = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        boolean isTeam = space.getSpaceType() != null && space.getSpaceType() == SpaceTypeEnum.TEAM.getValue();
        ThrowUtils.throwIf(isTeam && !spaceUserService.canView(space, loginUser),
                ErrorCode.FORBIDDEN_ERROR, "无权限查看该空间");
        return ResultUtils.success(spaceService.getSpaceVO(space, loginUser));
    }

    /**
     * 分页获取空间列表（仅管理员）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = spaceQueryRequest.getCurrent();
        long pageSize = spaceQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 条");
        Page<Space> spacePage = spaceService.page(new Page<>(current, pageSize), spaceService.getQueryWrapper(spaceQueryRequest));
        return ResultUtils.success(spacePage);
    }

    /**
     * 分页获取空间视图列表。
     * 不要求登录：匿名调用者能看到私有空间的元数据（维持现状），但看不到任何团队空间。
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,
                                                        HttpServletRequest request) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = spaceQueryRequest.getCurrent();
        long pageSize = spaceQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 条");
        User loginUser = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        // 可见性加进 SQL 条件而不是查完再过滤，否则页长会短于 pageSize、total 虚高
        LambdaQueryWrapper<Space> queryWrapper = spaceService.applyVisibility(
                spaceService.getQueryWrapper(spaceQueryRequest), loginUser);
        Page<Space> spacePage = spaceService.page(new Page<>(current, pageSize), queryWrapper);
        return ResultUtils.success(spaceService.getSpaceVOPage(spacePage, loginUser));
    }

    /**
     * 获取我的空间列表：我创建的 + 我作为成员加入的
     */
    @GetMapping("/mine")
    public BaseResponse<List<SpaceVO>> listMySpaces(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceService.listMySpaces(loginUser));
    }
    /**
     * 空间动态分页（按时间倒序；仅空间成员可查看）。
     * 图片已被删除的条目保留，picture 字段为 null。
     *
     * @param spaceId    空间 id
     * @param current    页码
     * @param pageSize   每页数量
     * @param request    前端传来的 session
     */
    @PostMapping("/activity/page")
    public BaseResponse<Page<SpaceActivityVO>> listSpaceActivities(
            @RequestParam("spaceId") Long spaceId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long pageSize,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PageRequest pageRequest = new PageRequest();
        pageRequest.setCurrent((int) current);
        pageRequest.setPageSize((int) pageSize);
        return ResultUtils.success(spaceActivityService.listSpaceActivities(spaceId, pageRequest, loginUser));
    }
}
