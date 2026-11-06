<template>
  <div class="page-container">
    <AdminTabs />

    <!-- 搜索 -->
    <a-card :bordered="false">
      <a-form layout="inline" :model="queryParams" @finish="handleSearch">
        <a-form-item label="账号">
          <a-input
            v-model:value="queryParams.userAccount"
            placeholder="按账号搜索"
            allow-clear
            style="width: 180px"
          />
        </a-form-item>
        <a-form-item label="昵称">
          <a-input
            v-model:value="queryParams.userName"
            placeholder="按昵称搜索"
            allow-clear
            style="width: 180px"
          />
        </a-form-item>
        <a-form-item label="角色">
          <a-select
            v-model:value="queryParams.userRole"
            placeholder="全部角色"
            allow-clear
            style="width: 140px"
            :options="roleOptions"
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
          <span>用户列表</span>
          <a-tag v-if="total" :bordered="false">共 {{ total }} 人</a-tag>
        </a-space>
      </template>
      <template #extra>
        <a-button type="primary" @click="openAddModal">
          <template #icon><PlusOutlined /></template>
          新增用户
        </a-button>
      </template>

      <a-table
        :columns="columns"
        :data-source="userList"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        size="middle"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'user'">
            <a-space>
              <a-avatar :src="record.userAvatar">
                <template #icon><UserOutlined /></template>
              </a-avatar>
              <div class="user-cell">
                <span class="user-cell__name">{{ record.userName || '未设置昵称' }}</span>
                <span class="user-cell__account">{{ record.userAccount }}</span>
              </div>
            </a-space>
          </template>

          <template v-else-if="column.key === 'userRole'">
            <a-tag :color="record.userRole === 'admin' ? 'gold' : 'blue'" :bordered="false">
              {{ record.userRole === 'admin' ? '管理员' : '普通用户' }}
            </a-tag>
          </template>

          <template v-else-if="column.key === 'userProfile'">
            <a-typography-paragraph
              :content="record.userProfile || '-'"
              :ellipsis="{ rows: 1, tooltip: record.userProfile }"
              style="margin: 0; max-width: 220px"
            />
          </template>

          <template v-else-if="column.key === 'createTime'">
            {{ formatDate(record.createTime) }}
          </template>

          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="openEditModal(record)">编辑</a-button>
              <a-button type="link" size="small" danger @click="doDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 新增 / 编辑弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      :title="isEdit ? '编辑用户' : '新增用户'"
      :confirm-loading="submitting"
      width="520px"
      @ok="handleSubmit"
    >
      <a-form ref="formRef" :model="formState" layout="vertical">
        <a-form-item label="账号" name="userAccount" :rules="accountRules">
          <a-input
            v-model:value="formState.userAccount"
            placeholder="请输入账号（不少于 4 位）"
            :disabled="isEdit"
            allow-clear
          />
        </a-form-item>

        <a-form-item v-if="!isEdit" label="初始密码">
          <a-input :value="'12345678'" disabled />
          <div class="form-tip">新增用户默认密码为 12345678，可在编辑时修改</div>
        </a-form-item>

        <a-form-item
          v-if="isEdit"
          label="密码"
          name="userPassword"
          :rules="[{ min: 8, message: '密码长度不能少于 8 位' }]"
        >
          <a-input-password
            v-model:value="formState.userPassword"
            placeholder="留空表示不修改密码"
            allow-clear
          />
        </a-form-item>

        <a-form-item label="昵称" name="userName">
          <a-input v-model:value="formState.userName" placeholder="请输入昵称" allow-clear />
        </a-form-item>

        <a-form-item label="头像地址" name="userAvatar">
          <a-input v-model:value="formState.userAvatar" placeholder="请输入头像 URL" allow-clear />
        </a-form-item>

        <a-form-item label="简介" name="userProfile">
          <a-textarea
            v-model:value="formState.userProfile"
            placeholder="请输入个人简介"
            :rows="3"
            show-count
            :maxlength="200"
          />
        </a-form-item>

        <a-form-item label="角色" name="userRole">
          <a-radio-group v-model:value="formState.userRole">
            <a-radio value="user">普通用户</a-radio>
            <a-radio value="admin">管理员</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined, UserOutlined } from '@ant-design/icons-vue'
import AdminTabs from '@/components/AdminTabs.vue'
import { addUser, deleteUser, listUserVOByPage, updateUser } from '@/api/userController'
import { formatDate } from '@/utils'

const columns = [
  { title: '用户', key: 'user', width: 240 },
  { title: '角色', key: 'userRole', width: 110 },
  { title: '简介', key: 'userProfile' },
  { title: '注册时间', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 140, fixed: 'right' as const },
]

const roleOptions = [
  { label: '普通用户', value: 'user' },
  { label: '管理员', value: 'admin' },
]

const userList = ref<API.UserVO[]>([])
const loading = ref(false)
const total = ref(0)
const current = ref(1)
const pageSize = ref(10)

const queryParams = reactive<API.UserQueryRequest>({
  userAccount: '',
  userName: '',
  userRole: undefined,
})

const modalOpen = ref(false)
const submitting = ref(false)
const isEdit = ref(false)
const formRef = ref()

const formState = reactive<API.UserUpdateRequest & { userAccount?: string }>({
  id: undefined,
  userAccount: '',
  userPassword: '',
  userName: '',
  userAvatar: '',
  userProfile: '',
  userRole: 'user',
})

const accountRules = [
  { required: true, message: '请输入账号' },
  { min: 4, message: '账号长度不能少于 4 位' },
]

const pagination = computed(() => ({
  current: current.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`,
}))

async function loadUsers() {
  loading.value = true
  try {
    const res = await listUserVOByPage({
      userAccount: queryParams.userAccount || undefined,
      userName: queryParams.userName || undefined,
      userRole: queryParams.userRole || undefined,
      current: current.value,
      pageSize: pageSize.value,
    })
    if (res.code === 0 && res.data) {
      userList.value = res.data.records ?? []
      total.value = Number(res.data.total ?? 0)
    } else {
      userList.value = []
      total.value = 0
    }
  } catch {
    userList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  current.value = 1
  loadUsers()
}

function handleReset() {
  queryParams.userAccount = ''
  queryParams.userName = ''
  queryParams.userRole = undefined
  handleSearch()
}

function handleTableChange(pag: { current?: number; pageSize?: number }) {
  current.value = pag.current ?? 1
  pageSize.value = pag.pageSize ?? 10
  loadUsers()
}

function resetForm() {
  formState.id = undefined
  formState.userAccount = ''
  formState.userPassword = ''
  formState.userName = ''
  formState.userAvatar = ''
  formState.userProfile = ''
  formState.userRole = 'user'
}

function openAddModal() {
  isEdit.value = false
  resetForm()
  modalOpen.value = true
}

function openEditModal(record: API.UserVO) {
  isEdit.value = true
  resetForm()
  formState.id = record.id
  formState.userAccount = record.userAccount
  formState.userName = record.userName
  formState.userAvatar = record.userAvatar
  formState.userProfile = record.userProfile
  formState.userRole = record.userRole ?? 'user'
  modalOpen.value = true
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    const res = isEdit.value
      ? await updateUser({
          id: formState.id,
          userPassword: formState.userPassword || undefined,
          userName: formState.userName,
          userAvatar: formState.userAvatar,
          userProfile: formState.userProfile,
          userRole: formState.userRole,
        })
      : await addUser({
          userAccount: formState.userAccount,
          userName: formState.userName,
          userAvatar: formState.userAvatar,
          userProfile: formState.userProfile,
          userRole: formState.userRole,
        })

    if (res.code === 0) {
      message.success(isEdit.value ? '修改成功' : '新增成功')
      modalOpen.value = false
      loadUsers()
    }
  } finally {
    submitting.value = false
  }
}

function doDelete(record: API.UserVO) {
  Modal.confirm({
    title: '确认删除该用户？',
    content: `账号「${record.userAccount}」将被逻辑删除。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      const res = await deleteUser({ id: record.id })
      if (res.code === 0) {
        message.success('删除成功')
        loadUsers()
      }
    },
  })
}

onMounted(loadUsers)
</script>

<style scoped>
.user-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.user-cell__name {
  font-weight: 500;
}

.user-cell__account {
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

.form-tip {
  margin-top: 4px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}
</style>
