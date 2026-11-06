import router from '@/router'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { message } from 'ant-design-vue'
import { isAdminRole } from '@/utils'

/**
 * 全局路由守卫
 *
 * 1. 首次进入应用时从后端拉取一次登录态（Session Cookie）
 * 2. /admin/** 仅管理员可访问
 * 3. 路由 meta.requiresLogin 的页面需要登录
 */
router.beforeEach(async (to) => {
  const loginUserStore = useLoginUserStore()

  // 首次进入，先同步登录态，避免拿不到用户信息就做权限判断
  if (!loginUserStore.hasFetched) {
    await loginUserStore.fetchLoginUser()
  }

  const loginUser = loginUserStore.loginUser
  const isLoggedIn = Boolean(loginUser?.id)

  // 管理端权限
  if (to.meta?.requiresAdmin && !isAdminRole(loginUser?.userRole)) {
    message.error('没有权限，请使用管理员账号登录')
    return { path: '/user/login', query: { redirect: to.fullPath } }
  }

  // 需登录
  if (to.meta?.requiresLogin && !isLoggedIn) {
    message.warning('请先登录')
    return { path: '/user/login', query: { redirect: to.fullPath } }
  }

  return true
})

export default router
