<template>
  <div class="page-container">
    <!-- 解析中：这一页的唯一职责是把人送到那一个私有空间，不该有可交互内容 -->
    <div v-if="!failed" class="private-space-resolving">
      <a-spin size="large" tip="正在打开我的私有空间…" />
    </div>

    <a-result v-else status="warning" title="没有找到你的私有空间" :sub-title="reason">
      <template #extra>
        <a-button type="primary" @click="resolve">重试</a-button>
        <a-button @click="router.push('/my_space')">去看团队空间</a-button>
      </template>
    </a-result>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listMySpace } from '@/api/spaceController'
import { SPACE_TYPE_ENUM } from '@/constant/space'

/**
 * 私有空间每人恰好一个，所以导航上给它的是一个直达入口而不是列表页。
 * 空间 id 只有查了才知道，这一页负责查完就 replace 过去——用 replace 不用 push，
 * 免得用户点后退又被弹回这个中转页。
 */
const route = useRoute()
const router = useRouter()
const failed = ref(false)
const reason = ref('')

async function resolve() {
  failed.value = false
  try {
    const res = await listMySpace()
    const spaces = res.code === 0 && res.data ? res.data : []
    const privateSpace = spaces.find((space) => space.spaceType === SPACE_TYPE_ENUM.PRIVATE)
    if (privateSpace?.id) {
      // 旧 /my_collect 链接经 redirect 带 view=collect 进来，透传给空间详情直达收藏段
      const viewSuffix = route.query.view === 'collect' ? '?view=collect' : ''
      await router.replace(`/space/${privateSpace.id}${viewSuffix}`)
      return
    }
    // 注册与建私有空间是同一事务，正常不会走到这里；真走到了说明数据被动过
    reason.value = '注册时应当自动创建私有空间，当前账号下没有找到。请联系管理员检查数据。'
    failed.value = true
  } catch {
    reason.value = '接口请求异常，请确认后端服务已启动。'
    failed.value = true
  }
}

onMounted(resolve)
</script>

<style scoped>
.private-space-resolving {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 40vh;
}
</style>
