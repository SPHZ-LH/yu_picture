<template>
  <div class="page-container">
    <AdminTabs />

    <!-- 缓存运行状态 -->
    <a-card :bordered="false" title="缓存运行状态">
      <template #extra>
        <a-button :loading="statsLoading" @click="loadStats">
          <template #icon><ReloadOutlined /></template>
          刷新状态
        </a-button>
      </template>

      <a-spin :spinning="statsLoading">
        <a-row :gutter="16">
          <a-col :span="8">
            <a-statistic
              title="图片缓存命中率"
              :value="percent(stats.pictureLocal?.hitRate)"
              suffix="%"
              :value-style="{ color: 'var(--app-accent, #1677ff)' }"
            />
            <div class="stat-detail">
              命中 {{ stats.pictureLocal?.hitCount ?? 0 }} / 未命中
              {{ stats.pictureLocal?.missCount ?? 0 }} · 当前缓存
              {{ stats.pictureLocal?.size ?? 0 }} 条
            </div>
          </a-col>
          <a-col :span="8">
            <a-statistic
              title="作者缓存命中率"
              :value="percent(stats.userLocal?.hitRate)"
              suffix="%"
              :value-style="{ color: 'var(--app-accent, #1677ff)' }"
            />
            <div class="stat-detail">
              命中 {{ stats.userLocal?.hitCount ?? 0 }} / 未命中
              {{ stats.userLocal?.missCount ?? 0 }} · 当前缓存
              {{ stats.userLocal?.size ?? 0 }} 条
            </div>
          </a-col>
          <a-col :span="8">
            <a-statistic
              title="Redis（二级缓存）"
              :value="stats.l2Circuit?.open ? '熔断中' : '正常'"
              :value-style="{
                color: stats.l2Circuit?.open ? '#cf1322' : '#3f8600',
              }"
            />
            <div class="stat-detail">
              连续失败 {{ stats.l2Circuit?.consecutiveFailures ?? 0 }} 次
              <a-tag v-if="stats.l2Circuit?.open" color="error" class="stat-tag">
                缓存已退化为直查数据库
              </a-tag>
            </div>
          </a-col>
        </a-row>
      </a-spin>
    </a-card>

    <!-- 手动失效缓存 -->
    <a-card :bordered="false" title="手动刷新（失效）缓存">
      <a-alert
        class="evict-tip"
        type="info"
        show-icon
        message="按 key 精确失效：绕过应用直接改库后，缓存最长会脏 30 分钟且重启无效，用此接口立即清除指定图片/作者的缓存。"
      />

      <a-form layout="inline" :model="formState" @finish="handleEvict">
        <a-form-item label="缓存类型" required>
          <a-select
            v-model:value="formState.type"
            style="width: 160px"
            :options="cacheTypeOptions"
          />
        </a-form-item>
        <a-form-item :label="formState.type === 'picture' ? '图片 ID' : '用户 ID'" required>
          <a-input
            v-model:value="formState.id"
            placeholder="雪花 id，可从图片/用户管理页复制"
            allow-clear
            style="width: 280px"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" danger :loading="evictLoading" html-type="submit">
            <template #icon><ClearOutlined /></template>
            清除缓存
          </a-button>
        </a-form-item>
      </a-form>

      <a-descriptions
        v-if="evictResult"
        class="evict-result"
        bordered
        size="small"
        :column="2"
        title="执行结果"
      >
        <a-descriptions-item label="目标">
          {{ evictResult.type === 'picture' ? '图片' : '作者' }} #{{ evictResult.id }}
        </a-descriptions-item>
        <a-descriptions-item label="一级缓存（本地）">
          <a-tag :color="evictResult.localRemoved ? 'success' : 'default'">
            {{ evictResult.localRemoved ? '已清除' : '本来没有缓存' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="二级缓存（Redis）">
          <a-tag :color="evictResult.redisRemoved ? 'success' : 'default'">
            {{ evictResult.redisRemoved ? '已清除' : '本来没有缓存' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="Redis 是否被跳过">
          <a-tag :color="evictResult.redisSkipped ? 'error' : 'success'">
            {{ evictResult.redisSkipped ? '熔断跳过，恢复后需重新执行' : '正常执行' }}
          </a-tag>
        </a-descriptions-item>
      </a-descriptions>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ClearOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import AdminTabs from '@/components/AdminTabs.vue'
import { evictCache, getCacheStats } from '@/api/cacheController'

const stats = ref<API.CacheStats>({})
const statsLoading = ref(false)
const evictLoading = ref(false)
const evictResult = ref<API.CacheEvictResult>()

const cacheTypeOptions = [
  { label: '图片缓存', value: 'picture' },
  { label: '作者缓存', value: 'user' },
]

const formState = reactive<API.CacheEvictRequest>({
  type: 'picture',
  id: undefined,
})

/** 命中率 0~1 转百分比（保留 1 位小数） */
function percent(rate?: number) {
  return rate === undefined ? 0 : Math.round(rate * 1000) / 10
}

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await getCacheStats()
    stats.value = res.data ?? {}
  } finally {
    statsLoading.value = false
  }
}

async function handleEvict() {
  const id = (formState.id ?? '').trim()
  // 雪花 id 超出 JS 安全整数范围，全程按字符串传输，只做格式校验
  if (!/^\d+$/.test(id) || BigInt(id) <= 0n) {
    message.warning('请输入正确的 ID（正整数）')
    return
  }
  evictLoading.value = true
  try {
    const res = await evictCache({ type: formState.type, id })
    // 业务失败（未登录/参数错误等）由 request 拦截器统一弹出提示，这里只处理成功分支
    if (res.code === 0 && res.data) {
      evictResult.value = res.data
      message.success('缓存失效操作已执行')
      await loadStats()
    }
  } finally {
    evictLoading.value = false
  }
}

onMounted(loadStats)
</script>

<style scoped>
.stat-detail {
  margin-top: 8px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

.stat-tag {
  margin-left: 8px;
}

.evict-tip {
  margin-bottom: 16px;
}

.evict-result {
  margin-top: 24px;
  max-width: 720px;
}
</style>
