<template>
  <div ref="chartRef" class="base-chart" :style="{ height }" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'
import { GALLERY_COLOR_PRIMARY } from '@/constant/theme'
import { useThemeStore } from '@/stores/useThemeStore'

/** 品牌 data-viz 色板：暖橙为主，黄/青做区分度，灰与暖白兜底，两套主题通用 */
const CHART_PALETTE = [
  GALLERY_COLOR_PRIMARY,
  '#ffc53d',
  '#4dd0c4',
  '#ff9e80',
  '#8a93a8',
  '#f4f5f9',
]

interface Props {
  /** ECharts 配置项 */
  option: EChartsOption
  /** 图表高度 */
  height?: string
  /** 加载状态 */
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  height: '300px',
  loading: false,
})

const themeStore = useThemeStore()
const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined
let resizeObserver: ResizeObserver | undefined

function render() {
  if (!chart) return
  // 暗色用内置 dark 主题负责坐标轴/图例/提示框配色；背景强制透明以融入面板
  chart.setOption(
    { ...props.option, color: CHART_PALETTE, backgroundColor: 'transparent' },
    true,
  )
}

function initChart() {
  if (!chartRef.value) return
  // ECharts 不支持初始化后换主题，切主题时整体销毁重建
  chart = echarts.init(chartRef.value, themeStore.isDark ? 'dark' : undefined)
  render()
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartRef.value)
}

onMounted(initChart)

watch(
  () => props.option,
  () => render(),
  { deep: true },
)

watch(
  () => themeStore.isDark,
  () => {
    resizeObserver?.disconnect()
    chart?.dispose()
    chart = undefined
    initChart()
  },
)

watch(
  () => props.loading,
  (value) => {
    if (!chart) return
    if (value) {
      chart.showLoading('default', {
        text: '加载中',
        color: GALLERY_COLOR_PRIMARY,
        textColor: themeStore.isDark ? '#8a93a8' : 'rgba(0,0,0,0.45)',
      })
    } else {
      chart.hideLoading()
    }
  },
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = undefined
})
</script>

<style scoped>
.base-chart {
  width: 100%;
}
</style>
