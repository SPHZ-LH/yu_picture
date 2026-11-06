/**
 * 动态壁纸的封面抽取与 mp4 结构探测。
 *
 * 封面必须按视频原始帧尺寸 1:1 绘制：后端把数据万象对封面的实测宽高直接当作视频宽高，
 * 这是"分辨率不采信客户端自报"的前提（见 docs/adr/0011）。降分辨率绘制会让 4K 视频
 * 被误判成 2K，所以宁可让封面大一些，靠逐级降质把它压进上限，列表侧再用
 * imageMogr2 的 URL 参数取小图。
 */

/** 封面体积上限（字节），与后端 VideoServiceImpl.MAX_COVER_SIZE 对齐 */
const MAX_COVER_BYTES = 5 * 1024 * 1024

/** JPEG 质量档位，压不进上限就降下一档 */
const COVER_QUALITY_STEPS = [0.85, 0.75, 0.62, 0.5]

const METADATA_TIMEOUT_MS = 15000
const SEEK_TIMEOUT_MS = 8000

/** 探测 mp4 盒结构时读取的头部字节数，够覆盖绝大多数 moov 前置/后置的情况 */
const PROBE_BYTES = 256 * 1024

/**
 * 帧"有内容"的方差下限，只用来在多个候选时间点里挑一张更好看的封面，
 * **不作为解码成败的判据**：白底线稿、纯色渐变这类极简壁纸的方差天然接近 0，
 * 拿它当闸门会把正当内容整批拒掉（实测一条 4K 动漫线稿全片方差为 0）。
 */
const MIN_FRAME_VARIANCE = 8

/** 采样结果：alpha 全 0 说明 drawImage 根本没画上去（解码器没交帧） */
interface FrameSample {
  decoded: boolean
  variance: number
}

export interface VideoCoverResult {
  /** 封面文件，带文件名（后端按扩展名做白名单校验） */
  coverFile: File
  /** 时长（秒，向下取整） */
  duration: number
  width: number
  height: number
  /** moov 是否排在 mdat 之前；null 表示头部没读全，无法判定 */
  webOptimized: boolean | null
}

function waitFor<T extends Event>(
  target: EventTarget,
  event: string,
  timeoutMs: number,
  onTimeout: () => Error,
): Promise<T> {
  return new Promise((resolve, reject) => {
    const onDone = (e: Event) => {
      cleanup()
      resolve(e as T)
    }
    const onError = () => {
      cleanup()
      reject(onTimeout())
    }
    const timer = setTimeout(onError, timeoutMs)
    target.addEventListener(event, onDone, { once: true })
    target.addEventListener('error', onError, { once: true })
    function cleanup() {
      clearTimeout(timer)
      target.removeEventListener(event, onDone)
      target.removeEventListener('error', onError)
    }
  })
}

/** 采样中心区域：alpha 判定"帧到底有没有画上来"，方差只用来挑好看的封面 */
function sampleFrame(ctx: CanvasRenderingContext2D, width: number, height: number): FrameSample {
  const sampleW = Math.min(64, width)
  const sampleH = Math.min(64, height)
  const { data } = ctx.getImageData((width - sampleW) / 2, (height - sampleH) / 2, sampleW, sampleH)
  let sum = 0
  let sumSq = 0
  let alphaSum = 0
  const count = data.length / 4
  for (let i = 0; i < data.length; i += 4) {
    const luma = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2]
    sum += luma
    sumSq += luma * luma
    alphaSum += data[i + 3]
  }
  const mean = sum / count
  return { decoded: alphaSum / count > 250, variance: sumSq / count - mean * mean }
}

/**
 * 依次尝试的取帧时间点。
 * <p>
 * 只取 0.1s 会被开头的黑场淡入打死（导出软件默认留淡入），所以从前往后退着找；
 * 反过来只取中点又会命中字幕卡或转场，所以第一档仍然靠前。
 */
function seekPoints(duration: number): number[] {
  const points = [0.1, duration * 0.25, duration * 0.5, duration * 0.75, Math.max(0, duration - 0.2)]
  const seen: number[] = []
  for (const p of points) {
    const clamped = Math.min(Math.max(p, 0), Math.max(0, duration - 0.05))
    if (!seen.some((s) => Math.abs(s - clamped) < 0.05)) {
      seen.push(clamped)
    }
  }
  return seen
}

/**
 * seek 到指定时间点。
 * <p>
 * 不等 requestVideoFrameCallback：实测它对不在文档流里的 video 元素根本不触发
 * （等满整个超时），白白多花 500ms/次；`seeked` + 后面按 alpha 判帧足够。
 * 注意别拿"当前值"再 seek 一次——Chrome 对无变化的 currentTime 不发 seeked，会撞满超时。
 */
async function seekTo(video: HTMLVideoElement, time: number): Promise<void> {
  const seeked = waitFor(video, 'seeked', SEEK_TIMEOUT_MS, () => new Error('该时间点取不到画面'))
  video.currentTime = time
  await seeked
}

/** 把本地文件解出一帧有内容的画面，产出封面与真实分辨率 */
export async function extractVideoCover(file: File): Promise<VideoCoverResult> {
  const objectUrl = URL.createObjectURL(file)
  const video = document.createElement('video')
  video.preload = 'auto'
  video.muted = true
  video.playsInline = true
  try {
    video.src = objectUrl
    await waitFor(
      video,
      'loadedmetadata',
      METADATA_TIMEOUT_MS,
      () =>
        new Error(
          `浏览器无法解析该视频（media error code=${video.error?.code ?? '未知'}），` +
            'H.265/HEVC 需要转成 H.264 才能上传',
        ),
    )
    const width = video.videoWidth
    const height = video.videoHeight
    const duration = video.duration
    if (!width || !height || !Number.isFinite(duration) || duration <= 0) {
      throw new Error('未能读取到视频的分辨率和时长，浏览器可能不支持该编码格式')
    }

    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      throw new Error('当前浏览器不支持 canvas 绘制')
    }

    // 与取帧无关，先做一次就好：读的是文件头部，不依赖 media 元素
    const webOptimized = await probeFaststart(file)
    const points = seekPoints(duration)
    let fallback: Blob | null = null
    const tried: string[] = []
    for (const point of points) {
      try {
        await seekTo(video, point)
      } catch {
        tried.push(`${point.toFixed(1)}s 取不到`)
        continue
      }
      ctx.drawImage(video, 0, 0, width, height)
      const sample = sampleFrame(ctx, width, height)
      if (!sample.decoded) {
        tried.push(`${point.toFixed(1)}s 无帧`)
        continue
      }
      const blob = await encodeWithinBudget(canvas)
      if (!blob) {
        throw new Error('封面压不进 5MB 上限，请压缩视频后重试')
      }
      if (sample.variance >= MIN_FRAME_VARIANCE) {
        return toResult(blob)
      }
      // 整条视频都很平（线稿、纯色极简）时会走到这里：留着第一张解出来的帧兜底
      fallback ??= blob
      tried.push(`${point.toFixed(1)}s 方差${Math.round(sample.variance)}`)
    }
    if (fallback) {
      return toResult(fallback)
    }
    throw new Error(
      `浏览器解不出该视频的任何一帧（${tried.join('，')}）。` +
        'H.265/HEVC 或异常 edit list 的 mp4 会这样，请转成 H.264 后重试',
    )

    function toResult(coverBlob: Blob): VideoCoverResult {
      return {
        coverFile: new File([coverBlob], 'cover.jpg', { type: 'image/jpeg' }),
        duration: Math.floor(duration),
        width,
        height,
        webOptimized,
      }
    }
  } finally {
    video.removeAttribute('src')
    video.load()
    URL.revokeObjectURL(objectUrl)
  }
}

async function encodeWithinBudget(canvas: HTMLCanvasElement): Promise<Blob | null> {
  for (const quality of COVER_QUALITY_STEPS) {
    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, 'image/jpeg', quality),
    )
    if (blob && blob.size <= MAX_COVER_BYTES) {
      return blob
    }
  }
  return null
}

/**
 * 探测 mp4 是否做了 Web 优化（faststart：moov 盒排在 mdat 之前）。
 * <p>
 * moov 在尾部时浏览器要先拉到文件末尾才能起播，hover 预览会明显卡顿。
 * 只读头部字节按顶层盒顺序扫描，读不到结论时返回 null 而不是误报。
 */
export async function probeFaststart(file: File): Promise<boolean | null> {
  const buffer = await file.slice(0, PROBE_BYTES).arrayBuffer()
  const view = new DataView(buffer)
  const decoder = new TextDecoder('ascii')
  let offset = 0
  while (offset + 8 <= buffer.byteLength) {
    let size = view.getUint32(offset)
    const type = decoder.decode(new Uint8Array(buffer, offset + 4, 4))
    if (type === 'moov') {
      return true
    }
    if (type === 'mdat') {
      return false
    }
    if (size === 1) {
      // 64 位 largesize 紧跟在 type 之后
      if (offset + 16 > buffer.byteLength) {
        return null
      }
      size = Number(view.getBigUint64(offset + 8))
    }
    if (size === 0) {
      // 盒延伸到文件末尾，后面没有兄弟盒可看了
      return null
    }
    if (size < 8) {
      return null
    }
    offset += size
  }
  return null
}
