<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">团队空间</h2>
        <p class="page-header__desc">
          我创建的与我加入的团队空间。私有空间请走导航上的「私有空间」入口，它每人只有一个
        </p>
      </div>
      <a-button type="primary" @click="router.push('/add_space')">
        <template #icon><PlusOutlined /></template>
        创建团队空间
      </a-button>
    </div>

    <a-spin :spinning="loading">
      <a-row v-if="spaceList.length" :gutter="[16, 16]">
        <a-col v-for="space in spaceList" :key="space.id" :xs="24" :sm="12" :lg="8" :xl="6">
          <a-card class="space-card" :bordered="false" hoverable>
            <div class="space-card__head">
              <div class="space-card__name">
                <FolderOpenOutlined class="space-card__icon" />
                <span :title="space.spaceName">{{ space.spaceName }}</span>
              </div>
              <a-tag :color="SPACE_LEVEL_COLOR_MAP[space.spaceLevel ?? 0]" :bordered="false">
                {{ SPACE_LEVEL_MAP[space.spaceLevel ?? 0] }}
              </a-tag>
            </div>

            <div class="space-card__role">
              <a-tag :color="SPACE_ROLE_COLOR_MAP[space.myRole ?? '']" :bordered="false">
                {{ myRoleLabel(space) }}
              </a-tag>
              <span class="space-card__members">
                {{ space.memberCount ?? 0 }} / {{ space.maxMemberCount ?? 0 }} 名成员
              </span>
            </div>

            <div class="space-card__usage">
              <div class="space-card__usage-label">
                <span>存储用量</span>
                <span>{{ formatSize(space.totalSize) }} / {{ formatSize(space.maxSize) }}</span>
              </div>
              <a-progress
                :percent="calcPercent(space.totalSize, space.maxSize)"
                :show-info="false"
                size="small"
                :status="sizeStatus(space)"
              />

              <div class="space-card__usage-label space-card__usage-label--gap">
                <span>图片数量</span>
                <span>{{ space.totalCount ?? 0 }} / {{ space.maxCount ?? 0 }}</span>
              </div>
              <a-progress
                :percent="calcPercent(space.totalCount, space.maxCount)"
                :show-info="false"
                size="small"
                :status="countStatus(space)"
              />
            </div>

            <div class="space-card__meta">
              <span>创建于 {{ formatDate(space.createTime, 'YYYY-MM-DD') }}</span>
            </div>

            <template #actions>
              <a-tooltip title="查看空间图片">
                <EyeOutlined @click="router.push(`/space/${space.id}`)" />
              </a-tooltip>
              <a-tooltip :title="canUpload(space) ? '上传图片' : '只读成员不能上传'">
                <CloudUploadOutlined
                  :class="{ 'space-card__disabled': !canUpload(space) }"
                  @click="canUpload(space) && router.push({ path: '/add_picture', query: { spaceId: space.id } })"
                />
              </a-tooltip>
              <a-tooltip :title="canManage(space) ? '空间分析' : '仅空间管理员可查看'">
                <BarChartOutlined
                  :class="{ 'space-card__disabled': !canManage(space) }"
                  @click="canManage(space) && router.push(`/space/${space.id}/analyze`)"
                />
              </a-tooltip>
              <a-tooltip :title="canManage(space) ? '编辑名称' : '仅空间管理员可编辑'">
                <EditOutlined
                  :class="{ 'space-card__disabled': !canManage(space) }"
                  @click="canManage(space) && openEditModal(space)"
                />
              </a-tooltip>
              <a-tooltip v-if="isAdmin" title="删除空间">
                <DeleteOutlined class="space-card__danger" @click="doDelete(space)" />
              </a-tooltip>
            </template>
          </a-card>
        </a-col>
      </a-row>

      <a-result
        v-else-if="loadError && !loading"
        status="warning"
        title="空间列表加载失败"
        sub-title="接口请求异常，请确认后端服务已启动并包含空间模块（/api/space/mine）"
      >
        <template #extra>
          <a-button type="primary" @click="loadMySpaces">重新加载</a-button>
        </template>
      </a-result>

      <a-empty v-else-if="!loading" description="你还没有创建或加入任何团队空间">
        <a-button type="primary" @click="router.push('/add_space')">立即创建</a-button>
      </a-empty>
    </a-spin>

    <!-- 编辑空间名称 -->
    <a-modal
      v-model:open="editModalOpen"
      title="编辑空间名称"
      :confirm-loading="editSubmitting"
      @ok="handleEditSubmit"
    >
      <a-form layout="vertical">
        <a-form-item label="空间名称" required>
          <a-input v-model:value="editForm.spaceName" placeholder="请输入空间名称" allow-clear />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  BarChartOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  FolderOpenOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue'
import { deleteSpace, editSpace, listMySpace } from '@/api/spaceController'
import {
  SPACE_LEVEL_COLOR_MAP,
  SPACE_LEVEL_MAP,
  SPACE_ROLE_COLOR_MAP,
  SPACE_ROLE_ENUM,
  SPACE_ROLE_MAP,
  SPACE_TYPE_ENUM,
} from '@/constant/space'
import { calcPercent, formatDate, formatSize } from '@/utils'
import { useLoginUserStore } from '@/stores/useLoginUserStore'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const isAdmin = computed(() => loginUserStore.loginUser?.userRole === 'admin')

const spaceList = ref<API.SpaceVO[]>([])
const loading = ref(false)
const loadError = ref(false)

/**
 * 卡片上的每个操作按「我在该空间的角色」显隐，而不是按站点身份。
 * 站点管理员在后端被视作所有空间的管理员，所以 myRole 已经涵盖这种情况。
 */
function canUpload(space: API.SpaceVO) {
  return space.myRole === SPACE_ROLE_ENUM.ADMIN || space.myRole === SPACE_ROLE_ENUM.EDITOR
}

function canManage(space: API.SpaceVO) {
  return space.myRole === SPACE_ROLE_ENUM.ADMIN
}

/** 自己创建的空间标「创建者」，被邀请进来的标角色名 */
function myRoleLabel(space: API.SpaceVO) {
  if (space.userId && space.userId === loginUserStore.loginUser?.id) {
    return '创建者'
  }
  return SPACE_ROLE_MAP[space.myRole ?? ''] ?? '成员'
}

const editModalOpen = ref(false)
const editSubmitting = ref(false)
const editForm = reactive<API.SpaceEditRequest>({
  id: undefined,
  spaceName: '',
})

/** 用量超 90% 时进度条转为警告色 */
function sizeStatus(space: API.SpaceVO) {
  const percent = calcPercent(space.totalSize, space.maxSize)
  if (percent >= 100) return 'exception'
  if (percent >= 90) return 'active' as const
  return 'normal' as const
}

function countStatus(space: API.SpaceVO) {
  const percent = calcPercent(space.totalCount, space.maxCount)
  if (percent >= 100) return 'exception'
  return 'normal' as const
}

async function loadMySpaces() {
  loading.value = true
  loadError.value = false
  try {
    const res = await listMySpace()
    if (res.code === 0 && res.data) {
      // /space/mine 返回全部有成员关系的空间，这里只留团队的：
      // 私有空间每人恰好一个，走导航上的「私有空间」直达入口，不在这个列表里
      spaceList.value = res.data.filter((space) => space.spaceType === SPACE_TYPE_ENUM.TEAM)
    } else {
      spaceList.value = []
      loadError.value = true
    }
  } catch {
    // HTTP 层异常（如接口不存在返回 404）也走错误态，避免误显示为「没有空间」
    spaceList.value = []
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function openEditModal(space: API.SpaceVO) {
  editForm.id = space.id
  editForm.spaceName = space.spaceName ?? ''
  editModalOpen.value = true
}

async function handleEditSubmit() {
  if (!editForm.spaceName?.trim()) {
    message.warning('请输入空间名称')
    return
  }
  editSubmitting.value = true
  try {
    const res = await editSpace({ id: editForm.id, spaceName: editForm.spaceName })
    if (res.code === 0) {
      message.success('修改成功')
      editModalOpen.value = false
      loadMySpaces()
    }
  } finally {
    editSubmitting.value = false
  }
}

function doDelete(space: API.SpaceVO) {
  Modal.confirm({
    title: '确认删除该空间？',
    content: `「${space.spaceName}」下的图片将一并删除，操作不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      const res = await deleteSpace({ id: space.id })
      if (res.code === 0) {
        message.success('删除成功')
        loadMySpaces()
      }
    },
  })
}

onMounted(loadMySpaces)
</script>

<style scoped>
.space-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.space-card__name {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
}

.space-card__name span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.space-card__icon {
  color: var(--app-accent, #1677ff);
}

.space-card__role {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.space-card__members {
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

.space-card__disabled {
  color: rgba(0, 0, 0, 0.25);
  cursor: not-allowed;
}

.space-card__usage {
  margin-top: 16px;
}

.space-card__usage-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

.space-card__usage-label--gap {
  margin-top: 14px;
}

.space-card__meta {
  margin-top: 14px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

.space-card__danger {
  color: #ff4d4f;
}
</style>
