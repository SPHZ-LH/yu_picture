<template>
  <div class="page-container">
    <a-card :bordered="false" :title="isEdit ? '编辑图片' : '创建图片'">
      <template #extra>
        <a-space>
          <a-button @click="goBack">{{ isEdit ? '返回' : '取消' }}</a-button>
          <a-button type="primary" :disabled="!picture?.id" :loading="submitting" @click="handleSubmit">
            {{ isEdit ? '保存修改' : '提交' }}
          </a-button>
        </a-space>
      </template>

      <a-row :gutter="24">
        <a-col :xs="24" :lg="14" class="upload-col">
          <PictureUpload
            :picture="picture"
            :space-id="spaceId"
            :pic-name="formState.name"
            @success="handleUploadSuccess"
          />
          <a-descriptions
            v-if="picture?.id"
            :column="2"
            size="small"
            bordered
            class="picture-page__meta"
          >
            <a-descriptions-item label="尺寸">
              {{ picture.picWidth }} × {{ picture.picHeight }}
            </a-descriptions-item>
            <a-descriptions-item label="大小">
              {{ formatSize(picture.picSize) }}
            </a-descriptions-item>
            <a-descriptions-item label="格式">
              {{ picture.picFormat?.toUpperCase() || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="宽高比">
              {{ picture.picScale ?? '-' }}
            </a-descriptions-item>
          </a-descriptions>
          <a-alert
            v-if="picture?.id && !isEdit"
            class="picture-page__tip"
            type="success"
            show-icon
            message="图片已上传，补全信息后点击右上角「提交」完成创建"
          />
        </a-col>

        <a-col :xs="24" :lg="10">
          <a-form :model="formState" layout="vertical" name="picture_upload" @finish="handleSubmit">
            <a-form-item
              label="名称"
              name="name"
              :rules="[{ required: true, message: '请输入图片名称' }]"
            >
              <a-input v-model:value="formState.name" placeholder="请输入图片名称" allow-clear />
            </a-form-item>

            <a-form-item label="简介" name="introduction">
              <a-textarea
                v-model:value="formState.introduction"
                placeholder="介绍一下这张图片的用途或来源"
                :rows="3"
                show-count
                :maxlength="200"
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
                mode="tags"
                placeholder="请选择或输入标签，回车确认"
                :options="tagOptions"
                :max-tag-count="5"
              />
            </a-form-item>
          </a-form>

          <a-button
            block
            class="picture-page__ai"
            :loading="aiPrefilling"
            :disabled="!picture?.id"
            @click="runAiPrefill(true)"
          >
            {{ isEdit ? 'AI 重新读图（覆盖表单）' : 'AI 读图填写' }}
          </a-button>
          <p class="picture-page__ai-tip">
            图会交给读图模型（阿里云百炼）看一遍，给出名称、简介、分类、标签建议；分类与标签只从既有词表里选，不会新增。
          </p>

          <a-alert
            type="info"
            show-icon
            :message="
              spaceId
                ? '将上传到指定空间，仅空间所有者与管理员可见。'
                : '将上传到公共图库，提交后进入人工审核，通过后才会对外可见。'
            "
          />
        </a-col>
      </a-row>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import PictureUpload from '@/components/PictureUpload.vue'
import {
  aiPrefillPictureInfo,
  editPicture,
  getPictureVOById,
  listPictureTagCategory,
} from '@/api/pictureController'
import { formatSize } from '@/utils'

const route = useRoute()
const router = useRouter()

const pictureId = computed(() => (route.query.id ? String(route.query.id) : undefined))
const spaceId = computed(() => (route.query.spaceId ? String(route.query.spaceId) : undefined))
const isEdit = computed(() => Boolean(pictureId.value))

const picture = ref<API.PictureVO | undefined>()

const formState = reactive<API.PictureEditRequest>({
  id: undefined,
  name: '',
  introduction: '',
  category: undefined,
  tags: [],
})

const categoryOptions = reactive<Array<{ label: string; value: string }>>([])
const tagOptions = reactive<Array<{ label: string; value: string }>>([])

const submitting = ref(false)
const aiPrefilling = ref(false)
/**
 * 发起 AI 预填时的表单快照：用来判断"哪些字段用户已经动过"。
 * 规则（docs/adr/0012）：自动预填不覆盖手填值，但会覆盖上传时派生的文件名占位名——它在快照之前写入，
 * 所以对比下来算"没动过"。
 */
let prefillBaseline: Record<'name' | 'introduction' | 'category' | 'tags', string> | null = null

function snapshotForm() {
  prefillBaseline = {
    name: formState.name ?? '',
    introduction: formState.introduction ?? '',
    category: formState.category ?? '',
    tags: JSON.stringify(formState.tags ?? []),
  }
}

function goBack() {
  router.back()
}

/**
 * 把 AI 建议填进表单。
 * overwrite=false（上传后自动）：只填"自快照以来没被动过"的字段；
 * overwrite=true（手动点按钮）：明确要覆盖，全部按建议填。
 */
function applyPrefill(prefill: API.PictureAiPrefillVO, overwrite: boolean) {
  const untouched = (field: 'name' | 'introduction' | 'category') =>
    overwrite || !prefillBaseline || (formState[field] ?? '') === prefillBaseline[field]
  if (prefill.name && untouched('name')) {
    formState.name = prefill.name
  }
  if (prefill.introduction && untouched('introduction')) {
    formState.introduction = prefill.introduction
  }
  if (prefill.category && untouched('category')) {
    formState.category = prefill.category
  }
  const tagsUntouched =
    overwrite || !prefillBaseline || JSON.stringify(formState.tags ?? []) === prefillBaseline.tags
  if (prefill.tags?.length && tagsUntouched) {
    formState.tags = [...prefill.tags]
  }
}

/**
 * 调后端读图填信息。
 * 自动路径（上传后）失败一律静默：AI 是给上传省事的，不能因为它没读出来就打断流程；
 * 手动路径是用户点了按钮才发生的，没有结果要明确说一声，否则像是点了没反应。
 */
async function runAiPrefill(overwrite: boolean) {
  if (!formState.id || aiPrefilling.value) {
    return
  }
  snapshotForm()
  aiPrefilling.value = true
  try {
    const res = await aiPrefillPictureInfo({ pictureId: formState.id })
    if (res.code === 0 && res.data) {
      applyPrefill(res.data, overwrite)
      message.success('AI 已读图填写，检查无误后再点保存')
    } else if (overwrite) {
      message.info('AI 没能读出可用信息，请手动填写')
    }
  } catch {
    if (overwrite) {
      message.info('AI 读图失败，请手动填写')
    }
  } finally {
    aiPrefilling.value = false
  }
}

function handleUploadSuccess(newPicture: API.PictureVO) {
  picture.value = { ...picture.value, ...newPicture }
  formState.id = newPicture.id
  if (!formState.name) {
    formState.name = newPicture.name ?? ''
  }
  // 新建路径上传完成即读图预填；编辑路径由「AI 重新读图」按钮触发
  if (!isEdit.value) {
    runAiPrefill(false)
  }
}

async function loadPicture() {
  if (!pictureId.value) return
  try {
    const res = await getPictureVOById(pictureId.value)
    if (res.code === 0 && res.data) {
      picture.value = res.data
      formState.id = res.data.id
      formState.name = res.data.name ?? ''
      formState.introduction = res.data.introduction ?? ''
      formState.category = res.data.category
      formState.tags = res.data.tags ?? []
    }
  } catch {
    message.error('图片信息加载失败')
  }
}

async function loadOptions() {
  try {
    const res = await listPictureTagCategory()
    if (res.code === 0 && res.data) {
      ;(res.data.categoryList ?? []).forEach((item) =>
        categoryOptions.push({ label: item, value: item }),
      )
      ;(res.data.tagList ?? []).forEach((item) => tagOptions.push({ label: item, value: item }))
    }
  } catch {
    // 选项加载失败不阻塞表单，标签支持手输
  }
}

async function handleSubmit() {
  if (!formState.id) {
    message.warning('请先上传图片')
    return
  }
  if (!formState.name) {
    message.warning('请输入图片名称')
    return
  }
  submitting.value = true
  try {
    const res = await editPicture({
      id: formState.id,
      name: formState.name,
      introduction: formState.introduction,
      category: formState.category,
      tags: formState.tags,
    })
    if (res.code === 0) {
      message.success(isEdit.value ? '保存成功' : '创建成功')
      if (spaceId.value) {
        router.push(`/space/${spaceId.value}`)
      } else {
        router.push(`/picture/${formState.id}`)
      }
    }
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadOptions()
  loadPicture()
})
</script>

<style scoped>
.picture-page__meta {
  margin-top: 16px;
}

.picture-page__ai {
  margin-top: 8px;
}

.picture-page__ai-tip {
  margin: 6px 0 12px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.picture-page__tip {
  margin-top: 16px;
}
</style>
