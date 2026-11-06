import { request } from '@/request'

/** 用户注册 */
export function registerUser(body: API.UserRegisterRequest) {
  return request.post<string>('/user/register', body)
}

/** 用户登录 */
export function loginUser(body: API.UserLoginRequest) {
  return request.post<API.LoginUserVO>('/user/login', body)
}

/** 获取当前登录用户（脱敏）。未登录返回 40100，属正常情况，不弹提示 */
export function getLoginUser() {
  return request.get<API.LoginUserVO>('/user/get/login', undefined, { skipErrorMessage: true })
}

/** 用户注销 */
export function logoutUser() {
  return request.post<boolean>('/user/logout')
}

/** 添加用户（管理员） */
export function addUser(body: API.UserAddRequest) {
  return request.post<string>('/user/add', body)
}

/** 根据 id 获取用户（原始实体，管理员） */
export function getUserById(id: string) {
  return request.get<API.User>('/user/get', { id })
}

/** 根据 id 获取用户视图（脱敏） */
export function getUserVOById(id: string) {
  return request.get<API.UserVO>('/user/get/vo', { id })
}

/** 删除用户（管理员） */
export function deleteUser(body: API.DeleteRequest) {
  return request.post<boolean>('/user/delete', body)
}

/** 更新用户（管理员） */
export function updateUser(body: API.UserUpdateRequest) {
  return request.post<boolean>('/user/update', body)
}

/** 自助修改本人资料 */
export function updateMyUser(body: API.UserUpdateMyRequest) {
  return request.post<boolean>('/user/update/my', body)
}

/** 自助修改本人密码 */
export function updateMyPassword(body: API.UserUpdatePasswordRequest) {
  return request.post<boolean>('/user/update/password', body)
}

/** 分页获取用户视图（管理员） */
export function listUserVOByPage(body: API.UserQueryRequest) {
  return request.post<API.Page<API.UserVO>>('/user/list/page/vo', body)
}
