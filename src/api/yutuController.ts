import { request } from '@/request'

/**
 * 屿图改版新增的后端接口封装（下载日志/榜单、专题、关注、喜欢、积分、
 * 收藏公开、多分辨率下载、标记位、空间动态、草稿）。
 *
 * 后端 JsonConfig 把 Long 序列化为字符串，所以 id 类字段统一按 string 声明。
 */

// ==================== 类型 ====================

export type PictureRankVO = {
  picture?: API.PictureVO
  /** 窗口内下载次数 */
  downloadCount?: string
  /** 当前名次（从 1 开始） */
  rank?: number
  /** 排名趋势：up/down/flat/new */
  trend?: 'up' | 'down' | 'flat' | 'new'
  /** 名次变化位数 */
  trendDelta?: number | null
}

export type PictureRankQueryRequest = {
  /** 榜单周期：day/week/month（滚动窗口） */
  period?: 'day' | 'week' | 'month'
  category?: string
  current?: number
  pageSize?: number
}

export type PictureDownloadTierVO = {
  /** 档位名：8K/4K/2K/1080P */
  tier?: string
  width?: number
  height?: number
}

export type PictureDownloadRecordVO = {
  picture?: API.PictureVO
  downloadTime?: string
}

export type TopicVO = {
  id?: string
  name?: string
  description?: string
  coverUrl?: string
  category?: string
  curatorId?: string
  curatorName?: string
  curatorAvatar?: string
  updateSchedule?: string
  isFeatured?: boolean
  /** 专题内图片数（公共图库过审口径） */
  pictureCount?: string
  createTime?: string
}

export type TopicPictureOpRequest = {
  topicId?: string
  pictureId?: string
}

export type FollowStatusVO = {
  userId?: string
  followed?: boolean
  followerCount?: string
}

export type FollowCountVO = {
  followerCount?: string
  followingCount?: string
}

export type UserPointsVO = {
  points?: string
}

export type CreatorVO = {
  id?: string
  userName?: string
  userAvatar?: string
  userProfile?: string
  createTime?: string
  /** 公开图库过审作品数 */
  pictureCount?: string
  /** 作品总下载量 */
  downloadCount?: string
  followerCount?: string
}

// ==================== 下载与榜单 ====================

/** 下载排行榜（匿名可访问；period 空按 day 处理） */
export function listPictureRanking(body: API.PictureRankQueryRequest) {
  return request.post<API.Page<API.PictureRankVO>>('/picture/ranking', body)
}

/** 图片可用的下载档位列表（详情页「选择分辨率下载」） */
export function listDownloadTiers(pictureId: string | number) {
  return request.get<API.PictureDownloadTierVO[]>('/picture/download/tiers', { pictureId })
}

/** 我的下载历史（登录用户） */
export function listMyDownloadHistory(body: API.PageRequest) {
  return request.post<API.Page<API.PictureDownloadRecordVO>>('/picture/download/history/my', body)
}

/** 分辨率档位 → 总像素预算（与后端清晰度档位阈值一致） */
export const TIER_PIXEL_BUDGETS: Record<string, number> = {
  '8K': 33177600,
  '4K': 8294400,
  '2K': 2073600,
  '1080P': 921600,
}

/** 按图片宽高估算清晰度档位名（低于 1080P 返回空串） */
export function resolutionTier(picWidth?: number | null, picHeight?: number | null): string {
  if (!picWidth || !picHeight || picWidth <= 0 || picHeight <= 0) return ''
  const pixels = picWidth * picHeight
  for (const [tier, budget] of Object.entries(TIER_PIXEL_BUDGETS)) {
    if (pixels >= budget) return tier
  }
  return ''
}

/** 衍生档下载地址（tier 空下载原图） */
export function buildPictureDownloadUrl(pictureId: string | number, tier?: string) {
  const base = `/api/picture/download/${pictureId}`
  return tier ? `${base}?tier=${encodeURIComponent(tier.toLowerCase())}` : base
}

// ==================== 专题 ====================

/** 专题列表（含实时图片计数与策划人，精选在前） */
export function listTopicVOs(category?: string) {
  return request.get<TopicVO[]>('/topic/list/vo', category ? { category } : undefined)
}

/** 本周精选专题（未设置时 data 为 null） */
export function getFeaturedTopicVO() {
  return request.get<TopicVO | null>('/topic/featured/vo')
}

/** 专题详情 */
export function getTopicVO(topicId: string | number) {
  return request.get<TopicVO>('/topic/get/vo', { topicId })
}

/** 专题内图片分页（公共图库过审图，按加入时间倒序） */
export function listTopicPictures(topicId: string | number, current = 1, pageSize = 12) {
  return request.get<API.Page<API.PictureVO>>('/topic/picture/page', { topicId, current, pageSize })
}

// ==================== 关注 ====================

/** 关注用户（不能关注自己；重复关注幂等） */
export function doFollow(followeeId: string | number) {
  return request.post<boolean>('/user/follow', { followeeId })
}

/** 取消关注（幂等） */
export function cancelFollow(followeeId: string | number) {
  return request.post<boolean>('/user/follow/cancel', { followeeId })
}

/** 关注状态与粉丝数（匿名 followed=false） */
export function getFollowStatus(followeeId: string | number) {
  return request.get<FollowStatusVO>('/user/follow/status', { followeeId })
}

/** 批量关注状态（创作者列表渲染关注按钮） */
export function listFollowStatusBatch(userIds: (string | number)[]) {
  return request.post<FollowStatusVO[]>('/user/follow/status/batch', { userIds })
}

/** 粉丝数 + 关注数 */
export function getFollowCounts(userId: string | number) {
  return request.get<FollowCountVO>('/user/follow/count', { userId })
}

// ==================== 喜欢（点赞） ====================

/** 点赞图片（重复点赞幂等） */
export function doLike(pictureId: string | number) {
  return request.post<boolean>('/picture/like', { pictureId })
}

/** 取消点赞（幂等） */
export function cancelLike(pictureId: string | number) {
  return request.post<boolean>('/picture/like/cancel', { pictureId })
}

/** 批量查询「这批图片里我点赞了哪些」（匿名返回空） */
export function listLikedPictureIds(pictureIds: (string | number)[]) {
  return request.post<string[]>('/picture/like/status/batch', { pictureIds })
}

/** 我的点赞列表 */
export function listMyLikedPictures(body: API.PageRequest) {
  return request.post<API.Page<API.PictureVO>>('/picture/like/list/my', body)
}

/** 某用户收到的点赞总数（获赞） */
export function getReceivedLikeCount(userId: string | number) {
  return request.get<number>('/picture/like/received/count', { userId })
}

// ==================== 创作者目录 ====================

/** 创作者目录分页（公开；sortItem=popular/works/newest） */
export function listCreatorPage(current: number, pageSize: number, sortItem?: string) {
  return request.get<API.Page<CreatorVO>>('/user/creator/list', { current, pageSize, sortItem })
}

// ==================== 积分 ====================

/** 我的积分余额 */
export function getMyPoints() {
  return request.get<UserPointsVO>('/user/points/my')
}

// ==================== 收藏公开 ====================

/** 某用户的公开收藏列表（未开启返回空页） */
export function listPublicCollectPictures(userId: string | number, current = 1, pageSize = 12) {
  return request.post<API.Page<API.PictureVO>>('/picture/collect/list/public', {
    userId,
    current,
    pageSize,
  })
}

// ==================== 标记位 ====================

/** 今日一图（未设置时 data 为 null） */
export function getDailyPickPicture() {
  return request.get<API.PictureVO | null>('/picture/daily')
}

/** 本周精选创作者（未设置时 data 为 null） */
export function getFeaturedCreator() {
  return request.get<API.UserVO | null>('/user/featured-creator')
}

// ==================== 空间动态 ====================

/** 空间动态分页（仅空间成员） */
export function listSpaceActivities(spaceId: string | number, current = 1, pageSize = 10) {
  return request.get<API.Page<SpaceActivityVO>>('/space/activity/page', {
    spaceId,
    current,
    pageSize,
  })
}

export type SpaceActivityVO = {
  id?: string
  user?: API.UserVO
  /** upload/edit/delete */
  action?: string
  targetId?: string
  /** 图片已删除时为 null */
  picture?: API.PictureVO | null
  createTime?: string
}
