<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">全空间分析</h2>
        <p class="page-header__desc">
          管理员视角的图库分析，可切换统计范围：公共图库（未归属空间的图片）或全空间（公共图库 + 所有空间）
        </p>
      </div>
      <a-space>
        <a-radio-group v-model:value="scope" button-style="solid">
          <a-radio-button value="public">公共图库</a-radio-button>
          <a-radio-button value="all">全空间</a-radio-button>
        </a-radio-group>
        <a-button @click="reload">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </a-space>
    </div>

    <!-- 空间使用排行 -->
    <a-card :bordered="false" title="空间使用排行（按存储容量）">
      <template #extra>
        <a-space>
          <a-select v-model:value="topN" size="small" style="width: 110px" :options="topNOptions" />
        </a-space>
      </template>
      <a-alert
        v-if="rankError"
        class="rank-error"
        type="error"
        show-icon
        message="排行数据加载失败"
        description="接口请求异常，请确认后端服务已包含空间分析模块（/api/space/analyze/rank）。"
      >
        <template #action>
          <a-button size="small" @click="loadRank">重试</a-button>
        </template>
      </a-alert>

      <a-table
        :columns="rankColumns"
        :data-source="rankList"
        :loading="rankLoading"
        :pagination="false"
        row-key="spaceId"
        size="middle"
      >
        <template #bodyCell="{ column, record, index }">
          <template v-if="column.key === 'rank'">
            <a-tag :color="index! < 3 ? 'gold' : 'default'" :bordered="false">
              {{ index! + 1 }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'spaceType'">
            {{ SPACE_TYPE_MAP[record.spaceType ?? 0] }}
          </template>
          <template v-else-if="column.key === 'totalSize'">
            {{ formatSize(record.totalSize) }}
          </template>
          <template v-else-if="column.key === 'usage'">
            <a-progress
              :percent="calcPercent(record.totalSize, record.maxSize)"
              size="small"
              style="max-width: 200px"
            />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="router.push(`/space/${record.spaceId}`)">
              查看
            </a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 分析面板 -->
    <SpaceAnalyzePanel
      :key="`${scope}-${panelKey}`"
      :query-all="scope === 'all'"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ReloadOutlined } from '@ant-design/icons-vue'
import SpaceAnalyzePanel from '@/components/analyze/SpaceAnalyzePanel.vue'
import { getSpaceRankAnalyze } from '@/api/spaceAnalyzeController'
import { SPACE_TYPE_MAP } from '@/constant/space'
import { calcPercent, formatSize } from '@/utils'

const router = useRouter()

const scope = ref<'public' | 'all'>('public')
const topN = ref(10)
const panelKey = ref(0)

const rankList = ref<API.SpaceRankAnalyzeResponse[]>([])
const rankLoading = ref(false)
const rankError = ref(false)

const topNOptions = [
  { label: 'Top 5', value: 5 },
  { label: 'Top 10', value: 10 },
  { label: 'Top 20', value: 20 },
  { label: 'Top 50', value: 50 },
]

const rankColumns = [
  { title: '#', key: 'rank', width: 70 },
  { title: '空间名称', dataIndex: 'spaceName', key: 'spaceName' },
  { title: '类型', key: 'spaceType', width: 110 },
  { title: '图片数量', dataIndex: 'totalCount', key: 'totalCount', width: 110 },
  { title: '已用容量', key: 'totalSize', width: 130 },
  { title: '容量使用率', key: 'usage', width: 220 },
  { title: '操作', key: 'action', width: 90 },
]

async function loadRank() {
  rankLoading.value = true
  rankError.value = false
  try {
    const res = await getSpaceRankAnalyze({ topN: topN.value })
    if (res.code === 0 && res.data) {
      rankList.value = res.data
    } else {
      rankList.value = []
      rankError.value = true
    }
  } catch {
    rankList.value = []
    rankError.value = true
  } finally {
    rankLoading.value = false
  }
}

function reload() {
  panelKey.value += 1
  loadRank()
}

onMounted(loadRank)
watch(topN, loadRank)
</script>

<style scoped>
.rank-error {
  margin-bottom: 16px;
  border-radius: 10px;
}
</style>
