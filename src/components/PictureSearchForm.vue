<template>
  <a-form class="picture-search-form" layout="inline" :model="formState" @finish="handleSearch">
    <a-form-item label="关键词" name="searchText">
      <a-input
        v-model:value="formState.searchText"
        placeholder="搜索名称 / 简介 / 标签"
        allow-clear
        style="width: 220px"
        @press-enter="handleSearch"
      >
        <template #prefix>
          <SearchOutlined />
        </template>
      </a-input>
    </a-form-item>

    <a-form-item label="分类" name="category">
      <a-select
        v-model:value="formState.category"
        placeholder="全部分类"
        allow-clear
        style="width: 150px"
        :options="categoryOptions"
      />
    </a-form-item>

    <a-form-item label="标签" name="tags">
      <a-select
        v-model:value="formState.tags"
        mode="multiple"
        placeholder="全部标签"
        allow-clear
        :max-tag-count="2"
        style="min-width: 200px"
        :options="tagOptions"
      />
    </a-form-item>

    <a-form-item v-if="showReviewStatus" label="审核状态" name="reviewStatus">
      <a-select
        v-model:value="formState.reviewStatus"
        placeholder="全部状态"
        allow-clear
        style="width: 140px"
        :options="PIC_REVIEW_STATUS_OPTIONS"
      />
    </a-form-item>

    <a-form-item label="排序" name="sortField">
      <a-space>
        <a-select
          v-model:value="formState.sortField"
          style="width: 120px"
          :options="PIC_SORT_FIELD_OPTIONS"
        />
        <a-select v-model:value="formState.sortOrder" style="width: 100px" :options="sortOrderOptions" />
      </a-space>
    </a-form-item>

    <a-form-item>
      <a-space>
        <a-button type="primary" html-type="submit">
          <template #icon><SearchOutlined /></template>
          搜索
        </a-button>
        <a-button @click="handleReset">重置</a-button>
      </a-space>
    </a-form-item>
  </a-form>
</template>

<script setup lang="ts">
import { onMounted, reactive, computed } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { listPictureTagCategory } from '@/api/pictureController'
import { PIC_REVIEW_STATUS_OPTIONS, PIC_SORT_FIELD_OPTIONS } from '@/constant/picture'

interface Props {
  /** 是否展示审核状态筛选（管理员场景） */
  showReviewStatus?: boolean
}

withDefaults(defineProps<Props>(), {
  showReviewStatus: false,
})

const emit = defineEmits<{ (e: 'search', params: API.PictureQueryRequest): void }>()

const formState = reactive<API.PictureQueryRequest>({
  searchText: '',
  category: undefined,
  tags: [],
  reviewStatus: undefined,
  sortField: 'createTime',
  sortOrder: 'descend',
})

const categoryOptions = reactive<Array<{ label: string; value: string }>>([])
const tagOptions = reactive<Array<{ label: string; value: string }>>([])

const sortOrderOptions = [
  { label: '降序', value: 'descend' },
  { label: '升序', value: 'ascend' },
]

/** 对外暴露给父组件的值：去掉空值，避免后端把空串当成查询条件 */
const cleanedParams = computed<API.PictureQueryRequest>(() => {
  const params: API.PictureQueryRequest = {}
  if (formState.searchText) params.searchText = formState.searchText
  if (formState.category) params.category = formState.category
  if (formState.tags?.length) params.tags = formState.tags
  if (formState.reviewStatus !== undefined && formState.reviewStatus !== null) {
    params.reviewStatus = formState.reviewStatus
  }
  if (formState.sortField) params.sortField = formState.sortField
  if (formState.sortOrder) params.sortOrder = formState.sortOrder
  return params
})

function handleSearch() {
  emit('search', { ...cleanedParams.value, current: 1 })
}

function handleReset() {
  formState.searchText = ''
  formState.category = undefined
  formState.tags = []
  formState.reviewStatus = undefined
  formState.sortField = 'createTime'
  formState.sortOrder = 'descend'
  emit('search', { current: 1 })
}

async function loadOptions() {
  try {
    const res = await listPictureTagCategory()
    if (res.code === 0 && res.data) {
      ;(res.data.categoryList ?? []).forEach((item) => {
        categoryOptions.push({ label: item, value: item })
      })
      ;(res.data.tagList ?? []).forEach((item) => {
        tagOptions.push({ label: item, value: item })
      })
    }
  } catch {
    // 选项加载失败不阻塞筛选表单，用户可以手输关键词
  }
}

onMounted(loadOptions)
</script>

<style scoped>
.picture-search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 0;
  padding: 16px;
  background: var(--app-panel, #fff);
  border-radius: 12px;
}

:deep(.ant-form-item) {
  margin-bottom: 8px;
}
</style>
