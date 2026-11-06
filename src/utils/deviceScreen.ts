/**
 * 访客设备屏幕的尺寸与比例（词条见根 CONTEXT.md「设备屏幕比例」「屏幕分辨率」）。
 *
 * 只取 screen.width/height 这一组，另外三个候选都不能用：
 * - screen.availWidth/availHeight 扣掉了任务栏/Dock：2560×1440 的屏会变成 2560×1400（≈1.83），
 *   比例被污染；
 * - window.innerWidth/innerHeight 是浏览器视口，拉一下窗口比例就变，根本不是屏幕；
 * - screen.orientation 只有横/竖两个值，粒度太粗（见 CONTEXT.md「屏幕方向」是图片的属性）。
 * 系统缩放（125%/150%）是等比的，所以 CSS 像素比例与物理像素比例一致，不必先除以 DPR。
 *
 * 物理像素点数 = CSS 尺寸 × devicePixelRatio：移动端 screen.width 是 CSS 尺寸，
 * 不乘 DPR 会把 1080p 手机显示成 360×780。多屏时 window.screen 就是当前窗口所在那块屏。
 *
 * 取值是页面加载时的一次快照：把窗口拖到另一块屏幕不会有事件通知，需要重取只能刷新。
 */

export interface DeviceScreen {
  /** 屏幕的 CSS 像素尺寸（screen.width/height） */
  cssWidth: number
  cssHeight: number
  /** 物理像素点数（css 尺寸 × devicePixelRatio），展示用 */
  physicalWidth: number
  physicalHeight: number
  /** 屏幕比例的数值形式（宽/高），如 16:9 → 1.7778 */
  ratio: number
  /** 最简整数比，同时可直接喂给 vue-cropper 的 fixedNumber */
  ratioPair: [number, number]
  /** 展示用文案：优先俗称（"16:9"），认不出来才退回最简整数比 */
  ratioLabel: string
}

/** 比例相等的判定容差：0.5%。1900×1080 与 16:9 只差 0.05%，按同一比例处理 */
export const RATIO_EPSILON = 0.005

/**
 * 有俗称的比例，展示时优先用俗称：真实分辨率几乎都不是"好看"的数，
 * 1707×1067 化简出来就是它自己（互质）、1366×768 化简成 683:384，
 * 这种标签等于把分辨率又说了一遍。命中俗称才说得清"这是块 16:10 的屏"。
 * 倒过来（竖屏 1080×1920）也要认：交换俗称的左右两边即可。
 */
const COMMON_RATIOS: Array<{ ratio: number; label: string }> = [
  { ratio: 1, label: '1:1' },
  { ratio: 5 / 4, label: '5:4' },
  { ratio: 4 / 3, label: '4:3' },
  { ratio: 3 / 2, label: '3:2' },
  { ratio: 16 / 10, label: '16:10' },
  { ratio: 16 / 9, label: '16:9' },
  { ratio: 19.5 / 9, label: '19.5:9' },
  { ratio: 20 / 9, label: '20:9' },
  { ratio: 21 / 9, label: '21:9' },
]

function gcd(a: number, b: number): number {
  return b === 0 ? a : gcd(b, a % b)
}

/** 两个比值是否算同一个比例（任一为 0/空则判否） */
export function isSameRatio(a: number, b: number): boolean {
  if (!(a > 0) || !(b > 0)) {
    return false
  }
  return Math.abs(a - b) / b <= RATIO_EPSILON
}

/** 比例 → 展示文案：先认俗称（含竖屏反向），认不出来退回最简整数比 */
export function describeRatio(ratio: number): string {
  for (const item of COMMON_RATIOS) {
    if (isSameRatio(ratio, item.ratio)) {
      return item.label
    }
    const [left, right] = item.label.split(':')
    if (isSameRatio(ratio, 1 / item.ratio)) {
      return `${right}:${left}`
    }
  }
  // 认不出俗称（如带鱼屏 2560×1080 = 2.37）就退回数值比，比 64:27 这种化简结果好读
  return ratio >= 1 ? `${ratio.toFixed(2)}:1` : `1:${(1 / ratio).toFixed(2)}`
}

/** "16:9" → 1.7778；格式不合法返回 null */
export function parseRatioLabel(label: string): number | null {
  const [w, h] = String(label).split(':').map((part) => Number(part))
  if (!(w > 0) || !(h > 0)) {
    return null
  }
  return w / h
}

/** 读取设备屏幕；取不到有效尺寸时返回 null（调用方需按"无设备信息"降级） */
export function getDeviceScreen(): DeviceScreen | null {
  const cssWidth = Math.round(Number(window.screen?.width ?? 0))
  const cssHeight = Math.round(Number(window.screen?.height ?? 0))
  if (!(cssWidth > 0) || !(cssHeight > 0)) {
    return null
  }
  const dpr = Number(window.devicePixelRatio) > 0 ? Number(window.devicePixelRatio) : 1
  const divisor = gcd(cssWidth, cssHeight) || 1
  return {
    cssWidth,
    cssHeight,
    physicalWidth: Math.round(cssWidth * dpr),
    physicalHeight: Math.round(cssHeight * dpr),
    ratio: cssWidth / cssHeight,
    ratioPair: [cssWidth / divisor, cssHeight / divisor],
    ratioLabel: describeRatio(cssWidth / cssHeight),
  }
}
