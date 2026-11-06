<template>
  <div class="video-wall">
    <a-spin :spinning="loading">
      <div v-if="dataList.length" class="video-wall__grid">
        <div
          v-for="video in dataList"
          :key="video.id"
          class="video-tile"
          role="link"
          tabindex="0"
          :aria-label="video.name || '未命名视频'"
          @mouseenter="onEnter(video)"
          @mouseleave="onLeave(video)"
          @click="goDetail(video)"
          @keydown.enter.prevent="goDetail(video)"
        >
          <div class="video-tile__frame" :style="{ aspectRatio: ratio }">
            <ProgressiveImage
              class="video-tile__poster"
              :src="cosResizeUrl(video.coverUrl)"
              :alt="video.name"
            />
            <!-- 首次 hover 才挂 src：没被 hover 过的视频一个字节都不该从 COS 拉走 -->
            <video
              v-if="armed.has(video.id ?? '')"
              :ref="(el) => setVideoEl(video.id ?? '', el as HTMLVideoElement | null)"
              class="video-tile__player"
              :class="{ 'video-tile__player--ready': ready.has(video.id ?? '') }"
              :src="video.url"
              muted
              loop
              playsinline
              preload="auto"
              @loadeddata="ready.add(video.id ?? '')"
            />
          </div>

          <span class="video-tile__badge">
            <PlayCircleFilled class="video-tile__badge-icon" />
            {{ formatDuration(video.videoDuration) || '动态' }}
          </span>

          <div class="video-tile__overlay">
            <div v-if="video.tags?.length" class="video-tile__tags">
              <span v-for="tag in video.tags.slice(0, 3)" :key="tag" class="video-tile__tag">
                {{ tag }}
              </span>
            </div>
            <div class="video-tile__actions">
              <button
                class="video-tile__btn"
                type="button"
                title="下载"
                @click.stop="doDownload(video, $event)"
              >
                <DownloadOutlined />
              </button>
              <template v-if="showOp">
                <button
                  class="video-tile__btn video-tile__btn--danger"
                  type="button"
                  title="删除"
                  @click.stop="doDelete(video, $event)"
                >
                  <DeleteOutlined />
                </button>
              </template>
            </div>

            <div class="video-tile__caption">
              <span class="video-tile__name">{{ video.name || '未命名' }}</span>
              <span v-if="video.videoWidth" class="video-tile__meta">
                <span class="video-tile__count" title="下载量">
                  <DownloadOutlined />
                  {{ formatCount(video.downloadCount) }}
                </span>
                {{ video.videoWidth }}×{{ video.videoHeight }}（{{ formatSize(video.videoSize) }}）
              </span>
            </div>
          </div>
        </div>
      </div>

      <a-empty v-else-if="error && !loading" description="视频加载失败">
        <template #image>
          <ExclamationCircleOutlined class="video-wall__empty-icon" />
        </template>
        <a-space direction="vertical">
          <span class="video-wall__hint">接口请求失败，请确认后端服务正常后重试</span>
          <a-button type="primary" @click="emit('reload')">重新加载</a-button>
        </a-space>
      </a-empty>

      <a-empty v-else-if="!loading" :description="emptyText ?? '这里还没有动态壁纸'">
        <template #image>
          <VideoCameraOutlined class="video-wall__empty-icon" />
        </template>
        <slot name="empty" />
      </a-empty>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import {
  DeleteOutlined,
  DownloadOutlined,
  ExclamationCircleOutlined,
  PlayCircleFilled,
  VideoCameraOutlined,
} from '@ant-design/icons-vue'
import ProgressiveImage from '@/components/ProgressiveImage.vue'
import { deleteVideo, downloadVideoById } from '@/api/videoController'
import { cosResizeUrl, formatCount, formatDuration, formatSize } from '@/utils'

interface Props {
  dataList: API.VideoVO[]
  loading?: boolean
  error?: boolean
  /** 是否显示编辑/删除操作（本人或管理员） */
  showOp?: boolean
  /** 图块宽高比，与壁纸墙保持一致 */
  ratio?: string
  emptyText?: string
}

withDefaults(defineProps<Props>(), {
  loading: false,
  error: false,
  showOp: false,
  ratio: '16 / 10',
  emptyText: undefined,
})

const emit = defineEmits<{
  (e: 'reload'): void
  (e: 'delete', video: API.VideoVO): void
}>()

const router = useRouter()

/** 已挂上 src 的视频 id */
const armed = reactive(new Set<string>())
/** 已解出首帧、可以淡入显示的视频 id */
const ready = reactive(new Set<string>())
const videoEls = ref(new Map<string, HTMLVideoElement>())

function setVideoEl(id: string, el: HTMLVideoElement | null) {
  if (el) {
    videoEls.value.set(id, el)
  } else {
    videoEls.value.delete(id)
  }
}

function onEnter(video: API.VideoVO) {
  const id = video.id ?? ''
  if (!id) return
  if (!armed.has(id)) {
    armed.add(id)
  }
  // play() 在无手势条件下对 muted 视频是允许的；失败只说明用户已经移开了鼠标
  videoEls.value.get(id)?.play().catch(() => {})
}

function onLeave(video: API.VideoVO) {
  // 只暂停、不移除元素：保留当前帧，避免画面闪回封面
  videoEls.value.get(video.id ?? '')?.pause()
}

function goDetail(video: API.VideoVO) {
  if (video.id) {
    router.push({ name: '视频详情', params: { id: video.id } })
  }
}

function doDownload(video: API.VideoVO, event: MouseEvent) {
  event.stopPropagation()
  if (!video.id) return
  downloadVideoById(video.id)
  message.loading({ content: '正在准备下载…', key: 'video-download', duration: 1.5 })
}

function doDelete(video: API.VideoVO, event: MouseEvent) {
  event.stopPropagation()
  if (!video.id) return
  Modal.confirm({
    title: '删除视频',
    content: `确定删除「${video.name || '未命名'}」吗？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      const res = await deleteVideo({ id: video.id })
      if (res.code === 0) {
        message.success('删除成功')
        emit('delete', video)
      }
    },
  })
}
</script>

<style scoped>
.video-wall__grid {
  display: grid;
  /* 与壁纸墙同一节奏：一页 4 列 × 3 行 */
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.video-tile {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  background: var(--app-panel);
  cursor: pointer;
}

.video-tile:focus-visible {
  outline: 2px solid var(--app-accent);
  outline-offset: 2px;
}

.video-tile__frame {
  width: 100%;
}

.video-tile :deep(.progressive-image) {
  width: 100%;
  height: 100%;
}

.video-tile :deep(.progressive-image__img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.video-tile__player {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transition: opacity 0.25s ease;
}

.video-tile__player--ready {
  opacity: 1;
}

/* 常驻角标：不 hover 也能看出这是动态壁纸 */
.video-tile__badge {
  position: absolute;
  top: 10px;
  right: 12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(13, 15, 20, 0.55);
  -webkit-backdrop-filter: blur(6px);
  backdrop-filter: blur(6px);
  color: rgba(244, 245, 249, 0.92);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.video-tile__badge-icon {
  color: var(--app-accent, #ff6b3d);
}

.video-tile__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  opacity: 0;
  transition: opacity 0.2s ease;
  background: linear-gradient(180deg, rgba(13, 15, 20, 0.32) 0%, rgba(13, 15, 20, 0) 30%, rgba(13, 15, 20, 0) 55%, rgba(13, 15, 20, 0.82) 100%);
}

.video-tile:hover .video-tile__overlay,
.video-tile:focus-within .video-tile__overlay {
  opacity: 1;
}

.video-tile__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-self: flex-start;
  padding: 10px 12px 0;
  min-width: 0;
}

.video-tile__tag {
  padding: 2px 10px;
  border: 1px solid rgba(244, 245, 249, 0.25);
  border-radius: 999px;
  background: rgba(13, 15, 20, 0.55);
  color: rgba(244, 245, 249, 0.92);
  font-size: 12px;
  white-space: nowrap;
}

.video-tile__actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-self: flex-end;
  padding: 10px 12px 0;
}

.video-tile__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 8px;
  background: rgba(13, 15, 20, 0.55);
  color: rgba(244, 245, 249, 0.92);
  cursor: pointer;
  transition: background 0.18s ease;
}

.video-tile__btn:hover {
  background: var(--app-accent, #ff6b3d);
  color: #fff;
}

.video-tile__btn--danger:hover {
  background: #ff4d4f;
}

.video-tile__caption {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px 12px;
  min-width: 0;
}

.video-tile__name {
  overflow: hidden;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.video-tile__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: rgba(244, 245, 249, 0.72);
  font-size: 12px;
}

.video-tile__count {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.video-wall__empty-icon {
  color: var(--app-text-secondary);
  font-size: 48px;
}

.video-wall__hint {
  color: var(--app-text-secondary);
}

@media (max-width: 1200px) {
  .video-wall__grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 900px) {
  .video-wall__grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 600px) {
  .video-wall__grid {
    grid-template-columns: 1fr;
  }
}
</style>
