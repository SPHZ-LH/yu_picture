<template>
  <div class="page-container">
    <a-card :bordered="false">
      <template #title>
        <a-space>
          <span>编辑图片</span>
          <span v-if="picture" class="edit-title__name">{{ picture.name || '未命名' }}</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-button @click="resetEdits">重置</a-button>
          <a-button @click="router.back()">取消</a-button>
          <a-button type="primary" :loading="submitting" @click="submit">保存</a-button>
        </a-space>
      </template>

      <a-spin :spinning="loading">
        <template v-if="picture">
          <a-row :gutter="24">
            <!-- 裁剪画布 -->
            <a-col :xs="24" :lg="15">
              <div
                ref="wrapEl"
                class="cropper-wrap"
                :style="wrapStyle"
                @pointerdown="cropTouched = true"
                @pointermove="refreshCropPixel"
                @pointerup="refreshCropPixel"
                @wheel.capture="onWrapWheel"
              >
                <VueCropper
                  v-if="mountReady"
                  ref="cropperRef"
                  :key="cropperKey"
                  :img="displaySrc"
                  :auto-crop="true"
                  :auto-crop-width="autoCropSize?.w ?? 0"
                  :auto-crop-height="autoCropSize?.h ?? 0"
                  :can-scale="true"
                  :center-box="true"
                  :can-move="true"
                  :can-move-box="true"
                  :fixed="ratioMode !== 'free'"
                  :fixed-number="fixedNumber"
                  :max-img-size="4096"
                  mode="contain"
                  @img-load="onImgLoad"
                />
                <!-- 角标显示"点保存会得到的"真实像素：组件自带的角标是显示尺寸（1920×1080 的图在容器里只有 726×408 宽），
                     和"当前画面"对不上；这里连"是否真会裁剪"也一并说清，默认占位框不算裁剪 -->
                <div v-if="cropPixel" class="cropper-size">
                  {{ cropPixel.cropped ? '裁剪' : '整图' }}
                  {{ cropPixel.w }} × {{ cropPixel.h }}
                  <span v-if="outputPixel" class="cropper-size__out">
                    → 输出 {{ outputPixel.w }} × {{ outputPixel.h }}
                  </span>
                </div>
              </div>
              <a-alert
                class="edit-tip"
                type="info"
                show-icon
                message="旋转、翻转会重置裁剪框。保存时按当前所见画面处理原图。"
              />
            </a-col>

            <!-- 操作面板 -->
            <a-col :xs="24" :lg="9">
              <div class="edit-panel">
                <div class="edit-panel__section">
                  <div class="edit-panel__label">旋转</div>
                  <a-space>
                    <a-button @click="rotateBy(-90)">
                      <template #icon><RotateLeftOutlined /></template>
                      左转 90°
                    </a-button>
                    <a-button @click="rotateBy(90)">
                      <template #icon><RotateRightOutlined /></template>
                      右转 90°
                    </a-button>
                  </a-space>
                </div>

                <div class="edit-panel__section">
                  <div class="edit-panel__label">翻转</div>
                  <a-space>
                    <a-button :type="mirror === 'horizontal' ? 'primary' : 'default'" @click="toggleFlip('horizontal')">
                      水平翻转
                    </a-button>
                    <a-button :type="mirror === 'vertical' ? 'primary' : 'default'" @click="toggleFlip('vertical')">
                      垂直翻转
                    </a-button>
                  </a-space>
                </div>

                <div class="edit-panel__section">
                  <div class="edit-panel__label">裁剪比例</div>
                  <a-radio-group v-model:value="ratioMode" button-style="solid">
                    <a-radio-button value="free">自由</a-radio-button>
                    <a-radio-button value="1:1">1:1</a-radio-button>
                    <a-radio-button value="4:3">4:3</a-radio-button>
                    <a-radio-button value="16:9">16:9</a-radio-button>
                    <a-radio-button value="original">原图比例</a-radio-button>
                    <a-radio-button v-if="deviceScreen" value="device">
                      我的屏幕 {{ deviceScreen.ratioLabel }}
                    </a-radio-button>
                  </a-radio-group>
                </div>

                <div class="edit-panel__section">
                  <div class="edit-panel__label">输出宽度（px，高度等比缩放）</div>
                  <a-input-number
                    v-model:value="outputWidth"
                    :min="1"
                    :max="10000"
                    placeholder="默认不缩放"
                    class="edit-panel__width"
                  />
                </div>

                <a-descriptions :column="1" size="small" class="edit-panel__meta">
                  <a-descriptions-item label="当前画面">
                    {{ transformedSize.w }} × {{ transformedSize.h }}
                  </a-descriptions-item>
                  <a-descriptions-item label="原图尺寸">
                    {{ picture.picWidth }} × {{ picture.picHeight }}
                  </a-descriptions-item>
                  <a-descriptions-item v-if="deviceScreen" label="你的屏幕">
                    {{ deviceScreen.physicalWidth }} × {{ deviceScreen.physicalHeight }}（{{
                      deviceScreen.ratioLabel
                    }}）
                  </a-descriptions-item>
                </a-descriptions>

                <a-alert
                  v-if="picture.spaceId == null"
                  class="edit-tip"
                  type="warning"
                  show-icon
                  message="公共图库图片保存后将重新进入审核流程。"
                />
              </div>
            </a-col>
          </a-row>
        </template>
        <a-empty v-else-if="!loading" description="图片不存在或已被删除">
          <a-button type="primary" @click="router.push('/')">返回公共图库</a-button>
        </a-empty>
      </a-spin>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { RotateLeftOutlined, RotateRightOutlined } from '@ant-design/icons-vue'
import { VueCropper } from 'vue-cropper'
import 'vue-cropper/dist/index.css'
import { getPictureVOById, transformPicture } from '@/api/pictureController'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { getDeviceScreen } from '@/utils/deviceScreen'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const picture = ref<API.PictureVO>()
const loading = ref(false)
const submitting = ref(false)
const cropperRef = ref()
const wrapEl = ref<HTMLElement | null>(null)
/** 设备屏幕快照（词条见 CONTEXT.md「设备屏幕比例」）；取不到时「我的屏幕」整档隐藏 */
const deviceScreen = getDeviceScreen()
/**
 * 自动裁剪框的初始尺寸（组件坐标 = 容器像素）。
 * 组件不传这两个值时会取容器的 80%——选「我的屏幕」只裁到 1536×864 而不是 1728×1080，
 * 白扔 20% 像素（放到 2560 宽的屏上要放大 1.7 倍，明显变糊），默认框还会让"拖一下图再保存"
 * 变成静默裁掉四边。给成整幅画面大小，自动框就是"该比例下的最大范围"。
 */
const autoCropSize = ref<{ w: number; h: number } | null>(null)

/** 顺时针累计旋转（四分 turn 数 0-3），旋转烘进本地重建的预览位图，不走组件内置旋转 */
const quarterTurns = ref(0)
/** 翻转状态（在所见画面上施加），与旋转共用同一条本地 canvas 管线 */
const mirror = ref<'' | 'horizontal' | 'vertical'>('')
const ratioMode = ref<'free' | '1:1' | '4:3' | '16:9' | 'original' | 'device'>('free')
const outputWidth = ref<number | undefined>()
/** 用户是否实际拖动/调整过裁剪框：未动过时保存不视为裁剪（自动框只是默认占位） */
const cropTouched = ref(false)
/** 当前预览位图（blob url）；为空时直接显示原图 */
const blobUrl = ref('')
/** bump 一次即重挂载 vue-cropper，用于「重置」回到首次加载的干净状态 */
const cropperKey = ref(0)
/** 原图 Image 缓存，供本地镜像绘制 */
let baseImage: HTMLImageElement | null = null

/**
 * 预览位图长边上限。容器最宽约 970 CSS px，1920 覆盖到 dpr 2 仍清晰，
 * 而编码耗时降到全尺寸的 1/4.6、体积降到 1/10（vue-cropper 收到位图后还要再解码+重编码一次）。
 * 只能按原图长边取定值：改成跟随容器尺寸会让位图比例与画面内容不再同源。
 */
const PREVIEW_MAX_EDGE = 1920
/**
 * 预览位图缓存，键 `${quarterTurns}|${mirror}`，状态空间上限 4×3；
 * 记下边长是因为容器必须按位图的实际比例贴合，见 wrapSize
 */
const previewCache = new Map<string, { url: string; w: number; h: number }>()
/** 当前预览位图的边长；null 表示直接显示原图 */
const previewSize = ref<{ w: number; h: number } | null>(null)
/** 递增的重建序号：连点时后完成的结果不得覆盖更新的状态 */
let rebuildSeq = 0

const pictureId = computed(() => String(route.params.id ?? ''))

const displaySrc = computed(() => blobUrl.value || toSameOriginCosUrl(picture.value?.url ?? ''))

/** 原图尺寸：优先库表字段，缺省时退回本地缓存的 Image 尺寸 */
const originalSize = computed(() => {
  const w = Number(picture.value?.picWidth ?? 0)
  const h = Number(picture.value?.picHeight ?? 0)
  if (w > 0 && h > 0) return { w, h }
  return baseImage ? { w: baseImage.naturalWidth, h: baseImage.naturalHeight } : { w: 0, h: 0 }
})

/** 旋转后所见画面的像素尺寸（翻转不改变尺寸，90°/270° 旋转时宽高互换） */
const transformedSize = computed(() => {
  const { w, h } = originalSize.value
  return quarterTurns.value % 2 === 1 ? { w: h, h: w } : { w, h }
})

const fixedNumber = computed<[number, number]>(() => {
  switch (ratioMode.value) {
    case '1:1':
      return [1, 1]
    case '4:3':
      return [4, 3]
    case '16:9':
      return [16, 9]
    case 'original':
      return transformedSize.value.w > 0
        ? [transformedSize.value.w, transformedSize.value.h]
        : [1, 1]
    case 'device':
      return deviceScreen ? deviceScreen.ratioPair : [1, 1]
    default:
      return [1, 1]
  }
})

/**
 * 容器贴合的比例取预览位图的实际边长，而非真实像素边长：位图边长要取整，
 * 与真实比例最多差 0.08%（库里 2413×810 那张就是最坏情况），按真实比例量容器
 * 会在两侧留下不到 1px 的灰边。未编辑时位图就是原图，比例精确，退回真实像素边长。
 */
const wrapSize = computed(() => previewSize.value ?? transformedSize.value)

/**
 * 容器贴合所见画面比例：横图时四周留白直接消失（vue-cropper 的透明棋盘格
 * 只在"容器比图片大"的空隙里露出）；竖图以 60vh 为高上限并水平居中。
 */
const wrapStyle = computed(() => {
  const { w, h } = wrapSize.value
  if (!w || !h) return { height: '60vh' }
  return {
    aspectRatio: `${w} / ${h}`,
    maxWidth: `min(100%, ${((60 * w) / h).toFixed(2)}vh)`,
    margin: '0 auto',
    height: 'auto',
    minHeight: '0',
  }
})

/**
 * 裁剪框在"所见画面"真实像素下的位置与尺寸。
 * 容器坐标 → 真实像素的换算只此一处，submit 与尺寸角标共用：
 * 角标显示的就是点保存会得到的画面，两者不可能各算各的。
 * 两轴各自取比例：预览位图边长取整后与真实像素比例可差 0.08%，共用一个比例会把误差摊到纵轴。
 */
function readCropPixelRect(): { x: number; y: number; w: number; h: number } | null {
  const cropper = cropperRef.value as { getImgAxis?: () => unknown; getCropAxis?: () => unknown } | undefined
  if (!cropper?.getImgAxis || !cropper.getCropAxis) return null
  // 组件旋转后 getImgAxis 返回的是旋转后的包围盒
  const imgAxis = readAxis(cropper.getImgAxis())
  const cropAxis = readAxis(cropper.getCropAxis())
  const displayW = imgAxis.x2 - imgAxis.x1
  const displayH = imgAxis.y2 - imgAxis.y1
  const view = transformedSize.value
  if (displayW <= 0 || displayH <= 0 || view.w <= 0 || view.h <= 0) return null
  const scaleX = view.w / displayW
  const scaleY = view.h / displayH
  let x = Math.round((cropAxis.x1 - imgAxis.x1) * scaleX)
  let y = Math.round((cropAxis.y1 - imgAxis.y1) * scaleY)
  let w = Math.round((cropAxis.x2 - cropAxis.x1) * scaleX)
  let h = Math.round((cropAxis.y2 - cropAxis.y1) * scaleY)
  x = Math.max(0, Math.min(x, view.w - 1))
  y = Math.max(0, Math.min(y, view.h - 1))
  w = Math.max(1, Math.min(w, view.w - x))
  h = Math.max(1, Math.min(h, view.h - y))
  return { x, y, w, h }
}

/**
 * 点保存时实际会得到的画面区域。
 * 没动过裁剪框时后端不裁剪，结果就是整幅所见画面——组件初始那个自动框只是占位（它还只覆盖 80%），
 * 这时候按框算出来的尺寸并不是保存结果；动过框且框没盖满整幅图时才按框裁剪。
 * 角标与 submit 共用这一个函数，杜绝"显示的尺寸"与"存下来的尺寸"各算一套。
 */
function resolveSavedCrop(): { x: number; y: number; w: number; h: number; cropped: boolean } | null {
  const view = transformedSize.value
  if (view.w <= 0 || view.h <= 0) return null
  const rect = readCropPixelRect()
  if (!rect) return null
  const cropped =
    cropTouched.value && (rect.x > 0 || rect.y > 0 || rect.w < view.w || rect.h < view.h)
  return cropped ? { ...rect, cropped: true } : { x: 0, y: 0, w: view.w, h: view.h, cropped: false }
}

/** 尺寸角标：保存后会得到的真实像素尺寸 */
const cropPixel = ref<{ w: number; h: number; cropped: boolean } | null>(null)

/** 设了输出宽度且与裁剪宽不同时，最终尺寸会被缩放，角标里一并说明 */
const outputPixel = computed(() => {
  const rect = cropPixel.value
  const target = outputWidth.value
  if (!rect || !target || target === rect.w) return null
  return { w: target, h: Math.round((target * rect.h) / rect.w) }
})

/**
 * vue-cropper 拖动改裁剪框时不发事件（changeCropNow 只改内部值），
 * 所以按指针移动刷新，再挂一个低频兜底：交互结束时指针可能在容器外，
 * 只在指针事件里刷会漏掉那一次。值不变时不写 ref，避免无谓的重渲染。
 */
function refreshCropPixel() {
  const saved = resolveSavedCrop()
  const current = cropPixel.value
  if (!saved) {
    if (current) cropPixel.value = null
    return
  }
  if (!current || current.w !== saved.w || current.h !== saved.h || current.cropped !== saved.cropped) {
    cropPixel.value = { w: saved.w, h: saved.h, cropped: saved.cropped }
  }
}

/** 仅上传者本人或管理员可进入编辑 */
const canManage = computed(() => {
  const user = loginUserStore.loginUser
  if (!user?.id || !picture.value) return false
  return user.userRole === 'admin' || String(picture.value.userId) === String(user.id)
})

/**
 * 把 COS 直链改写成 /cos-file 同源代理路径（vite/nginx 转发到 COS，见 vite.config.ts）。
 * vue-cropper 要用 crossOrigin 图像 + XHR arraybuffer 读像素/EXIF，
 * 跨域直连在部分浏览器环境（缓存、本地代理、CORS 配置差异）下会静默失败。
 */
function toSameOriginCosUrl(url: string): string {
  try {
    const parsed = new URL(url, window.location.origin)
    if (parsed.hostname.endsWith('.myqcloud.com')) {
      return `/cos-file${parsed.pathname}${parsed.search}`
    }
    return url
  } catch {
    return url
  }
}

function onImgLoad(e: string) {
  if (e === 'error') {
    message.error('预览图加载失败，请刷新重试')
    return
  }
  // 初始自动裁剪框只是默认占位：组件装载过程中的事件不算"用户裁剪"
  nextTick(() => {
    cropTouched.value = false
    refreshCropPixel()
  })
}

/**
 * 缩小下限：vue-cropper 的滚轮监听挂在 window 上，且只校验裁剪框不越出图片、
 * 不校验图片是否小于容器——往外滚会把图片缩进容器里，露出遮罩"黑边"。
 * 这里在捕获阶段拦截：缩小方向且已接近下限时阻止事件冒泡（组件收不到这次缩放），
 * 并把 scale 吸附到精确下限——滚轮步长约 6.7%，只拦不吸会"跨过"下限留缝。
 */
function onWrapWheel(e: WheelEvent) {
  // 滚轮缩放会同时改变图片与裁剪框，角标跟着刷新（nextTick 后组件已改完 scale）
  nextTick(refreshCropPixel)
  const c = cropperRef.value as
    | { canScale?: boolean; scale?: number; trueWidth?: number; trueHeight?: number }
    | undefined
  if (!c?.canScale || !c.trueWidth || !c.trueHeight || !c.scale) return
  if (e.deltaY <= 0) return // 放大方向（或触控板反向），不拦截
  const wrap = e.currentTarget as HTMLElement
  const coverScale = Math.max(wrap.clientWidth / c.trueWidth, wrap.clientHeight / c.trueHeight)
  if (c.scale <= coverScale * 1.12) {
    e.preventDefault()
    e.stopPropagation()
    // 拦截的同时吸附到精确下限：滚轮步长约 6.7%，不吸会停在"差一格"的灰边上
    c.scale = coverScale
  }
}

/** 旋转不走组件内置能力：它的 rotate 监听只重画裁剪框，不更新 trueWidth/trueHeight，contain 仍按旋转前的尺寸算，容器贴合会留缝 */
async function rotateBy(delta: number) {
  quarterTurns.value = (quarterTurns.value + delta / 90 + 4) % 4
  await rebuildDisplayImage()
}

async function toggleFlip(direction: 'horizontal' | 'vertical') {
  mirror.value = mirror.value === direction ? '' : direction
  await rebuildDisplayImage()
}

/**
 * 用本地 canvas 按「先翻转、再旋转」重绘原图（与后端 imageMogr2 链顺序一致，无网络请求）。
 * 预览编码用 JPEG 并按 PREVIEW_MAX_EDGE 限边：vue-cropper 收到任何 img 都会用 outputType
 * （默认 jpeg）解码后重编码一遍，所以 PNG 的无损和透明度最终都留不住，只剩编码开销。
 */
async function rebuildDisplayImage() {
  const seq = ++rebuildSeq
  if (quarterTurns.value === 0 && mirror.value === '') {
    blobUrl.value = ''
    previewSize.value = null
    return
  }
  const key = `${quarterTurns.value}|${mirror.value}`
  const cached = previewCache.get(key)
  if (cached) {
    applyPreview(cached)
    return
  }
  const base = await ensureBaseImage()
  if (!base || seq !== rebuildSeq) return
  const sw = base.naturalWidth
  const sh = base.naturalHeight
  const ratio = Math.min(1, PREVIEW_MAX_EDGE / Math.max(sw, sh))
  const dw = Math.round(sw * ratio)
  const dh = Math.round(sh * ratio)
  const swap = quarterTurns.value % 2 === 1
  const canvas = document.createElement('canvas')
  canvas.width = swap ? dh : dw
  canvas.height = swap ? dw : dh
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  // 变换按 translate → rotate → scale 复合，作用于图片坐标，等价于先镜像后旋转
  ctx.translate(canvas.width / 2, canvas.height / 2)
  ctx.rotate((quarterTurns.value * Math.PI) / 2)
  if (mirror.value === 'horizontal') {
    ctx.scale(-1, 1)
  } else if (mirror.value === 'vertical') {
    ctx.scale(1, -1)
  }
  ctx.drawImage(base, -dw / 2, -dh / 2, dw, dh)
  const blob = await new Promise<Blob | null>((resolve) => {
    canvas.toBlob(resolve, 'image/jpeg', 0.92)
  })
  if (!blob || seq !== rebuildSeq) return
  const url = URL.createObjectURL(blob)
  const preview = { url, w: canvas.width, h: canvas.height }
  previewCache.set(key, preview)
  applyPreview(preview)
}

/** 边长与 url 一起写：容器比例和 vue-cropper 的新 img 在同一次 flush 落地 */
function applyPreview(preview: { url: string; w: number; h: number }) {
  previewSize.value = { w: preview.w, h: preview.h }
  blobUrl.value = preview.url
}

function clearPreviewCache() {
  previewCache.forEach(({ url }) => URL.revokeObjectURL(url))
  previewCache.clear()
}

function ensureBaseImage(): Promise<HTMLImageElement | null> {
  if (baseImage && baseImage.complete && baseImage.naturalWidth > 0) {
    return Promise.resolve(baseImage)
  }
  const src = toSameOriginCosUrl(picture.value?.url ?? '')
  if (!src) return Promise.resolve(null)
  return new Promise((resolve) => {
    const img = new Image()
    img.onload = () => {
      baseImage = img
      resolve(img)
    }
    img.onerror = () => resolve(null)
    img.src = src
  })
}

// vue-cropper 不监听 fixed/fixedNumber，切换比例后需手动重生成裁剪框；
// 必须 nextTick 等子组件拿到新 props 再调用，否则 goAutoCrop 读到旧比例。
// 点选比例即视为裁剪意图（保存时按该比例框裁剪）
watch(ratioMode, async (mode) => {
  if (mode === 'free') return
  await nextTick()
  const cropper = cropperRef.value as { goAutoCrop?: () => void } | undefined
  cropper?.goAutoCrop?.()
  cropTouched.value = true
  // goAutoCrop 把新框写进组件内部后才量得到，多等一拍再取
  await nextTick()
  refreshCropPixel()
})

function resetEdits() {
  quarterTurns.value = 0
  mirror.value = ''
  ratioMode.value = 'free'
  outputWidth.value = undefined
  cropTouched.value = false
  blobUrl.value = ''
  previewSize.value = null
  clearPreviewCache()
  // vue-cropper 没有可靠的"回初始态"API：rotateClear 不存在，goAutoCrop 只重画裁剪框、
  // 不还原用户的缩放与平移。重挂载组件才能真正回到首次加载的状态。
  cropperKey.value += 1
}

async function loadPicture() {
  if (!pictureId.value) return
  loading.value = true
  try {
    const res = await getPictureVOById(pictureId.value)
    if (res.code === 0 && res.data) {
      picture.value = res.data
    } else {
      picture.value = undefined
    }
  } finally {
    loading.value = false
  }
}

/** 兼容读取 vue-cropper 坐标方法（返回 {x1,y1,x2,y2}，缺失时兜底为 0） */
function readAxis(value: unknown): { x1: number; y1: number; x2: number; y2: number } {
  const axis = (value ?? {}) as Record<string, unknown>
  const num = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) ? v : 0)
  return {
    x1: num(axis.x1),
    y1: num(axis.y1),
    x2: num(axis.x2),
    y2: num(axis.y2),
  }
}

async function submit() {
  const target = picture.value
  if (!target?.id) return
  const cropper = cropperRef.value as { getImgAxis?: () => unknown; getCropAxis?: () => unknown } | undefined
  if (!cropper?.getImgAxis || !cropper.getCropAxis) {
    message.error('编辑器尚未就绪，请稍后再试')
    return
  }
  const saved = resolveSavedCrop()
  if (!saved) {
    message.error('未能获取图片显示区域，请刷新重试')
    return
  }

  const body: API.PictureTransformRequest = {
    id: target.id,
    rotate: quarterTurns.value * 90,
    flip: mirror.value || undefined,
  }
  if (saved.cropped) {
    body.cropX = saved.x
    body.cropY = saved.y
    body.cropW = saved.w
    body.cropH = saved.h
  }
  if (outputWidth.value && (!saved.cropped || outputWidth.value !== saved.w)) {
    body.outputWidth = outputWidth.value
  }
  if (quarterTurns.value === 0 && !mirror.value && !saved.cropped && !body.outputWidth) {
    message.warning('未做任何编辑')
    return
  }

  submitting.value = true
  try {
    const res = await transformPicture(body)
    if (res.code === 0 && res.data) {
      message.success('保存成功')
      router.push(`/picture/${target.id}`)
    }
  } finally {
    submitting.value = false
  }
}

/**
 * vue-cropper 只在挂载瞬间量一次容器、之后不感知尺寸变化，因此必须保证它挂载时
 * 布局已经稳定：容器盒子由 aspect-ratio 预留（与图片是否加载无关），再预解码原图
 * 让页面滚动条状态定型，最后才挂载组件——第一次测量即最终尺寸，不会有缝隙。
 */
const mountReady = ref(false)

/** 角标的低频兜底刷新（交互在容器外结束、或组件内部改框没走我们的代码路径时靠它收敛） */
let cropPixelTimer: number | undefined

onBeforeUnmount(() => {
  if (cropPixelTimer !== undefined) {
    window.clearInterval(cropPixelTimer)
  }
  // 当前预览位图也在缓存里，统一由 clearPreviewCache 释放
  clearPreviewCache()
})

onMounted(async () => {
  cropPixelTimer = window.setInterval(refreshCropPixel, 300)
  await loadPicture()
  if (picture.value && !canManage.value) {
    message.error('无权限编辑该图片')
    router.back()
    return
  }
  if (picture.value) {
    await ensureBaseImage()
    await nextTick()
    // 容器由 CSS 的 aspect-ratio 预留，此时量到的就是组件将要量到的同一个尺寸
    const rect = wrapEl.value?.getBoundingClientRect()
    if (rect && rect.width > 0 && rect.height > 0) {
      autoCropSize.value = { w: Math.floor(rect.width), h: Math.floor(rect.height) }
    }
    mountReady.value = true
  }
})
</script>

<style scoped>
.cropper-wrap {
  position: relative;
  height: 60vh;
  min-height: 360px;
  border-radius: 12px;
  background: var(--app-panel-hover, #f0f2f5);
  overflow: hidden;
}

/* 组件自带的尺寸角标是"显示尺寸"（图在容器里被缩过），换成下面按真实像素算的那个 */
.cropper-wrap :deep(.crop-info) {
  display: none;
}

.cropper-size {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
  font-size: 12px;
  line-height: 18px;
  pointer-events: none;
}

.cropper-size__out {
  opacity: 0.75;
}

/* 去掉 vue-cropper 自带的透明棋盘格：残余留白显示为纯色底板 */
.cropper-wrap :deep(.vue-cropper) {
  background-image: none;
}

.edit-title__name {
  color: var(--app-text-secondary, #999);
  font-size: 14px;
  font-weight: 400;
}

.edit-panel__section {
  margin-bottom: 20px;
}

.edit-panel__label {
  margin-bottom: 8px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.65));
}

.edit-panel__width {
  width: 200px;
}

.edit-panel__meta {
  margin-bottom: 16px;
}

.edit-tip {
  margin-top: 16px;
}
</style>
