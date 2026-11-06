<template>
  <div class="analyze-panel">
    <a-alert
      v-if="hasError"
      class="analyze-error"
      type="error"
      show-icon
      message="分析数据加载失败"
      description="空间分析接口请求异常，请确认后端服务已包含空间分析模块（/api/space/analyze/*）。"
    >
      <template #action>
        <a-button size="small" @click="reloadAll">重试</a-button>
      </template>
    </a-alert>

    <!-- 概览统计 -->
    <a-row :gutter="[16, 16]">
      <a-col :xs="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <a-statistic
            title="已用容量"
            :value="Number(usage?.usedSize ?? 0)"
            :formatter="formatSizeFormatter"
          />
          <div class="stat-card__sub">
            <a-progress
              :percent="usage?.usedSizeRatio ?? calcPercent(usage?.usedSize, usage?.maxSize)"
              size="small"
              :stroke-color="progressColor(usage?.usedSizeRatio ?? 0)"
            />
            <span class="stat-card__hint">上限 {{ formatSize(usage?.maxSize) }}</span>
          </div>
        </a-card>
      </a-col>

      <a-col :xs="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <a-statistic title="图片数量" :value="Number(usage?.usedCount ?? 0)" suffix="张" />
          <div class="stat-card__sub">
            <a-progress
              :percent="usage?.usedCountRatio ?? calcPercent(usage?.usedCount, usage?.maxCount)"
              size="small"
              :stroke-color="progressColor(usage?.usedCountRatio ?? 0)"
            />
            <span class="stat-card__hint">上限 {{ usage?.maxCount ?? 0 }} 张</span>
          </div>
        </a-card>
      </a-col>

      <a-col :xs="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <a-statistic
            title="平均单图大小"
            :value="Number(avgSize)"
            :formatter="formatSizeFormatter"
          />
          <div class="stat-card__sub">
            <span class="stat-card__hint">
              由已用容量 / 图片数量估算，含未过审图片
            </span>
          </div>
        </a-card>
      </a-col>

      <a-col :xs="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <a-statistic title="分类 / 标签种类" :value="categoryCount" suffix="类" />
          <div class="stat-card__sub">
            <span class="stat-card__hint">共覆盖 {{ tagCount }} 个标签</span>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 分类 & 标签 -->
    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :xl="12">
        <a-card :bordered="false" title="图片分类分析">
          <template #extra>
            <a-radio-group v-model:value="categoryMetric" size="small" button-style="solid">
              <a-radio-button value="count">按数量</a-radio-button>
              <a-radio-button value="totalSize">按容量</a-radio-button>
            </a-radio-group>
          </template>
          <BaseChart :option="categoryOption" height="320px" :loading="loading.category" />
        </a-card>
      </a-col>

      <a-col :xs="24" :xl="12">
        <a-card :bordered="false" title="图片标签分析">
          <template #extra>
            <a-select
              v-model:value="tagTopN"
              size="small"
              style="width: 110px"
              :options="tagTopNOptions"
            />
          </template>
          <BaseChart :option="tagOption" height="320px" :loading="loading.tag" />
        </a-card>
      </a-col>
    </a-row>

    <!-- 大小 & 趋势 -->
    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :xl="10">
        <a-card :bordered="false" title="图片大小分布">
          <BaseChart :option="sizeOption" height="320px" :loading="loading.size" />
        </a-card>
      </a-col>

      <a-col :xs="24" :xl="14">
        <a-card :bordered="false" title="上传行为趋势">
          <template #extra>
            <a-space>
              <a-radio-group v-model:value="granularity" size="small" button-style="solid">
                <a-radio-button value="day">按天</a-radio-button>
                <a-radio-button value="week">按周</a-radio-button>
                <a-radio-button value="month">按月</a-radio-button>
              </a-radio-group>
            </a-space>
          </template>
          <a-alert
            class="analyze-tip"
            type="info"
            show-icon
            :banner="false"
            message="统计区间为后端默认的最近 30 天（接口暂未约束日期入参格式，故前端的自定义时间范围未启用）"
          />
          <BaseChart :option="trendOption" height="300px" :loading="loading.trend" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseChart from '@/components/analyze/BaseChart.vue'
import {
  getSpaceCategoryAnalyze,
  getSpaceSizeAnalyze,
  getSpaceTagAnalyze,
  getSpaceUploadTrendAnalyze,
  getSpaceUsageAnalyze,
} from '@/api/spaceAnalyzeController'
import { calcPercent, formatSize, PIC_SIZE_RANGE_ORDER } from '@/utils'
import { useThemeStore } from '@/stores/useThemeStore'

// a-statistic 的 formatter 签名是 ({ value }) => VNodeTypes，在 script 里定义以便类型正确
const themeStore = useThemeStore()

const formatSizeFormatter = (v: { value: number | string }) => formatSize(Number(v.value))

interface Props {
  /** 要分析的空间 id，为空表示公共图库 / 全空间 */
  querySpaceId?: string
  /** 是否分析全空间（仅管理员生效） */
  queryAll?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  querySpaceId: undefined,
  queryAll: false,
})

/** 基础查询参数 */
const baseParams = computed<API.SpaceAnalyzeQueryRequest>(() => {
  const params: API.SpaceAnalyzeQueryRequest = {}
  if (props.querySpaceId) {
    params.querySpaceId = props.querySpaceId
  }
  if (props.queryAll) {
    params.queryAll = true
  }
  return params
})

const usage = ref<API.SpaceUsageAnalyzeResponse>()
const categoryList = ref<API.SpaceCategoryAnalyzeResponse[]>([])
const tagList = ref<API.SpaceTagAnalyzeResponse[]>([])
const sizeList = ref<API.SpaceSizeAnalyzeResponse[]>([])
const trendList = ref<API.SpaceUploadTrendAnalyzeResponse[]>([])

/** 分析接口是否出现异常（如后端未包含空间分析模块） */
const hasError = ref(false)

const loading = ref({
  usage: false,
  category: false,
  tag: false,
  size: false,
  trend: false,
})

const categoryMetric = ref<'count' | 'totalSize'>('count')
const tagTopN = ref(10)
const granularity = ref<'day' | 'week' | 'month'>('day')

const tagTopNOptions = [
  { label: 'Top 5', value: 5 },
  { label: 'Top 10', value: 10 },
  { label: 'Top 20', value: 20 },
]

const avgSize = computed(() => {
  const total = Number(usage.value?.usedSize ?? 0)
  const count = Number(usage.value?.usedCount ?? 0)
  return count > 0 ? total / count : 0
})

const categoryCount = computed(() => categoryList.value.length)
const tagCount = computed(() => tagList.value.length)

// 图表文字/网格线颜色跟随主题：暗底用暖白系，浅底用黑色系
const axisTextColor = computed(() => (themeStore.isDark ? 'rgba(244,245,249,0.72)' : 'rgba(0,0,0,0.65)'))
const axisSubTextColor = computed(() => (themeStore.isDark ? 'rgba(244,245,249,0.5)' : 'rgba(0,0,0,0.45)'))
const axisLineColor = computed(() => (themeStore.isDark ? 'rgba(244,245,249,0.14)' : 'rgba(0,0,0,0.12)'))
const splitLineColor = computed(() => (themeStore.isDark ? 'rgba(244,245,249,0.08)' : 'rgba(0,0,0,0.06)'))
const pieBorderColor = computed(() => (themeStore.isDark ? '#161a23' : '#ffffff'))

function progressColor(percent: number) {
  if (percent >= 100) return '#ff4d4f'
  if (percent >= 90) return '#faad14'
  return '#ff6b3d'
}

/** 分类分析：柱状图 */
const categoryOption = computed<EChartsOption>(() => {
  const sorted = [...categoryList.value].sort(
    (a, b) => Number(b[categoryMetric.value] ?? 0) - Number(a[categoryMetric.value] ?? 0),
  )
  const isSize = categoryMetric.value === 'totalSize'
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (value: unknown) => (isSize ? formatSize(Number(value)) : `${value} 张`),
    },
    grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: sorted.map((item) => item.category ?? '未分类'),
      axisLabel: { color: axisTextColor.value, interval: 0, rotate: sorted.length > 6 ? 30 : 0 },
      axisLine: { lineStyle: { color: axisLineColor.value } },
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: axisSubTextColor.value,
        formatter: (value: number) => (isSize ? formatSize(value) : String(value)),
      },
      splitLine: { lineStyle: { color: splitLineColor.value } },
    },
    series: [
      {
        type: 'bar',
        name: isSize ? '占用容量' : '图片数量',
        data: sorted.map((item) => Number(item[categoryMetric.value] ?? 0)),
        barMaxWidth: 48,
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: '#ffb15c' },
              { offset: 1, color: '#ff6b3d' },
            ],
          },
        },
      },
    ],
  }
})

/** 标签分析：横向柱状图 */
const tagOption = computed<EChartsOption>(() => {
  const sorted = [...tagList.value]
    .sort((a, b) => Number(b.count ?? 0) - Number(a.count ?? 0))
    .slice(0, tagTopN.value)
    .reverse()

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (value: unknown) => `${value} 张`,
    },
    grid: { left: 8, right: 32, top: 16, bottom: 8, containLabel: true },
    xAxis: {
      type: 'value',
      axisLabel: { color: axisSubTextColor.value },
      splitLine: { lineStyle: { color: splitLineColor.value } },
    },
    yAxis: {
      type: 'category',
      data: sorted.map((item) => item.tag ?? ''),
      axisLabel: { color: axisTextColor.value },
      axisLine: { lineStyle: { color: axisLineColor.value } },
    },
    series: [
      {
        type: 'bar',
        name: '图片数量',
        data: sorted.map((item) => Number(item.count ?? 0)),
        barMaxWidth: 18,
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#4dd0c4' },
              { offset: 1, color: '#ff6b3d' },
            ],
          },
        },
        label: {
          show: true,
          position: 'right',
          color: axisSubTextColor.value,
        },
      },
    ],
  }
})

/** 大小分布：环形图 */
const sizeOption = computed<EChartsOption>(() => {
  const ordered = [...sizeList.value].sort(
    (a, b) =>
      PIC_SIZE_RANGE_ORDER.indexOf(a.sizeRange ?? '') - PIC_SIZE_RANGE_ORDER.indexOf(b.sizeRange ?? ''),
  )
  const colors = ['#ff9e80', '#ff6b3d', '#ffc53d', '#4dd0c4', '#8a93a8']

  return {
    tooltip: {
      trigger: 'item',
      formatter: (params: unknown) => {
        const p = params as { name: string; value: number; percent: number }
        return `${p.name}<br/>${p.value} 张（${p.percent}%）`
      },
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      textStyle: { color: axisTextColor.value },
    },
    series: [
      {
        type: 'pie',
        radius: ['45%', '68%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: pieBorderColor.value, borderWidth: 2 },
        label: { show: true, formatter: '{b}\n{d}%', color: axisTextColor.value },
        data: ordered.map((item, index) => ({
          name: item.sizeRange ?? '未知',
          value: Number(item.count ?? 0),
          itemStyle: { color: colors[index % colors.length] },
        })),
      },
    ],
  }
})

/** 上传趋势：折线 + 柱状 */
const trendOption = computed<EChartsOption>(() => {
  const timePoints = trendList.value.map((item) => item.timePoint ?? '')
  const counts = trendList.value.map((item) => Number(item.count ?? 0))

  return {
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value: unknown) => `${value} 张`,
    },
    grid: { left: 8, right: 16, top: 32, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: timePoints,
      axisLabel: { color: axisTextColor.value },
      axisLine: { lineStyle: { color: axisLineColor.value } },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: axisSubTextColor.value },
      splitLine: { lineStyle: { color: splitLineColor.value } },
    },
    series: [
      {
        type: 'line',
        name: '上传数量',
        data: counts,
        smooth: true,
        symbolSize: 6,
        lineStyle: { width: 3, color: '#ff6b3d' },
        itemStyle: { color: '#ff6b3d' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(255,107,61,0.28)' },
              { offset: 1, color: 'rgba(255,107,61,0.02)' },
            ],
          },
        },
      },
    ],
  }
})

async function loadUsage() {
  loading.value.usage = true
  try {
    const res = await getSpaceUsageAnalyze({ ...baseParams.value })
    usage.value = res.code === 0 ? res.data : undefined
    if (res.code !== 0) hasError.value = true
  } catch {
    usage.value = undefined
    hasError.value = true
  } finally {
    loading.value.usage = false
  }
}

async function loadCategory() {
  loading.value.category = true
  try {
    const res = await getSpaceCategoryAnalyze({ ...baseParams.value })
    categoryList.value = res.code === 0 && res.data ? res.data : []
    if (res.code !== 0) hasError.value = true
  } catch {
    categoryList.value = []
    hasError.value = true
  } finally {
    loading.value.category = false
  }
}

async function loadTag() {
  loading.value.tag = true
  try {
    const res = await getSpaceTagAnalyze({ ...baseParams.value })
    tagList.value = res.code === 0 && res.data ? res.data : []
    if (res.code !== 0) hasError.value = true
  } catch {
    tagList.value = []
    hasError.value = true
  } finally {
    loading.value.tag = false
  }
}

async function loadSize() {
  loading.value.size = true
  try {
    const res = await getSpaceSizeAnalyze({ ...baseParams.value })
    sizeList.value = res.code === 0 && res.data ? res.data : []
    if (res.code !== 0) hasError.value = true
  } catch {
    sizeList.value = []
    hasError.value = true
  } finally {
    loading.value.size = false
  }
}

async function loadTrend() {
  loading.value.trend = true
  try {
    const res = await getSpaceUploadTrendAnalyze({
      ...baseParams.value,
      granularity: granularity.value,
    })
    trendList.value = res.code === 0 && res.data ? res.data : []
    if (res.code !== 0) hasError.value = true
  } catch {
    trendList.value = []
    hasError.value = true
  } finally {
    loading.value.trend = false
  }
}

/** 重新加载全部分析数据 */
function reloadAll() {
  hasError.value = false
  loadUsage()
  loadCategory()
  loadTag()
  loadSize()
  loadTrend()
}

onMounted(reloadAll)

watch(granularity, loadTrend)

defineExpose({ reloadAll })
</script>

<style scoped>
.analyze-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.analyze-error {
  border-radius: 10px;
}

.stat-card__sub {
  margin-top: 12px;
}

.stat-card__hint {
  display: block;
  margin-top: 4px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
  line-height: 1.4;
}

.analyze-tip {
  margin-bottom: 12px;
  border-radius: 8px;
}
</style>
