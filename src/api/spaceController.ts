import { request } from '@/request'

/** 创建空间 */
export function addSpace(body: API.SpaceAddRequest) {
  return request.post<string>('/space/add', body)
}

/** 删除空间（管理员） */
export function deleteSpace(body: API.DeleteRequest) {
  return request.post<boolean>('/space/delete', body)
}

/** 更新空间（管理员，可改级别并按级别重置配额） */
export function updateSpace(body: API.SpaceUpdateRequest) {
  return request.post<boolean>('/space/update', body)
}

/** 编辑空间（仅修改空间名称） */
export function editSpace(body: API.SpaceEditRequest) {
  return request.post<boolean>('/space/edit', body)
}

/** 根据 id 获取空间（原始实体，管理员） */
export function getSpaceById(id: string) {
  return request.get<API.Space>('/space/get', { id })
}

/** 根据 id 获取空间视图（脱敏） */
export function getSpaceVOById(id: string) {
  return request.get<API.SpaceVO>('/space/get/vo', { id })
}

/** 分页获取空间（原始实体，管理员） */
export function listSpaceByPage(body: API.SpaceQueryRequest) {
  return request.post<API.Page<API.Space>>('/space/list/page', body)
}

/** 分页获取空间视图 */
export function listSpaceVOByPage(body: API.SpaceQueryRequest) {
  return request.post<API.Page<API.SpaceVO>>('/space/list/page/vo', body)
}

/** 获取我的空间列表（我创建的 + 我作为成员加入的） */
export function listMySpace() {
  return request.get<API.SpaceVO[]>('/space/mine')
}

// ==================== 空间成员 ====================

/** 添加成员（仅空间管理员） */
export function addSpaceMember(body: API.SpaceUserAddRequest) {
  return request.post<string>('/space/user/add', body)
}

/** 成员列表（任何成员可看） */
export function listSpaceMembers(spaceId: string) {
  return request.get<API.SpaceUserVO[]>('/space/user/list', { spaceId })
}

/** 修改成员角色（仅空间管理员） */
export function editSpaceMemberRole(body: API.SpaceUserEditRoleRequest) {
  return request.post<boolean>('/space/user/edit/role', body)
}

/** 移出成员（仅空间管理员） */
export function deleteSpaceMember(body: API.SpaceUserDeleteRequest) {
  return request.post<boolean>('/space/user/delete', body)
}

/** 搜索可邀请的用户（仅空间管理员，已排除现有成员） */
export function searchInvitableUsers(spaceId: string, searchText: string) {
  return request.get<API.UserVO[]>('/space/user/search', { spaceId, searchText })
}
