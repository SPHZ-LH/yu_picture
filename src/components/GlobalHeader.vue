<template>
  <header class="yutu-header">
    <div class="yutu-wrap yutu-header__inner">
      <RouterLink to="/" class="yutu-header__brand" aria-label="屿图首页">
        <span class="yutu-header__brand-zh">屿图</span>
        <span class="yutu-header__brand-en">YUTU</span>
      </RouterLink>

      <nav class="yutu-header__links" aria-label="主导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="yutu-header__link"
          :class="{ 'yutu-header__link--on': isActive(item.path) }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="yutu-header__actions">
        <button class="yutu-header__icon" type="button" aria-label="搜索" @click="goSearch">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
            <circle cx="11" cy="11" r="7" />
            <path d="m20 20-3.6-3.6" />
          </svg>
        </button>

        <template v-if="!isLoggedIn">
          <a class="yutu-header__ghost" href="/user/login" @click.prevent="router.push('/user/login')">登录</a>
        </template>

        <a class="yutu-header__upload" href="/add_picture" @click.prevent="router.push('/add_picture')">
          上传作品
        </a>

        <a-dropdown v-if="isLoggedIn" placement="bottomRight">
          <a class="yutu-header__avatar" href="javascript:;" :aria-label="loginUser.userName ?? '个人主页'">
            {{ avatarChar }}
          </a>
          <template #overlay>
            <a-menu @click="onUserMenuClick">
              <a-menu-item key="profile">
                <UserOutlined />
                我的主页
              </a-menu-item>
              <a-menu-item key="my_private_space">
                <FolderOutlined />
                个人空间
              </a-menu-item>
              <a-menu-item key="my_space">
                <TeamOutlined />
                团队空间
              </a-menu-item>
              <a-menu-item key="user_settings">
                <SettingOutlined />
                账号设置
              </a-menu-item>
              <template v-if="isAdmin">
                <a-menu-divider />
                <a-menu-item key="admin">
                  <CrownOutlined />
                  管理后台
                </a-menu-item>
              </template>
              <a-menu-divider />
              <a-menu-item key="logout">
                <LogoutOutlined />
                退出登录
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import {
  CrownOutlined,
  FolderOutlined,
  LogoutOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { useCollectStore } from '@/stores/useCollectStore'
import { logoutUser } from '@/api/userController'

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()
const collectStore = useCollectStore()
const { loginUser } = storeToRefs(loginUserStore)

const isLoggedIn = computed(() => Boolean(loginUser.value?.id))
const isAdmin = computed(() => loginUser.value?.userRole === 'admin')

/** 头像字符：取昵称首字（与设计稿的头像圆一致） */
const avatarChar = computed(() => (loginUser.value?.userName ?? '客').slice(0, 1))

const navItems = [
  { path: '/', label: '发现' },
  { path: '/topics', label: '专题' },
  { path: '/ranking', label: '榜单' },
  { path: '/creators', label: '创作者' },
  { path: '/my_private_space', label: '个人空间' },
  { path: '/my_space', label: '团队空间' },
]

function isActive(path: string): boolean {
  if (path === '/') return route.path === '/'
  return route.path === path || route.path.startsWith(`${path}/`)
}

function goSearch() {
  router.push('/search_picture')
}

function onUserMenuClick({ key }: { key: string | number }) {
  if (key === 'profile') {
    if (loginUser.value?.id) router.push(`/user_profile/${loginUser.value.id}`)
    return
  }
  if (key === 'my_private_space') {
    router.push('/my_private_space')
    return
  }
  if (key === 'my_space') {
    router.push('/my_space')
    return
  }
  if (key === 'user_settings') {
    router.push('/user_settings')
    return
  }
  if (key === 'admin') {
    router.push('/admin/userManage')
    return
  }
  if (key === 'logout') {
    Modal.confirm({
      title: '确认退出登录？',
      content: '退出后需要重新登录才能访问个人空间与上传作品。',
      okText: '退出',
      cancelText: '取消',
      onOk: async () => {
        const res = await logoutUser()
        if (res.code === 0) {
          loginUserStore.clearLoginUser()
          // 收藏态属于登录用户，换人登录前必须清空，否则星标会串到下一个人
          collectStore.reset()
          message.success('已退出登录')
          router.push('/user/login')
        } else {
          message.error(res.message || '退出登录失败')
        }
      },
    })
  }
}
</script>

<style scoped>
.yutu-header {
  /* header 在 .yutu 页面包装器之外，变量自带一份，不受旧主题影响 */
  --yutu-bg: #fafaf9;
  --yutu-surface: #ffffff;
  --yutu-ink: #17191c;
  --yutu-ink-2: #75797f;
  --yutu-ink-3: #a8abaf;
  --yutu-line: #e9e9e6;
  --yutu-wash: #f1f1ef;
  --yutu-radius: 12px;

  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(250, 250, 249, 0.86);
  -webkit-backdrop-filter: blur(14px);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--yutu-line, #e9e9e6);
}

.yutu-header a {
  color: inherit;
}

.yutu-header__inner {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 40px;
}

.yutu-header__brand {
  display: flex;
  align-items: baseline;
  gap: 9px;
  flex-shrink: 0;
}

.yutu-header__brand-zh {
  font-family: 'Noto Serif SC', serif;
  font-weight: 600;
  font-size: 21px;
  letter-spacing: 0.04em;
  color: var(--yutu-ink, #17191c);
}

.yutu-header__brand-en {
  font-size: 10.5px;
  letter-spacing: 0.22em;
  color: var(--yutu-ink-3, #a8abaf);
  font-weight: 500;
}

.yutu-header__links {
  display: flex;
  gap: 30px;
  font-size: 14px;
  color: var(--yutu-ink-2, #75797f);
  min-width: 0;
}

.yutu-header__link {
  transition: color 0.2s;
  white-space: nowrap;
}

.yutu-header__link:hover {
  color: var(--yutu-ink, #17191c);
}

.yutu-header__link--on {
  color: var(--yutu-ink, #17191c);
  font-weight: 500;
}

.yutu-header__actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.yutu-header__icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: var(--yutu-ink-2, #75797f);
  border: none;
  background: none;
  cursor: pointer;
  transition:
    background 0.2s,
    color 0.2s;
}

.yutu-header__icon:hover {
  background: var(--yutu-wash, #f1f1ef);
  color: var(--yutu-ink, #17191c);
}

.yutu-header__ghost {
  font-size: 13.5px;
  color: var(--yutu-ink-2, #75797f);
  transition: color 0.2s;
}

.yutu-header__ghost:hover {
  color: var(--yutu-ink, #17191c);
}

.yutu-header__upload {
  background: var(--yutu-ink, #17191c);
  color: #fff;
  font-size: 13.5px;
  font-weight: 500;
  padding: 9px 20px;
  border-radius: 999px;
  transition: opacity 0.2s;
  white-space: nowrap;
}

.yutu-header__upload:hover {
  opacity: 0.85;
}

.yutu-header__avatar {
  width: 31px;
  height: 31px;
  border-radius: 50%;
  background: var(--yutu-ink, #17191c);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 500;
  user-select: none;
}

@media (max-width: 900px) {
  .yutu-header__links {
    display: none;
  }
}
</style>
