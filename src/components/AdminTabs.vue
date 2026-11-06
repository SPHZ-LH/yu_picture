<template>
  <div class="admin-tabs">
    <a-tabs :active-key="activeKey" @change="handleChange">
      <a-tab-pane key="/admin/userManage" tab="用户管理" />
      <a-tab-pane key="/admin/pictureManage" tab="图片管理" />
      <a-tab-pane key="/admin/videoManage" tab="动态壁纸" />
      <a-tab-pane key="/admin/spaceManage" tab="空间管理" />
      <a-tab-pane key="/admin/analyze" tab="全空间分析" />
      <a-tab-pane key="/admin/cacheManage" tab="缓存运维" />
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const activeKey = computed(() => route.path)

function handleChange(key: string | number) {
  router.push(String(key))
}
</script>

<style scoped>
.admin-tabs {
  padding: 0 16px;
  background: var(--app-panel, #fff);
  border-radius: 12px;
}

:deep(.ant-tabs-nav) {
  margin-bottom: 0;
}
</style>
