<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">空间分析</h2>
        <p class="page-header__desc">
          {{ space ? `空间「${space.spaceName}」` : '' }}
          的存储占用与图片构成分析（统计包含未过审图片，已删除图片不计入）
        </p>
      </div>
      <a-space>
        <a-button @click="reload">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button @click="router.push(`/space/${spaceId}`)">返回空间</a-button>
      </a-space>
    </div>

    <SpaceAnalyzePanel :key="panelKey" :query-space-id="spaceId" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ReloadOutlined } from '@ant-design/icons-vue'
import SpaceAnalyzePanel from '@/components/analyze/SpaceAnalyzePanel.vue'
import { getSpaceVOById } from '@/api/spaceController'

const route = useRoute()
const router = useRouter()

const spaceId = computed(() => String(route.params.id ?? ''))
const space = ref<API.SpaceVO>()
const panelKey = ref(0)

async function loadSpace() {
  if (!spaceId.value) return
  try {
    const res = await getSpaceVOById(spaceId.value)
    if (res.code === 0 && res.data) {
      space.value = res.data
    }
  } catch {
    // 空间信息仅用于标题展示，失败不阻塞分析面板
  }
}

function reload() {
  // 通过重建组件触发全部图表重新加载
  panelKey.value += 1
  loadSpace()
}

onMounted(loadSpace)
</script>
