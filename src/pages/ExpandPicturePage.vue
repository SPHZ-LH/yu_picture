<template>
  <div class="page-container expand-page">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">画面扩展</h2>
        <p class="page-header__desc">AI 在原图边界外补全像素，把画幅扩展到目标宽高比</p>
      </div>
      <a-button @click="router.push(`/picture/${pictureId}`)">返回图片详情</a-button>
    </div>

    <a-spin :spinning="loadingPicture">
      <div class="expand-stack">
        <a-card class="expand-card" :bordered="false">
          <div class="expand-head">
            <h3>原图与目标画幅</h3>
            <p>扩展为付费生成，每次提交消耗一次生成额度</p>
          </div>
          <div class="expand-preview">
            <img v-if="picture?.url" :src="picture.url" :alt="picture?.name" />
          </div>
          <p v-if="deviceScreen" class="expand-screen">
            你的屏幕 {{ deviceScreen.physicalWidth }} × {{ deviceScreen.physicalHeight }}（{{
              deviceScreen.ratioLabel
            }}）
          </p>
          <div class="expand-ratios">
            <button
              v-if="deviceScreen"
              type="button"
              class="ratio-chip ratio-chip--device"
              :class="{ 'ratio-chip--on': isDeviceRatioSelected }"
              :disabled="isDeviceRatioBlocked"
              :title="
                isDeviceRatioBlocked
                  ? '原图已经是这个画幅，服务端不支持扩展成相同比例'
                  : matchedDeviceRatio
                    ? '与下方目标画幅同一档，只是按你的设备算出来的'
                    : '服务端没有这个预设比例，会按像素补边把画布扩展成它'
              "
              @click="selectDeviceRatio"
            >
              我的屏幕 {{ deviceScreen.ratioLabel }}
              <span v-if="isDeviceRatioBlocked" class="ratio-chip__note">已是当前画幅</span>
            </button>
            <button
              v-for="item in RATIOS"
              :key="item.value"
              type="button"
              class="ratio-chip"
              :class="{ 'ratio-chip--on': ratio === item.value }"
              :disabled="isSourceRatio(item.value)"
              :title="
                isSourceRatio(item.value) ? '原图已经是这个画幅，服务端不支持扩展成相同比例' : ''
              "
              @click="ratio = item.value"
            >
              {{ item.label }}
              <span v-if="isSourceRatio(item.value)" class="ratio-chip__note">已是当前画幅</span>
            </button>
          </div>
          <p v-if="ratioHint" class="expand-hint">{{ ratioHint }}</p>
          <a-button type="primary" :loading="submitting" :disabled="polling || !ratio" @click="submit">
            开始生成
          </a-button>
        </a-card>

        <a-card v-if="taskId" class="expand-card" :bordered="false">
          <div class="expand-head">
            <h3>生成结果</h3>
            <p v-if="polling">通常需要数十秒，可以稍后回到本页查看</p>
            <p v-else-if="status === 'SUCCEEDED'">对比确认后，再决定是否设为当前展示内容</p>
          </div>
          <div v-if="polling" class="expand-waiting">
            <a-spin />
            <span>生成中…</span>
          </div>
          <div v-else-if="status === 'FAILED'" class="expand-failed">
            <p>{{ failMessage || '生成失败，请重试' }}</p>
            <a-button @click="submit">重试</a-button>
          </div>
          <template v-else-if="status === 'SUCCEEDED'">
            <div class="expand-compare">
              <figure>
                <img :src="picture?.url" alt="原图" />
                <figcaption>原图</figcaption>
              </figure>
              <figure>
                <img :src="resultUrl" alt="扩展结果" />
                <figcaption>扩展结果 {{ ratio }}</figcaption>
              </figure>
            </div>
            <div class="expand-save">
              <a-button type="primary" :loading="saving" @click="save">
                保存为当前展示内容
              </a-button>
              <p v-if="!picture?.spaceId" class="expand-save__note">
                保存后公共图库的这张图片将重新进入审核
              </p>
            </div>
          </template>
        </a-card>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  getExpandResult,
  getPictureVOById,
  saveExpand,
  submitExpand,
} from '@/api/pictureController'
import { getDeviceScreen, isSameRatio, parseRatioLabel } from '@/utils/deviceScreen'

const RATIOS = [
  { value: '1:1', label: '1:1 方图' },
  { value: '4:3', label: '4:3 横屏' },
  { value: '3:4', label: '3:4 竖屏' },
  { value: '16:9', label: '16:9 宽屏' },
  { value: '9:16', label: '9:16 手机竖屏' },
]

const POLL_MS = 3000
// 轮询上限 2 分钟；超时后提示稍后重试（后端无状态，不留残余）
const POLL_LIMIT = (2 * 60 * 1000) / POLL_MS

const route = useRoute()
const router = useRouter()
const pictureId = computed(() => String(route.params.id ?? ''))
const storageKey = computed(() => `expand-task-${pictureId.value}`)

/** 设备屏幕快照（词条见 CONTEXT.md「设备屏幕比例」）：用来把默认画幅定成"我这块屏的形状" */
const deviceScreen = getDeviceScreen()

const picture = ref<API.PictureVO>()
const loadingPicture = ref(true)
/** 目标画幅：不再写死默认值，装载后由设备屏幕比例推导（见 pickDefaultRatio） */
const ratio = ref('')
const submitting = ref(false)
const saving = ref(false)
const taskId = ref('')
const status = ref('')
const resultUrl = ref('')
const failMessage = ref('')
const polling = ref(false)

/** 原图当前画幅；库表尺寸缺失时返回 0（视为未知，此时不禁用任何档位） */
const sourceRatio = computed(() => {
  const w = Number(picture.value?.picWidth ?? 0)
  const h = Number(picture.value?.picHeight ?? 0)
  return w > 0 && h > 0 ? w / h : 0
})

/** 设备屏幕比例命中的档位（五档都不是就是空） */
const matchedDeviceRatio = computed(() => {
  if (!deviceScreen) {
    return ''
  }
  const hit = RATIOS.find((item) => {
    const value = parseRatioLabel(item.value)
    return value != null && isSameRatio(deviceScreen.ratio, value)
  })
  return hit?.value ?? ''
})

/** 该档位是否等于原图画幅：服务端会判 FAILED，所以置灰不可选（docs/adr/0010） */
function isSourceRatio(value: string): boolean {
  const target = parseRatioLabel(value)
  return target != null && isSameRatio(sourceRatio.value, target)
}

/**
 * 「我的屏幕」要扩展到的画幅：
 * 设备比例落在服务端五档内就用那一档（走 output_ratio，画质路径最短），
 * 落在外面（16:10、19.5:9 这类）就用设备比例本身——后端会改用像素偏移把画布补成这个比例，
 * 因此"我的屏幕"不再有"扩图不支持"的死路（见 docs/adr/0010）。
 */
const deviceRatioTarget = computed(() => matchedDeviceRatio.value || deviceScreen?.ratioLabel || '')

/** 目标画幅与原图相同时扩图没有可补的边界，服务端两种模式都会拒，档位置灰 */
const isDeviceRatioBlocked = computed(
  () => !!deviceRatioTarget.value && isSourceRatio(deviceRatioTarget.value),
)

/** 默认画幅 = 设备屏幕比例；与源图同比例时不给默认值，必须另选（否则必然是一单失败） */
function pickDefaultRatio(): string {
  if (!deviceRatioTarget.value || isDeviceRatioBlocked.value) {
    return ''
  }
  return deviceRatioTarget.value
}

/** 预选/不可选的原因说明：只在有话可说时显示，压住一行不占位 */
const ratioHint = computed(() => {
  if (!ratio.value) {
    if (deviceRatioTarget.value) {
      return `你的屏幕比例 ${deviceScreen?.ratioLabel} 与原图相同，扩图需要另选一个目标画幅`
    }
    return '请选择目标画幅'
  }
  if (deviceScreen && ratio.value === deviceRatioTarget.value) {
    return matchedDeviceRatio.value
      ? `已按你的屏幕比例 ${deviceScreen.ratioLabel} 预选`
      : `已按你的屏幕比例 ${deviceScreen.ratioLabel} 预选，该比例不在固定档位内，会按像素补边扩展成它`
  }
  return ''
})

/** 「我的屏幕」档位是否正是当前选中的画幅（它与下方五档共用同一套选项语义） */
const isDeviceRatioSelected = computed(
  () => !!deviceRatioTarget.value && ratio.value === deviceRatioTarget.value,
)

/** 点「我的屏幕」= 选中它对应的画幅；与原图同比例时按钮禁用，点不到 */
function selectDeviceRatio() {
  if (deviceRatioTarget.value && !isDeviceRatioBlocked.value) {
    ratio.value = deviceRatioTarget.value
  }
}

let pollTimer: number | undefined
let pollCount = 0

function stopPolling() {
  if (pollTimer !== undefined) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
  polling.value = false
}

function startPolling(tid: string) {
  stopPolling()
  pollCount = 0
  polling.value = true
  status.value = 'RUNNING'
  pollTimer = window.setInterval(async () => {
    pollCount += 1
    const res = await getExpandResult(tid)
    if (res.code === 0 && res.data) {
      status.value = res.data.status ?? 'RUNNING'
      if (status.value === 'SUCCEEDED') {
        resultUrl.value = res.data.imageUrl ?? ''
        stopPolling()
        return
      }
      if (status.value === 'FAILED') {
        failMessage.value = res.data.message ?? ''
        stopPolling()
        return
      }
    }
    if (pollCount >= POLL_LIMIT) {
      stopPolling()
      status.value = 'FAILED'
      failMessage.value = '生成比预期久，请稍后重试'
    }
  }, POLL_MS)
}

async function submit() {
  if (!ratio.value) {
    return
  }
  submitting.value = true
  try {
    const res = await submitExpand({ pictureId: pictureId.value, outputRatio: ratio.value })
    if (res.code === 0 && res.data) {
      taskId.value = res.data
      // 刷新后可凭 sessionStorage 续轮询（docs/adr/0010）；连同画幅一起存，
      // 否则刷新后结果图的说明会标成当前选中的档位而不是这一单实际用的
      sessionStorage.setItem(
        storageKey.value,
        JSON.stringify({ taskId: res.data, ratio: ratio.value }),
      )
      resultUrl.value = ''
      failMessage.value = ''
      startPolling(res.data)
    }
  } finally {
    submitting.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const res = await saveExpand({ pictureId: pictureId.value, taskId: taskId.value })
    if (res.code === 0) {
      message.success('已保存')
      sessionStorage.removeItem(storageKey.value)
      router.push(`/picture/${pictureId.value}`)
    }
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const res = await getPictureVOById(pictureId.value)
    if (res.code === 0) {
      picture.value = res.data
    }
    // 原图尺寸到手后才能判断"默认画幅是否与原图相同"，预选放在这里而不是 setup
    ratio.value = pickDefaultRatio()
    // 续接刷新前的任务
    const pending = sessionStorage.getItem(storageKey.value)
    if (pending) {
      // 只认新格式 { taskId, ratio }；更早的纯 taskId 值无法解析，忽略即可
      try {
        const parsed = JSON.parse(pending) as { taskId?: string; ratio?: string }
        if (parsed?.taskId) {
          taskId.value = parsed.taskId
          if (parsed.ratio) {
            ratio.value = parsed.ratio
          }
          startPolling(parsed.taskId)
        }
      } catch {
        sessionStorage.removeItem(storageKey.value)
      }
    }
  } finally {
    loadingPicture.value = false
  }
})

onBeforeUnmount(stopPolling)
</script>

<style scoped>
.expand-page {
  max-width: 880px;
  margin: 0 auto;
}

.expand-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.expand-card :deep(.ant-card-body) {
  padding: 24px;
}

.expand-head {
  margin-bottom: 16px;
}

.expand-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text);
}

.expand-head p {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.expand-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  border-radius: 12px;
  background: var(--app-panel-hover);
}

.expand-preview img {
  max-height: 320px;
  max-width: 100%;
  object-fit: contain;
  border-radius: 8px;
}

.expand-screen {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--app-text-secondary);
}

/* 虚线边框表示这一档是按你的设备算出来的，不是服务端固定档位 */
.ratio-chip--device {
  border-style: dashed;
}

.expand-ratios {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0;
}

.ratio-chip {
  padding: 6px 14px;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: var(--app-chip-bg);
  color: var(--app-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition:
    color 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease;
}

.ratio-chip:not(:disabled):hover {
  background: var(--app-chip-bg-hover);
}

.ratio-chip:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.ratio-chip__note {
  margin-left: 6px;
  font-size: 11px;
  color: var(--app-text-secondary);
}

.expand-hint {
  margin: -6px 0 14px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.ratio-chip--on {
  border-color: var(--app-accent);
  background: var(--app-accent-soft);
  color: var(--app-accent);
}

.ratio-chip:focus-visible {
  outline: 2px solid var(--app-accent);
  outline-offset: 2px;
}

.expand-waiting {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 0;
  color: var(--app-text-secondary);
}

.expand-failed {
  padding: 24px 0;
  text-align: center;
}

.expand-failed p {
  margin: 0 0 12px;
  color: var(--app-text-secondary);
}

.expand-compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.expand-compare figure {
  margin: 0;
}

.expand-compare img {
  width: 100%;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: var(--app-panel-hover);
}

.expand-compare figcaption {
  margin-top: 6px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.expand-save {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.expand-save__note {
  margin: 0;
  font-size: 12px;
  color: var(--app-text-secondary);
}

@media (max-width: 640px) {
  .expand-compare {
    grid-template-columns: 1fr;
  }
}
</style>
