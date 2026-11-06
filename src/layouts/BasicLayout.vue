<template>
  <a-config-provider :theme="antTheme">
    <a-layout
      class="basic-layout"
      :class="{ 'theme-gallery': themeStore.isDark && !isYutu, 'basic-layout--yutu': isYutu }"
    >
      <GlobalHeader />

      <a-layout-content class="basic-layout__content">
        <div class="basic-layout__inner">
          <RouterView v-slot="{ Component }">
            <transition name="fade-slide" mode="out-in">
              <component :is="Component" />
            </transition>
          </RouterView>
        </div>
      </a-layout-content>

      <a-layout-footer v-if="!isYutu" class="basic-layout__footer">
        <RouterLink to="/" class="basic-layout__footer-brand">云图库</RouterLink>
        <span>给电脑和手机挑张好壁纸</span>
        <a-divider type="vertical" />
        <a
          href="http://localhost:8080/api/doc.html"
          target="_blank"
          rel="noreferrer noopener"
        >
          接口文档
        </a>
        <a-divider type="vertical" />
        <span>© {{ new Date().getFullYear() }}</span>
      </a-layout-footer>
    </a-layout>
  </a-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { GALLERY_ANT_THEME_DARK, GALLERY_ANT_THEME_LIGHT } from '@/constant/theme'
import { useThemeStore } from '@/stores/useThemeStore'
import GlobalHeader from '@/components/GlobalHeader.vue'

const themeStore = useThemeStore()
const route = useRoute()

/** 屿图改版页面：自带设计系统，走透明布局（无旧页 padding/背景/旧页脚，antd 恒浅色） */
const YUTU_PATHS = ['/', '/topics', '/ranking', '/creators']
const isYutu = computed(() => YUTU_PATHS.some((p) => route.path === p || route.path.startsWith(`${p}/`)))

const antTheme = computed(() =>
  isYutu.value || !themeStore.isDark ? GALLERY_ANT_THEME_LIGHT : GALLERY_ANT_THEME_DARK,
)
</script>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background: var(--app-bg);
}

.basic-layout__content {
  padding: 24px;
}

.basic-layout__inner {
  max-width: 1400px;
  margin: 0 auto;
}

/* 屿图改版页：内容全宽交给页面自身（.yutu-wrap 自带 1200px 列），外层只保留背景 */
.basic-layout--yutu {
  background: var(--yutu-bg, #fafaf9);
}

.basic-layout--yutu .basic-layout__content {
  padding: 0;
}

.basic-layout--yutu .basic-layout__inner {
  max-width: none;
}

.basic-layout__footer {
  padding: 20px 24px;
  text-align: center;
  color: var(--app-text-secondary);
  background: transparent;
  font-size: 13px;
}

.basic-layout__footer-brand {
  margin-right: 10px;
  font-weight: 700;
  font-size: 14px;
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
