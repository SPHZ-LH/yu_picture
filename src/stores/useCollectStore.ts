import { ref } from 'vue'
import { defineStore } from 'pinia'
import { cancelCollectPicture, collectPicture, listCollectedPictureIds } from '@/api/pictureController'

/**
 * 图片收藏状态（当前用户收藏了哪些图）
 *
 * 为什么前端还要自己存一份：后端刻意不把「我是否已收藏」并进 PictureVO
 * （PictureVO 走多级缓存、所有用户共享，会话相关的字段混进去会跨用户串状态），
 * 所以这份状态只能由前端按会话自己维护。
 *
 * 用法约定：
 * - 列表页在拿到数据后调一次 loadStatus(ids)，整页星标一次问清；
 * - 详情页等单张场景也用 loadStatus([id])，不必为它单开接口；
 * - 收藏/取消成功后用 setCollected 就地更新，避免整页重拉。
 */
export const useCollectStore = defineStore('collect', () => {
  /** 当前已收藏的图片 id 集合（id 一律按字符串比较：后端把 Long 序列化成了字符串） */
  const collectedIds = ref<Set<string>>(new Set())

  function isCollected(pictureId?: string | number | null): boolean {
    if (pictureId === undefined || pictureId === null || pictureId === '') {
      return false
    }
    return collectedIds.value.has(String(pictureId))
  }

  /** 就地写入某张图片的收藏状态 */
  function setCollected(pictureId: string | number, collected: boolean) {
    const key = String(pictureId)
    const next = new Set(collectedIds.value)
    if (collected) {
      next.add(key)
    } else {
      next.delete(key)
    }
    collectedIds.value = next
  }

  /**
   * 拉取这批图片的收藏状态并覆盖本地记录。
   * 未登录时后端返回空集合，因此调用方无需自己判断登录态。
   */
  async function loadStatus(pictureIds: Array<string | number | undefined | null>) {
    const ids = pictureIds
      .filter((id) => id !== undefined && id !== null && String(id) !== '')
      .map((id) => String(id))
    if (!ids.length) {
      return
    }
    try {
      const res = await listCollectedPictureIds(ids)
      if (res.code !== 0) {
        return
      }
      const next = new Set(collectedIds.value)
      // 先清掉这一批再按结果写回：曾收藏但已被取消的图片才能从集合里消失
      ids.forEach((id) => next.delete(id))
      ;(res.data ?? []).forEach((id) => next.add(String(id)))
      collectedIds.value = next
    } catch {
      // 星标拿不到不影响浏览，保持现状即可
    }
  }

  /**
   * 切换收藏状态（已收藏则取消，否则收藏）。
   * 成功后返回服务端的最新状态与收藏量；请求失败返回 null，调用方据此跳过后续处理。
   * 提示文案与登录引导由调用方负责，本 store 不做 UI 决策。
   */
  async function toggle(
    pictureId: string | number,
  ): Promise<{ collected: boolean; collectCount: number } | null> {
    const next = !isCollected(pictureId)
    const res = next ? await collectPicture(String(pictureId)) : await cancelCollectPicture(String(pictureId))
    if (res.code !== 0) {
      return null
    }
    setCollected(pictureId, next)
    return { collected: next, collectCount: Number(res.data?.collectCount ?? 0) }
  }

  /** 退出登录时清空：否则上一个用户的星标会留给下一个人 */
  function reset() {
    collectedIds.value = new Set()
  }

  return { collectedIds, isCollected, setCollected, loadStatus, toggle, reset }
})
