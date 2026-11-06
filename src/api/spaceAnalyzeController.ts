import { request } from '@/request'

/**
 * 空间分析相关接口
 *
 * 权限规则：
 * - querySpaceId 不为空：仅该空间所有者或管理员
 * - querySpaceId 为空且 queryAll=false：仅管理员（分析公共图库）
 * - querySpaceId 为空且 queryAll=true：仅管理员（分析全空间）
 * - rank 接口仅管理员
 */

/** 空间资源使用分析 */
export function getSpaceUsageAnalyze(params: API.SpaceAnalyzeQueryRequest) {
  return request.get<API.SpaceUsageAnalyzeResponse>('/space/analyze/usage', params)
}

/** 空间图片分类分析 */
export function getSpaceCategoryAnalyze(params: API.SpaceAnalyzeQueryRequest) {
  return request.get<API.SpaceCategoryAnalyzeResponse[]>('/space/analyze/category', params)
}

/** 空间图片标签分析 */
export function getSpaceTagAnalyze(params: API.SpaceAnalyzeQueryRequest) {
  return request.get<API.SpaceTagAnalyzeResponse[]>('/space/analyze/tag', params)
}

/** 空间图片大小分析 */
export function getSpaceSizeAnalyze(params: API.SpaceAnalyzeQueryRequest) {
  return request.get<API.SpaceSizeAnalyzeResponse[]>('/space/analyze/size', params)
}

/** 用户上传行为分析（granularity: day / week / month） */
export function getSpaceUploadTrendAnalyze(params: API.SpaceAnalyzeQueryRequest) {
  return request.get<API.SpaceUploadTrendAnalyzeResponse[]>('/space/analyze/upload/trend', params)
}

/** 空间使用排行分析（管理员，按存储量取前 topN） */
export function getSpaceRankAnalyze(params: API.SpaceAnalyzeQueryRequest) {
  return request.get<API.SpaceRankAnalyzeResponse[]>('/space/analyze/rank', params)
}
