/**
 * COS 直链的同源改写：把 *.myqcloud.com 直链换成 /cos-file/<key> 同源代理路径。
 *
 * 为什么不直连：vue-cropper / XHR / fetch 直连 COS 在部分浏览器环境会静默失败
 * （CORS、缓存、代理差异），编辑页与图片预览统一走同源代理（vite.config.ts 的
 * /cos-file 转发，生产由 nginx 提供等价 location）。<img> 直连虽然可用，
 * 但统一改写可以让所有资源路径同源，URL 一致、缓存行为一致。
 */
export function sameOriginCosUrl(url?: string | null): string {
  if (!url) return ''
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
