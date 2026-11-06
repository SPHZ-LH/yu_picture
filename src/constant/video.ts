/**
 * 动态壁纸的排序维度：与首页保留给图片的那几档对齐，字段名换成 video 表的列。
 * 收藏量不在其中——视频没有收藏功能（见 docs/adr/0011）。
 */
export const VIDEO_SORT_FIELD_OPTIONS = [
  { label: '最新', value: 'createTime' },
  { label: '宽度', value: 'videoWidth' },
  { label: '高度', value: 'videoHeight' },
  { label: '下载量', value: 'downloadCount' },
]
