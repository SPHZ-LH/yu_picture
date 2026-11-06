<template>
  <div class="page-container">
    <a-card :bordered="false" title="上传动态壁纸">
      <template #extra>
        <a-space>
          <a-button @click="router.back()">取消</a-button>
          <a-button type="primary" :loading="submitting" :disabled="!picked" @click="handleSubmit">
            提交
          </a-button>
        </a-space>
      </template>

      <a-row :gutter="24">
        <a-col :xs="24" :lg="14" class="upload-col">
          <a-upload-dragger
            v-if="!picked"
            name="file"
            :multiple="false"
            :show-upload-list="false"
            :before-upload="handlePick"
            accept="video/mp4,.mp4"
            class="video-upload__dragger"
          >
            <p class="ant-upload-drag-icon">
              <CloudUploadOutlined />
            </p>
            <p class="ant-upload-text">点击或拖拽 mp4 到此处</p>
            <p class="ant-upload-hint">
              仅支持 mp4，单个不超过 {{ MAX_VIDEO_SIZE_MB }} MB；上传时会自动抽取一帧作为封面
            </p>
          </a-upload-dragger>

          <div v-else class="video-upload__preview">
            <video
              :src="previewUrl"
              class="video-upload__player"
              controls
              muted
              playsinline
              preload="metadata"
            />
            <a-descriptions :column="2" size="small" class="video-upload__meta">
              <a-descriptions-item label="封面">
                <img v-if="coverUrl" :src="coverUrl" alt="封面" class="video-upload__cover" />
              </a-descriptions-item>
              <a-descriptions-item label="分辨率">
                {{ cover?.width }} × {{ cover?.height }}
              </a-descriptions-item>
              <a-descriptions-item label="时长">{{ formatDuration(cover?.duration) }}</a-descriptions-item>
              <a-descriptions-item label="体积">{{ formatSize(picked.size) }}</a-descriptions-item>
            </a-descriptions>
            <a-alert
              v-if="cover && cover.webOptimized === false"
              type="warning"
              show-icon
              message="该视频未做 Web 优化（moov 盒在文件尾部），列表悬停预览会明显变慢。不影响下载，建议用 ffmpeg -movflags +faststart 重新导出。"
            />
            <a-space class="video-upload__actions">
              <a-button :loading="extracting" @click="triggerPick">重新选择</a-button>
              <a-button @click="clearPicked">清除</a-button>
            </a-space>
          </div>

          <input
            ref="fileInputRef"
            type="file"
            accept="video/mp4,.mp4"
            style="display: none"
            @change="onFileInputChange"
          />
        </a-col>

        <a-col :xs="24" :lg="10">
          <a-form :model="formState" layout="vertical" name="video_upload">
            <a-form-item label="名称" name="name" :rules="[{ required: true }]">
              <a-input v-model:value="formState.name" placeholder="请输入视频名称" allow-clear />
            </a-form-item>
            <a-form-item label="简介" name="introduction">
              <a-textarea
                v-model:value="formState.introduction"
                placeholder="一句话说明这条壁纸适合什么场景"
                :maxlength="500"
                :rows="3"
                show-count
              />
            </a-form-item>
            <a-form-item label="分类" name="category">
              <a-select
                v-model:value="formState.category"
                placeholder="请选择分类"
                allow-clear
                :options="categoryOptions"
              />
            </a-form-item>
            <a-form-item label="标签" name="tags">
              <a-select
                v-model:value="formState.tags"
                mode="multiple"
                placeholder="请选择标签"
                allow-clear
                :options="tagOptions"
              />
            </a-form-item>
          </a-form>
          <a-alert
            v-if="!spaceId"
            type="info"
            show-icon
            message="将上传到公共图库，提交后进入人工审核，通过后才会对外可见。"
          />
        </a-col>
      </a-row>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { CloudUploadOutlined } from '@ant-design/icons-vue'
import { uploadVideo } from '@/api/videoController'
import { listPictureTagCategory } from '@/api/pictureController'
import { extractVideoCover, type VideoCoverResult } from '@/utils/videoCover'
import { formatDuration, formatSize } from '@/utils'

const MAX_VIDEO_SIZE_MB = 50
const MAX_VIDEO_SIZE = MAX_VIDEO_SIZE_MB * 1024 * 1024

const route = useRoute()
const router = useRouter()

/** 目标空间来自查询参数，与创建图片一致；为空表示公共图库 */
const spaceId = computed(() => (route.query.spaceId ? String(route.query.spaceId) : undefined))

const picked = ref<File | null>(null)
const cover = ref<VideoCoverResult | null>(null)
const previewUrl = ref('')
const coverUrl = ref('')
const extracting = ref(false)
const submitting = ref(false)
const fileInputRef = ref<HTMLInputElement>()

const formState = reactive({
  name: '',
  introduction: '',
  category: undefined as string | undefined,
  tags: [] as string[],
})

const categoryOptions = ref<{ label: string; value: string }[]>([])
const tagOptions = ref<{ label: string; value: string }[]>([])

listPictureTagCategory().then((res) => {
  if (res.code === 0 && res.data) {
    categoryOptions.value = (res.data.categoryList ?? []).map((v) => ({ label: v, value: v }))
    tagOptions.value = (res.data.tagList ?? []).map((v) => ({ label: v, value: v }))
  }
})

function handlePick(file: File) {
  void acceptFile(file)
  // 返回 false 阻止 antd 自动上传，改由提交按钮统一发送
  return false
}

function onFileInputChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    void acceptFile(file)
  }
  input.value = ''
}

function triggerPick() {
  fileInputRef.value?.click()
}

async function acceptFile(file: File) {
  if (!file.name.toLowerCase().endsWith('.mp4')) {
    message.error('仅支持 mp4 视频')
    return
  }
  if (file.size > MAX_VIDEO_SIZE) {
    message.error(`视频大小不能超过 ${MAX_VIDEO_SIZE_MB} MB`)
    return
  }
  extracting.value = true
  try {
    const result = await extractVideoCover(file)
    resetUrls()
    picked.value = file
    cover.value = result
    previewUrl.value = URL.createObjectURL(file)
    coverUrl.value = URL.createObjectURL(result.coverFile)
    if (!formState.name) {
      formState.name = file.name.replace(/\.mp4$/i, '')
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '视频解析失败')
  } finally {
    extracting.value = false
  }
}

function clearPicked() {
  resetUrls()
  picked.value = null
  cover.value = null
}

function resetUrls() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  if (coverUrl.value) URL.revokeObjectURL(coverUrl.value)
  previewUrl.value = ''
  coverUrl.value = ''
}

onBeforeUnmount(resetUrls)

async function handleSubmit() {
  if (!picked.value || !cover.value) {
    message.warning('请先选择视频')
    return
  }
  if (!formState.name) {
    message.warning('请输入视频名称')
    return
  }
  submitting.value = true
  try {
    const res = await uploadVideo(picked.value, cover.value.coverFile, {
      name: formState.name,
      introduction: formState.introduction,
      category: formState.category,
      tags: formState.tags,
      spaceId: spaceId.value,
      duration: cover.value.duration,
    })
    if (res.code === 0 && res.data?.id) {
      message.success('上传成功')
      clearPicked()
      router.push(spaceId.value ? `/space/${spaceId.value}` : `/video/${res.data.id}`)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.video-upload__dragger {
  border-radius: 14px;
}

:deep(.video-upload__dragger .ant-upload-drag-icon) {
  margin-bottom: 8px;
  color: var(--app-accent, #ff6b3d);
  font-size: 40px;
}

.video-upload__preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.video-upload__player {
  width: 100%;
  max-height: 380px;
  border-radius: 14px;
  background: #000;
}

.video-upload__cover {
  width: 128px;
  border-radius: 8px;
  object-fit: cover;
}

.video-upload__meta {
  margin-bottom: 4px;
}

.video-upload__actions {
  align-self: flex-start;
}
</style>
