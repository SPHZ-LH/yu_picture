<template>
  <div class="page-container">
    <AdminTabs />

    <!-- 筛选 -->
    <a-card class="common-search-form" :bordered="false" title="筛选条件">
      <template #extra>
        <a-space>
          <a-button type="primary" @click="doSearch">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetSearch">重置</a-button>
        </a-space>
      </template>
      <a-form :model="searchParams" layout="inline" name="videoManageQuery">
        <a-form-item label="id" name="id">
          <a-input v-model:value="searchParams.id" placeholder="请输入视频 id" allow-clear />
        </a-form-item>
        <a-form-item label="名称" name="name">
          <a-input v-model:value="searchParams.name" placeholder="请输入视频名称" allow-clear />
        </a-form-item>
        <a-form-item label="审核状态" name="reviewStatus">
          <a-select
            v-model:value="searchParams.reviewStatus"
            style="min-width: 150px"
            placeholder="请选择审核状态"
            :options="PIC_REVIEW_STATUS_OPTIONS"
            allow-clear
          />
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 列表 -->
    <a-card :bordered="false">
      <template #title>
        <a-space>
          <span>动态壁纸列表</span>
          <a-tag v-if="total" :bordered="false">共 {{ total }} 条</a-tag>
        </a-space>
      </template>
      <template #extra>
        <a-button type="primary" @click="router.push('/add_video')">
          <template #icon><PlusOutlined /></template>
          上传动态壁纸
        </a-button>
      </template>

      <a-table
        :columns="columns"
        :data-source="videoList"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        size="middle"
        :scroll="{ x: 1100 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'video'">
            <div class="video-cell">
              <img v-if="record.coverUrl" :src="cosResizeUrl(record.coverUrl, 200)" :alt="record.name" class="video-cell__img" />
              <div class="video-cell__info">
                <span class="video-cell__name">{{ record.name || '未命名' }}</span>
                <span class="video-cell__id">ID: {{ record.id }}</span>
              </div>
            </div>
          </template>

          <template v-else-if="column.key === 'spec'">
            {{ record.videoWidth }}×{{ record.videoHeight }} ·
            {{ formatDuration(record.videoDuration) || '—' }} ·
            {{ formatSize(record.videoSize) }}
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
              <a-button type="link" size="small" @click="openPreview(record)">预览</a-button>
              <a-button type="link" size="small" @click="openReviewModal(record)">审核</a-button>
              <a-button type="link" size="small" danger @click="doDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 审片弹窗：管理员要能看到内容才能判，这里直接内嵌播放器 -->
    <a-modal
      v-model:open="previewOpen"
      :title="current?.name || '视频预览'"
      :footer="null"
      :width="860"
      destroy-on-close
    >
      <video
        v-if="previewOpen && current?.url"
        :src="current.url"
        class="video-preview__player"
        controls
        autoplay
        muted
        playsinline
      />
    </a-modal>

    <a-modal
      v-model:open="reviewModalOpen"
      title="视频审核"
      :confirm-loading="reviewSubmitting"
      @ok="handleReviewSubmit"
    >
      <a-form :model="reviewForm" layout="vertical" name="videoReviewForm">
        <a-form-item label="审核结果" name="reviewStatus">
          <a-radio-group v-model:value="reviewForm.reviewStatus" :options="PIC_REVIEW_STATUS_OPTIONS" />
        </a-form-item>
        <a-form-item label="审核信息" name="reviewMessage">
          <a-textarea v-model:value="reviewForm.reviewMessage" placeholder="拒绝时建议写明原因" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import AdminTabs from '@/components/AdminTabs.vue'
import { deleteVideo, listVideoByPage, reviewVideo } from '@/api/videoController'
import {
  PIC_REVIEW_STATUS_COLOR_MAP,
  PIC_REVIEW_STATUS_MAP,
  PIC_REVIEW_STATUS_OPTIONS,
} from '@/constant/picture'
import { cosResizeUrl, formatDate, formatDuration, formatSize } from '@/utils'

const router = useRouter()

const columns = [
  { title: '视频', dataIndex: 'name', key: 'video', width: 280, ellipsis: true },
  { title: '规格', key: 'spec', width: 200 },
  { title: '审核状态', dataIndex: 'reviewStatus', key: 'reviewStatus', width: 110 },
  { title: '归属', dataIndex: 'spaceId', key: 'spaceId', width: 120 },
  { title: '上传时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' as const },
]

const videoList = ref<API.Video[]>([])
const total = ref(0)
const loading = ref(false)
const current = ref<API.Video>()
const previewOpen = ref(false)
const reviewModalOpen = ref(false)
const reviewSubmitting = ref(false)
const reviewForm = reactive<{ reviewStatus: number; reviewMessage: string }>({
  reviewStatus: 1,
  reviewMessage: '',
})

const searchParams = reactive<API.VideoQueryRequest>({
  id: undefined,
  name: undefined,
  reviewStatus: undefined,
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50'],
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listVideoByPage({
      ...searchParams,
      current: pagination.current,
      pageSize: pagination.pageSize,
      sortField: 'createTime',
      sortOrder: 'descend',
    })
    if (res.code === 0 && res.data) {
      videoList.value = res.data.records ?? []
      total.value = Number(res.data.total ?? 0)
    }
  } finally {
    loading.value = false
  }
}

const doSearch = () => {
  pagination.current = 1
  void fetchData()
}

const resetSearch = () => {
  searchParams.id = undefined
  searchParams.name = undefined
  searchParams.reviewStatus = undefined
  doSearch()
}

const handleTableChange = (pag: { current?: number; pageSize?: number }) => {
  pagination.current = pag.current ?? 1
  pagination.pageSize = pag.pageSize ?? 10
  void fetchData()
}

const openPreview = (record: API.Video) => {
  current.value = record
  previewOpen.value = true
}

const openReviewModal = (record: API.Video) => {
  current.value = record
  reviewForm.reviewStatus = record.reviewStatus === 2 ? 1 : record.reviewStatus ?? 1
  reviewForm.reviewMessage = record.reviewMessage ?? ''
  reviewModalOpen.value = true
}

const handleReviewSubmit = async () => {
  if (!current.value?.id) return
  reviewSubmitting.value = true
  try {
    const res = await reviewVideo({
      id: current.value.id,
      reviewStatus: reviewForm.reviewStatus,
      reviewMessage: reviewForm.reviewMessage,
    })
    if (res.code === 0) {
      message.success('审核已提交')
      reviewModalOpen.value = false
      void fetchData()
    }
  } finally {
    reviewSubmitting.value = false
  }
}

const doDelete = (record: API.Video) => {
  Modal.confirm({
    title: '删除视频',
    content: `确定删除「${record.name || '未命名'}」吗？对象存储中的文件会保留。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      const res = await deleteVideo({ id: record.id })
      if (res.code === 0) {
        message.success('删除成功')
        void fetchData()
      }
    },
  })
}

onMounted(fetchData)
</script>

<style scoped>
.video-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.video-cell__img {
  width: 72px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 6px;
  object-fit: cover;
  background: var(--app-panel-hover, #f0f2f5);
}

.video-cell__info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.video-cell__name {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.video-cell__id {
  color: var(--app-text-secondary);
  font-size: 12px;
}

.video-preview__player {
  width: 100%;
  max-height: 60vh;
  border-radius: 12px;
  background: #0d0f14;
}
</style>
