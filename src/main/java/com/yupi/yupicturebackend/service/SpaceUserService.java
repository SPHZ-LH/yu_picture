package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.dto.space.SpaceUserAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceUserDeleteRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceUserEditRoleRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.SpaceUser;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.vo.SpaceUserVO;
import com.yupi.yupicturebackend.model.vo.UserVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 空间成员关系（space_user 表）。
 * <p>
 * 成员关系是获得空间访问权的<b>唯一</b>依据：私有空间也有一条创建者的 admin 记录，
 * 所以鉴权只查这张表，不按 spaceType 分支。设计见根 docs/adr/0002。
 *
 * @author SPHZ
 */
public interface SpaceUserService extends IService<SpaceUser> {

    /**
     * 取登录用户在某空间的有效角色，非成员返回 null。
     * 站点管理员一律视为 admin——沿用改造前各处 {@code || isAdmin} 的语义，不退化管理端能力。
     */
    SpaceRoleEnum getEffectiveRole(Space space, User loginUser);

    /**
     * 校验登录用户在某空间是否达到 minRole 这一档，不满足抛 NO_AUTH_ERROR，空间为空抛 NOT_FOUND_ERROR
     */
    void checkPermission(Space space, User loginUser, SpaceRoleEnum minRole);

    /**
     * 同上，但用调用方给的提示语——上传、编辑、分析各处的失败原因不一样，
     * 统一成一句「无权限」会让前端没法告诉用户到底缺什么
     */
    void checkPermission(Space space, User loginUser, SpaceRoleEnum minRole, String errorMessage);

    /**
     * 同 {@link #checkPermission(Space, User, SpaceRoleEnum)}，按 spaceId 自行取空间
     */
    void checkPermission(Long spaceId, User loginUser, SpaceRoleEnum minRole);

    /**
     * 登录用户能否看到该空间（成员或站点管理员）。loginUser 为 null 时返回 false，不抛异常——
     * 供列表接口按行过滤使用。
     */
    boolean canView(Space space, User loginUser);

    /**
     * 写入创建者的 admin 成员记录。幂等：已存在则跳过（供创建空间与存量回填共用）
     */
    void saveCreatorAsAdmin(Long spaceId, Long userId);

    /**
     * 添加成员。要求调用者是该空间 admin；校验成员上限（含创建者）与目标用户存在。
     *
     * @return 成员关系 id
     */
    Long addMember(SpaceUserAddRequest request, User loginUser);

    /**
     * 修改成员角色。要求调用者是该空间 admin；创建者的角色不可改
     */
    boolean editMemberRole(SpaceUserEditRoleRequest request, User loginUser);

    /**
     * 移出成员。要求调用者是该空间 admin；创建者不可被移出
     */
    boolean removeMember(SpaceUserDeleteRequest request, User loginUser);

    /**
     * 成员列表（含用户信息与创建者标记）。任何成员都能看，非成员抛 NO_AUTH_ERROR
     */
    List<SpaceUserVO> listMembers(Long spaceId, User loginUser);

    /**
     * 当前成员数（含创建者）
     */
    long countMembers(Long spaceId);

    /**
     * 某用户有成员关系的空间 id 列表。私有空间也有成员记录，所以「我创建的」是它的子集
     */
    List<Long> listMySpaceIds(Long userId);

    /**
     * 批量取某用户在多个空间的角色，返回 spaceId -> 角色值。用于列表接口一次查完，避免逐行查库
     */
    Map<Long, String> mapMyRoles(Collection<Long> spaceIds, Long userId);

    /**
     * 搜索可邀请的用户（按账号或昵称模糊匹配，排除已是成员的人）。
     * 要求调用者是该空间 admin——把「能查用户」这个能力绑在「已经在管一个空间」上，
     * 避免任何登录用户都能拿它枚举全站账号。
     */
    List<UserVO> searchInvitableUsers(Long spaceId, String searchText, User loginUser);
}
