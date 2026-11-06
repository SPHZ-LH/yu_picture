import dayjs from 'dayjs'

/**
 * 格式化字节数为可读字符串
 */
export function formatSize(size?: number | string | null): string {
  const bytes = Number(size)
  if (!size || Number.isNaN(bytes) || bytes <= 0) {
    return '0 B'
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = Math.floor(Math.log(bytes) / Math.log(1024))
  const value = bytes / Math.pow(1024, index)
  return `${value.toFixed(value >= 100 || index === 0 ? 0 : 1)} ${units[index]}`
}

/**
 * 计数展示：过万缩写为 x.x万（参考主流壁纸站的写法）
 */
export function formatCount(value?: number | string | null): string {
  const n = Number(value)
  if (!value || Number.isNaN(n) || n <= 0) {
    return '0'
  }
  return n >= 10000 ? `${(n / 10000).toFixed(1)}万` : String(n)
}

/**
 * 通过后端下载接口下载图片：接口原子自增下载量后 302 到 COS 原图。
 * 优先 fetch 成 blob 保存（保证「保存文件」行为与文件名）；
 * 跨域受限时退回新窗口打开原图（下载量已在后端计入）。
 *
 * @returns true 表示已按 blob 成功保存，调用方可据此做本地计数 +1
 */
export async function downloadPictureByApi(
  pictureId: string,
  name: string,
  picFormat?: string,
): Promise<boolean> {
  const endpoint = `/api/picture/download/${pictureId}`
  const extension = picFormat === 'jpeg' ? 'jpg' : picFormat || 'jpg'
  const filename = `${name || 'picture'}.${extension}`
  try {
    const response = await fetch(endpoint, { credentials: 'same-origin' })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const blob = await response.blob()
    const objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(objectUrl)
    return true
  } catch {
    window.open(endpoint, '_blank')
    return false
  }
}

/**
 * 格式化时间
 */
export function formatDate(time?: string | Date | null, pattern = 'YYYY-MM-DD HH:mm'): string {
  if (!time) {
    return '-'
  }
  const parsed = dayjs(time)
  return parsed.isValid() ? parsed.format(pattern) : '-'
}

/**
 * 相对时间（如「3 天前」）
 */
export function formatRelativeTime(time?: string | Date | null): string {
  if (!time) {
    return '-'
  }
  const target = dayjs(time)
  if (!target.isValid()) {
    return '-'
  }
  const diffMinutes = dayjs().diff(target, 'minute')
  if (diffMinutes < 1) return '刚刚'
  if (diffMinutes < 60) return `${diffMinutes} 分钟前`
  const diffHours = Math.floor(diffMinutes / 60)
  if (diffHours < 24) return `${diffHours} 小时前`
  const diffDays = Math.floor(diffHours / 24)
  if (diffDays < 30) return `${diffDays} 天前`
  return target.format('YYYY-MM-DD')
}

/**
 * 安全解析标签
 * 后端 Picture 实体的 tags 为 JSON 字符串，PictureVO 为数组
 */
export function parseTags(tags?: string[] | string | null): string[] {
  if (!tags) {
    return []
  }
  if (Array.isArray(tags)) {
    return tags.filter(Boolean)
  }
  try {
    const parsed = JSON.parse(tags)
    return Array.isArray(parsed) ? parsed.filter(Boolean) : []
  } catch {
    return String(tags)
      .split(',')
      .map((tag) => tag.trim())
      .filter(Boolean)
  }
}

/**
 * 计算数值占最大值的百分比（0-100，保留一位小数）
 */
export function calcPercent(value?: number | string | null, total?: number | string | null): number {
  const v = Number(value || 0)
  const t = Number(total || 0)
  if (!t || Number.isNaN(v) || Number.isNaN(t) || t <= 0) {
    return 0
  }
  return Math.min(100, Number(((v / t) * 100).toFixed(1)))
}

/** 图片大小分析的分段顺序（后端返回 <100KB / 100KB-1MB / >1MB） */
export const PIC_SIZE_RANGE_ORDER = ['<100KB', '100KB-1MB', '>1MB']

/** 判断是否为管理员角色 */
export function isAdminRole(role?: string): boolean {
  return role === 'admin'
}

/**
 * 给 COS 图片地址追加数据万象的即时缩放参数。
 * <p>
 * 视频封面按原始帧尺寸入库（后端要用它的实测宽高作为视频宽高），列表只需要小图，
 * 用 URL 参数取小图可以免得为同一张封面再存一个派生对象。
 */
export function cosResizeUrl(url?: string, width = 600): string {
  if (!url) {
    return ''
  }
  const sep = url.includes('?') ? '&' : '?'
  return `${url}${sep}imageMogr2/thumbnail/${width}x/format/jpg/quality/80`
}

/** 秒数格式化成 m:ss，用于视频时长角标 */
export function formatDuration(seconds?: number | null): string {
  if (!seconds || seconds <= 0) {
    return ''
  }
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}
