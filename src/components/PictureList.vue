<template>
  <!-- 壁纸墙变体：等尺寸网格（固定比例裁剪）+ 悬停操作层，供公共浏览页使用 -->
  <div v-if="variant === 'wall'" class="picture-wall">
    <a-spin :spinning="loading">
      <div v-if="dataList.length" class="picture-wall__grid">
        <div
          v-for="picture in dataList"
          :key="picture.id"
          v-jelly
          class="wall-tile"
          role="link"
          tabindex="0"
          :aria-label="picture.name || '未命名图片'"
          @click="goDetail(picture)"
          @keydown.enter.prevent="goDetail(picture)"
        >
          <div class="wall-tile__frame" :style="{ aspectRatio: ratio }">
            <ProgressiveImage
              class="wall-tile__img"
              :src="picture.thumbnailUrl || picture.url"
              :alt="picture.name"
            />
          </div>

          <div class="wall-tile__overlay">
            <div class="wall-tile__top">
              <div v-if="picture.tags?.length" class="wall-tile__tags">
                <span
                  v-for="tag in picture.tags.slice(0, 3)"
                  :key="tag"
                  class="wall-tile__tag"
                >
                  {{ tag }}
                </span>
              </div>
              <div class="wall-tile__actions">
                <button
                  v-if="showCollect && isLoggedIn"
                  class="wall-tile__btn wall-tile__btn--collect"
                  :class="{
                    'wall-tile__btn--on': collectStore.isCollected(picture.id),
                    'wall-tile__btn--pop': poppingId === String(picture.id),
                  }"
                  type="button"
                  :title="collectStore.isCollected(picture.id) ? '取消收藏' : '收藏'"
                  :aria-pressed="collectStore.isCollected(picture.id)"
                  @click.stop="doCollect(picture, $event)"
                >
                  <StarFilled v-if="collectStore.isCollected(picture.id)" />
                  <StarOutlined v-else />
                </button>
                <button
                  class="wall-tile__btn"
                  type="button"
                  title="复制链接"
                  @click.stop="doCopyLink(picture, $event)"
                >
                  <LinkOutlined />
                </button>
                <template v-if="showOp">
                  <button
                    class="wall-tile__btn"
                    type="button"
                    title="编辑"
                    @click.stop="doEdit(picture, $event)"
                  >
                    <EditOutlined />
                  </button>
                  <button
                    class="wall-tile__btn wall-tile__btn--danger"
                    type="button"
                    title="删除"
                    @click.stop="doDelete(picture, $event)"
                  >
                    <DeleteOutlined />
                  </button>
                </template>
              </div>
            </div>

            <button
              class="wall-tile__go"
              type="button"
              @click.stop="doDownload(picture, $event)"
            >
              <DownloadOutlined />
              下载
            </button>

            <div class="wall-tile__caption">
              <div class="wall-tile__caption-main">
                <span class="wall-tile__name">{{ picture.name || '未命名' }}</span>
                <button
                  v-if="authorName(picture)"
                  class="wall-tile__author"
                  type="button"
                  :title="`查看 ${authorName(picture)} 的全部作品`"
                  @click.stop="goAuthor(picture)"
                >
                  by {{ authorName(picture) }}
                </button>
              </div>
              <span v-if="picture.picWidth" class="wall-tile__meta">
                <span class="wall-tile__count" title="收藏量">
                  <StarOutlined />
                  {{ formatCount(picture.collectCount) }}
                </span>
                <span class="wall-tile__count" title="下载量">
                  <DownloadOutlined />
                  {{ formatCount(picture.downloadCount) }}
                </span>
                {{ picture.picWidth }}×{{ picture.picHeight }}（{{ formatSize(picture.picSize) }}）
              </span>
            </div>
          </div>
        </div>
      </div>

      <a-empty v-else-if="error && !loading" description="图片加载失败">
        <template #image>
          <ExclamationCircleOutlined class="picture-wall__empty-icon" />
        </template>
        <a-space direction="vertical">
          <span class="picture-wall__error-hint">接口请求失败，请确认后端服务正常后重试</span>
          <a-button type="primary" @click="emit('reload')">重新加载</a-button>
        </a-space>
      </a-empty>

      <a-empty v-else-if="!loading" :description="emptyText ?? '这里还没有壁纸'">
        <template #image>
          <PictureOutlined class="picture-wall__empty-icon" />
        </template>
        <slot name="empty" />
      </a-empty>
    </a-spin>
  </div>

  <!-- 默认变体：卡片列表，供空间 / 管理等工具页使用 -->
  <div v-else class="picture-list">
    <a-spin :spinning="loading">
      <div v-if="dataList.length" class="picture-list__grid">
        <a-card
          v-for="picture in dataList"
          :key="picture.id"
          class="picture-card"
          :bordered="false"
          hoverable
          @click="goDetail(picture)"
        >
          <template #cover>
            <div class="picture-card__cover">
              <ProgressiveImage
                class="picture-card__img"
                :src="picture.thumbnailUrl || picture.url"
                :alt="picture.name"
              />
            </div>
          </template>

          <a-card-meta :title="picture.name || '未命名'">
            <template #description>
              <div class="picture-card__meta">
                <span>
                  {{ picture.picWidth }}×{{ picture.picHeight }} · {{ formatSize(picture.picSize) }}
                </span>
                <button
                  v-if="authorName(picture)"
                  class="picture-card__author"
                  type="button"
                  :title="`查看 ${authorName(picture)} 的全部作品`"
                  @click.stop="goAuthor(picture)"
                >
                  by {{ authorName(picture) }}
                </button>
              </div>
            </template>
          </a-card-meta>

          <div v-if="picture.category || picture.tags?.length" class="picture-card__tags">
            <a-tag v-if="picture.category" color="blue" :bordered="false">
              {{ picture.category }}
            </a-tag>
            <a-tag v-for="tag in picture.tags" :key="tag" :bordered="false">{{ tag }}</a-tag>
          </div>

          <template v-if="showOp" #actions>
            <a-tooltip title="查看详情">
              <EyeOutlined @click="goDetail(picture)" />
            </a-tooltip>
            <a-tooltip title="下载">
              <DownloadOutlined @click="doDownload(picture, $event)" />
            </a-tooltip>
            <a-tooltip title="复制链接">
              <LinkOutlined @click="doCopyLink(picture, $event)" />
            </a-tooltip>
            <a-tooltip title="编辑">
              <EditOutlined @click="doEdit(picture, $event)" />
            </a-tooltip>
            <a-tooltip title="删除">
              <DeleteOutlined class="picture-card__danger" @click="doDelete(picture, $event)" />
            </a-tooltip>
          </template>
        </a-card>
      </div>

      <a-empty v-else-if="error && !loading" description="图片加载失败">
        <template #image>
          <ExclamationCircleOutlined class="picture-list__empty-icon" />
        </template>
        <a-space direction="vertical">
          <span class="picture-list__error-hint">接口请求失败，请确认后端服务正常后重试</span>
          <a-button type="primary" @click="emit('reload')">重新加载</a-button>
        </a-space>
      </a-empty>

      <a-empty v-else-if="!loading" :description="emptyText ?? '暂无图片'">
        <template #image>
          <PictureOutlined class="picture-list__empty-icon" />
        </template>
        <slot name="empty" />
      </a-empty>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { storeToRefs } from 'pinia'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  EyeOutlined,
  LinkOutlined,
  PictureOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons-vue'
import { vJelly } from '@/directives/jelly'
import { deletePicture } from '@/api/pictureController'
import ProgressiveImage from '@/components/ProgressiveImage.vue'
import { useCollectStore } from '@/stores/useCollectStore'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { downloadPictureByApi, formatCount, formatSize } from '@/utils'

interface Props {
  dataList?: API.PictureVO[]
  loading?: boolean
  /** 是否展示操作栏 */
  showOp?: boolean
  /** 是否展示收藏按钮（仅登录用户可见）；空间/管理页看图即用，不需要收藏 */
  showCollect?: boolean
  /** 加载是否出错：为 true 时展示错误态而非「暂无图片」，避免把接口失败误报成空数据 */
  error?: boolean
  /** wall-壁纸墙（公共浏览页）；default-卡片列表（空间/管理页） */
  variant?: 'wall' | 'default'
  /** 空态文案，按使用场景定制（相似图搜索等） */
  emptyText?: string
  /** 壁纸墙统一裁剪比例；首页按屏幕筛选变化（全部 4:3 / 横屏 16:10 / 竖屏 3:4） */
  ratio?: string
}

const props = withDefaults(defineProps<Props>(), {
  dataList: () => [],
  loading: false,
  showOp: false,
  showCollect: false,
  error: false,
  variant: 'default',
  emptyText: undefined,
  ratio: '4 / 3',
})

const emit = defineEmits<{
  (e: 'reload'): void
  /** 收藏状态变化：我的收藏页据此把已取消收藏的图块移出列表 */
  (e: 'collectChange', picture: API.PictureVO, collected: boolean): void
}>()

const router = useRouter()
const collectStore = useCollectStore()
const loginUserStore = useLoginUserStore()
const { loginUser } = storeToRefs(loginUserStore)

const isLoggedIn = computed(() => Boolean(loginUser.value?.id))

/** 作者展示名：优先昵称，回退账号 */
function authorName(picture: API.PictureVO): string {
  return picture.user?.userName || picture.user?.userAccount || ''
}

/** 跳转作者主页：这是「从一张图找到同一个人其他作品」的入口 */
function goAuthor(picture: API.PictureVO) {
  const authorId = picture.user?.id ?? picture.userId
  if (authorId) {
    router.push(`/user_profile/${authorId}`)
  }
}

/**
 * 刚被收藏的图块 id：只让「用户刚点的那一张」播放一次星标弹跳，
 * 否则整页已收藏的星标会在渲染时一起跳，变成噪音而非反馈。
 */
const poppingId = ref('')

async function doCollect(picture: API.PictureVO, event: MouseEvent) {
  event.stopPropagation()
  if (!picture.id) return
  const result = await collectStore.toggle(picture.id)
  if (!result) {
    message.error('操作失败，请稍后重试')
    return
  }
  picture.collectCount = result.collectCount
  if (result.collected) {
    message.success('已收藏')
    poppingId.value = String(picture.id)
    window.setTimeout(() => {
      if (poppingId.value === String(picture.id)) poppingId.value = ''
    }, 360)
  } else {
    message.success('已取消收藏')
  }
  emit('collectChange', picture, result.collected)
}

/** 列表数据变化即刷新整页星标：一次批量请求，不逐张问 */
watch(
  () => props.dataList,
  (list) => {
    if (!props.showCollect || !isLoggedIn.value) return
    void collectStore.loadStatus((list ?? []).map((item) => item.id))
  },
)

function goDetail(picture: API.PictureVO) {
  router.push(`/picture/${picture.id}`)
}

/** 通过后端下载接口下载（服务端原子自增下载量），blob 保存成功后本地计数即时 +1 */
async function doDownload(picture: API.PictureVO, event: MouseEvent) {
  event.stopPropagation()
  if (!picture.url) return
  const saved = await downloadPictureByApi(
    String(picture.id),
    picture.name || 'picture',
    picture.picFormat,
  )
  if (saved) {
    picture.downloadCount = Number(picture.downloadCount ?? 0) + 1
  }
}

async function doCopyLink(picture: API.PictureVO, event: MouseEvent) {
  event.stopPropagation()
  const link = `${window.location.origin}/picture/${picture.id}`
  try {
    await navigator.clipboard.writeText(link)
    message.success('链接已复制到剪贴板')
  } catch {
    message.warning(`复制失败，请手动复制：${link}`)
  }
}

function doEdit(picture: API.PictureVO, event: MouseEvent) {
  event.stopPropagation()
  router.push({
    path: '/add_picture',
    query: { id: picture.id, spaceId: picture.spaceId },
  })
}

function doDelete(picture: API.PictureVO, event: MouseEvent) {
  event.stopPropagation()
  Modal.confirm({
    title: '确认删除这张图片？',
    content: `「${picture.name || '未命名'}」删除后不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      const res = await deletePicture({ id: picture.id })
      if (res.code === 0) {
        message.success('删除成功')
        emit('reload')
      } else {
        message.error(res.message || '删除失败')
      }
    },
  })
}
</script>

<style scoped>
/* ==================== wall 变体 ==================== */

.picture-wall__grid {
  display: grid;
  /* 一页 4 列 × 3 行（每页 12 张），图块够大、信息层放得下 */
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.wall-tile {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  background: var(--app-panel);
  cursor: pointer;
}

.wall-tile:focus-visible {
  outline: 2px solid var(--app-accent);
  outline-offset: 2px;
}

.wall-tile__frame {
  width: 100%;
}

.wall-tile :deep(.progressive-image) {
  width: 100%;
  height: 100%;
  /* 果冻抖动时防止倾斜露底 */
  transform: scale(1.08);
}

.wall-tile :deep(.progressive-image__img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s ease;
}

.wall-tile:hover :deep(.progressive-image__img--loaded) {
  transform: scale(1.04);
}

.wall-tile__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  opacity: 0;
  transition: opacity 0.2s ease;
  background: linear-gradient(180deg, rgba(13, 15, 20, 0.32) 0%, rgba(13, 15, 20, 0) 30%, rgba(13, 15, 20, 0) 55%, rgba(13, 15, 20, 0.82) 100%);
}

.wall-tile:hover .wall-tile__overlay,
.wall-tile:focus-within .wall-tile__overlay {
  opacity: 1;
}

.wall-tile__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
}

.wall-tile__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.wall-tile__tag {
  padding: 2px 10px;
  border: 1px solid rgba(244, 245, 249, 0.25);
  border-radius: 999px;
  background: rgba(13, 15, 20, 0.55);
  -webkit-backdrop-filter: blur(6px);
  backdrop-filter: blur(6px);
  color: rgba(244, 245, 249, 0.92);
  font-size: 12px;
  white-space: nowrap;
}

/* 居中主操作：下载（参考大图卡片的「前往」式醒目按钮） */
.wall-tile__go {
  align-self: center;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin: 6px 0;
  padding: 8px 24px;
  border: none;
  border-radius: 999px;
  background: var(--app-accent, #ff6b3d);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 8px 22px rgba(255, 107, 61, 0.42);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.wall-tile__go:hover {
  transform: scale(1.05);
  box-shadow: 0 10px 26px rgba(255, 107, 61, 0.55);
}

.wall-tile__actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  /* 操作钮恒贴右上：.wall-tile__top 是 space-between，无标签的图块只剩这一个子元素，
     缺了 auto 外边距它会落到左上，星标位置就随「有没有标签」左右乱跳。 */
  margin-left: auto;
}

.wall-tile__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  color: #f4f5f9;
  background: rgba(13, 15, 20, 0.55);
  -webkit-backdrop-filter: blur(6px);
  backdrop-filter: blur(6px);
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}

.wall-tile__btn:hover {
  background: #ff6b3d;
}

.wall-tile__btn--danger:hover {
  background: #e5484d;
}

/* 已收藏：把星标染成强调色即可。图块中央已有一个橙色「下载」药丸，
   这里再铺满橙色圆点会互相抢眼，故只改字形颜色，底色保持玻璃质感。 */
.wall-tile__btn--on {
  color: var(--app-accent, #ff6b3d);
}

.wall-tile__btn--on:hover {
  background: var(--app-accent-soft, rgba(255, 107, 61, 0.15));
}

/* 收藏成功的反馈动效：只挂在用户刚点的那一张上（见 poppingId），不是整页齐跳 */
.wall-tile__btn--pop :deep(.anticon) {
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

.wall-tile__caption {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
}

.wall-tile__caption-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.wall-tile__name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: #f4f5f9;
  font-size: 13px;
  font-weight: 500;
}

/* 作者入口：从一张图找到同一个人其他作品的路径，文案沿用卡片变体的「by xxx」 */
.wall-tile__author {
  max-width: 100%;
  overflow: hidden;
  padding: 0;
  border: none;
  background: transparent;
  color: rgba(244, 245, 249, 0.62);
  font-size: 11px;
  text-align: left;
  white-space: nowrap;
  text-overflow: ellipsis;
  cursor: pointer;
  transition: color 0.18s ease;
}

.wall-tile__author:hover {
  color: var(--app-accent, #ff6b3d);
}

.wall-tile__meta {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  gap: 8px;
  color: rgba(244, 245, 249, 0.75);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.wall-tile__count {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.wall-tile__count :deep(.anticon) {
  font-size: 11px;
}

.picture-wall__empty-icon {
  font-size: 56px;
  color: rgba(244, 245, 249, 0.18);
}

.picture-wall__error-hint {
  color: var(--app-text-secondary);
  font-size: 13px;
}

@media (max-width: 1100px) {
  .picture-wall__grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 820px) {
  .picture-wall__grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }

  .wall-tile {
    border-radius: 10px;
  }
}

/* ==================== default 变体 ==================== */

.picture-list__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.picture-card {
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid var(--app-border, rgba(5, 5, 5, 0.06));
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.picture-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.45);
}

.picture-card__cover {
  position: relative;
  height: 180px;
  overflow: hidden;
  background: var(--app-panel-hover, #f0f2f5);
}

.picture-card__cover :deep(.progressive-image) {
  width: 100%;
  height: 100%;
}

.picture-card__cover :deep(.progressive-image__img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition:
    opacity 0.4s ease,
    transform 0.35s ease;
}

.picture-card:hover .picture-card__cover :deep(.progressive-image__img) {
  transform: scale(1.06);
}

.picture-card__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
}

.picture-card__author {
  align-self: flex-start;
  padding: 0;
  border: none;
  background: transparent;
  color: inherit;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  transition: color 0.18s ease;
}

.picture-card__author:hover {
  color: var(--app-accent, #ff6b3d);
}

.picture-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 8px;
}

.picture-card__danger {
  color: #ff4d4f;
}

.picture-list__empty-icon {
  font-size: 56px;
  color: rgba(244, 245, 249, 0.2);
}

.picture-list__error-hint {
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 13px;
}

:deep(.ant-card-body) {
  padding: 12px;
}

:deep(.ant-card-actions) {
  border-radius: 0 0 12px 12px;
}
</style>
