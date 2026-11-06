import { theme } from 'ant-design-vue'

/**
 * 画廊主题（「暗房灯箱」暗色 + 同源浅色）：所有页面共用，保证品牌一致。
 * 注意：以下色值与 src/assets/main.css 中 CSS 变量一一对应，改动需同步。
 */
export const GALLERY_COLOR_PRIMARY = '#ff6b3d'

/** 暗色（默认）：antd 走 darkAlgorithm */
export const GALLERY_ANT_THEME_DARK = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorPrimary: GALLERY_COLOR_PRIMARY,
    colorInfo: GALLERY_COLOR_PRIMARY,
    borderRadius: 10,
  },
}

/** 浅色：与暗色同一强调色，仅明暗算法不同 */
export const GALLERY_ANT_THEME_LIGHT = {
  token: {
    colorPrimary: GALLERY_COLOR_PRIMARY,
    colorInfo: GALLERY_COLOR_PRIMARY,
    borderRadius: 10,
  },
}
