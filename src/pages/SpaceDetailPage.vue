<template>
  <div class="page-container">
    <!-- 空间信息 -->
    <a-result
      v-if="spaceError"
      status="warning"
      title="空间信息加载失败"
      sub-title="接口请求异常，请确认后端服务已启动并包含空间模块（/api/space/get/vo）"
    >
      <template #extra>
        <a-button type="primary" @click="reloadAll">重新加载</a-button>
        <!-- 加载失败时拿不到 spaceType，无法判断该回私有还是团队，统一回团队空间列表（导航上另有私有空间入口） -->
        <a-button @click="router.push('/my_space')">返回团队空间</a-button>
      </template>
    </a-result>

    <a-card v-else-if="space" :bordered="false">
      <div class="space-header">
        <div class="space-header__left">
          <div class="space-header__title">
            <FolderOpenOutlined class="space-header__icon" />
            <h2>{{ space.spaceName }}</h2>
            <a-tag :color="SPACE_LEVEL_COLOR_MAP[space.spaceLevel ?? 0]" :bordered="false">
              {{ SPACE_LEVEL_MAP[space.spaceLevel ?? 0] }}
            </a-tag>
            <a-tag :bordered="false">{{ SPACE_TYPE_MAP[space.spaceType ?? 0] }}</a-tag>
          </div>
          <div class="space-header__meta">
            <span>创建于 {{ formatDate(space.createTime) }}</span>
            <a-divider type="vertical" />
            <span>空间 ID：{{ space.id }}</span>
          </div>
        </div>

        <div class="space-header__right">
          <a-space wrap>
            <a-button
              v-if="canUpload"
              type="primary"
              @click="router.push({ path: '/add_picture', query: { spaceId: space.id } })"
            >
              <template #icon><CloudUploadOutlined /></template>
              上传图片
            </a-button>
            <a-button
              v-if="canUpload"
              @click="router.push({ path: '/add_video', query: { spaceId: space.id } })"
            >
              <template #icon><VideoCameraOutlined /></template>
              上传动态壁纸
            </a-button>
            <a-button v-if="isSpaceAdmin" @click="router.push(`/space/${space.id}/analyze`)">
              <template #icon><BarChartOutlined /></template>
              空间分析
            </a-button>
            <a-button v-if="isSpaceAdmin" @click="openEditModal">
              <template #icon><EditOutlined /></template>
              编辑
            </a-button>
          </a-space>
        </div>
      </div>

      <a-divider style="margin: 16px 0" />

      <a-row :gutter="24">
        <a-col :xs="24" :sm="isTeam ? 8 : 12">
          <div class="space-quota">
            <div class="space-quota__label">
              <span>存储用量</span>
              <span>{{ formatSize(space.totalSize) }} / {{ formatSize(space.maxSize) }}</span>
            </div>
            <a-progress :percent="calcPercent(space.totalSize, space.maxSize)" size="small" />
          </div>
        </a-col>
        <a-col :xs="24" :sm="isTeam ? 8 : 12">
          <div class="space-quota">
            <div class="space-quota__label">
              <span>图片数量</span>
              <span>{{ space.totalCount ?? 0 }} / {{ space.maxCount ?? 0 }}</span>
            </div>
            <a-progress :percent="calcPercent(space.totalCount, space.maxCount)" size="small" />
          </div>
        </a-col>
        <!-- 私有空间恒为创建者一人，展示成员配额没有信息量 -->
        <a-col v-if="isTeam" :xs="24" :sm="8">
          <div class="space-quota">
            <div class="space-quota__label">
              <span>成员数量</span>
              <span :class="{ 'space-quota__over': memberOverCap }">
                {{ space.memberCount ?? 0 }} / {{ space.maxMemberCount ?? 0 }}
              </span>
            </div>
            <!-- 降低上限不清退已有成员，所以这里可能超过 100%，进度条按满格显示并标红数字 -->
            <a-progress
              :percent="Math.min(calcPercent(space.memberCount, space.maxMemberCount), 100)"
              :status="memberOverCap ? 'exception' : 'normal'"
              size="small"
            />
          </div>
        </a-col>
      </a-row>
    </a-card>

    <!-- 成员管理：团队空间才有意义，私有空间只有创建者一个人 -->
    <SpaceMemberPanel
      v-if="!spaceError && space && isTeam"
      :space-id="String(space.id)"
      :can-manage="isSpaceAdmin"
      :max-member-count="space.maxMemberCount"
      @changed="loadSpace"
    />

    <!-- 检索：分辨率档位、屏幕方向、色系都是图片像素的派生条件，对视频不成立，所以只在静态下显示 -->
    <PictureSearchForm v-if="!spaceError && mediaKind === 'static'" @search="handleSearch" />

    <!-- 空间内容 -->
    <a-card v-if="!spaceError" :bordered="false">
      <template #title>
        <a-space>
          <span>{{ contentTitle }}</span>
          <a-tag v-if="total" :bordered="false">
            共 {{ total }} {{ mediaKind === 'video' ? '条' : '张' }}
          </a-tag>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-segmented
            v-model:value="mediaKind"
            size="small"
            :options="mediaKindOptions"
            @change="handleMediaKindChange"
          />
          <a-radio-group v-model:value="pageSize" size="small" @change="handlePageSizeChange">
            <a-radio-button :value="10">10 / 页</a-radio-button>
            <a-radio-button :value="20">20 / 页</a-radio-button>
          </a-radio-group>
        </a-space>
      </template>

      <PictureList
        v-if="mediaKind === 'static'"
        :data-list="pictureList"
        :loading="loading"
        :error="loadError"
        show-op
        @reload="loadPictures"
      />

      <!-- 收藏段沿用「我的收藏」页的图墙观感；取消收藏即把图块移出列表 -->
      <PictureList
        v-else-if="mediaKind === 'collect'"
        variant="wall"
        show-collect
        :data-list="collectList"
        :loading="loading"
        :error="loadError"
        empty-text="还没有收藏任何壁纸"
        @reload="loadCollects"
        @collect-change="onCollectChange"
      >
        <template #empty>
          <a-button type="primary" @click="router.push('/')">去公共图库逛逛</a-button>
        </template>
      </PictureList>

      <VideoList
        v-else
        :data-list="videoList"
        :loading="loading"
        :error="loadError"
        show-op
        @reload="loadVideos"
      />

      <div v-if="total > pageSize" class="space-pagination">
        <a-pagination
          v-model:current="current"
          :total="total"
          :page-size="pageSize"
          :show-size-changer="false"
          show-less-items
          @change="loadContent"
        />
      </div>
    </a-card>

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
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  BarChartOutlined,
  CloudUploadOutlined,
  EditOutlined,
  FolderOpenOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons-vue'
import PictureList from '@/components/PictureList.vue'
import PictureSearchForm from '@/components/PictureSearchForm.vue'
import SpaceMemberPanel from '@/components/SpaceMemberPanel.vue'
import VideoList from '@/components/VideoList.vue'
import { listMyCollectPictureVOByPage, listPictureVOByPage } from '@/api/pictureController'
import { listVideoVOByPage } from '@/api/videoController'
import { editSpace, getSpaceVOById } from '@/api/spaceController'
import {
  SPACE_LEVEL_COLOR_MAP,
  SPACE_LEVEL_MAP,
  SPACE_ROLE_ENUM,
  SPACE_TYPE_ENUM,
  SPACE_TYPE_MAP,
} from '@/constant/space'
import { calcPercent, formatDate, formatSize } from '@/utils'
import { useLoginUserStore } from '@/stores/useLoginUserStore'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const spaceId = computed(() => String(route.params.id ?? ''))

const space = ref<API.SpaceVO>()
const isTeam = computed(() => space.value?.spaceType === SPACE_TYPE_ENUM.TEAM)
// 站点管理员在后端被视作该空间的管理员，所以 myRole 已经涵盖「空间管理员」与「站点管理员」两种情况
const isSpaceAdmin = computed(() => space.value?.myRole === SPACE_ROLE_ENUM.ADMIN)
const canUpload = computed(
  () => isSpaceAdmin.value || space.value?.myRole === SPACE_ROLE_ENUM.EDITOR,
)
const memberOverCap = computed(() => {
  const max = Number(space.value?.maxMemberCount ?? 0)
  return max > 0 && Number(space.value?.memberCount ?? 0) > max
})
const spaceError = ref(false)
/** 空间里同时放着静态图片与动态壁纸，用与首页一致的分段控件切换；私有空间额外有「我的收藏」段 */
const mediaKind = ref<'static' | 'video' | 'collect'>('static')
const pictureList = ref<API.PictureVO[]>([])
const videoList = ref<API.VideoVO[]>([])
const collectList = ref<API.PictureVO[]>([])
const loading = ref(false)
const loadError = ref(false)
const current = ref(1)
const pageSize = ref(10)
const total = ref(0)
const queryParams = ref<API.PictureQueryRequest>({})

/**
 * 收藏段只属于「自己的私有空间」：收藏是用户维度的数据，不是空间维度。
 * 管理员代管别人的私有空间时不显示这一段，避免把管理员自己的收藏误当成空间内容。
 */
const showCollectTab = computed(
  () =>
    space.value?.spaceType === SPACE_TYPE_ENUM.PRIVATE &&
    space.value?.userId === loginUserStore.loginUser?.id,
)

const mediaKindOptions = computed(() => {
  const options = [
    { label: '静态图片', value: 'static' },
    { label: '动态壁纸', value: 'video' },
  ]
  if (showCollectTab.value) {
    options.push({ label: '我的收藏', value: 'collect' })
  }
  return options
})

const contentTitle = computed(() => {
  if (mediaKind.value === 'video') return '空间动态壁纸'
  if (mediaKind.value === 'collect') return '我的收藏'
  return '空间图片'
})

const editModalOpen = ref(false)
const editSubmitting = ref(false)
const editForm = reactive<API.SpaceEditRequest>({ id: undefined, spaceName: '' })

async function loadSpace() {
  if (!spaceId.value) return
  spaceError.value = false
  try {
    const res = await getSpaceVOById(spaceId.value)
    if (res.code === 0 && res.data) {
      space.value = res.data
      // 带着 view=collect 进了非私有空间或别人的私有空间：收藏段不存在，回落到图片段并补拉
      if (!showCollectTab.value && mediaKind.value === 'collect') {
        mediaKind.value = 'static'
        loadContent()
      }
    } else {
      // 业务层失败（如 40400 空间不存在）
      space.value = undefined
      spaceError.value = true
    }
  } catch {
    // HTTP 层失败（如接口不存在返回 404），不要静默成空白页
    space.value = undefined
    spaceError.value = true
  }
}

async function loadPictures() {
  if (!spaceId.value) return
  loading.value = true
  loadError.value = false
  try {
    const res = await listPictureVOByPage({
      ...queryParams.value,
      spaceId: spaceId.value,
      current: current.value,
      pageSize: pageSize.value,
    })
    if (res.code === 0 && res.data) {
      pictureList.value = res.data.records ?? []
      total.value = Number(res.data.total ?? 0)
    } else {
      pictureList.value = []
      total.value = 0
      loadError.value = true
    }
  } catch {
    pictureList.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function loadVideos() {
  if (!spaceId.value) return
  loading.value = true
  loadError.value = false
  try {
    const res = await listVideoVOByPage({
      spaceId: spaceId.value,
      current: current.value,
      pageSize: pageSize.value,
    })
    if (res.code === 0 && res.data) {
      videoList.value = res.data.records ?? []
      total.value = Number(res.data.total ?? 0)
    } else {
      videoList.value = []
      total.value = 0
      loadError.value = true
    }
  } catch {
    videoList.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

/** 我的收藏列表：后端固定按收藏时间倒序，不支持检索条件 */
async function loadCollects() {
  loading.value = true
  loadError.value = false
  try {
    const res = await listMyCollectPictureVOByPage({
      current: current.value,
      pageSize: pageSize.value,
    })
    if (res.code === 0 && res.data) {
      collectList.value = res.data.records ?? []
      total.value = Number(res.data.total ?? 0)
    } else {
      collectList.value = []
      total.value = 0
      loadError.value = true
    }
  } catch {
    collectList.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

/** 只拉当前这一类：切到视频还去查图片是白花一次请求 */
function loadContent() {
  if (mediaKind.value === 'video') return loadVideos()
  if (mediaKind.value === 'collect') return loadCollects()
  return loadPictures()
}

/**
 * 在收藏段取消收藏即等于把这张图移出收藏夹，图块直接离场（不整页重拉，避免闪一下）。
 * 若当前页被清空但后面还有收藏，则回拉一页把空位补上。
 */
function onCollectChange(picture: API.PictureVO, collected: boolean) {
  if (collected) return
  const removedId = String(picture.id)
  collectList.value = collectList.value.filter((item) => String(item.id) !== removedId)
  total.value = Math.max(total.value - 1, 0)
  if (!collectList.value.length && total.value > 0) {
    loadCollects()
  }
}

function reloadAll() {
  loadSpace()
  loadContent()
}

function handleMediaKindChange() {
  current.value = 1
  total.value = 0
  loadContent()
}

function handleSearch(params: API.PictureQueryRequest) {
  queryParams.value = params
  current.value = 1
  loadPictures()
}

function handlePageSizeChange() {
  current.value = 1
  loadContent()
}

function openEditModal() {
  editForm.id = space.value?.id
  editForm.spaceName = space.value?.spaceName ?? ''
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
      loadSpace()
    }
  } finally {
    editSubmitting.value = false
  }
}

onMounted(() => {
  // 旧 /my_collect 链接经中转页 replace 时带着 view=collect，直达收藏段
  if (route.query.view === 'collect') {
    mediaKind.value = 'collect'
  }
  loadSpace()
  loadContent()
})
</script>

<style scoped>
.space-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.space-header__title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.space-header__title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.space-header__icon {
  color: var(--app-accent, #1677ff);
  font-size: 20px;
}

.space-header__meta {
  margin-top: 8px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 13px;
}

.space-quota__label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.65));
  font-size: 13px;
}

.space-quota__over {
  color: #ff4d4f;
  font-weight: 600;
}

.space-pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
