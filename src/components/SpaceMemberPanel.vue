<template>
  <a-card :bordered="false">
    <template #title>
      <a-space>
        <span>空间成员</span>
        <a-tag :bordered="false" :color="overCap ? 'error' : undefined">
          {{ members.length }} / {{ maxMemberCount ?? 0 }} 人
        </a-tag>
        <a-tag v-if="overCap" color="warning" :bordered="false">已超上限</a-tag>
      </a-space>
    </template>
    <template #extra>
      <!-- 上限含创建者，达到上限就再也加不进人；超额时（管理员降过等级）同样挡住新增 -->
      <a-tooltip :title="inviteDisabledReason">
        <!-- disabled 的按钮不派发鼠标事件，套一层 span 才能让 tooltip 弹出，否则「为什么点不动」永远看不到 -->
        <span class="invite-btn-wrap">
          <a-button type="primary" size="small" :disabled="!canManage || !!inviteDisabledReason" @click="openInvite">
            <template #icon><UserAddOutlined /></template>
            邀请成员
          </a-button>
        </span>
      </a-tooltip>
    </template>

    <a-table
      :columns="columns"
      :data-source="members"
      :loading="loading"
      :pagination="false"
      row-key="id"
      size="middle"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'user'">
          <div class="member-user">
            <a-avatar :src="record.user?.userAvatar" :size="32">
              {{ (record.user?.userName ?? '?').charAt(0) }}
            </a-avatar>
            <div class="member-user__text">
              <span class="member-user__name">
                {{ record.user?.userName ?? '未知用户' }}
                <a-tag v-if="record.creator" color="purple" :bordered="false">创建者</a-tag>
              </span>
              <span class="member-user__account">{{ record.user?.userAccount }}</span>
            </div>
          </div>
        </template>

        <template v-else-if="column.key === 'spaceRole'">
          <!-- 创建者的角色不可改：降级会让团队空间失去唯一管理员，且没有别的途径恢复 -->
          <a-select
            v-if="canManage && !record.creator"
            :value="record.spaceRole"
            :options="SPACE_ROLE_OPTIONS"
            size="small"
            style="width: 110px"
            @change="(value) => handleRoleChange(record, String(value))"
          />
          <a-tag v-else :color="SPACE_ROLE_COLOR_MAP[record.spaceRole]" :bordered="false">
            {{ record.spaceRoleText ?? SPACE_ROLE_MAP[record.spaceRole] }}
          </a-tag>
        </template>

        <template v-else-if="column.key === 'createTime'">
          {{ formatDate(record.createTime) }}
        </template>

        <template v-else-if="column.key === 'action'">
          <a-tooltip :title="record.creator ? '创建者不可被移出' : ''">
            <!-- 同上：disabled 按钮不吃鼠标事件，靠 span 兜住 tooltip -->
            <span>
              <a-popconfirm
                title="确定把该成员移出空间？"
                ok-text="移出"
                cancel-text="取消"
                :disabled="!canManage || record.creator"
                @confirm="handleRemove(record)"
              >
                <a-button type="link" danger size="small" :disabled="!canManage || record.creator">移出</a-button>
              </a-popconfirm>
            </span>
          </a-tooltip>
        </template>
      </template>
    </a-table>

    <!-- 邀请弹窗 -->
    <a-modal
      v-model:open="inviteOpen"
      title="邀请成员"
      :footer="null"
      :confirm-loading="false"
      @cancel="closeInvite"
    >
      <a-input-search
        v-model:value="searchText"
        placeholder="输入账号或昵称搜索，至少 2 个字符"
        enter-button="搜索"
        :loading="searching"
        @search="handleSearch"
      />
      <p class="invite-hint">只列出还不是本空间成员的用户，最多 10 条。</p>

      <a-list :data-source="searchResults" :loading="searching" size="small">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta>
              <template #avatar>
                <a-avatar :src="item.userAvatar" :size="32">{{ (item.userName ?? '?').charAt(0) }}</a-avatar>
              </template>
              <template #title>{{ item.userName }}</template>
              <template #description>{{ item.userAccount }}</template>
            </a-list-item-meta>
            <template #actions>
              <a-select v-model:value="inviteRole[item.id ?? '']" :options="SPACE_ROLE_OPTIONS" size="small" style="width: 110px" />
              <a-button type="link" size="small" :loading="inviting" @click="handleInvite(item)">邀请</a-button>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { UserAddOutlined } from '@ant-design/icons-vue'
import {
  addSpaceMember,
  deleteSpaceMember,
  editSpaceMemberRole,
  listSpaceMembers,
  searchInvitableUsers,
} from '@/api/spaceController'
import { SPACE_ROLE_COLOR_MAP, SPACE_ROLE_ENUM, SPACE_ROLE_MAP, SPACE_ROLE_OPTIONS } from '@/constant/space'
import { formatDate } from '@/utils'

const props = defineProps<{
  spaceId: string
  /** 当前用户是否为该空间管理员，决定能否增删改成员 */
  canManage: boolean
  maxMemberCount?: string | number
}>()

const emit = defineEmits<{
  /** 成员数变了，让父组件重新拉空间信息以刷新「已用 / 上限」 */
  (e: 'changed'): void
}>()

const columns = [
  { title: '成员', key: 'user' },
  { title: '角色', key: 'spaceRole', width: 140 },
  { title: '加入时间', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 90 },
]

const members = ref<API.SpaceUserVO[]>([])
const loading = ref(false)

const inviteOpen = ref(false)
const searchText = ref('')
const searchResults = ref<API.UserVO[]>([])
const searching = ref(false)
const inviting = ref(false)
/** 每个候选用户各自记住选中的角色，切人不会串 */
const inviteRole = reactive<Record<string, string>>({})

const maxMemberCount = computed(() => Number(props.maxMemberCount ?? 0))
const overCap = computed(() => maxMemberCount.value > 0 && members.value.length > maxMemberCount.value)
const inviteDisabledReason = computed(() => {
  if (maxMemberCount.value > 0 && members.value.length >= maxMemberCount.value) {
    return `成员数已达上限（${maxMemberCount.value} 人，含创建者）`
  }
  return ''
})

async function loadMembers() {
  if (!props.spaceId) return
  loading.value = true
  try {
    const res = await listSpaceMembers(props.spaceId)
    members.value = res.code === 0 && res.data ? res.data : []
  } catch {
    members.value = []
  } finally {
    loading.value = false
  }
}

function openInvite() {
  inviteOpen.value = true
  searchText.value = ''
  searchResults.value = []
}

function closeInvite() {
  inviteOpen.value = false
}

async function handleSearch() {
  if (searchText.value.trim().length < 2) {
    message.warning('至少输入 2 个字符')
    return
  }
  searching.value = true
  try {
    const res = await searchInvitableUsers(props.spaceId, searchText.value.trim())
    searchResults.value = res.code === 0 && res.data ? res.data : []
    // 默认编辑者：邀请进来多半是为了协作上传，只读反而要再改一次。
    // 同时把默认值写进控件——否则下拉框显示为空、提交却按 editor 走，
    // 邀请人根本看不出自己给出去的是什么权限
    searchResults.value.forEach((user) => {
      if (user.id && !inviteRole[user.id]) {
        inviteRole[user.id] = SPACE_ROLE_ENUM.EDITOR
      }
    })
    if (res.code === 0 && searchResults.value.length === 0) {
      message.info('没有匹配到可邀请的用户')
    }
  } finally {
    searching.value = false
  }
}

async function handleInvite(user: API.UserVO) {
  if (!user.id) return
  inviting.value = true
  try {
    const res = await addSpaceMember({
      spaceId: props.spaceId,
      userId: user.id,
      spaceRole: inviteRole[user.id] ?? SPACE_ROLE_ENUM.EDITOR,
    })
    if (res.code === 0) {
      message.success(`已邀请 ${user.userName ?? ''}`)
      searchResults.value = searchResults.value.filter((item) => item.id !== user.id)
      await loadMembers()
      emit('changed')
    }
  } finally {
    inviting.value = false
  }
}

async function handleRoleChange(record: API.SpaceUserVO, value: string) {
  if (!record.userId) return
  const res = await editSpaceMemberRole({
    spaceId: props.spaceId,
    userId: record.userId,
    spaceRole: value,
  })
  if (res.code === 0) {
    message.success('角色已更新')
    await loadMembers()
  } else {
    // 失败要把选择弹回去，否则界面上显示的角色和库里不一致
    await loadMembers()
  }
}

async function handleRemove(record: API.SpaceUserVO) {
  if (!record.userId) return
  const res = await deleteSpaceMember({ spaceId: props.spaceId, userId: record.userId })
  if (res.code === 0) {
    message.success('已移出空间')
    await loadMembers()
    emit('changed')
  }
}

onMounted(loadMembers)
</script>

<style scoped>
.member-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.member-user__text {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.member-user__name {
  font-weight: 500;
}

.member-user__account {
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

.invite-hint {
  margin: 8px 0 12px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

.invite-btn-wrap {
  display: inline-block;
}
</style>
