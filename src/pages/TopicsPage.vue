<template>
  <div class="yutu topics-page">
    <main class="yutu-wrap">
      <!-- 本周精选专题 hero -->
      <a v-if="featured" class="topic-hero" href="javascript:;" @click="openTopic(featured)">
        <img :src="sameOriginCosUrl(featured.coverUrl ?? '')" :alt="featured.name ?? ''" />
        <span class="topic-hero__cap">
          <span class="topic-hero__kicker">本周精选专题</span>
          <strong class="topic-hero__title">{{ featured.name }}</strong>
          <span class="topic-hero__meta">
            {{ featured.pictureCount ?? 0 }} 张作品
            <template v-if="featured.curatorName"> · 由 @{{ featured.curatorName }} 策划</template>
            <template v-if="featured.description"> · {{ featured.description }}</template>
          </span>
        </span>
        <span class="topic-hero__cta">浏览专题</span>
      </a>

      <!-- 全部专题 -->
      <section class="yutu-sec" :style="{ marginTop: featured ? '64px' : '56px' }">
        <div class="yutu-sec-head" style="margin-bottom: 18px">
          <h2>全部专题</h2>
          <span class="yutu-sec-note">由编辑与创作者共同维护，持续更新</span>
        </div>

        <div class="chips" role="group" aria-label="按分类筛选">
          <button
            v-for="cat in categoryChips"
            :key="cat.value"
            type="button"
            class="yutu-chip"
            :aria-pressed="category === cat.value"
            @click="setCategory(cat.value)"
          >
            {{ cat.label }} {{ cat.count }}
          </button>
        </div>

        <div v-if="visibleTopics.length" class="topics-grid">
          <a
            v-for="topic in visibleTopics"
            :key="topic.id"
            class="topic"
            href="javascript:;"
            @click="openTopic(topic)"
          >
            <figure class="topic__fig">
              <img v-if="topic.coverUrl" :src="sameOriginCosUrl(topic.coverUrl)" :alt="topic.name ?? ''" loading="lazy" />
              <span class="topic__count">{{ topic.pictureCount ?? 0 }} 张</span>
            </figure>
            <h3>{{ topic.name }}</h3>
            <p class="topic__desc">{{ topic.description }}</p>
            <p class="topic__meta">
              <template v-if="topic.updateSchedule">{{ topic.updateSchedule }}</template>
              <template v-if="topic.curatorName"> · @{{ topic.curatorName }} 策划</template>
            </p>
          </a>
        </div>
        <a-empty v-else-if="!loading" description="暂无专题" />
      </section>

      <!-- 专题内图片 -->
      <section v-if="selectedTopic" class="yutu-sec">
        <div class="yutu-sec-head">
          <h2>{{ selectedTopic.name }}</h2>
          <span class="yutu-sec-note">{{ selectedTopic.description }}</span>
        </div>
        <a-spin :spinning="picturesLoading">
          <div v-if="topicPictures.length" class="photo-grid">
            <YutuPictureCard
              v-for="pic in topicPictures"
              :key="pic.id"
              :picture="pic"
              :to="`/picture/${pic.id}`"
              :sub-caption="pic.user?.userName ? `@${pic.user.userName}` : ''"
              show-collect
            />
          </div>
          <a-empty v-else-if="!picturesLoading" description="该专题还没有作品" />
        </a-spin>
        <div v-if="picturesTotal > TOPIC_PAGE_SIZE" class="pager">
          <a-pagination
            :current="picturesCurrent"
            :total="picturesTotal"
            :page-size="TOPIC_PAGE_SIZE"
            :show-size-changer="false"
            @change="onPicturesPageChange"
          />
        </div>
      </section>
    </main>

    <YutuFooter />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import YutuFooter from '@/components/YutuFooter.vue'
import YutuPictureCard from '@/components/YutuPictureCard.vue'
import { getFeaturedTopicVO, listTopicPictures, listTopicVOs, type TopicVO } from '@/api/yutuController'
import { sameOriginCosUrl } from '@/utils/cosUrl'

const TOPIC_PAGE_SIZE = 12

const loading = ref(true)
const topics = ref<TopicVO[]>([])
const featured = ref<TopicVO | null>(null)
const category = ref('')

// 专题内图片（点击卡片展开）
const selectedTopic = ref<TopicVO | null>(null)
const topicPictures = ref<API.PictureVO[]>([])
const picturesTotal = ref(0)
const picturesCurrent = ref(1)
const picturesLoading = ref(false)

/** 分类 chips：全部 + 按专题分类聚合计数 */
const categoryChips = computed(() => {
  const counter = new Map<string, number>()
  for (const topic of topics.value) {
    const key = topic.category || '其他'
    counter.set(key, (counter.get(key) ?? 0) + 1)
  }
  return [
    { label: '全部', value: '', count: topics.value.length },
    ...Array.from(counter.entries()).map(([value, count]) => ({ label: value, value, count })),
  ]
})

const visibleTopics = computed(() =>
  category.value ? topics.value.filter((t) => (t.category || '其他') === category.value) : topics.value,
)

function setCategory(value: string) {
  category.value = value
}

async function openTopic(topic: TopicVO) {
  selectedTopic.value = topic
  picturesCurrent.value = 1
  await loadTopicPictures()
  document.getElementById('topic-pictures')?.scrollIntoView({ behavior: 'smooth' })
}

async function loadTopicPictures() {
  if (!selectedTopic.value?.id) return
  picturesLoading.value = true
  try {
    const res = await listTopicPictures(selectedTopic.value.id, picturesCurrent.value, TOPIC_PAGE_SIZE)
    if (res.code === 0 && res.data) {
      topicPictures.value = res.data.records ?? []
      picturesTotal.value = Number(res.data.total ?? 0)
    } else {
      topicPictures.value = []
      picturesTotal.value = 0
    }
  } catch {
    topicPictures.value = []
    picturesTotal.value = 0
  } finally {
    picturesLoading.value = false
  }
}

function onPicturesPageChange(page: number) {
  picturesCurrent.value = page
  loadTopicPictures()
}

onMounted(async () => {
  try {
    const [listRes, featuredRes] = await Promise.all([listTopicVOs(), getFeaturedTopicVO()])
    topics.value = listRes.code === 0 && listRes.data ? listRes.data : []
    featured.value = featuredRes.code === 0 ? (featuredRes.data ?? null) : null
  } catch {
    topics.value = []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
/* hero */
.topic-hero {
  position: relative;
  display: block;
  margin-top: 56px;
  border-radius: 16px;
  overflow: hidden;
  aspect-ratio: 1800 / 686;
  max-height: 420px;
  background: var(--yutu-wash);
}

.topic-hero img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.topic-hero__cap {
  position: absolute;
  inset: auto 0 0 0;
  padding: 64px 36px 28px;
  background: linear-gradient(180deg, rgba(12, 14, 16, 0) 0%, rgba(12, 14, 16, 0.62) 100%);
  color: #fff;
}

.topic-hero__kicker {
  display: block;
  font-size: 11.5px;
  letter-spacing: 0.14em;
  color: rgba(255, 255, 255, 0.72);
}

.topic-hero__title {
  display: block;
  font-family: 'Noto Serif SC', serif;
  font-size: 30px;
  font-weight: 600;
  margin-top: 6px;
  letter-spacing: 0.02em;
}

.topic-hero__meta {
  display: block;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.78);
  margin-top: 6px;
}

.topic-hero__cta {
  position: absolute;
  right: 32px;
  bottom: 30px;
  background: #fff;
  color: var(--yutu-ink);
  font-size: 13.5px;
  font-weight: 500;
  padding: 10px 22px;
  border-radius: 999px;
  transition: opacity 0.2s;
}

.topic-hero__cta:hover {
  opacity: 0.88;
}

/* chips + grid */
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.topics-grid {
  margin-top: 30px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 40px 24px;
}

.topic {
  display: block;
}

.topic__fig {
  position: relative;
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

.topic__count {
  position: absolute;
  right: 12px;
  bottom: 12px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--yutu-ink);
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 999px;
}

.topic h3 {
  font-size: 15.5px;
  font-weight: 500;
  margin-top: 14px;
}

.topic__desc {
  font-size: 13px;
  color: var(--yutu-ink-2);
  margin-top: 4px;
}

.topic__meta {
  font-size: 12.5px;
  color: var(--yutu-ink-3);
  margin-top: 2px;
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

@media (max-width: 1080px) {
  .topics-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .photo-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 720px) {
  .topics-grid,
  .photo-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .topic-hero {
    aspect-ratio: 16 / 9;
  }

  .topic-hero__title {
    font-size: 22px;
  }
}
</style>
