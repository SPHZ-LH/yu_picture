<template>
  <div class="picture-upload">
    <a-upload-dragger
      v-if="!previewUrl"
      name="file"
      :multiple="false"
      :show-upload-list="false"
      :before-upload="beforeUpload"
      :disabled="uploading"
      accept="image/jpeg,image/jpg,image/png,image/webp"
      class="picture-upload__dragger"
    >
      <p class="ant-upload-drag-icon">
        <CloudUploadOutlined v-if="!uploading" />
        <LoadingOutlined v-else />
      </p>
      <p class="ant-upload-text">{{ uploading ? '正在上传…' : '点击或拖拽图片到此处上传' }}</p>
      <p class="ant-upload-hint">
        支持 JPG / PNG / WEBP，单张不超过 {{ MAX_SIZE_MB }} MB
      </p>
    </a-upload-dragger>

    <div v-else class="picture-upload__preview">
      <img :src="previewUrl" :alt="picture?.name || '预览图'" />
      <div class="picture-upload__preview-mask">
        <a-space>
          <a-button size="small" :loading="uploading" @click="triggerUpload">重新上传</a-button>
          <a-button size="small" @click="triggerPreview">查看大图</a-button>
        </a-space>
      </div>
    </div>

    <!-- 隐藏的 input，用于「重新上传」 -->
    <input
      ref="fileInputRef"
      type="file"
      accept="image/jpeg,image/jpg,image/png,image/webp"
      style="display: none"
      @change="onFileInputChange"
    />

    <a-modal
      v-model:open="previewVisible"
      :footer="null"
      :width="880"
      centered
      title="图片预览"
    >
      <img v-if="previewUrl" :src="previewUrl" alt="预览图" style="width: 100%" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { CloudUploadOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import { uploadPicture } from '@/api/pictureController'

interface Props {
  /** 已上传/待编辑的图片，用于回显与「重新上传」时携带 id */
  picture?: API.PictureVO
  /** 目标空间 id，为空则上传到公共图库 */
  spaceId?: string
  /** 图片名称，作为上传时的 picName */
  picName?: string
}

const props = withDefaults(defineProps<Props>(), {
  picture: undefined,
  spaceId: undefined,
  picName: undefined,
})

const emit = defineEmits<{ (e: 'success', picture: API.PictureVO): void }>()

const MAX_SIZE_MB = 50
const MAX_SIZE = MAX_SIZE_MB * 1024 * 1024

const uploading = ref(false)
const previewUrl = ref<string>('')
const previewVisible = ref(false)
const fileInputRef = ref<HTMLInputElement>()

watch(
  () => props.picture?.url,
  (url) => {
    previewUrl.value = url ?? ''
  },
  { immediate: true },
)

/** 允许的扩展名：与后端 FilePictureUpload 的白名单逐字一致，避免前端放行、后端才拒 */
const ALLOW_SUFFIX = ['jpeg', 'jpg', 'png', 'webp']

/** 上传前的本地校验（口径与后端一致：看扩展名 + 看大小） */
function validate(file: File): boolean {
  const suffix = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : ''
  if (!ALLOW_SUFFIX.includes(suffix)) {
    message.error('只支持 JPG / PNG / WEBP 格式')
    return false
  }
  if (file.size > MAX_SIZE) {
    message.error(`图片大小不能超过 ${MAX_SIZE_MB} MB`)
    return false
  }
  return true
}

async function doUpload(file: File) {
  uploading.value = true
  try {
    const res = await uploadPicture(file, {
      // 已有图片 id 时表示「重新上传」，后端会覆盖原图
      id: props.picture?.id,
      picName: props.picName,
      spaceId: props.spaceId,
    })
    if (res.code === 0 && res.data) {
      previewUrl.value = res.data.url ?? ''
      message.success('上传成功')
      emit('success', res.data)
    }
  } finally {
    uploading.value = false
  }
}

function beforeUpload(file: File) {
  if (!validate(file)) {
    return false
  }
  doUpload(file)
  // 返回 false 阻止 antd 自动上传，改由自定义逻辑处理
  return false
}

function triggerUpload() {
  fileInputRef.value?.click()
}

function onFileInputChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file && validate(file)) {
    doUpload(file)
  }
  input.value = ''
}

function triggerPreview() {
  previewVisible.value = true
}

/** 对外暴露：当前预览地址，供父组件判断是否已上传 */
const hasPicture = computed(() => Boolean(previewUrl.value))

defineExpose({ hasPicture })
</script>

<style scoped>
.picture-upload__dragger {
  border-radius: 12px;
}

:deep(.picture-upload__dragger .ant-upload-drag-icon) {
  margin-bottom: 8px;
  color: var(--app-accent, #1677ff);
  font-size: 40px;
}

.picture-upload__preview {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: 12px;
  background: var(--app-panel-hover, #f0f2f5);
}

.picture-upload__preview img {
  width: 100%;
  max-height: 420px;
  object-fit: contain;
}

.picture-upload__preview-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.picture-upload__preview:hover .picture-upload__preview-mask {
  opacity: 1;
}
</style>
