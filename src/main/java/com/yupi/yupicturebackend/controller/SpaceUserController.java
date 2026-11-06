package com.yupi.yupicturebackend.controller;

import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.model.dto.space.SpaceUserAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceUserDeleteRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceUserEditRoleRequest;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.SpaceUserVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 空间成员接口。
 * <p>
 * 每个方法都只要求「已登录」（{@code mustRole = DEFAULT_ROLE}），空间内的角色判定在 Service 里做：
 * 增删改角色要该空间的 admin，看名单任何成员都行。设计见根 docs/adr/0002。
 */
@Slf4j
@RestController
@RequestMapping("/space/user")
public class SpaceUserController {

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private UserService userService;

    /**
     * 添加成员（仅空间管理员）
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Long> addMember(@RequestBody SpaceUserAddRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(spaceUserService.addMember(request, loginUser));
    }

    /**
     * 成员列表（任何成员可看，非成员看不到）
     */
    @GetMapping("/list")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<List<SpaceUserVO>> listMembers(@RequestParam long spaceId, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(spaceUserService.listMembers(spaceId, loginUser));
    }

    /**
     * 修改成员角色（仅空间管理员，创建者的角色不可改）
     */
    @PostMapping("/edit/role")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> editMemberRole(@RequestBody SpaceUserEditRoleRequest request,
                                                HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(spaceUserService.editMemberRole(request, loginUser));
    }

    /**
     * 移出成员（仅空间管理员，创建者不可被移出）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> removeMember(@RequestBody SpaceUserDeleteRequest request,
                                              HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(spaceUserService.removeMember(request, loginUser));
    }

    /**
     * 搜索可邀请的用户（仅空间管理员）。
     * <p>
     * 绑在 spaceId 上而不是开放给任意登录用户：能查用户的前提是已经在管一个空间，
     * 否则这个接口就成了全站账号枚举器。已登录用户的信息本身就是公开资料，返回脱敏的 UserVO。
     */
    @GetMapping("/search")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<List<UserVO>> searchInvitableUsers(@RequestParam long spaceId,
                                                           @RequestParam String searchText,
                                                           HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(spaceUserService.searchInvitableUsers(spaceId, searchText, loginUser));
    }
}
