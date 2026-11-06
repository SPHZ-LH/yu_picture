<template>
  <a-config-provider :theme="antTheme">
    <div class="exception-page" :class="{ 'theme-gallery': themeStore.isDark }">
      <a-result status="404" title="404" sub-title="抱歉，你访问的页面不存在">
        <template #extra>
          <a-button type="primary" @click="router.push('/')">返回公共图库</a-button>
        </template>
      </a-result>
    </div>
  </a-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { GALLERY_ANT_THEME_DARK, GALLERY_ANT_THEME_LIGHT } from '@/constant/theme'
import { useThemeStore } from '@/stores/useThemeStore'

const router = useRouter()
const themeStore = useThemeStore()

const antTheme = computed(() => (themeStore.isDark ? GALLERY_ANT_THEME_DARK : GALLERY_ANT_THEME_LIGHT))
</script>

<style scoped>
.exception-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--app-bg, #f5f7fa);
}
</style>
