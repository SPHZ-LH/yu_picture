<template>
  <div class="auth-page">
    <h2 class="auth-page__title">欢迎回来</h2>
    <p class="auth-page__desc">登录后可上传图片、管理你的私有空间与团队空间</p>

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

      <a-form-item>
        <a-button type="primary" html-type="submit" size="large" block :loading="loading">
          登录
        </a-button>
      </a-form-item>
    </a-form>

    <div class="auth-page__footer">
      还没有账号？
      <RouterLink :to="{ path: '/user/register', query: route.query }">立即注册</RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { LockOutlined, UserOutlined } from '@ant-design/icons-vue'
import { loginUser } from '@/api/userController'
import { useLoginUserStore } from '@/stores/useLoginUserStore'

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()

const loading = ref(false)
const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

async function handleSubmit() {
  loading.value = true
  try {
    const res = await loginUser({
      userAccount: formState.userAccount,
      userPassword: formState.userPassword,
    })
    if (res.code === 0 && res.data) {
      loginUserStore.setLoginUser(res.data)
      message.success(`欢迎回来，${res.data.userName || res.data.userAccount}`)
      const redirect = (route.query.redirect as string) || '/'
      router.replace(redirect)
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
