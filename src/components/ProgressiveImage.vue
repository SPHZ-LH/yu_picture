<template>
  <div class="progressive-image" :class="{ 'progressive-image--loaded': loaded }">
    <img
      v-if="!failed"
      v-bind="$attrs"
      :key="retryNonce"
      class="progressive-image__img"
      :class="{ 'progressive-image__img--loaded': loaded }"
      :src="srcWithNonce"
      :loading="eager ? 'eager' : 'lazy'"
      :alt="alt"
      @load="loaded = true"
      @error="failed = true"
    />
    <div v-else class="progressive-image__fallback" title="点击重试" @click="retry">
      <PictureOutlined />
      <span>加载失败，点击重试</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { PictureOutlined } from '@ant-design/icons-vue'

/**
 * 渐进式图片：加载期间显示占位底色（微光动画），加载完成后淡入，失败可点击重试。
 * <p>
 * 尺寸、裁剪等样式通过 attrs 透传给内部主图；父组件如需用 scoped CSS 命中
 * 内部元素，请使用 :deep(.progressive-image__img)。
 */
defineOptions({ inheritAttrs: false })

const props = defineProps<{
  src?: string
  alt?: string
  /** 首屏关键图传 true 关闭懒加载 */
  eager?: boolean
}>()

const loaded = ref(false)
const failed = ref(false)
const retryNonce = ref(0)

const srcWithNonce = computed(() =>
  retryNonce.value
    ? `${props.src}${props.src?.includes('?') ? '&' : '?'}r=${retryNonce.value}`
    : props.src,
)

watch(
  () => props.src,
  () => {
    loaded.value = false
    failed.value = false
  },
)

function retry() {
  failed.value = false
  loaded.value = false
  retryNonce.value += 1
}
</script>

<style scoped>
.progressive-image {
  display: block;
  overflow: hidden;
  /* 加载期间的占位底色 + 微光扫过动画 */
  background: var(--app-shimmer, linear-gradient(110deg, #eceef1 8%, #f7f8fa 18%, #eceef1 33%));
  background-size: 200% 100%;
  animation: progressive-image-shimmer 1.4s ease-in-out infinite;
}

.progressive-image--loaded,
.progressive-image--error {
  animation: none;
}

.progressive-image__img {
  opacity: 0;
  transition: opacity 0.4s ease;
}

.progressive-image__img--loaded {
  opacity: 1;
}

.progressive-image__fallback {
  display: flex;
  height: 100%;
  min-height: 120px;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  justify-content: center;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 13px;
  cursor: pointer;
}

@keyframes progressive-image-shimmer {
  to {
    background-position: -200% 0;
  }
}
</style>
