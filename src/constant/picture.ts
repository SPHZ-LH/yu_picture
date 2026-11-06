/**
 * 图片审核状态常量
 * 与后端 PictureReviewRequest.reviewStatus 对应
 */
export const PIC_REVIEW_STATUS_ENUM = {
  REVIEWING: 0,
  ACCEPT: 1,
  REJECT: 2,
} as const

export const PIC_REVIEW_STATUS_MAP: Record<number, string> = {
  0: '待审核',
  1: '通过',
  2: '拒绝',
}

/** 审核状态对应的标签色 */
export const PIC_REVIEW_STATUS_COLOR_MAP: Record<number, string> = {
  0: 'processing',
  1: 'success',
  2: 'error',
}

export const PIC_REVIEW_STATUS_OPTIONS = Object.keys(PIC_REVIEW_STATUS_MAP).map((key) => {
  const value = Number(key)
  return {
    label: PIC_REVIEW_STATUS_MAP[value],
    value,
  }
})

/**
 * 以图搜图查询图大小上限。
 * <p>
 * 与后端 PictureSearchConstant.MAX_QUERY_IMAGE_BYTES 保持一致：本地文件与图片链接是
 * 同一件事的两种输入方式，上限与提示语都只写一份，避免两处口径错位。
 */
export const PIC_SEARCH_MAX_SIZE_MB = 50

export const PIC_SEARCH_MAX_SIZE_BYTES = PIC_SEARCH_MAX_SIZE_MB * 1024 * 1024

/** 超限提示语：入口处与搜图页共用同一句话（后端 PictureSearchConstant 也是同一口径） */
export const PIC_SEARCH_OVER_SIZE_MESSAGE = `图片不能超过 ${PIC_SEARCH_MAX_SIZE_MB}MB`

/** 图片排序字段（对应 PictureQueryRequest.sortField） */
export const PIC_SORT_FIELD_OPTIONS = [
  { label: '最新', value: 'createTime' },
  { label: '最早', value: 'editTime' },
  { label: '名称', value: 'name' },
  { label: '大小', value: 'picSize' },
  { label: '宽度', value: 'picWidth' },
  { label: '高度', value: 'picHeight' },
  { label: '宽高比', value: 'picScale' },
  { label: '格式', value: 'picFormat' },
  { label: '下载量', value: 'downloadCount' },
  { label: '收藏量', value: 'collectCount' },
]

/** 色系筛选（对应 PictureQueryRequest.colorFamily，色值用于下拉里的色点） */
export const PIC_COLOR_FAMILY_OPTIONS = [
  { label: '偏蓝', value: 'blue', color: '#3b82f6' },
  { label: '偏绿', value: 'green', color: '#22c55e' },
  { label: '偏红', value: 'red', color: '#ef4444' },
  { label: '偏黄', value: 'yellow', color: '#eab308' },
  { label: '紫', value: 'purple', color: '#a855f7' },
  { label: '粉', value: 'pink', color: '#ec4899' },
  { label: '灰白', value: 'gray', color: '#9ca3af' },
  { label: '暗色', value: 'dark', color: '#4b5563' },
]
