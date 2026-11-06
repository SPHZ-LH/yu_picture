<template>
  <a-config-provider :theme="antTheme">
    <div class="user-layout" :class="{ 'theme-gallery': themeStore.isDark }">
      <div class="user-layout__body">
        <RouterLink to="/" class="user-layout__brand">
          <span class="user-layout__brand-mark" aria-hidden="true">
            <i /><i /><i /><i class="user-layout__brand-dot" />
          </span>
          <span class="user-layout__brand-text">云图库</span>
        </RouterLink>

        <a-card class="user-layout__card" :bordered="false">
          <RouterView />
        </a-card>

        <RouterLink to="/" class="user-layout__back">返回公共图库</RouterLink>
      </div>
    </div>
  </a-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { GALLERY_ANT_THEME_DARK, GALLERY_ANT_THEME_LIGHT } from '@/constant/theme'
import { useThemeStore } from '@/stores/useThemeStore'

const themeStore = useThemeStore()

const antTheme = computed(() => (themeStore.isDark ? GALLERY_ANT_THEME_DARK : GALLERY_ANT_THEME_LIGHT))
</script>

<style scoped>
.user-layout {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 32px 16px;
}

.user-layout__body {
  width: 100%;
  max-width: 400px;
  text-align: center;
}

.user-layout__brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 22px;
  color: var(--app-text);
}

/* 与 GlobalHeader 同款像素块标识：缺角的一块用强调色 */
.user-layout__brand-mark {
  display: grid;
  grid-template-columns: 13px 13px;
  grid-template-rows: 13px 13px;
  gap: 3px;
}

.user-layout__brand-mark i {
  border-radius: 4px;
  background: currentColor;
}

.user-layout__brand-dot {
  background: var(--app-accent);
}

.user-layout__brand-text {
  font-size: 23px;
  font-weight: 700;
  letter-spacing: 1px;
}

.user-layout__card {
  border-radius: 16px;
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  box-shadow: var(--app-float-shadow, 0 12px 40px rgba(15, 35, 75, 0.1));
  text-align: left;
}

.user-layout__back {
  display: inline-block;
  margin-top: 20px;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.user-layout__back:hover {
  color: var(--app-accent);
}
</style>
