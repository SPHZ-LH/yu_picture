<template>
  <div class="profile-page">
    <a-spin :spinning="loadingUser">
      <section v-if="user" class="profile-plaque">
        <a-avatar :size="64" :src="user.userAvatar" class="profile-plaque__avatar">
          <template #icon><UserOutlined /></template>
        </a-avatar>

        <div class="profile-plaque__main">
          <div class="profile-plaque__name-row">
            <h1 class="profile-plaque__name">{{ user.userName || user.userAccount }}</h1>
            <a-tag v-if="user.userRole === 'admin'" color="orange" :bordered="false">管理员</a-tag>
          </div>
          <p class="profile-plaque__account">@{{ user.userAccount }}</p>
          <p v-if="user.userProfile" class="profile-plaque__bio">{{ user.userProfile }}</p>

          <div class="profile-plaque__facts">
            <span class="profile-fact">
              <span class="profile-fact__label">加入时间</span>
              <span class="profile-fact__value">{{ (user.createTime || '').slice(0, 10) || '-' }}</span>
            </span>
            <span class="profile-fact">
              <span class="profile-fact__label">公开作品</span>
              <span class="profile-fact__value">{{ total }} 张</span>
            </span>
          </div>
        </div>

        <a-button v-if="isSelf" class="profile-plaque__action" @click="router.push('/user_settings')">
          <template #icon><SettingOutlined /></template>
          账号设置
        </a-button>
      </section>

      <a-empty v-else-if="!loadingUser" description="用户不存在或已注销" class="profile-page__missing">
        <a-button type="primary" @click="router.push('/')">返回公共图库</a-button>
      </a-empty>
    </a-spin>

    <template v-if="user">
      <!-- 只列公共图库里的已过审图片：私有空间与待审核内容不对外 -->
      <section class="profile-works">
        <div class="profile-works__head">
          <h2 class="profile-works__title">{{ user.userName || user.userAccount }} 的公开作品</h2>
          <span v-if="total" class="profile-works__count">{{ total }} 张</span>
        </div>

        <PictureList
          variant="wall"
          show-collect
          :data-list="pictureList"
          :loading="loadingPictures"
          :error="loadError"
          :empty-text="isSelf ? '你还没有公开作品' : `${user.userName || user.userAccount} 还没有公开作品`"
          @reload="loadPictures"
        >
          <template #empty>
            <a-button v-if="isSelf" type="primary" @click="router.push('/add_picture')">
              创建图片
            </a-button>
            <a-button v-else @click="router.push('/')">去公共图库逛逛</a-button>
          </template>
        </PictureList>
      </section>

      <div v-if="total > pageSize" class="profile-page__pagination">
        <a-pagination
          v-model:current="current"
          :total="total"
          :page-size="pageSize"
          :show-size-changer="false"
          show-less-items
          show-quick-jumper
          @change="loadPictures"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { SettingOutlined, UserOutlined } from '@ant-design/icons-vue'
import PictureList from '@/components/PictureList.vue'
import { getUserVOById } from '@/api/userController'
import { listPictureVOByPage } from '@/api/pictureController'
import { useLoginUserStore } from '@/stores/useLoginUserStore'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const userId = computed(() => String(route.params.id ?? ''))

const user = ref<API.UserVO>()
const loadingUser = ref(false)

const pictureList = ref<API.PictureVO[]>([])
const loadingPictures = ref(false)
const loadError = ref(false)
const current = ref(1)
// 一页 4 列 × 3 行；后端 pageSize 上限 20
const pageSize = ref(12)
const total = ref(0)

const isSelf = computed(
  () => Boolean(userId.value) && String(loginUserStore.loginUser?.id ?? '') === userId.value,
)

async function loadUser() {
  if (!userId.value) return
  loadingUser.value = true
  try {
    const res = await getUserVOById(userId.value)
    user.value = res.code === 0 && res.data ? res.data : undefined
  } catch {
    user.value = undefined
  } finally {
    loadingUser.value = false
  }
}

/**
 * 该作者的公开作品：不传 spaceId，后端固定按「公共图库 + 已过审」过滤，
 * 因此这里天然只会拿到他上传到公共图库的那部分作品。
 */
async function loadPictures() {
  if (!userId.value) return
  loadingPictures.value = true
  loadError.value = false
  try {
    const res = await listPictureVOByPage({
      userId: userId.value,
      current: current.value,
      pageSize: pageSize.value,
      sortField: 'createTime',
      sortOrder: 'descend',
    })
    pictureList.value = res.code === 0 && res.data ? res.data.records ?? [] : []
    total.value = res.code === 0 && res.data ? Number(res.data.total ?? 0) : 0
    loadError.value = res.code !== 0
  } catch {
    pictureList.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loadingPictures.value = false
  }
}

/** 站内从一个主页跳到另一个主页时组件会复用，需整体重置 */
function reloadAll() {
  current.value = 1
  user.value = undefined
  loadUser()
  loadPictures()
}

onMounted(reloadAll)

watch(userId, reloadAll)
</script>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 作者铭牌：一行平铺的横向信息带，不做成大卡片——
   这张页面上真正的主角是下面的作品墙，铭牌只负责交代「这是谁」。 */
.profile-plaque {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  padding: 4px 0 0;
}

.profile-plaque__avatar {
  flex-shrink: 0;
  border: 1px solid var(--app-border);
}

.profile-plaque__main {
  flex: 1 1 auto;
  min-width: 0;
}

.profile-plaque__name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.profile-plaque__name {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--app-text);
  word-break: break-all;
}

.profile-plaque__account {
  margin: 2px 0 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.profile-plaque__bio {
  max-width: 60ch;
  margin: 10px 0 0;
  color: var(--app-text);
  font-size: 14px;
  line-height: 1.6;
}

.profile-plaque__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
  margin-top: 14px;
}

/* 标签在上、数值在下：两个事实各自成对，不用「·」串成一行装饰性元信息 */
.profile-fact {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.profile-fact__label {
  color: var(--app-text-secondary);
  font-size: 12px;
}

.profile-fact__value {
  color: var(--app-text);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.profile-plaque__action {
  flex-shrink: 0;
}

.profile-page__missing {
  padding: 48px 0;
}

.profile-works__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}

.profile-works__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--app-text);
  word-break: break-all;
}

.profile-works__count {
  color: var(--app-text-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.profile-page__pagination {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}

@media (max-width: 700px) {
  .profile-plaque {
    flex-wrap: wrap;
  }

  .profile-plaque__name {
    font-size: 20px;
  }
}
</style>
