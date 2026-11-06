<template>
  <div class="page-container">
    <AdminTabs />

    <!-- 筛选 -->
    <a-card :bordered="false">
      <a-form layout="inline" :model="queryParams" @finish="handleSearch">
        <a-form-item label="空间名称">
          <a-input
            v-model:value="queryParams.spaceName"
            placeholder="按名称搜索"
            allow-clear
            style="width: 180px"
          />
        </a-form-item>
        <a-form-item label="空间 id">
          <a-input
            v-model:value="queryParams.id"
            placeholder="按 id 精确查询"
            allow-clear
            style="width: 160px"
          />
        </a-form-item>
        <a-form-item label="等级">
          <a-select
            v-model:value="queryParams.spaceLevel"
            placeholder="全部等级"
            allow-clear
            style="width: 140px"
            :options="SPACE_LEVEL_OPTIONS"
          />
        </a-form-item>
        <a-form-item label="类型">
          <a-select
            v-model:value="queryParams.spaceType"
            placeholder="全部类型"
            allow-clear
            style="width: 140px"
            :options="SPACE_TYPE_OPTIONS"
          />
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
    </a-card>

    <!-- 列表 -->
    <a-card :bordered="false">
      <template #title>
        <a-space>
          <span>空间列表</span>
          <a-tag v-if="total" :bordered="false">共 {{ total }} 个</a-tag>
        </a-space>
      </template>
      <template #extra>
        <a-button type="primary" @click="router.push('/add_space')">
          <template #icon><PlusOutlined /></template>
          创建空间
        </a-button>
      </template>

      <a-alert
        v-if="loadError"
        class="load-error-alert"
        type="error"
        show-icon
        message="空间列表加载失败"
        description="接口请求异常，请确认后端服务已包含空间模块（/api/space/list/page）后重试。"
      >
        <template #action>
          <a-button size="small" @click="loadSpaces">重试</a-button>
        </template>
      </a-alert>

      <a-table
        :columns="columns"
        :data-source="spaceList"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        size="middle"
        :scroll="{ x: 1180 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'spaceName'">
            <a-space>
              <FolderOpenOutlined style="color: var(--app-accent, #1677ff)" />
              <span class="space-name">{{ record.spaceName }}</span>
            </a-space>
          </template>

          <template v-else-if="column.key === 'spaceLevel'">
            <a-tag :color="SPACE_LEVEL_COLOR_MAP[record.spaceLevel ?? 0]" :bordered="false">
              {{ SPACE_LEVEL_MAP[record.spaceLevel ?? 0] }}
            </a-tag>
          </template>

          <template v-else-if="column.key === 'spaceType'">
            {{ SPACE_TYPE_MAP[record.spaceType ?? 0] }}
          </template>

          <template v-else-if="column.key === 'usage'">
            <div class="usage-cell">
              <span>
                {{ formatSize(record.totalSize) }} / {{ formatSize(record.maxSize) }}
              </span>
              <a-progress
                :percent="calcPercent(record.totalSize, record.maxSize)"
                size="small"
                :show-info="false"
              />
              <span class="usage-cell__sub">
                {{ record.totalCount ?? 0 }} / {{ record.maxCount ?? 0 }} 张
              </span>
            </div>
          </template>

          <template v-else-if="column.key === 'createTime'">
            {{ formatDate(record.createTime) }}
          </template>

          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a-button type="link" size="small" @click="router.push(`/space/${record.id}`)">
                查看
              </a-button>
              <a-button type="link" size="small" @click="openEditModal(record)">编辑</a-button>
              <a-button type="link" size="small" danger @click="doDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 编辑弹窗 -->
    <a-modal
      v-model:open="editModalOpen"
      title="编辑空间"
      :confirm-loading="editSubmitting"
      @ok="handleEditSubmit"
    >
      <a-form layout="vertical">
        <a-form-item label="空间名称" required>
          <a-input v-model:value="editForm.spaceName" placeholder="请输入空间名称" allow-clear />
        </a-form-item>
        <a-form-item label="空间等级">
          <a-select v-model:value="editForm.spaceLevel" :options="SPACE_LEVEL_OPTIONS" />
          <div class="form-tip">调整等级会按新等级重置该空间的配额上限</div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { FolderOpenOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import AdminTabs from '@/components/AdminTabs.vue'
import { deleteSpace, listSpaceByPage, updateSpace } from '@/api/spaceController'
import {
  SPACE_LEVEL_COLOR_MAP,
  SPACE_LEVEL_MAP,
  SPACE_LEVEL_OPTIONS,
  SPACE_TYPE_MAP,
  SPACE_TYPE_OPTIONS,
} from '@/constant/space'
import { calcPercent, formatDate, formatSize } from '@/utils'

const router = useRouter()

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '空间名称', key: 'spaceName', width: 200 },
  { title: '等级', key: 'spaceLevel', width: 100 },
  { title: '类型', key: 'spaceType', width: 100 },
  { title: '用户 id', dataIndex: 'userId', key: 'userId', width: 100 },
  { title: '配额使用', key: 'usage', width: 220 },
  { title: '创建时间', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 170, fixed: 'right' as const },
]

const spaceList = ref<API.Space[]>([])
const loading = ref(false)
const loadError = ref(false)
const total = ref(0)
const current = ref(1)
const pageSize = ref(10)

const queryParams = reactive<API.SpaceQueryRequest>({
  spaceName: '',
  id: undefined,
  spaceLevel: undefined,
  spaceType: undefined,
})

const editModalOpen = ref(false)
const editSubmitting = ref(false)
const editForm = reactive<API.SpaceUpdateRequest>({
  id: undefined,
  spaceName: '',
  spaceLevel: 0,
})

const pagination = computed(() => ({
  current: current.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`,
}))

async function loadSpaces() {
  loading.value = true
  loadError.value = false
  try {
    const res = await listSpaceByPage({
      id: queryParams.id || undefined,
      spaceName: queryParams.spaceName || undefined,
      spaceLevel: queryParams.spaceLevel,
      spaceType: queryParams.spaceType,
      current: current.value,
      pageSize: pageSize.value,
    })
    if (res.code === 0 && res.data) {
      spaceList.value = res.data.records ?? []
      total.value = Number(res.data.total ?? 0)
    } else {
      spaceList.value = []
      total.value = 0
      loadError.value = true
    }
  } catch {
    spaceList.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  current.value = 1
  loadSpaces()
}

function handleReset() {
  queryParams.spaceName = ''
  queryParams.id = undefined
  queryParams.spaceLevel = undefined
  queryParams.spaceType = undefined
  handleSearch()
}

function handleTableChange(pag: { current?: number; pageSize?: number }) {
  current.value = pag.current ?? 1
  pageSize.value = pag.pageSize ?? 10
  loadSpaces()
}

function openEditModal(record: API.Space) {
  editForm.id = record.id
  editForm.spaceName = record.spaceName ?? ''
  editForm.spaceLevel = record.spaceLevel ?? 0
  editModalOpen.value = true
}

async function handleEditSubmit() {
  if (!editForm.spaceName?.trim()) {
    message.warning('请输入空间名称')
    return
  }
  editSubmitting.value = true
  try {
    const res = await updateSpace({
      id: editForm.id,
      spaceName: editForm.spaceName,
      spaceLevel: editForm.spaceLevel,
    })
    if (res.code === 0) {
      message.success('修改成功')
      editModalOpen.value = false
      loadSpaces()
    }
  } finally {
    editSubmitting.value = false
  }
}

function doDelete(record: API.Space) {
  Modal.confirm({
    title: '确认删除该空间？',
    content: `「${record.spaceName}」下的图片将一并删除，操作不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      const res = await deleteSpace({ id: record.id })
      if (res.code === 0) {
        message.success('删除成功')
        loadSpaces()
      }
    },
  })
}

onMounted(loadSpaces)
</script>

<style scoped>
.load-error-alert {
  margin-bottom: 16px;
}

.space-name {
  font-weight: 500;
}

.usage-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.65));
}

.usage-cell__sub {
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
}

.form-tip {
  margin-top: 4px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}
</style>
