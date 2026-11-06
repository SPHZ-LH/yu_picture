<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">相似图搜索</h2>
        <p class="page-header__desc">上传图片、粘贴图片链接或指定图库图片，找出图库中内容相似的图片</p>
      </div>
      <a-button @click="router.back()">返回</a-button>
    </div>

    <a-card :bordered="false">
      <a-spin :spinning="searching">
        <a-upload-dragger
          v-if="!queryPreviewUrl"
          name="file"
          :show-upload-list="false"
          :custom-request="doSearchByUpload"
          accept="image/jpeg,image/png,image/webp"
        >
          <p class="search-upload__icon"><InboxOutlined /></p>
          <p class="search-upload__text">点击或拖拽图片到此处搜索</p>
          <p class="search-upload__hint">
            支持 jpg / png / webp，{{ PIC_SEARCH_MAX_SIZE_MB }}MB
            以内；查询图仅作为检索输入，不会进入图库
          </p>
        </a-upload-dragger>
        <div v-else class="search-query">
          <div class="search-query__image">
            <!-- 网络图片可能被来源站防盗链挡住预览，但后端已经抓到了它，检索不受影响 -->
            <img
              v-if="!previewFailed"
              :src="queryPreviewUrl"
              :alt="queryName"
              @error="previewFailed = true"
            />
            <span v-else class="search-query__image-fallback">预览不可用<br />不影响检索结果</span>
          </div>
          <div class="search-query__info">
            <div class="search-query__name">{{ queryName || '查询图' }}</div>
            <a-space wrap>
              <a-button type="primary" :loading="searching" @click="rerunSearch">
                <template #icon><SearchOutlined /></template>
                搜索相似图片
              </a-button>
              <a-button @click="resetQuery">换一张</a-button>
            </a-space>
            <a-typography-paragraph
              v-if="queryOriginUrl"
              :content="queryOriginUrl"
              :ellipsis="{ rows: 2, expandable: true, symbol: '展开' }"
              copyable
              class="search-query__url"
            />
          </div>
        </div>
      </a-spin>
    </a-card>

    <a-card v-if="searched" :bordered="false" title="搜索结果" class="search-results">
      <PictureList variant="wall" show-collect :data-list="results" :loading="searching" />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { UploadRequestOption } from 'ant-design-vue/es/vc-upload/interface'
import { InboxOutlined, SearchOutlined } from '@ant-design/icons-vue'
import PictureList from '@/components/PictureList.vue'
import {
  PIC_SEARCH_MAX_SIZE_BYTES,
  PIC_SEARCH_MAX_SIZE_MB,
  PIC_SEARCH_OVER_SIZE_MESSAGE,
} from '@/constant/picture'
import { pendingImageQuery } from '@/utils/pendingImageQuery'
import {
  getPictureVOById,
  searchPictureByImage,
  searchPictureByImageUrl,
  searchPictureById,
} from '@/api/pictureController'

const route = useRoute()
const router = useRouter()

const searching = ref(false)
const searched = ref(false)
const results = ref<API.PictureVO[]>([])

// 查询图预览：本地文件用 blob 地址、网络图片直接用其自身地址、库内图片用缩略图
const queryPreviewUrl = ref('')
const queryOriginUrl = ref('')
const queryName = ref('')
const queryPictureId = computed(() => String(route.query.pictureId ?? ''))

/** 本次查询图的来源，决定「搜索相似图片」重跑时该调哪个接口 */
type QuerySource =
  | { kind: 'file'; file: File }
  | { kind: 'url'; url: string }
  | { kind: 'picture'; pictureId: string }

const querySource = ref<QuerySource | null>(null)

/** 预览加载失败（多为来源站防盗链）：只影响预览，后端已抓到图，检索照常 */
const previewFailed = ref(false)

// 换查询图后要重新给预览一次机会，否则上一张的失败状态会粘住
watch(queryPreviewUrl, () => {
  previewFailed.value = false
})

/** 自己创建的 blob 预览地址，换图/离开页面时要主动释放，否则每次搜索漏一份内存 */
let ownedPreviewUrl = ''

function releasePreviewUrl() {
  if (ownedPreviewUrl) {
    URL.revokeObjectURL(ownedPreviewUrl)
    ownedPreviewUrl = ''
  }
}

function toFile(raw: string | Blob): File {
  if (raw instanceof File) return raw
  return new File([raw], 'query.png', { type: raw instanceof Blob ? raw.type : 'image/png' })
}

/** 链接没有可读的文件名时退回域名，别让标题栏空着 */
function urlName(url: string): string {
  try {
    const parsed = new URL(url)
    const last = parsed.pathname.split('/').filter(Boolean).pop() ?? ''
    return decodeURIComponent(last) || parsed.hostname
  } catch {
    return url
  }
}

/** 本地文件开搜：页面内拖拽与首页「以图搜图」带过来的文件走同一条路 */
async function startSearchWithFile(file: File) {
  if (file.size > PIC_SEARCH_MAX_SIZE_BYTES) {
    message.error(PIC_SEARCH_OVER_SIZE_MESSAGE)
    return
  }
  releasePreviewUrl()
  queryPreviewUrl.value = URL.createObjectURL(file)
  ownedPreviewUrl = queryPreviewUrl.value
  queryName.value = file.name
  queryOriginUrl.value = ''
  querySource.value = { kind: 'file', file }
  await runSearch(() => searchPictureByImage(file))
}

/** 图片链接开搜：图片由后端抓取，预览直接用该地址自身 */
async function startSearchWithUrl(url: string) {
  releasePreviewUrl()
  queryPreviewUrl.value = url
  queryOriginUrl.value = url
  queryName.value = urlName(url)
  querySource.value = { kind: 'url', url }
  await runSearch(() => searchPictureByImageUrl(url))
}

async function doSearchByUpload(options: UploadRequestOption) {
  await startSearchWithFile(toFile(options.file))
}

/** 重跑当前查询图：三种来源各走各的接口，「搜索相似图片」按钮对它们都有效 */
async function rerunSearch() {
  const source = querySource.value
  if (!source) return
  if (source.kind === 'file') {
    await runSearch(() => searchPictureByImage(source.file))
  } else if (source.kind === 'url') {
    await runSearch(() => searchPictureByImageUrl(source.url))
  } else {
    await runSearch(() => searchPictureById(source.pictureId))
  }
}

async function runSearch(request: () => Promise<API.BaseResponse<API.PictureVO[]>>) {
  searching.value = true
  searched.value = true
  try {
    const res = await request()
    // 业务失败由 request 拦截器统一提示，这里只消费成功结果
    if (res.code === 0) {
      results.value = res.data ?? []
    }
  } finally {
    searching.value = false
  }
}

function resetQuery() {
  releasePreviewUrl()
  queryPreviewUrl.value = ''
  queryOriginUrl.value = ''
  queryName.value = ''
  querySource.value = null
  results.value = []
  searched.value = false
}

onMounted(async () => {
  // 首页「以图搜图」带过来的查询图（本地文件或图片链接）：取用后立刻清空，
  // 回退/再进本页不会重搜
  const pending = pendingImageQuery.value
  if (pending) {
    pendingImageQuery.value = null
    if (pending.kind === 'file') {
      await startSearchWithFile(pending.file)
    } else {
      await startSearchWithUrl(pending.url)
    }
    return
  }
  if (!queryPictureId.value) return
  // 从图片详情跳转进来：加载该图作为检索输入并自动搜索
  const res = await getPictureVOById(queryPictureId.value)
  if (res.code === 0 && res.data) {
    releasePreviewUrl()
    queryPreviewUrl.value = res.data.thumbnailUrl || res.data.url || ''
    queryOriginUrl.value = res.data.url || ''
    queryName.value = res.data.name || ''
    querySource.value = { kind: 'picture', pictureId: queryPictureId.value }
    await rerunSearch()
  }
})

onBeforeUnmount(releasePreviewUrl)
</script>

<style scoped>
.search-upload__icon {
  margin: 32px 0 8px;
  font-size: 40px;
  color: var(--app-accent, #1677ff);
}

.search-upload__text {
  font-size: 15px;
  font-weight: 600;
}

.search-upload__hint {
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

.search-query {
  display: flex;
  gap: 20px;
  align-items: center;
}

.search-query__image {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 200px;
  height: 150px;
  padding: 8px;
  border-radius: 12px;
  background: var(--app-panel, #f0f2f5);
}

.search-query__image img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 6px;
}

/* 预览裂了也要说清「检索没受影响」，否则用户会以为整件事失败了 */
.search-query__image-fallback {
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
  line-height: 1.6;
  text-align: center;
}

.search-query__name {
  margin-bottom: 10px;
  font-size: 15px;
  font-weight: 600;
  word-break: break-all;
}

.search-query__url {
  margin-top: 10px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

.search-results {
  margin-top: 16px;
}
</style>
