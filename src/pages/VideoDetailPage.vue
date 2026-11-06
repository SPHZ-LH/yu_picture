<template>
  <div class="page-container">
    <a-spin :spinning="loading">
      <a-card v-if="video" :bordered="false">
        <template #title>
          <a-space>
            <span>{{ video.name || '未命名动态壁纸' }}</span>
            <a-tag v-if="video.videoFormat" color="orange">动态</a-tag>
          </a-space>
        </template>
        <template #extra>
          <a-space>
            <a-button @click="router.back()">返回</a-button>
            <a-button type="primary" @click="doDownload">
              <template #icon><DownloadOutlined /></template>
              下载
            </a-button>
          </a-space>
        </template>

        <a-row :gutter="24">
          <a-col :xs="24" :lg="16">
            <div class="video-stage">
              <video
                v-if="playing"
                ref="playerRef"
                class="video-stage__player"
                :src="video.url"
                controls
                autoplay
                playsinline
              />
              <template v-else>
                <img v-if="video.coverUrl" :src="video.coverUrl" :alt="video.name" class="video-stage__poster" />
                <button class="video-stage__play" type="button" aria-label="播放" @click="startPlay">
                  <CaretRightFilled />
                </button>
              </template>
            </div>
            <a-alert
              class="video-stage__tip"
              type="info"
              show-icon
              message="设为动态壁纸需搭配 Wallpaper Engine / Lively 等客户端；手机动态壁纸视机型支持。"
            />
          </a-col>

          <a-col :xs="24" :lg="8">
            <a-descriptions :column="1" size="small" title="视频信息">
              <a-descriptions-item label="分辨率">
                {{ video.videoWidth }} × {{ video.videoHeight }}
              </a-descriptions-item>
              <a-descriptions-item label="时长">
                {{ formatDuration(video.videoDuration) || '—' }}
              </a-descriptions-item>
              <a-descriptions-item label="体积">{{ formatSize(video.videoSize) }}</a-descriptions-item>
              <a-descriptions-item label="格式">{{ video.videoFormat }}</a-descriptions-item>
              <a-descriptions-item label="下载量">{{ formatCount(video.downloadCount) }}</a-descriptions-item>
              <a-descriptions-item v-if="video.category" label="分类">{{ video.category }}</a-descriptions-item>
              <a-descriptions-item v-if="video.tags?.length" label="标签">
                <a-space wrap>
                  <a-tag v-for="tag in video.tags" :key="tag">{{ tag }}</a-tag>
                </a-space>
              </a-descriptions-item>
              <a-descriptions-item v-if="video.user?.userName" label="上传者">
                {{ video.user.userName }}
              </a-descriptions-item>
              <a-descriptions-item label="上传时间">
                {{ formatDate(video.createTime) }}
              </a-descriptions-item>
            </a-descriptions>
            <p v-if="video.introduction" class="video-detail__intro">{{ video.introduction }}</p>
          </a-col>
        </a-row>
      </a-card>

      <a-empty v-else-if="!loading" description="视频不存在或已被删除">
        <a-button type="primary" @click="router.push('/')">返回公共图库</a-button>
      </a-empty>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { CaretRightFilled, DownloadOutlined } from '@ant-design/icons-vue'
import { downloadVideoById, getVideoVOById } from '@/api/videoController'
import { formatDate, formatCount, formatDuration, formatSize } from '@/utils'

const route = useRoute()
const router = useRouter()

const video = ref<API.VideoVO>()
const loading = ref(false)
const playing = ref(false)
const playerRef = ref<HTMLVideoElement>()

const videoId = () => String(route.params.id ?? '')

async function load() {
  const id = videoId()
  if (!id) return
  loading.value = true
  playing.value = false
  try {
    const res = await getVideoVOById(id)
    video.value = res.code === 0 ? res.data : undefined
  } finally {
    loading.value = false
  }
}

async function startPlay() {
  playing.value = true
  await nextTick()
  // 带声音播放必须由这次点击授权；被策略拒绝时退回静音，至少不让画面停在封面上
  playerRef.value
    ?.play()
    .catch(() => {
      if (playerRef.value) {
        playerRef.value.muted = true
        void playerRef.value.play().catch(() => message.error('浏览器阻止了自动播放，请点击画面播放'))
      }
    })
}

function doDownload() {
  if (!video.value?.id) return
  downloadVideoById(video.value.id)
  message.loading({ content: '正在准备下载…', key: 'video-download', duration: 1.5 })
}

// 详情页之间跳转时路由复用组件，必须监听 id 变化重新拉取
watch(() => route.params.id, load)

onMounted(load)
</script>

<style scoped>
.video-stage {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  background: #0d0f14;
}

.video-stage__player,
.video-stage__poster {
  display: block;
  width: 100%;
  max-height: 68vh;
  object-fit: contain;
}

.video-stage__play {
  position: absolute;
  top: 50%;
  left: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border: none;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  background: var(--app-accent, #ff6b3d);
  color: #fff;
  font-size: 30px;
  cursor: pointer;
  box-shadow: 0 10px 30px rgba(255, 107, 61, 0.45);
  transition: transform 0.18s ease;
}

.video-stage__play:hover {
  transform: translate(-50%, -50%) scale(1.06);
}

.video-stage__tip {
  margin-top: 16px;
}

.video-detail__intro {
  margin: 16px 0 0;
  color: var(--app-text-secondary);
  line-height: 1.7;
}
</style>
