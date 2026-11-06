<template>
  <div class="yutu ranking-page">
    <main class="yutu-wrap">
      <section class="yutu-sec" style="margin-top: 56px">
        <div class="yutu-sec-head">
          <h2>热门下载榜</h2>
          <span class="yutu-sec-note">按滚动窗口下载量实时统计</span>
        </div>

        <div class="tabs" role="tablist" aria-label="榜单周期">
          <button
            v-for="p in PERIODS"
            :key="p.value"
            type="button"
            class="tab"
            role="tab"
            :aria-selected="period === p.value"
            @click="setPeriod(p.value)"
          >
            {{ p.label }}
          </button>
        </div>

        <div class="chips" role="group" aria-label="按分类筛选" style="margin-top: 20px">
          <button
            v-for="cat in categoryChips"
            :key="cat.value"
            type="button"
            class="yutu-chip"
            :aria-pressed="category === cat.value"
            @click="setCategory(cat.value)"
          >
            {{ cat.label }}
          </button>
        </div>

        <!-- 前三领奖台 -->
        <div v-if="podium.length" class="podium">
          <a
            v-for="item in podium"
            :key="item.picture?.id ?? item.rank"
            class="podium-card"
            href="javascript:;"
            @click="goDetail(item)"
          >
            <img :src="sameOriginCosUrl(item.picture?.thumbnailUrl || item.picture?.url)" :alt="item.picture?.name ?? ''" loading="lazy" />
            <span class="podium-card__rank">{{ item.rank }}</span>
            <span class="podium-card__cap">
              <strong class="podium-card__title">{{ item.picture?.name }}</strong>
              <span class="podium-card__meta">
                @{{ item.picture?.user?.userName ?? '屿图' }} · {{ formatCount(item.downloadCount) }} 次下载
                <template v-if="tierOf(item)"> · {{ tierOf(item) }}</template>
              </span>
            </span>
          </a>
        </div>

        <!-- 4 名以后列表 -->
        <ol v-if="restList.length" class="rank-list">
          <li v-for="item in restList" :key="item.picture?.id ?? item.rank" class="rank-item" @click="goDetail(item)">
            <span class="rank-item__no">{{ item.rank }}</span>
            <img :src="sameOriginCosUrl(item.picture?.thumbnailUrl || item.picture?.url)" :alt="item.picture?.name ?? ''" loading="lazy" />
            <div class="rank-item__t">
              <strong>{{ item.picture?.name }}</strong>
              <span>@{{ item.picture?.user?.userName ?? '屿图' }}</span>
            </div>
            <div class="rank-item__meta">
              <span class="rank-item__trend" :class="`rank-item__trend--${item.trend ?? 'flat'}`">
                <template v-if="item.trend === 'up'">↑ {{ item.trendDelta }}</template>
                <template v-else-if="item.trend === 'down'">↓ {{ item.trendDelta }}</template>
                <template v-else-if="item.trend === 'new'"><i class="rank-item__new">新上榜</i></template>
                <template v-else>—</template>
              </span>
              <span><b>{{ formatCount(item.downloadCount) }}</b> 次下载</span>
              <span v-if="tierOf(item)" class="rank-item__res">{{ tierOf(item) }}</span>
            </div>
          </li>
        </ol>

        <a-spin :spinning="loading">
          <a-empty v-if="!loading && !podium.length && !restList.length" description="该周期暂无下载记录" />
        </a-spin>
      </section>
    </main>

    <YutuFooter />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import YutuFooter from '@/components/YutuFooter.vue'
import { listPictureRanking, resolutionTier, type PictureRankVO } from '@/api/yutuController'
import { sameOriginCosUrl } from '@/utils/cosUrl'
import { formatCount } from '@/utils'
import { listPictureTagCategory } from '@/api/pictureController'

const PERIODS = [
  { label: '日榜', value: 'day' },
  { label: '周榜', value: 'week' },
  { label: '月榜', value: 'month' },
] as const

type PeriodValue = (typeof PERIODS)[number]['value']

const router = useRouter()
const period = ref<PeriodValue>('day')
const category = ref('')
const categoryChips = ref<Array<{ label: string; value: string }>>([{ label: '全部', value: '' }])
const items = ref<PictureRankVO[]>([])
const loading = ref(false)

const podium = computed(() => items.value.slice(0, 3))
const restList = computed(() => items.value.slice(3))

function tierOf(item: PictureRankVO): string {
  return resolutionTier(item.picture?.picWidth, item.picture?.picHeight)
}

function goDetail(item: PictureRankVO) {
  if (item.picture?.id) router.push(`/picture/${item.picture.id}`)
}

function setPeriod(value: PeriodValue) {
  if (period.value === value) return
  period.value = value
  loadRanking()
}

function setCategory(value: string) {
  category.value = value
  loadRanking()
}

async function loadRanking() {
  loading.value = true
  try {
    const res = await listPictureRanking({
      period: period.value,
      category: category.value || undefined,
      current: 1,
      pageSize: 15,
    })
    items.value = res.code === 0 && res.data ? res.data.records ?? [] : []
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  loadRanking()
  // 分类 chips：全部 + 站点分类词表（榜单接口支持 category 过滤）
  try {
    const res = await listPictureTagCategory()
    const categories = res.code === 0 && res.data ? (res.data.categoryList ?? []) : []
    categoryChips.value = [
      { label: '全部', value: '' },
      ...categories.map((name) => ({ label: name, value: name })),
    ]
  } catch {
    // 拿不到词表就只留「全部」
  }
})
</script>

<style scoped>
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

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

/* 领奖台 */
.podium {
  margin-top: 32px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.podium-card {
  position: relative;
  display: block;
  border-radius: var(--yutu-radius);
  overflow: hidden;
  aspect-ratio: 16 / 10;
  background: var(--yutu-wash);
}

.podium-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s cubic-bezier(0.22, 0.6, 0.3, 1);
}

.podium-card:hover img {
  transform: scale(1.025);
}

.podium-card__rank {
  position: absolute;
  top: 14px;
  left: 14px;
  font-family: 'Noto Serif SC', serif;
  font-weight: 600;
  font-size: 26px;
  color: #fff;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
}

.podium-card__cap {
  position: absolute;
  inset: auto 0 0 0;
  padding: 40px 18px 16px;
  background: linear-gradient(180deg, rgba(12, 14, 16, 0) 0%, rgba(12, 14, 16, 0.6) 100%);
  color: #fff;
}

.podium-card__title {
  display: block;
  font-size: 15px;
  font-weight: 500;
}

.podium-card__meta {
  display: block;
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.78);
  margin-top: 2px;
}

/* 列表 */
.rank-list {
  margin-top: 28px;
  list-style: none;
  padding: 0;
  margin-bottom: 0;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid var(--yutu-line);
  cursor: pointer;
}

.rank-item:last-child {
  border-bottom: none;
}

.rank-item__no {
  font-family: 'Noto Serif SC', serif;
  font-weight: 600;
  font-size: 17px;
  color: var(--yutu-ink-3);
  width: 28px;
  flex: none;
  text-align: center;
}

.rank-item img {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  object-fit: cover;
  flex: none;
  background: var(--yutu-wash);
}

.rank-item__t {
  min-width: 0;
  flex: 1;
}

.rank-item__t strong {
  display: block;
  font-size: 14.5px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rank-item__t span {
  font-size: 12.5px;
  color: var(--yutu-ink-3);
}

.rank-item__meta {
  display: flex;
  align-items: center;
  gap: 18px;
  font-size: 13px;
  color: var(--yutu-ink-2);
  flex: none;
}

.rank-item__trend {
  min-width: 48px;
  text-align: center;
  font-size: 12.5px;
}

.rank-item__trend--up {
  color: #2e7d32;
}

.rank-item__trend--down {
  color: #b3563a;
}

.rank-item__new {
  font-style: normal;
  font-size: 11.5px;
  color: var(--yutu-ink);
  border: 1px solid var(--yutu-ink);
  border-radius: 999px;
  padding: 1px 8px;
}

.rank-item__res {
  font-size: 12px;
  color: var(--yutu-ink-3);
}

@media (max-width: 900px) {
  .podium {
    grid-template-columns: 1fr;
  }

  .rank-item__meta {
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
  }
}
</style>
