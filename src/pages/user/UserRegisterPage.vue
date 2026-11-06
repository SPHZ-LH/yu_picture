<template>
  <div class="auth-page">
    <h2 class="auth-page__title">创建账号</h2>
    <p class="auth-page__desc">注册即自动获得一个普通版私有空间，之后还可创建团队空间邀请成员协作</p>

    <a-form :model="formState" layout="vertical" @finish="handleSubmit">
      <a-form-item
        label="账号"
        name="userAccount"
        :rules="[
          { required: true, message: '请输入账号' },
          { min: 4, message: '账号长度不能少于 4 位' },
        ]"
      >
        <a-input
          v-model:value="formState.userAccount"
          size="large"
          placeholder="请输入账号"
          allow-clear
        >
          <template #prefix><UserOutlined /></template>
        </a-input>
      </a-form-item>

      <a-form-item
        label="密码"
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码长度不能少于 8 位' },
        ]"
      >
        <a-input-password
          v-model:value="formState.userPassword"
          size="large"
          placeholder="请输入密码"
          allow-clear
        >
          <template #prefix><LockOutlined /></template>
        </a-input-password>
      </a-form-item>

      <a-form-item
        label="确认密码"
        name="checkPassword"
        :rules="[
          { required: true, message: '请再次输入密码' },
          { validator: validateCheckPassword, trigger: 'change' },
        ]"
      >
        <a-input-password
          v-model:value="formState.checkPassword"
          size="large"
          placeholder="请再次输入密码"
          allow-clear
        >
          <template #prefix><SafetyOutlined /></template>
        </a-input-password>
      </a-form-item>

      <a-form-item>
        <a-button type="primary" html-type="submit" size="large" block :loading="loading">
          注册
        </a-button>
      </a-form-item>
    </a-form>

    <div class="auth-page__footer">
      已有账号？
      <RouterLink :to="{ path: '/user/login', query: route.query }">去登录</RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { LockOutlined, SafetyOutlined, UserOutlined } from '@ant-design/icons-vue'
import { registerUser } from '@/api/userController'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

async function validateCheckPassword(_rule: unknown, value: string) {
  if (!value) {
    return Promise.reject('请再次输入密码')
  }
  if (value !== formState.userPassword) {
    return Promise.reject('两次输入的密码不一致')
  }
  return Promise.resolve()
}

async function handleSubmit() {
  loading.value = true
  try {
    const res = await registerUser({
      userAccount: formState.userAccount,
      userPassword: formState.userPassword,
      checkPassword: formState.checkPassword,
    })
    if (res.code === 0) {
      message.success('注册成功，已为你创建私有空间，请登录')
      router.push({ path: '/user/login', query: route.query })
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page__title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--app-text, rgba(0, 0, 0, 0.88));
}

.auth-page__desc {
  margin: 6px 0 22px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 13px;
}

.auth-page__footer {
  margin-top: 8px;
  text-align: center;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 13px;
}
</style>
