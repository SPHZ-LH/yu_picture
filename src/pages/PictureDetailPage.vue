<template>
  <div class="page-container">
    <a-spin :spinning="loading">
      <a-card v-if="picture" :bordered="false">
        <a-row :gutter="24">
          <!-- 图片区 -->
          <a-col :xs="24" :lg="15">
            <div class="detail-image">
              <img
                v-if="picture.thumbnailUrl && originPreview"
                class="detail-image__preview"
                :src="picture.thumbnailUrl"
                alt=""
                aria-hidden="true"
              />
              <img
                class="detail-image__origin"
                :class="{ 'detail-image__origin--loading': picture.thumbnailUrl && originPreview }"
                :src="picture.url"
                :alt="picture.name"
                @load="originPreview = false"
                @error="originPreview = false"
              />
            </div>
          </a-col>

          <!-- 信息区 -->
          <a-col :xs="24" :lg="9">
            <div class="detail-info">
              <h2 class="detail-info__title">{{ picture.name || '未命名' }}</h2>

              <a-space wrap class="detail-info__actions">
                <a-button type="primary" @click="doDownload">
                  <template #icon><DownloadOutlined /></template>
                  下载原图
                </a-button>
                <a-button
                  class="detail-collect"
                  :class="{ 'detail-collect--on': collected }"
                  :aria-pressed="collected"
                  @click="doCollect"
                >
                  <template #icon>
                    <StarFilled v-if="collected" :class="{ 'detail-collect__pop': collectPopping }" />
                    <StarOutlined v-else />
                  </template>
                  {{ collected ? '已收藏' : '收藏' }}
                  <span v-if="collectCount > 0" class="detail-collect__count">
                    {{ formatCount(collectCount) }}
                  </span>
                </a-button>
                <a-button @click="doCopyLink">
                  <template #icon><LinkOutlined /></template>
                  复制链接
                </a-button>
                <a-button v-if="canManage" @click="doEditTransform">
                  <template #icon><ScissorOutlined /></template>
                  画面编辑
                </a-button>
                <a-button v-if="canManage" @click="doExpand">
                  <template #icon><ExpandOutlined /></template>
                  扩展画幅
                </a-button>
                <a-button v-if="canManage" @click="doEdit">
                  <template #icon><EditOutlined /></template>
                  编辑信息
                </a-button>
                <a-button v-if="canManage" danger @click="doDelete">
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </a-space>

              <a-divider />

              <a-descriptions :column="1" size="small">
                <a-descriptions-item label="简介">
                  {{ picture.introduction || '暂无简介' }}
                </a-descriptions-item>
                <a-descriptions-item label="分类">
                  <a-tag v-if="picture.category" :bordered="false">
                    {{ picture.category }}
                  </a-tag>
                  <span v-else>-</span>
                </a-descriptions-item>
                <a-descriptions-item label="标签">
                  <template v-if="picture.tags?.length">
                    <a-tag v-for="tag in picture.tags" :key="tag" :bordered="false">{{ tag }}</a-tag>
                  </template>
                  <span v-else>-</span>
                </a-descriptions-item>
                <a-descriptions-item label="尺寸">
                  {{ picture.picWidth }} × {{ picture.picHeight }}（宽高比 {{ picture.picScale ?? '-' }}）
                </a-descriptions-item>
                <a-descriptions-item label="大小">
                  {{ formatSize(picture.picSize) }}
                </a-descriptions-item>
                <a-descriptions-item label="格式">
                  {{ picture.picFormat?.toUpperCase() || '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="上传者">
                  <RouterLink
                    v-if="picture.user?.id"
                    class="detail-author"
                    :to="`/user_profile/${picture.user.id}`"
                    :title="`查看 ${picture.user.userName || picture.user.userAccount} 的全部作品`"
                  >
                    <a-avatar :size="20" :src="picture.user.userAvatar">
                      <template #icon><UserOutlined /></template>
                    </a-avatar>
                    <span>{{ picture.user.userName || picture.user.userAccount }}</span>
                    <RightOutlined class="detail-author__more" />
                  </RouterLink>
                  <span v-else>-</span>
                </a-descriptions-item>
                <a-descriptions-item label="上传时间">
                  {{ formatDate(picture.createTime) }}
                </a-descriptions-item>
                <a-descriptions-item v-if="picture.spaceId" label="所属空间">
                  <RouterLink :to="`/space/${picture.spaceId}`">空间 #{{ picture.spaceId }}</RouterLink>
                </a-descriptions-item>
              </a-descriptions>
            </div>
          </a-col>
        </a-row>
      </a-card>

      <a-empty v-else-if="!loading" description="图片不存在或已被删除">
        <a-button type="primary" @click="router.push('/')">返回公共图库</a-button>
      </a-empty>
    </a-spin>

    <!-- 相似图片：滚动到该区域才发起检索（IntersectionObserver 懒加载） -->
    <section v-if="picture" ref="similarSectionRef" class="similar-section">
      <div class="similar-section__head">
        <h3 class="similar-section__title">相似图片</h3>
        <span
          v-if="similarState === 'done' && similarList.length"
          class="similar-section__count"
        >
          {{ similarList.length }} 张
        </span>
      </div>
      <PictureList
        v-if="similarState !== 'idle'"
        variant="wall"
        show-collect
        :data-list="similarList"
        :loading="similarState === 'loading'"
        :error="similarState === 'error'"
        empty-text="没有找到相似图片"
        @reload="loadSimilar"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ExpandOutlined,
  LinkOutlined,
  RightOutlined,
  ScissorOutlined,
  StarFilled,
  StarOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import {
  deletePicture,
  getPictureVOById,
  searchPictureById,
} from '@/api/pictureController'
import PictureList from '@/components/PictureList.vue'
import { useCollectStore } from '@/stores/useCollectStore'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { downloadPictureByApi, formatCount, formatDate, formatSize } from '@/utils'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const collectStore = useCollectStore()

const picture = ref<API.PictureVO>()
const loading = ref(false)

const pictureId = computed(() => String(route.params.id ?? ''))

/** 当前用户是否已收藏本图（收藏态由会话决定，不能取自共享缓存的 PictureVO） */
const collected = computed(() => collectStore.isCollected(pictureId.value))

/** 收藏量以图片记录为准；收藏/取消后用服务端返回值就地修正 */
const collectCount = computed(() => Number(picture.value?.collectCount ?? 0))

/** 仅在用户刚点下收藏时播放一次星标反馈动效 */
const collectPopping = ref(false)
let collectPopTimer: number | undefined

/**
 * 渐进式加载（blur-up）：原图加载期间，在原图上方叠加一张模糊放大的缩略图占位，
 * 原图 load/error 事件后淡入替换。无缩略图的老数据直接显示原图。
 */
const originPreview = ref(true)

watch(
  () => picture.value?.id,
  () => {
    originPreview.value = !!picture.value?.thumbnailUrl
  },
  { immediate: true },
)

/** 仅上传者本人或管理员可编辑 / 删除 */
const canManage = computed(() => {
  const user = loginUserStore.loginUser
  if (!user?.id || !picture.value) return false
  return user.userRole === 'admin' || String(picture.value.userId) === String(user.id)
})

async function loadPicture() {
  if (!pictureId.value) return
  loading.value = true
  try {
    const res = await getPictureVOById(pictureId.value)
    if (res.code === 0 && res.data) {
      picture.value = res.data
    } else {
      picture.value = undefined
    }
    // 收藏态单独问后端：PictureVO 所有用户共享，不带「我是否已收藏」
    if (loginUserStore.loginUser?.id) {
      await collectStore.loadStatus([pictureId.value])
    }
  } finally {
    loading.value = false
  }
}

/** 通过后端下载接口下载原图（服务端原子自增下载量），成功后本地计数即时 +1 */
async function doDownload() {
  const pic = picture.value
  if (!pic?.url || !pic.id) return
  const saved = await downloadPictureByApi(String(pic.id), pic.name || 'picture', pic.picFormat)
  if (saved) {
    pic.downloadCount = Number(pic.downloadCount ?? 0) + 1
  }
}

/** 收藏 / 取消收藏：未登录先去登录，登录后回到本页 */
async function doCollect() {
  const pic = picture.value
  if (!pic?.id) return
  if (!loginUserStore.loginUser?.id) {
    message.warning('登录后即可收藏')
    router.push({ path: '/user/login', query: { redirect: route.fullPath } })
    return
  }
  const result = await collectStore.toggle(pic.id)
  if (!result) {
    message.error('操作失败，请稍后重试')
    return
  }
  // 收藏量以服务端返回为准，避免本地自增在并发下与库内计数偏离
  pic.collectCount = result.collectCount
  if (result.collected) {
    message.success('已收藏')
    collectPopping.value = true
    window.clearTimeout(collectPopTimer)
    collectPopTimer = window.setTimeout(() => {
      collectPopping.value = false
    }, 360)
  } else {
    message.success('已取消收藏')
  }
}

async function doCopyLink() {
  const link = window.location.href
  try {
    await navigator.clipboard.writeText(link)
    message.success('链接已复制到剪贴板')
  } catch {
    message.warning(`复制失败，请手动复制：${link}`)
  }
}

/** 跳转画面编辑页（裁剪/等比缩放/旋转/翻转） */
function doEditTransform() {
  router.push(`/edit_picture/${picture.value?.id}`)
}

/** 跳转画面扩展页（AI 补全边界外内容，换画幅） */
function doExpand() {
  router.push(`/picture/expand/${picture.value?.id}`)
}

function doEdit() {
  router.push({
    path: '/add_picture',
    query: { id: picture.value?.id, spaceId: picture.value?.spaceId },
  })
}

function doDelete() {
  Modal.confirm({
    title: '确认删除这张图片？',
    content: '删除后不可恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      const res = await deletePicture({ id: picture.value?.id })
      if (res.code === 0) {
        message.success('删除成功')
        const spaceId = picture.value?.spaceId
        if (spaceId) {
          router.push(`/space/${spaceId}`)
        } else {
          router.push('/')
        }
      }
    },
  })
}

// ==================== 相似图片（下滑懒加载） ====================

const similarSectionRef = ref<HTMLElement>()
const similarState = ref<'idle' | 'loading' | 'done' | 'error'>('idle')
const similarList = ref<API.PictureVO[]>([])
let similarObserver: IntersectionObserver | undefined

/** 检索相似图片；过滤掉当前图自身，避免"相似"里出现自己 */
async function loadSimilar() {
  const selfId = pictureId.value
  if (!selfId || similarState.value === 'loading' || similarState.value === 'done') return
  similarState.value = 'loading'
  try {
    const res = await searchPictureById(selfId)
    if (pictureId.value !== selfId) return // 期间已切换图片，丢弃过期结果
    if (res.code === 0) {
      similarList.value = (res.data ?? []).filter((item) => String(item.id) !== selfId)
      similarState.value = 'done'
    } else {
      similarState.value = 'error'
    }
  } catch {
    if (pictureId.value === selfId) {
      similarState.value = 'error'
    }
  }
}

/** 重建观察器：IntersectionObserver 在 observe 时会立刻上报一次相交状态，
 *  因此用户停在区域中间切换图片也能正确触发加载 */
function startSimilarWatch() {
  similarObserver?.disconnect()
  if (!similarSectionRef.value) return
  similarObserver = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        loadSimilar()
      }
    },
    // 提前 240px 预加载，滚到可见时结果大多已就绪
    { rootMargin: '0px 0px 240px 0px' },
  )
  similarObserver.observe(similarSectionRef.value)
}

function resetSimilar() {
  similarList.value = []
  similarState.value = 'idle'
}

onMounted(async () => {
  await loadPicture()
  nextTick(startSimilarWatch)
})

onBeforeUnmount(() => {
  similarObserver?.disconnect()
  similarObserver = undefined
  window.clearTimeout(collectPopTimer)
})

/** 站内从相似图再点进另一张详情时组件会复用，需整体重置 */
watch(pictureId, () => {
  resetSimilar()
  loadPicture()
  nextTick(startSimilarWatch)
})
</script>

<style scoped>
.detail-image {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  padding: 16px;
  border-radius: 12px;
  background: var(--app-panel-hover, #f0f2f5);
}

.detail-image img {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
  border-radius: 8px;
}

.detail-image__preview {
  position: absolute;
  inset: 16px;
  width: calc(100% - 32px);
  height: calc(100% - 32px);
  /* 模糊放大铺满，藏住缩略图分辨率不足的锯齿 */
  filter: blur(18px);
  transform: scale(1.04);
  pointer-events: none;
}

.detail-image__origin {
  opacity: 1;
  transition: opacity 0.5s ease;
}

.detail-image__origin--loading {
  opacity: 0;
}

.detail-info__title {
  margin: 0 0 16px;
  font-size: 20px;
  font-weight: 600;
  word-break: break-all;
}

.detail-info__actions {
  margin-bottom: 4px;
}

/* 已收藏态沿用首页筛选胶囊的「选中」样式（描边 + 淡底 + 强调色），
   全站只用一个强调色表达「已选中」，不另造一套状态色。 */
.detail-collect--on {
  border-color: var(--app-accent, #ff6b3d);
  background: var(--app-accent-soft, rgba(255, 107, 61, 0.12));
  color: var(--app-accent, #ff6b3d);
}

.detail-collect--on:hover {
  border-color: var(--app-accent, #ff6b3d);
  color: var(--app-accent, #ff6b3d);
}

.detail-collect__count {
  margin-left: 6px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.detail-collect--on .detail-collect__count {
  color: inherit;
  opacity: 0.75;
}

/* 收藏成功的反馈动效：只在用户点击那一刻播放一次 */
.detail-collect__pop {
  display: inline-block;
  animation: collect-pop 0.34s ease;
}

@keyframes collect-pop {
  0% {
    transform: scale(1);
  }
  45% {
    transform: scale(1.38);
  }
  100% {
    transform: scale(1);
  }
}

/* 上传者入口：可点到本人主页看全部作品 */
.detail-author {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: inherit;
}

.detail-author:hover {
  color: var(--app-accent, #ff6b3d);
}

.detail-author__more {
  font-size: 10px;
  opacity: 0.5;
}

/* ==================== 相似图片区 ==================== */

.similar-section__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}

.similar-section__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--app-text, rgba(0, 0, 0, 0.88));
}

.similar-section__count {
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 13px;
}
</style>
