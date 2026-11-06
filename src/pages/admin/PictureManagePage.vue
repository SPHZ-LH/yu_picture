<template>
  <div class="page-container">
    <AdminTabs />

    <!-- 筛选 -->
    <PictureSearchForm show-review-status @search="handleSearch" />

    <!-- 列表 -->
    <a-card :bordered="false">
      <template #title>
        <a-space>
          <span>图片列表</span>
          <a-tag v-if="total" :bordered="false">共 {{ total }} 张</a-tag>
          <a-tag v-if="selectedRowKeys.length" color="blue" :bordered="false">
            已选 {{ selectedRowKeys.length }} 张
          </a-tag>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-button
            :disabled="!selectedRowKeys.length"
            :loading="batchLoading"
            @click="handleBatchReview(PIC_REVIEW_STATUS_ENUM.ACCEPT)"
          >
            批量通过
          </a-button>
          <a-button
            :disabled="!selectedRowKeys.length"
            :loading="batchLoading"
            danger
            @click="handleBatchReview(PIC_REVIEW_STATUS_ENUM.REJECT)"
          >
            批量拒绝
          </a-button>
          <a-button type="primary" @click="router.push('/add_picture/batch')">
            <template #icon><PlusOutlined /></template>
            批量创建
          </a-button>
        </a-space>
      </template>

      <a-table
        :columns="columns"
        :data-source="pictureList"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
        row-key="id"
        size="middle"
        :scroll="{ x: 1200 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'picture'">
            <div class="picture-cell">
              <img :src="record.url" :alt="record.name" class="picture-cell__img" />
              <div class="picture-cell__info">
                <span class="picture-cell__name">{{ record.name || '未命名' }}</span>
                <span class="picture-cell__id">ID: {{ record.id }}</span>
              </div>
            </div>
          </template>

          <template v-else-if="column.key === 'category'">
            <a-tag v-if="record.category" color="blue" :bordered="false">
              {{ record.category }}
            </a-tag>
            <span v-else>-</span>
          </template>

          <template v-else-if="column.key === 'tags'">
            <template v-if="parseTags(record.tags).length">
              <a-tag v-for="tag in parseTags(record.tags)" :key="tag" :bordered="false">
                {{ tag }}
              </a-tag>
            </template>
            <span v-else>-</span>
          </template>

          <template v-else-if="column.key === 'reviewStatus'">
            <a-tag :color="PIC_REVIEW_STATUS_COLOR_MAP[record.reviewStatus ?? 0]" :bordered="false">
              {{ PIC_REVIEW_STATUS_MAP[record.reviewStatus ?? 0] }}
            </a-tag>
          </template>

          <template v-else-if="column.key === 'spaceId'">
            <RouterLink v-if="record.spaceId" :to="`/space/${record.spaceId}`">
              空间 #{{ record.spaceId }}
            </RouterLink>
            <a-tag v-else :bordered="false">公共图库</a-tag>
          </template>

          <template v-else-if="column.key === 'createTime'">
            {{ formatDate(record.createTime) }}
          </template>

          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a-button type="link" size="small" @click="router.push(`/picture/${record.id}`)">
                查看
              </a-button>
              <a-button type="link" size="small" @click="router.push(`/edit_picture/${record.id}`)">
                画面编辑
              </a-button>
              <a-button type="link" size="small" @click="openReviewModal(record)">审核</a-button>
              <a-button type="link" size="small" danger @click="doDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 审核弹窗 -->
    <a-modal
      v-model:open="reviewModalOpen"
      title="图片审核"
      :confirm-loading="reviewSubmitting"
      @ok="handleReviewSubmit"
    >
      <a-form layout="vertical">
        <a-form-item label="审核结果" required>
          <a-radio-group v-model:value="reviewForm.reviewStatus">
            <a-radio :value="PIC_REVIEW_STATUS_ENUM.ACCEPT">通过</a-radio>
            <a-radio :value="PIC_REVIEW_STATUS_ENUM.REJECT">拒绝</a-radio>
            <a-radio :value="PIC_REVIEW_STATUS_ENUM.REVIEWING">待审核</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="审核信息">
          <a-textarea
            v-model:value="reviewForm.reviewMessage"
            placeholder="拒绝时请说明原因，便于上传者修改"
            :rows="3"
            show-count
            :maxlength="200"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import AdminTabs from '@/components/AdminTabs.vue'
import PictureSearchForm from '@/components/PictureSearchForm.vue'
import { deletePicture, listPictureByPage, reviewPicture } from '@/api/pictureController'
import {
  PIC_REVIEW_STATUS_COLOR_MAP,
  PIC_REVIEW_STATUS_ENUM,
  PIC_REVIEW_STATUS_MAP,
} from '@/constant/picture'
import { formatDate, parseTags } from '@/utils'

const router = useRouter()

const columns = [
  { title: '图片', key: 'picture', width: 260, fixed: 'left' as const },
  { title: '分类', key: 'category', width: 110 },
  { title: '标签', key: 'tags', width: 200 },
  { title: '归属', key: 'spaceId', width: 130 },
  { title: '审核状态', key: 'reviewStatus', width: 110 },
  { title: '创建时间', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 180, fixed: 'right' as const },
]

const pictureList = ref<API.Picture[]>([])
const loading = ref(false)
const total = ref(0)
const current = ref(1)
const pageSize = ref(10)
const queryParams = ref<API.PictureQueryRequest>({})

const selectedRowKeys = ref<string[]>([])
const batchLoading = ref(false)

const reviewModalOpen = ref(false)
const reviewSubmitting = ref(false)
const reviewForm = reactive<API.PictureReviewRequest>({
  id: undefined,
  reviewStatus: PIC_REVIEW_STATUS_ENUM.ACCEPT,
  reviewMessage: '',
})

const pagination = computed(() => ({
  current: current.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`,
}))

async function loadPictures() {
  loading.value = true
  try {
    // 管理员使用 listPictureByPage：可查看全部图片（含未过审），不受公共图库过滤限制
    const res = await listPictureByPage({
      ...queryParams.value,
      current: current.value,
      pageSize: pageSize.value,
    })
    if (res.code === 0 && res.data) {
      pictureList.value = res.data.records ?? []
      total.value = Number(res.data.total ?? 0)
    } else {
      pictureList.value = []
      total.value = 0
    }
  } catch {
    pictureList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch(params: API.PictureQueryRequest) {
  queryParams.value = params
  current.value = 1
  selectedRowKeys.value = []
  loadPictures()
}

function handleTableChange(pag: { current?: number; pageSize?: number }) {
  current.value = pag.current ?? 1
  pageSize.value = pag.pageSize ?? 10
  loadPictures()
}

function onSelectChange(keys: (string | number)[]) {
  selectedRowKeys.value = keys.map(String)
}

function openReviewModal(record: API.Picture) {
  reviewForm.id = record.id
  reviewForm.reviewStatus = record.reviewStatus ?? PIC_REVIEW_STATUS_ENUM.REVIEWING
  reviewForm.reviewMessage = record.reviewMessage ?? ''
  reviewModalOpen.value = true
}

async function handleReviewSubmit() {
  reviewSubmitting.value = true
  try {
    const res = await reviewPicture({
      id: reviewForm.id,
      reviewStatus: reviewForm.reviewStatus,
      reviewMessage: reviewForm.reviewMessage,
    })
    if (res.code === 0) {
      message.success('审核完成')
      reviewModalOpen.value = false
      loadPictures()
    }
  } finally {
    reviewSubmitting.value = false
  }
}

async function handleBatchReview(status: number) {
  const action = PIC_REVIEW_STATUS_MAP[status]
  Modal.confirm({
    title: `确认批量${action}？`,
    content: `将把选中的 ${selectedRowKeys.value.length} 张图片设为「${action}」。`,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      batchLoading.value = true
      try {
        // 后端提供的是单张审核接口，这里并发提交
        const results = await Promise.all(
          selectedRowKeys.value.map((id) =>
            reviewPicture({ id, reviewStatus: status, reviewMessage: `批量${action}` }),
          ),
        )
        const successCount = results.filter((res) => res.code === 0).length
        if (successCount === selectedRowKeys.value.length) {
          message.success(`已${action} ${successCount} 张图片`)
        } else {
          message.warning(`${successCount} / ${selectedRowKeys.value.length} 张处理成功`)
        }
        selectedRowKeys.value = []
        loadPictures()
      } finally {
        batchLoading.value = false
      }
    },
  })
}

function doDelete(record: API.Picture) {
  Modal.confirm({
    title: '确认删除这张图片？',
    content: `「${record.name || '未命名'}」删除后不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      const res = await deletePicture({ id: record.id })
      if (res.code === 0) {
        message.success('删除成功')
        loadPictures()
      }
    },
  })
}

onMounted(loadPictures)
</script>

<style scoped>
.picture-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.picture-cell__img {
  width: 56px;
  height: 56px;
  object-fit: cover;
  border-radius: 8px;
  background: var(--app-panel-hover, #f0f2f5);
}

.picture-cell__info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.4;
}

.picture-cell__name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 160px;
}

.picture-cell__id {
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}
</style>
