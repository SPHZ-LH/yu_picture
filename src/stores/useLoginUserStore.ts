import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getLoginUser } from '@/api/userController'

/**
 * 登录用户全局状态
 *
 * 说明：后端基于 Session Cookie 维持登录态，前端只在内存中保存用户信息，
 * 页面刷新后通过 fetchLoginUser() 重新拉取。
 */
export const useLoginUserStore = defineStore('loginUser', () => {
  /** 当前登录用户，未登录时 userRole 为空 */
  const loginUser = ref<API.LoginUserVO>({
    userName: '未登录',
  })

  /** 是否已从后端拉取过登录态 */
  const hasFetched = ref(false)

  /** 从后端拉取当前登录用户 */
  async function fetchLoginUser() {
    try {
      const res = await getLoginUser()
      if (res.code === 0 && res.data) {
        loginUser.value = res.data
      } else {
        loginUser.value = { userName: '未登录' }
      }
    } catch {
      loginUser.value = { userName: '未登录' }
    } finally {
      hasFetched.value = true
    }
  }

  /** 登录成功后写入用户信息 */
  function setLoginUser(user: API.LoginUserVO) {
    loginUser.value = user
  }

  /** 退出登录后清空用户信息 */
  function clearLoginUser() {
    loginUser.value = { userName: '未登录' }
  }

  return { loginUser, hasFetched, fetchLoginUser, setLoginUser, clearLoginUser }
})
