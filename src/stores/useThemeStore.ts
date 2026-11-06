import { defineStore } from 'pinia'
import { ref } from 'vue'

const STORAGE_KEY = 'yu-picture-theme'

/**
 * 站点主题状态：dark-「暗房灯箱」暗色（默认）；light-浅色。
 * 持久化到 localStorage，刷新与跨页面保持一致。
 */
export const useThemeStore = defineStore('theme', () => {
  const isDark = ref(localStorage.getItem(STORAGE_KEY) !== 'light')

  function toggleTheme() {
    isDark.value = !isDark.value
    localStorage.setItem(STORAGE_KEY, isDark.value ? 'dark' : 'light')
  }

  return { isDark, toggleTheme }
})
