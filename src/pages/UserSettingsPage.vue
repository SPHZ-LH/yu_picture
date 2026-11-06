<template>
  <div class="page-container settings-page">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">账号设置</h2>
        <p class="page-header__desc">管理你的头像、昵称与登录密码</p>
      </div>
    </div>

    <div class="settings-stack">
      <a-card class="set-card" :bordered="false">
        <div class="identity">
          <button
            class="profile-avatar"
            type="button"
            :disabled="uploading"
            title="换头像"
            @click="fileInput?.click()"
          >
            <img v-if="form.userAvatar" :src="form.userAvatar" alt="当前头像" />
            <UserOutlined v-else class="profile-avatar__placeholder" />
            <span class="profile-avatar__mask">
              <LoadingOutlined v-if="uploading" spin />
              <template v-else>换头像</template>
            </span>
          </button>
          <input
            ref="fileInput"
            type="file"
            accept=".jpg,.jpeg,.png,.webp"
            hidden
            @change="onFileChange"
          />
          <div class="identity-main">
            <div class="identity-name-row">
              <span class="identity-name">{{ form.userName || loginUser.userName }}</span>
              <a-tag v-if="loginUser.userRole === 'admin'" color="orange" :bordered="false">
                管理员
              </a-tag>
            </div>
            <p class="identity-meta">@{{ loginUser.userAccount }}</p>
            <p v-if="avatarPicked" class="identity-hint">头像已选择，保存后生效</p>
          </div>
          <div class="identity-fact">
            <span class="identity-fact__label">加入于</span>
            <span class="identity-fact__value">{{ (loginUser.createTime || '').slice(0, 10) }}</span>
          </div>
        </div>
      </a-card>

      <a-card class="set-card" :bordered="false">
        <div class="set-head">
          <h3>资料</h3>
          <p>昵称和简介会展示在你的图片与空间旁</p>
        </div>
        <a-form layout="vertical" class="set-form">
          <a-form-item label="昵称">
            <a-input
              v-model:value="form.userName"
              :maxlength="30"
              placeholder="1-30 个字"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="简介">
            <a-textarea
              v-model:value="form.userProfile"
              :maxlength="200"
              :rows="3"
              show-count
              placeholder="介绍一下自己"
            />
          </a-form-item>
          <a-button type="primary" :loading="savingProfile" @click="saveProfile">
            保存修改
          </a-button>
        </a-form>
      </a-card>

      <a-card class="set-card" :bordered="false">
        <div class="set-head">
          <h3>登录密码</h3>
          <p>修改后当前设备保持登录，其它设备下次请求时失效</p>
        </div>
        <a-form layout="vertical" class="set-form">
          <a-form-item label="旧密码">
            <a-input-password
              v-model:value="pwdForm.oldPassword"
              placeholder="当前在用的密码"
            />
          </a-form-item>
          <a-form-item label="新密码">
            <a-input-password v-model:value="pwdForm.newPassword" placeholder="至少 8 位" />
          </a-form-item>
          <a-form-item label="确认新密码">
            <a-input-password
              v-model:value="pwdForm.confirmPassword"
              placeholder="再输入一次新密码"
            />
          </a-form-item>
          <a-button type="primary" :loading="savingPwd" @click="savePassword">
            更新密码
          </a-button>
        </a-form>
      </a-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { LoadingOutlined, UserOutlined } from '@ant-design/icons-vue'
import { updateMyPassword, updateMyUser } from '@/api/userController'
import { uploadFile } from '@/api/fileController'
import { useLoginUserStore } from '@/stores/useLoginUserStore'

const ALLOW_SUFFIX = ['jpg', 'jpeg', 'png', 'webp']
const MAX_SIZE = 2 * 1024 * 1024

const loginUserStore = useLoginUserStore()
const loginUser = computed(() => loginUserStore.loginUser)

const form = reactive({
  userName: loginUser.value.userName ?? '',
  userProfile: loginUser.value.userProfile ?? '',
  userAvatar: loginUser.value.userAvatar ?? '',
})
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const fileInput = ref<HTMLInputElement>()
const uploading = ref(false)
const avatarPicked = ref(false)
const savingProfile = ref(false)
const savingPwd = ref(false)

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 清空以便重复选择同一文件
  input.value = ''
  if (!file) return
  const suffix = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!ALLOW_SUFFIX.includes(suffix)) {
    message.error('只支持 jpg、png、webp 图片')
    return
  }
  if (file.size > MAX_SIZE) {
    message.error('图片不能超过 2M')
    return
  }
  uploading.value = true
  try {
    const res = await uploadFile(file)
    if (res.code === 0 && res.data) {
      form.userAvatar = res.data
      avatarPicked.value = true
      message.success('头像已上传')
    }
  } finally {
    uploading.value = false
  }
}

async function saveProfile() {
  const userName = form.userName.trim()
  if (!userName) {
    message.error('昵称不能为空')
    return
  }
  savingProfile.value = true
  try {
    const res = await updateMyUser({
      userName,
      userProfile: form.userProfile,
      userAvatar: form.userAvatar || undefined,
    })
    if (res.code === 0) {
      message.success('已保存')
      avatarPicked.value = false
      // 后端已回写 Session，重新拉取让头部昵称同步更新
      await loginUserStore.fetchLoginUser()
      form.userName = loginUser.value.userName ?? ''
      form.userProfile = loginUser.value.userProfile ?? ''
      form.userAvatar = loginUser.value.userAvatar ?? ''
    }
  } finally {
    savingProfile.value = false
  }
}

async function savePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    message.error('密码不能为空')
    return
  }
  if (pwdForm.newPassword.length < 8) {
    message.error('新密码至少 8 位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    message.error('两次输入的新密码不一致')
    return
  }
  savingPwd.value = true
  try {
    const res = await updateMyPassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    if (res.code === 0) {
      message.success('密码已更新')
      pwdForm.oldPassword = ''
      pwdForm.newPassword = ''
      pwdForm.confirmPassword = ''
    }
  } finally {
    savingPwd.value = false
  }
}
</script>

<style scoped>
.settings-page {
  max-width: 560px;
  margin: 0 auto;
}

.settings-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.set-card :deep(.ant-card-body) {
  padding: 24px;
}

.identity {
  display: flex;
  align-items: center;
  gap: 16px;
}

.profile-avatar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 88px;
  height: 88px;
  padding: 0;
  border: 1px solid var(--app-border-strong);
  border-radius: 12px;
  background: var(--app-panel-hover);
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.18s ease;
}

.profile-avatar:hover {
  box-shadow: 0 0 0 2px var(--app-accent);
}

.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-avatar__placeholder {
  font-size: 30px;
  color: var(--app-text-secondary);
}

.profile-avatar__mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
  opacity: 0;
  transition: opacity 0.18s ease;
}

.profile-avatar:hover .profile-avatar__mask,
.profile-avatar:focus-visible .profile-avatar__mask,
.profile-avatar:disabled .profile-avatar__mask {
  opacity: 1;
}

.profile-avatar:focus-visible {
  outline: 2px solid var(--app-accent);
  outline-offset: 3px;
}

.identity-main {
  min-width: 0;
}

.identity-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.identity-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--app-text);
  overflow-wrap: anywhere;
}

.identity-meta {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--app-text-secondary);
  overflow-wrap: anywhere;
}

.identity-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--app-accent);
}

.identity-fact {
  margin-left: auto;
  flex: none;
  text-align: right;
}

.identity-fact__label {
  display: block;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.identity-fact__value {
  display: block;
  margin-top: 2px;
  font-size: 14px;
  color: var(--app-text);
}

.set-head {
  margin-bottom: 16px;
}

.set-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text);
}

.set-head p {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.set-form {
  max-width: none;
}

.set-form :deep(.ant-form-item) {
  margin-bottom: 16px;
}

/* 控件白底深描边，避免灰控件糊在灰底上 */
.settings-page :deep(.ant-input),
.settings-page :deep(.ant-input-affix-wrapper),
.settings-page :deep(.ant-input-textarea .ant-input) {
  background: var(--app-panel);
  border-color: var(--app-border-strong);
}
</style>
