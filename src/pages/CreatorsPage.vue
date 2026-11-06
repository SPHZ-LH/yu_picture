<template>
  <div class="yutu creators-page">
    <main class="yutu-wrap">
      <!-- 本周精选创作者 hero -->
      <section v-if="featuredCreator" class="creator-hero">
        <a class="creator-hero__fig" href="javascript:;" @click="goProfile(featuredCreator)">
          <img
            v-if="heroCover"
            :src="sameOriginCosUrl(heroCover)"
            :alt="`${featuredCreator.userName} 的封面`"
          />
          <span class="creator-hero__kicker">本周精选创作者</span>
        </a>
        <div class="creator-hero__body">
          <div class="creator-hero__author">
            <span class="avatar-char avatar-char--lg">{{ firstChar(featuredCreator.userName) }}</span>
            <strong class="creator-hero__name">{{ featuredCreator.userName }}</strong>
          </div>
          <p class="creator-hero__metarow">
            <template v-if="featuredCreator.region">{{ featuredCreator.region }} · </template>
            加入 {{ formatDate(featuredCreator.createTime, 'YYYY 年 M 月') }}
          </p>
          <p v-if="featuredCreator.userProfile" class="creator-hero__bio">{{ featuredCreator.userProfile }}</p>
          <div class="creator-hero__stats">
            <span><b>{{ formatCount(heroStats.pictureCount) }}</b> 张作品</span>
            <span><b>{{ formatCount(heroStats.followerCount) }}</b> 粉丝</span>
            <span><b>{{ formatCount(heroStats.downloadCount) }}</b> 次下载</span>
          </div>
          <div class="creator-hero__actions">
            <button
              v-if="isLoggedIn && !isSelf(featuredCreator)"
              type="button"
              class="btn-primary"
              @click="toggleFollow(featuredCreator)"
            >
              {{ heroFollowed ? '已关注' : '+ 关注' }}
            </button>
            <a class="yutu-sec-more" href="javascript:;" style="margin-left: auto" @click="goProfile(featuredCreator)">
              访问主页
            </a>
          </div>
        </div>
      </section>

      <!-- 全部创作者 -->
      <section class="yutu-sec" style="margin-top: 72px">
        <div class="yutu-sec-head">
          <h2>全部创作者</h2>
          <span class="yutu-sec-note">每一位镜头背后的人</span>
        </div>

        <div class="chips" role="group" aria-label="排序方式">
          <button
            v-for="s in SORTS"
            :key="s.value"
            type="button"
            class="yutu-chip"
            :aria-pressed="sortItem === s.value"
            @click="setSort(s.value)"
          >
            {{ s.label }}
          </button>
        </div>

        <a-spin :spinning="loading">
          <div v-if="creators.length" class="creator-grid">
            <div v-for="creator in creators" :key="creator.id" class="creator-card">
              <span class="avatar-char">{{ firstChar(creator.userName) }}</span>
              <span class="creator-card__name">{{ creator.userName }}</span>
              <span class="creator-card__bio">{{ creator.userProfile || '这位创作者还没有简介。' }}</span>
              <span class="creator-card__stats">
                <b>{{ formatCount(creator.pictureCount) }}</b> 张作品 ·
                <b>{{ formatCount(creator.followerCount) }}</b> 粉丝
              </span>
              <div class="creator-card__actions">
                <button
                  v-if="isLoggedIn && !isSelf(creator)"
                  type="button"
                  class="btn-ghost"
                  :class="{ 'btn-ghost--on': isFollowed(creator.id) }"
                  @click="toggleFollow(creator)"
                >
                  {{ isFollowed(creator.id) ? '已关注' : '关注' }}
                </button>
                <a class="creator-card__more" href="javascript:;" @click="goProfile(creator)">主页</a>
              </div>
            </div>
          </div>
          <a-empty v-else-if="!loading" description="还没有创作者" />
        </a-spin>

        <div v-if="total > PAGE_SIZE" class="pager">
          <a-pagination
            :current="current"
            :total="total"
            :page-size="PAGE_SIZE"
            :show-size-changer="false"
            @change="onPageChange"
          />
        </div>
      </section>
    </main>

    <YutuFooter />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { storeToRefs } from 'pinia'
import YutuFooter from '@/components/YutuFooter.vue'
import {
  cancelFollow,
  doFollow,
  getFeaturedCreator,
  getFollowStatus,
  listCreatorPage,
  listFollowStatusBatch,
} from '@/api/yutuController'
import { sameOriginCosUrl } from '@/utils/cosUrl'
import { formatCount, formatDate } from '@/utils'
import { useLoginUserStore } from '@/stores/useLoginUserStore'

const PAGE_SIZE = 8

const SORTS = [
  { label: '人气优先', value: 'popular' },
  { label: '作品最多', value: 'works' },
  { label: '最新加入', value: 'newest' },
] as const

type SortValue = (typeof SORTS)[number]['value']

interface CreatorItem {
  id?: string
  userName?: string
  userAvatar?: string
  userProfile?: string
  createTime?: string
  pictureCount?: string
  downloadCount?: string
  followerCount?: string
}

const router = useRouter()
const loginUserStore = useLoginUserStore()
const { loginUser } = storeToRefs(loginUserStore)
const isLoggedIn = computed(() => Boolean(loginUser.value?.id))

const featuredCreator = ref<API.UserVO | null>(null)
const heroStats = ref<{ pictureCount: number; followerCount: number; downloadCount: number }>({
  pictureCount: 0,
  followerCount: 0,
  downloadCount: 0,
})
const heroFollowed = ref(false)
const heroCover = ref('')

const sortItem = ref<SortValue>('popular')
const creators = ref<CreatorItem[]>([])
const followedIds = ref<Set<string>>(new Set())
const current = ref(1)
const total = ref(0)
const loading = ref(false)

function firstChar(name?: string): string {
  return (name ?? '客').slice(0, 1)
}

function isSelf(user: { id?: string }): boolean {
  return Boolean(user.id && user.id === String(loginUser.value?.id ?? ''))
}

function isFollowed(userId?: string): boolean {
  return Boolean(userId && followedIds.value.has(userId))
}

function goProfile(user: { id?: string }) {
  if (user.id) router.push(`/user_profile/${user.id}`)
}

function setSort(value: SortValue) {
  if (sortItem.value === value) return
  sortItem.value = value
  current.value = 1
  loadCreators()
}

function onPageChange(page: number) {
  current.value = page
  loadCreators()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function toggleFollow(user: { id?: string }) {
  if (!user.id) return
  try {
    if (isFollowed(user.id)) {
      await cancelFollow(user.id)
      followedIds.value.delete(user.id)
      if (featuredCreator.value?.id === user.id) heroFollowed.value = false
      message.success('已取消关注')
    } else {
      await doFollow(user.id)
      followedIds.value.add(user.id)
      if (featuredCreator.value?.id === user.id) heroFollowed.value = true
      message.success('已关注')
    }
    followedIds.value = new Set(followedIds.value)
  } catch {
    // 失败提示由请求层统一弹出
  }
}

async function loadCreators() {
  loading.value = true
  try {
    const res = await listCreatorPage(current.value, PAGE_SIZE, sortItem.value)
    if (res.code === 0 && res.data) {
      creators.value = res.data.records ?? []
      total.value = Number(res.data.total ?? 0)
      // 批量拉关注状态（登录用户）
      if (isLoggedIn.value) {
        const ids = creators.value.filter((c) => c.id && !isSelf(c)).map((c) => c.id!)
        if (ids.length) {
          const statusRes = await listFollowStatusBatch(ids)
          if (statusRes.code === 0 && statusRes.data) {
            followedIds.value = new Set(
              statusRes.data.filter((s) => s.followed && s.userId).map((s) => s.userId!),
            )
          }
        }
      }
    } else {
      creators.value = []
      total.value = 0
    }
  } catch {
    creators.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  loadCreators()
  // 精选创作者 hero：基本信息 + 聚合数据 + 关注状态
  try {
    const res = await getFeaturedCreator()
    if (res.code === 0 && res.data) {
      featuredCreator.value = res.data
      heroCover.value = res.data.coverUrl ?? ''
      const creatorId = res.data.id
      if (creatorId) {
        // 复用创作者聚合查询拿统计（featured-creator 接口只有资料没有聚合数）
        const statsRes = await listCreatorPage(1, 20, 'works')
        const matched = (statsRes.code === 0 && statsRes.data ? statsRes.data.records ?? [] : []).find(
          (c) => c.id === creatorId,
        )
        if (matched) {
          heroStats.value = {
            pictureCount: Number(matched.pictureCount ?? 0),
            followerCount: Number(matched.followerCount ?? 0),
            downloadCount: Number(matched.downloadCount ?? 0),
          }
          heroCover.value = ''
        }
        if (isLoggedIn.value && !isSelf(res.data)) {
          const statusRes = await getFollowStatus(creatorId)
          heroFollowed.value = statusRes.code === 0 ? Boolean(statusRes.data?.followed) : false
        }
      }
    }
  } catch {
    featuredCreator.value = null
  }
})
</script>

<style scoped>
.creator-hero {
  margin-top: 56px;
  display: grid;
  grid-template-columns: 0.9fr 1.1fr;
  gap: 40px;
  align-items: stretch;
}

.creator-hero__fig {
  position: relative;
  display: block;
  border-radius: 16px;
  overflow: hidden;
  min-height: 300px;
  background: var(--yutu-wash);
}

.creator-hero__fig img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  position: absolute;
  inset: 0;
}

.creator-hero__kicker {
  position: absolute;
  left: 18px;
  bottom: 16px;
  font-size: 11.5px;
  letter-spacing: 0.14em;
  color: #fff;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.4);
}

.creator-hero__body {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.creator-hero__author {
  display: flex;
  align-items: center;
  gap: 12px;
}

.creator-hero__name {
  font-family: 'Noto Serif SC', serif;
  font-size: 26px;
  font-weight: 600;
}

.creator-hero__metarow {
  margin-top: 10px;
  font-size: 13px;
  color: var(--yutu-ink-3);
}

.creator-hero__bio {
  margin-top: 10px;
  font-size: 14px;
  color: var(--yutu-ink-2);
  max-width: 34em;
}

.creator-hero__stats {
  margin-top: 16px;
  display: flex;
  gap: 24px;
  font-size: 13.5px;
  color: var(--yutu-ink-2);
}

.creator-hero__stats b {
  color: var(--yutu-ink);
  font-size: 16px;
  margin-right: 4px;
}

.creator-hero__actions {
  margin-top: 20px;
  display: flex;
  align-items: center;
}

.avatar-char {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--yutu-ink);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 18px;
  font-weight: 500;
  flex: none;
}

.avatar-char--lg {
  width: 56px;
  height: 56px;
  font-size: 22px;
}

.btn-primary {
  background: var(--yutu-ink);
  color: #fff;
  font-size: 13.5px;
  font-weight: 500;
  padding: 10px 28px;
  border-radius: 999px;
  border: none;
  cursor: pointer;
  transition: opacity 0.2s;
}

.btn-primary:hover {
  opacity: 0.85;
}

.btn-ghost {
  background: none;
  border: 1px solid var(--yutu-line);
  color: var(--yutu-ink-2);
  font-size: 12.5px;
  padding: 6px 16px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-ghost:hover {
  border-color: #c9cac6;
  color: var(--yutu-ink);
}

.btn-ghost--on {
  border-color: var(--yutu-ink);
  color: var(--yutu-ink);
}

/* chips + grid */
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.creator-grid {
  margin-top: 28px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.creator-card {
  border: 1px solid var(--yutu-line);
  border-radius: var(--yutu-radius);
  background: var(--yutu-surface);
  padding: 24px 20px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  transition: box-shadow 0.2s;
}

.creator-card:hover {
  box-shadow: 0 6px 24px rgba(23, 25, 28, 0.06);
}

.creator-card__name {
  font-size: 15px;
  font-weight: 500;
}

.creator-card__bio {
  font-size: 12.5px;
  color: var(--yutu-ink-2);
  line-height: 1.55;
  min-height: 40px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.creator-card__stats {
  font-size: 12.5px;
  color: var(--yutu-ink-3);
}

.creator-card__stats b {
  color: var(--yutu-ink);
}

.creator-card__actions {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.creator-card__more {
  font-size: 12.5px;
  color: var(--yutu-ink-3);
  margin-left: auto;
}

.creator-card__more:hover {
  color: var(--yutu-ink);
}

.pager {
  margin-top: 40px;
  display: flex;
  justify-content: center;
}

@media (max-width: 1080px) {
  .creator-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .creator-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .creator-grid {
    grid-template-columns: 1fr;
  }
}
</style>
