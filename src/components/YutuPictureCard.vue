<template>
  <component
    :is="to ? RouterLink : 'figure'"
    :to="to ? to : undefined"
    class="yutu-card"
    @click="to ? undefined : emit('open')"
  >
    <div class="yutu-card__ph">
      <img :src="src" :alt="picture.name ?? '图片'" loading="lazy" />
    </div>
    <figcaption class="yutu-card__cap">
      <div class="yutu-card__main">
        <div class="yutu-card__title">{{ caption ?? picture.name }}</div>
        <div v-if="subCaption" class="yutu-card__author">{{ subCaption }}</div>
      </div>
      <div class="yutu-card__side">
        <span v-if="tier" class="yutu-card__tier">{{ tier }}</span>
        <button
          v-if="showCollect && isLoggedIn"
          class="yutu-card__star"
          :class="{ 'yutu-card__star--on': collected }"
          type="button"
          :aria-label="collected ? '取消收藏' : '收藏'"
          @click.stop="onToggleCollect"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" :fill="collected ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round">
            <path d="M12 20.3 4.9 13a4.7 4.7 0 0 1 0-6.6 4.6 4.6 0 0 1 6.5 0l.6.6.6-.6a4.6 4.6 0 0 1 6.5 0 4.7 4.7 0 0 1 0 6.6Z" />
          </svg>
        </button>
      </div>
    </figcaption>
  </component>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { message } from 'ant-design-vue'
import { storeToRefs } from 'pinia'
import { useCollectStore } from '@/stores/useCollectStore'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { resolutionTier } from '@/api/yutuController'
import { sameOriginCosUrl } from '@/utils/cosUrl'


const props = withDefaults(
  defineProps<{
    picture: API.PictureVO
    /** 点击跳转路由（详情页），不传则只发 open 事件 */
    to?: string
    /** 覆盖标题（榜单/专题等场景的展示名） */
    caption?: string
    /** 副标题（@作者 / 相对时间等） */
    subCaption?: string
    /** 是否显示收藏星标（仅公共浏览页传） */
    showCollect?: boolean
  }>(),
  {
    to: undefined,
    caption: undefined,
    subCaption: undefined,
    showCollect: false,
  },
)

const emit = defineEmits<{ (e: 'open'): void }>()

const collectStore = useCollectStore()
const loginUserStore = useLoginUserStore()
const { loginUser } = storeToRefs(loginUserStore)
const isLoggedIn = computed(() => Boolean(loginUser.value?.id))

const src = computed(() => sameOriginCosUrl(props.picture.thumbnailUrl || props.picture.url))
const tier = computed(() => resolutionTier(props.picture.picWidth, props.picture.picHeight))
const collected = computed(() => collectStore.isCollected(props.picture.id))

onMounted(() => {
  // 挂载即向 store 报到：本页图片进入批量星标查询的候选（store 内部去重、整页一次请求）
  if (props.showCollect && isLoggedIn.value) {
    collectStore.loadStatus([props.picture.id])
  }
})

async function onToggleCollect() {
  if (!props.picture.id) return
  try {
    const next = await collectStore.toggle(props.picture.id)
    if (next?.collected) {
      message.success('已收藏')
    }
  } catch {
    // 失败提示由请求层统一弹出
  }
}
</script>
