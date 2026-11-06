<template>
  <div class="yutu home">
    <main class="yutu-wrap">
      <!-- Hero：左文案+搜索+色系+清晰度，右今日一图 -->
      <section class="hero">
        <div class="hero-copy">
          <h1 class="hero-title">好图，自有归处。</h1>
          <p class="hero-sub">屿图收录 4K 至 8K 的高分壁纸与摄影作品。按色彩、分辨率或关键词浏览，找到你的下一张桌面。</p>

          <form class="hero-search" role="search" @submit.prevent="onSearch">
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" class="hero-search__icon">
              <circle cx="11" cy="11" r="7" />
              <path d="m20 20-3.6-3.6" />
            </svg>
            <input v-model="heroKeyword" type="search" placeholder="搜索色彩、关键词或摄影师" aria-label="搜索图片" />
            <a-popover v-model:open="imageSearchOpen" trigger="click" placement="bottomRight" :arrow="false">
              <button type="button" class="hero-search__cam" aria-label="以图搜图">
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M4 8h3l2-3h6l2 3h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1Z" />
                  <circle cx="12" cy="13" r="3.4" />
                </svg>
              </button>
              <template #content>
                <div class="image-search-panel">
                  <button type="button" class="image-search-panel__upload" @click="pickQueryImage">
                    <UploadOutlined />
                    上传本地图片
                  </button>
                  <div class="image-search-panel__split"><span>或</span></div>
                  <div class="image-search-panel__url">
                    <a-input v-model:value="queryUrl" placeholder="粘贴图片链接" allow-clear @press-enter="submitQueryUrl" />
                    <a-button type="primary" :disabled="!queryUrl.trim()" @click="submitQueryUrl">搜图</a-button>
                  </div>
                  <p class="image-search-panel__hint">支持 jpg / png / webp，{{ PIC_SEARCH_MAX_SIZE_MB }}MB 以内</p>
                </div>
              </template>
            </a-popover>
            <button type="submit" class="hero-search__go" aria-label="搜索">
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <path d="M5 12h14" />
                <path d="m13 6 6 6-6 6" />
              </svg>
            </button>
          </form>
          <input ref="queryInputRef" type="file" accept="image/jpeg,image/png,image/webp" style="display: none" @change="onQueryFileChange" />

          <div class="palette" role="group" aria-label="按颜色筛选">
            <span class="palette-hint">按颜色找图</span>
            <button
              v-for="c in COLOR_OPTIONS"
              :key="c.value"
              type="button"
              class="yutu-dot"
              :style="{ background: c.color }"
              :title="c.label"
              :aria-pressed="colorFamily === c.value"
              @click="setColor(c.value)"
            />
          </div>

          <div class="chips" role="group" aria-label="按分辨率筛选">
            <button
              v-for="chip in SIZE_CHIPS"
              :key="chip.value"
              type="button"
              class="yutu-chip"
              :aria-pressed="sizeChip === chip.value"
              @click="setSizeChip(chip.value)"
            >
              {{ chip.label }}
            </button>
          </div>
        </div>

        <figure v-if="daily" class="hero-fig">
          <img :src="sameOriginCosUrl(daily.url)" :alt="daily.name ?? '今日一图'" />
          <figcaption class="hero-fig__cap">
            <div>
              <span class="hero-fig__kicker">今日一图</span>
              <strong class="hero-fig__title">{{ daily.name }}</strong>
              <span class="hero-fig__meta">@{{ daily.user?.userName ?? '屿图' }}</span>
            </div>
            <a class="hero-fig__dl" :href="`/picture/${daily.id}`" @click.prevent="router.push(`/picture/${daily.id}`)">
              下载 {{ resolutionTier(daily.picWidth, daily.picHeight) || '原图' }}
            </a>
          </figcaption>
        </figure>
      </section>

      <!-- 精选专题 -->
      <section v-if="topics.length" class="yutu-sec">
        <div class="yutu-sec-head">
          <h2>精选专题</h2>
          <a class="yutu-sec-more" href="/topics" @click.prevent="router.push('/topics')">查看全部专题</a>
        </div>
        <div class="topics">
          <a v-for="topic in topics" :key="topic.id" class="topic" href="javascript:;" @click="router.push('/topics')">
            <figure class="topic__fig">
              <img v-if="topic.coverUrl" :src="sameOriginCosUrl(topic.coverUrl)" :alt="topic.name ?? ''" loading="lazy" />
            </figure>
            <h3>{{ topic.name }}</h3>
            <p>{{ topic.pictureCount ?? 0 }} 张作品<template v-if="topic.updateSchedule">，{{ topic.updateSchedule }}</template></p>
          </a>
        </div>
      </section>

      <!-- 热门下载榜 -->
      <section v-if="trend.length" class="yutu-sec">
        <div class="yutu-sec-head">
          <h2>热门下载榜</h2>
          <a class="yutu-sec-more" href="/ranking" @click.prevent="router.push('/ranking')">查看完整榜单</a>
        </div>
        <ol class="trend">
          <li v-for="item in trend" :key="item.picture?.id ?? item.rank">
            <span class="trend__rank">{{ item.rank }}</span>
            <img :src="sameOriginCosUrl(item.picture?.thumbnailUrl || item.picture?.url)" :alt="item.picture?.name ?? ''" loading="lazy" />
            <div class="trend__t">
              <strong>{{ item.picture?.name }}</strong>
              <span>{{ formatCount(item.downloadCount) }} 次下载<template v-if="rankTier(item)">，{{ rankTier(item) }}</template></span>
            </div>
          </li>
        </ol>
      </section>

      <!-- 桌面横屏精选 -->
      <section v-if="widePicks.length" class="yutu-sec">
        <div class="yutu-sec-head">
          <h2>桌面横屏精选</h2>
          <span class="yutu-sec-note">适配桌面与双屏，16:9 / 16:10</span>
        </div>
        <div class="wide-grid">
          <a v-for="pic in widePicks" :key="pic.id" class="wide-card" href="javascript:;" @click="router.push(`/picture/${pic.id}`)">
            <figure class="wide-fig">
              <img :src="sameOriginCosUrl(pic.thumbnailUrl || pic.url)" :alt="pic.name ?? ''" loading="lazy" />
              <span v-if="resolutionTier(pic.picWidth, pic.picHeight)" class="wide-res">
                {{ resolutionTier(pic.picWidth, pic.picHeight) }}
              </span>
            </figure>
            <div class="wide-cap">
              <div>
                <div class="yutu-card__title">{{ pic.name }}</div>
                <div class="yutu-card__author">@{{ pic.user?.userName ?? '屿图' }}</div>
              </div>
            </div>
          </a>
        </div>
      </section>

      <!-- 最新作品 -->
      <section class="yutu-sec">
        <div class="tabs" role="tablist" aria-label="作品列表筛选">
          <button
            v-for="tab in TABS"
            :key="tab.value"
            type="button"
            class="tab"
            role="tab"
            :aria-selected="activeTab === tab.value"
            @click="setTab(tab.value)"
          >
            {{ tab.label }}
          </button>
          <span class="tabs-count">共 {{ formatCount(total) }} 张作品</span>
        </div>

        <a-spin :spinning="loading">
          <div v-if="gridPictures.length" class="photo-grid">
            <YutuPictureCard
              v-for="pic in gridPictures"
              :key="`${activeTab}-${pic.id}`"
              :picture="pic"
              :to="`/picture/${pic.id}`"
              :sub-caption="pic.user?.userName ? `@${pic.user.userName}` : ''"
              show-collect
            />
          </div>
          <a-empty v-else-if="!loading" description="没有符合筛选条件的壁纸">
            <a-button v-if="hasActiveFilters" type="primary" @click="clearFilters">清除筛选</a-button>
          </a-empty>
        </a-spin>

        <div v-if="total > PAGE_SIZE" class="pager">
          <a-pagination
            :current="current"
            :total="total"
            :page-size="PAGE_SIZE"
            :show-size-changer="false"
            show-quick-jumper
            @change="onPageChange"
          />
        </div>
      </section>
    </main>

    <YutuFooter />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { UploadOutlined } from '@ant-design/icons-vue'
import { storeToRefs } from 'pinia'
import YutuFooter from '@/components/YutuFooter.vue'
import YutuPictureCard from '@/components/YutuPictureCard.vue'
import { listPictureVOByPage } from '@/api/pictureController'
import {
  getDailyPickPicture,
  listPictureRanking,
  listTopicVOs,
  resolutionTier,
  type PictureRankVO,
  type TopicVO,
} from '@/api/yutuController'
import { PIC_SEARCH_MAX_SIZE_BYTES, PIC_SEARCH_MAX_SIZE_MB, PIC_SEARCH_OVER_SIZE_MESSAGE } from '@/constant/picture'
import { pendingImageQuery } from '@/utils/pendingImageQuery'
import { sameOriginCosUrl } from '@/utils/cosUrl'
import { formatCount } from '@/utils'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { useCollectStore } from '@/stores/useCollectStore'

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()
const collectStore = useCollectStore()
const { loginUser } = storeToRefs(loginUserStore)
void loginUser
void collectStore

const PAGE_SIZE = 12

/** 屿图九色系（与后端 ColorExtractor 分桶一一对应） */
const COLOR_OPTIONS = [
  { label: '墨黑', value: 'dark', color: '#17191C' },
  { label: '月白', value: 'light', color: '#F2F4F5' },
  { label: '米白', value: 'cream', color: '#EDE3D2' },
  { label: '赭红', value: 'red', color: '#A64B35' },
  { label: '杏黄', value: 'yellow', color: '#E0A64B' },
  { label: '苔绿', value: 'green', color: '#6E8B5E' },
  { label: '黛蓝', value: 'blue', color: '#34566E' },
  { label: '青紫', value: 'purple', color: '#6E5E8B' },
  { label: '雾灰', value: 'gray', color: '#C6C9CD' },
] as const

/** 尺寸 chips：三个清晰度档 + 两个屏幕方向 */
const SIZE_CHIPS = [
  { label: '全部尺寸', value: '' },
  { label: '8K', value: '8k' },
  { label: '4K', value: '4k' },
  { label: '2K', value: '2k' },
  { label: '手机竖屏', value: 'portrait' },
  { label: '桌面横屏', value: 'landscape' },
] as const

type SizeChipValue = (typeof SIZE_CHIPS)[number]['value']

const TIER_PIXELS: Record<string, number> = { '8k': 33177600, '4k': 8294400, '2k': 2073600 }

const TABS = [
  { label: '最新上传', value: 'latest' },
  { label: '本周最受欢迎', value: 'week' },
  { label: '编辑精选', value: 'featured' },
] as const

type TabValue = (typeof TABS)[number]['value']

// ==================== 状态 ====================

const heroKeyword = ref('')
const colorFamily = ref('')
const sizeChip = ref<SizeChipValue>('')
const activeTab = ref<TabValue>('latest')
const current = ref(1)

const daily = ref<API.PictureVO | null>(null)
const topics = ref<TopicVO[]>([])
const trend = ref<PictureRankVO[]>([])
const widePicks = ref<API.PictureVO[]>([])
const gridPictures = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(false)

const hasActiveFilters = computed(() => Boolean(colorFamily.value || sizeChip.value))

// ==================== 筛选与 URL 同步 ====================

function setColor(value: string) {
  colorFamily.value = colorFamily.value === value ? '' : value
  current.value = 1
  syncQuery()
  loadGrid()
}

function setSizeChip(value: SizeChipValue) {
  sizeChip.value = value
  current.value = 1
  syncQuery()
  loadGrid()
}

function setTab(value: TabValue) {
  if (activeTab.value === value) return
  activeTab.value = value
  current.value = 1
  syncQuery()
  loadGrid()
}

function clearFilters() {
  colorFamily.value = ''
  sizeChip.value = ''
  heroKeyword.value = ''
  current.value = 1
  syncQuery()
  loadGrid()
}

function onPageChange(page: number) {
  current.value = page
  syncQuery()
  loadGrid()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function onSearch() {
  current.value = 1
  syncQuery()
  loadGrid()
}

function syncQuery() {
  const query: Record<string, string> = {}
  if (current.value > 1) query.page = String(current.value)
  if (colorFamily.value) query.color = colorFamily.value
  if (sizeChip.value) query.size = sizeChip.value
  if (heroKeyword.value) query.k = heroKeyword.value
  if (activeTab.value !== 'latest') query.tab = activeTab.value
  router.replace({ query })
}

function parseQueryState() {
  const q = route.query
  const page = Number(q.page)
  current.value = Number.isInteger(page) && page > 0 ? page : 1
  colorFamily.value =
    typeof q.color === 'string' && COLOR_OPTIONS.some((c) => c.value === q.color) ? q.color : ''
  sizeChip.value =
    typeof q.size === 'string' && SIZE_CHIPS.some((c) => c.value === q.size) ? (q.size as SizeChipValue) : ''
  heroKeyword.value = typeof q.k === 'string' ? q.k : ''
  activeTab.value =
    q.tab === 'week' || q.tab === 'featured' ? (q.tab as TabValue) : 'latest'
}

// 支持浏览器前进后退
watch(
  () => route.query,
  () => {
    if (route.name !== 'home') return
    parseQueryState()
    loadGrid()
  },
)

// ==================== 数据加载 ====================

function buildGridRequest(): Record<string, unknown> {
  const filters: Record<string, unknown> = {
    current: current.value,
    pageSize: PAGE_SIZE,
  }
  if (activeTab.value === 'latest') {
    filters.sortField = 'createTime'
    filters.sortOrder = 'descend'
  }
  if (activeTab.value === 'featured') {
    filters.isFeatured = true
  }
  if (heroKeyword.value) filters.searchText = heroKeyword.value
  if (colorFamily.value) filters.colorFamily = colorFamily.value
  const pixels = TIER_PIXELS[sizeChip.value]
  if (pixels) filters.minPixels = pixels
  if (sizeChip.value === 'portrait') filters.orientation = 'portrait'
  if (sizeChip.value === 'landscape') filters.orientation = 'landscape'
  return filters
}

async function loadGrid() {
  loading.value = true
  try {
    if (activeTab.value === 'week') {
      // 本周最受欢迎：下载日志的周榜（近 7 天滚动窗口）
      const res = await listPictureRanking({
        period: 'week',
        current: current.value,
        pageSize: PAGE_SIZE,
      })
      if (res.code === 0 && res.data) {
        gridPictures.value = (res.data.records ?? [])
          .map((item) => item.picture)
          .filter((pic): pic is API.PictureVO => Boolean(pic))
        total.value = Number(res.data.total ?? 0)
      } else {
        gridPictures.value = []
        total.value = 0
      }
    } else {
      const res = await listPictureVOByPage(buildGridRequest() as API.PictureQueryRequest)
      if (res.code === 0 && res.data) {
        gridPictures.value = res.data.records ?? []
        total.value = Number(res.data.total ?? 0)
      } else {
        gridPictures.value = []
        total.value = 0
      }
    }
  } catch {
    gridPictures.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadSections() {
  // 今日一图（管理员设置；未设置时 hero 只剩文案）
  getDailyPickPicture()
    .then((res) => {
      daily.value = res.code === 0 ? (res.data ?? null) : null
    })
    .catch(() => {})
  // 精选专题前 3（精选排最前）
  listTopicVOs()
    .then((res) => {
      topics.value = res.code === 0 && res.data ? res.data.slice(0, 3) : []
    })
    .catch(() => {})
  // 热门下载榜 top5（近 24 小时）
  listPictureRanking({ period: 'day', current: 1, pageSize: 5 })
    .then((res) => {
      trend.value = res.code === 0 && res.data ? res.data.records ?? [] : []
    })
    .catch(() => {})
  // 桌面横屏精选 2×2：横屏 + 4K 档
  listPictureVOByPage({
    current: 1,
    pageSize: 4,
    orientation: 'landscape',
    minPixels: 8294400,
    sortField: 'downloadCount',
    sortOrder: 'descend',
  } as API.PictureQueryRequest)
    .then((res) => {
      widePicks.value = res.code === 0 && res.data ? res.data.records ?? [] : []
    })
    .catch(() => {})
}

function rankTier(item: PictureRankVO): string {
  return resolutionTier(item.picture?.picWidth, item.picture?.picHeight)
}

// ==================== 以图搜图（自旧版首页移植） ====================

const queryInputRef = ref<HTMLInputElement | null>(null)
const imageSearchOpen = ref(false)
const queryUrl = ref('')

function pickQueryImage() {
  imageSearchOpen.value = false
  queryInputRef.value?.click()
}

function onQueryFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  // 超限就在入口拦下：否则会白跳一次搜图页再被拒，用户还得自己退回来
  if (file.size > PIC_SEARCH_MAX_SIZE_BYTES) {
    message.error(PIC_SEARCH_OVER_SIZE_MESSAGE)
    return
  }
  pendingImageQuery.value = { kind: 'file', file }
  router.push('/search_picture')
}

function submitQueryUrl() {
  const url = queryUrl.value.trim()
  if (!url) return
  if (!/^https?:\/\//i.test(url)) {
    message.warning('请填写 http 或 https 开头的图片链接')
    return
  }
  pendingImageQuery.value = { kind: 'url', url }
  queryUrl.value = ''
  imageSearchOpen.value = false
  router.push('/search_picture')
}

// ==================== 挂载 ====================

onMounted(() => {
  parseQueryState()
  loadGrid()
  loadSections()
})
</script>

<style scoped>
/* ---------- Hero ---------- */
.hero {
  margin-top: 60px;
  display: grid;
  grid-template-columns: 1.04fr 0.96fr;
  gap: 60px;
  align-items: center;
}

.hero-title {
  font-family: 'Noto Serif SC', serif;
  font-weight: 500;
  font-size: 46px;
  line-height: 1.32;
  letter-spacing: 0.02em;
  margin: 0;
}

.hero-sub {
  margin-top: 18px;
  font-size: 15px;
  color: var(--yutu-ink-2);
  max-width: 30em;
  font-weight: 300;
}

.hero-search {
  margin-top: 34px;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 58px;
  padding: 0 7px 0 21px;
  background: var(--yutu-surface);
  border: 1px solid var(--yutu-line);
  border-radius: 999px;
  box-shadow: 0 1px 2px rgba(23, 25, 28, 0.04);
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.hero-search:focus-within {
  border-color: #c9cac6;
  box-shadow: 0 2px 10px rgba(23, 25, 28, 0.06);
}

.hero-search__icon {
  color: var(--yutu-ink-3);
  flex: none;
}

.hero-search input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font: inherit;
  font-size: 14px;
  color: var(--yutu-ink);
  min-width: 0;
}

.hero-search input::placeholder {
  color: var(--yutu-ink-3);
}

.hero-search__cam {
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: none;
  color: var(--yutu-ink-3);
  display: grid;
  place-items: center;
  cursor: pointer;
  transition:
    color 0.2s,
    background 0.2s;
}

.hero-search__cam:hover {
  color: var(--yutu-ink);
  background: var(--yutu-wash);
}

.hero-search__go {
  flex: none;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--yutu-ink);
  color: #fff;
  display: grid;
  place-items: center;
  border: none;
  cursor: pointer;
  transition: opacity 0.2s;
}

.hero-search__go:hover {
  opacity: 0.85;
}

.palette {
  margin-top: 22px;
  display: flex;
  align-items: center;
  gap: 13px;
  flex-wrap: wrap;
}

.palette-hint {
  font-size: 12.5px;
  color: var(--yutu-ink-3);
  margin-right: 3px;
}

.chips {
  margin-top: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

/* 今日一图 */
.hero-fig {
  position: relative;
  border-radius: 16px;
  overflow: hidden;
  aspect-ratio: 4 / 4.6;
  background: var(--yutu-wash);
  margin: 0;
}

.hero-fig img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-fig__cap {
  position: absolute;
  inset: auto 0 0 0;
  padding: 56px 22px 20px;
  background: linear-gradient(180deg, rgba(12, 14, 16, 0) 0%, rgba(12, 14, 16, 0.58) 100%);
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  color: #fff;
}

.hero-fig__kicker {
  display: block;
  font-size: 11px;
  letter-spacing: 0.14em;
  color: rgba(255, 255, 255, 0.72);
}

.hero-fig__title {
  display: block;
  font-size: 16px;
  font-weight: 500;
  margin-top: 3px;
  letter-spacing: 0.02em;
}

.hero-fig__meta {
  display: block;
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.72);
  margin-top: 2px;
}

.hero-fig__dl {
  flex: none;
  background: #fff;
  color: var(--yutu-ink);
  font-size: 13.5px;
  font-weight: 500;
  padding: 9px 18px;
  border-radius: 999px;
  transition: opacity 0.2s;
}

.hero-fig__dl:hover {
  opacity: 0.88;
}

/* ---------- 精选专题 ---------- */
.topics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.topic__fig {
  border-radius: var(--yutu-radius);
  overflow: hidden;
  aspect-ratio: 16 / 10;
  background: var(--yutu-wash);
  margin: 0;
}

.topic__fig img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s cubic-bezier(0.22, 0.6, 0.3, 1);
}

.topic:hover .topic__fig img {
  transform: scale(1.035);
}

.topic h3 {
  font-size: 15.5px;
  font-weight: 500;
  margin-top: 14px;
  letter-spacing: 0.02em;
}

.topic p {
  font-size: 13px;
  color: var(--yutu-ink-3);
  margin-top: 3px;
}

/* ---------- 热门下载榜 ---------- */
.trend {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 20px;
  list-style: none;
  padding: 0;
  margin: 0;
}

.trend li {
  display: flex;
  align-items: center;
  gap: 13px;
  min-width: 0;
  cursor: pointer;
}

.trend__rank {
  font-family: 'Noto Serif SC', serif;
  font-weight: 600;
  font-size: 21px;
  color: #c9cbc7;
  width: 22px;
  flex: none;
  font-style: normal;
}

.trend li:first-child .trend__rank {
  color: var(--yutu-ink);
}

.trend img {
  width: 54px;
  height: 54px;
  border-radius: 10px;
  object-fit: cover;
  flex: none;
  background: var(--yutu-wash);
}

.trend__t {
  min-width: 0;
}

.trend__t strong {
  display: block;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.trend__t span {
  display: block;
  font-size: 12.5px;
  color: var(--yutu-ink-3);
  margin-top: 1px;
}

/* ---------- 桌面横屏精选 ---------- */
.wide-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 28px 24px;
}

.wide-fig {
  position: relative;
  border-radius: var(--yutu-radius);
  overflow: hidden;
  aspect-ratio: 16 / 9;
  background: var(--yutu-wash);
  margin: 0;
}

.wide-fig img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s cubic-bezier(0.22, 0.6, 0.3, 1);
}

.wide-card:hover .wide-fig img {
  transform: scale(1.025);
}

.wide-res {
  position: absolute;
  top: 14px;
  right: 14px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--yutu-ink);
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 999px;
  letter-spacing: 0.02em;
}

.wide-cap {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  padding-top: 12px;
}

/* ---------- 主网格 ---------- */
.tabs {
  display: flex;
  align-items: baseline;
  gap: 28px;
  border-bottom: 1px solid var(--yutu-line);
}

.tab {
  padding: 4px 2px 13px;
  font-size: 14px;
  color: var(--yutu-ink-2);
  border: none;
  background: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  cursor: pointer;
  transition: color 0.2s;
}

.tab:hover {
  color: var(--yutu-ink);
}

.tab[aria-selected='true'] {
  color: var(--yutu-ink);
  font-weight: 500;
  border-bottom-color: var(--yutu-ink);
}

.tabs-count {
  margin-left: auto;
  font-size: 13px;
  color: var(--yutu-ink-3);
}

.photo-grid {
  margin-top: 30px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 36px 24px;
}

.pager {
  margin-top: 40px;
  display: flex;
  justify-content: center;
}

/* ---------- 以图搜图面板（自旧版移植） ---------- */
.image-search-panel {
  width: 300px;
}

.image-search-panel__upload {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 0;
  border: 1px dashed var(--yutu-line);
  border-radius: 8px;
  background: none;
  cursor: pointer;
  color: var(--yutu-ink-2);
  transition:
    color 0.2s,
    border-color 0.2s;
}

.image-search-panel__upload:hover {
  color: var(--yutu-ink);
  border-color: #c9cac6;
}

.image-search-panel__split {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 10px 0;
  color: var(--yutu-ink-3);
  font-size: 12px;
}

.image-search-panel__split::before,
.image-search-panel__split::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--yutu-line);
}

.image-search-panel__url {
  display: flex;
  gap: 8px;
}

.image-search-panel__hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--yutu-ink-3);
}

/* ---------- 响应式 ---------- */
@media (max-width: 1080px) {
  .hero {
    grid-template-columns: 1fr;
    gap: 44px;
  }

  .hero-fig {
    aspect-ratio: 4 / 3;
    max-width: 640px;
  }

  .photo-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .trend {
    grid-template-columns: repeat(3, 1fr);
  }

  .hero-title {
    font-size: 40px;
  }
}

@media (max-width: 720px) {
  .photo-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 24px 16px;
  }

  .topics {
    grid-template-columns: 1fr;
  }

  .trend {
    grid-template-columns: repeat(2, 1fr);
  }

  .wide-grid {
    grid-template-columns: 1fr;
  }

  .hero-title {
    font-size: 33px;
  }
}
</style>
