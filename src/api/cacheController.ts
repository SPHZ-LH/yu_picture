import { request } from '@/request'

/**
 * 缓存运维接口（仅管理员）
 *
 * 使用场景：有人绕过应用直接改了数据库时，应用侧的多级缓存最长会脏到
 * L2 TTL（30 分钟）且重启无效，此时需要按 key 精确手动失效。
 */

/** 手动失效指定缓存（type: picture / user） */
export function evictCache(params: API.CacheEvictRequest) {
  return request.post<API.CacheEvictResult>('/cache/evict', params)
}

/** 查看缓存运行状态：两级 L1 命中率 + L2（Redis）熔断器状态 */
export function getCacheStats() {
  return request.get<API.CacheStats>('/cache/stats')
}
