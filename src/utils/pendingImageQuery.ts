import { ref } from 'vue'

/**
 * 首页「以图搜图」选好的查询图，交给搜图页消费。
 *
 * 查询图有两种来源，用 kind 区分而不是放两个 ref：两条路径互斥，
 * 分成两个变量迟早出现「两个都填了、以哪个为准」的模糊状态。
 *
 * 为什么不塞进 URL：File 没法序列化到 query 里；URL 形式虽然能塞，
 * 但和已有的 ?pictureId= 挤在同一个 query 里反而分不清来源，统一走这里更省事。
 * 放在模块作用域即可——两个页面都在同一个 SPA 里，路由切换不会销毁它；
 * 刷新页面就没了（也正合适：刷新后不该自动重搜一次）。
 * 搜图页取用后必须清空，否则来回切页面会反复触发同一张图的检索。
 */
export type PendingImageQuery = { kind: 'file'; file: File } | { kind: 'url'; url: string }

export const pendingImageQuery = ref<PendingImageQuery | null>(null)
