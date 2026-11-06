import { request } from '@/request'

/** 上传图片文件（可重新上传：传 id） */
export function uploadPicture(file: File, body?: API.PictureUploadRequest) {
  return request.upload<API.PictureVO>('/picture/upload', file, {
    id: body?.id,
    picName: body?.picName,
    spaceId: body?.spaceId,
  })
}

/** 通过 URL 上传图片 */
export function uploadPictureByUrl(body: API.PictureUploadRequest) {
  return request.post<API.PictureVO>('/picture/upload/url', body)
}

/** 批量抓取上传图片（管理员） */
export function uploadPictureByBatch(body: API.PictureUploadByBatchRequest) {
  return request.post<number>('/picture/upload/batch', body)
}

/** 删除图片 */
export function deletePicture(body: API.DeleteRequest) {
  return request.post<boolean>('/picture/delete', body)
}

/** 更新图片（管理员） */
export function updatePicture(body: API.PictureUpdateRequest) {
  return request.post<boolean>('/picture/update', body)
}

/** 编辑图片（本人或管理员） */
export function editPicture(body: API.PictureEditRequest) {
  return request.post<boolean>('/picture/edit', body)
}

/** 画面编辑（裁剪/等比缩放/旋转/翻转），像素处理在数据万象完成，返回更新后的图片视图 */
export function transformPicture(body: API.PictureTransformRequest) {
  return request.post<API.PictureVO>('/picture/transform', body)
}

/** 分页查询图片（原始实体，管理员） */
export function listPictureByPage(body: API.PictureQueryRequest) {
  return request.post<API.Page<API.Picture>>('/picture/list/page', body)
}

/** 分页查询图片视图（公共图库免登录，空间图片需空间所有者或管理员） */
export function listPictureVOByPage(body: API.PictureQueryRequest) {
  return request.post<API.Page<API.PictureVO>>('/picture/list/page/vo', body)
}

/** 根据 id 获取图片视图 */
export function getPictureVOById(id: string) {
  return request.get<API.PictureVO>('/picture/get/vo', { id })
}

/** 根据 id 获取图片（原始实体，管理员） */
export function getPictureById(id: string) {
  return request.get<API.Picture>('/picture/get', { id })
}

/** 审核图片（管理员） */
export function reviewPicture(body: API.PictureReviewRequest) {
  return request.post<boolean>('/picture/review', body)
}

/** 获取标签 / 分类选项列表 */
export function listPictureTagCategory() {
  return request.get<API.PictureTagCategory>('/picture/tag_category')
}

/** 相似图搜索：上传本地图片作为检索输入，返回按相似度降序的图片列表 */
export function searchPictureByImage(file: File) {
  return request.upload<API.PictureVO[]>('/picture/search/picture_by_image', file)
}

/** 相似图搜索：以库内图片作为检索输入 */
export function searchPictureById(pictureId: string) {
  return request.post<API.PictureVO[]>('/picture/search/picture_by_image', null, {
    params: { pictureId },
  })
}

/**
 * 相似图搜索：以图片的网络地址作为检索输入。
 * 图片由后端抓取，故只接受公网 http/https 地址，且仅支持 jpg/png/webp。
 */
export function searchPictureByImageUrl(imageUrl: string) {
  return request.post<API.PictureVO[]>('/picture/search/picture_by_image', null, {
    params: { imageUrl },
  })
}

/** 相似图库全量同步（管理员）：存量图片重新入库，返回成功数 */
export function syncPictureGallery() {
  return request.post<number>('/picture/gallery/sync')
}

/** 提交画面扩展任务（作者 / 空间所有者 / 管理员） */
export function submitExpand(body: API.PictureExpandSubmitRequest) {
  return request.post<string>('/picture/expand/submit', body)
}

/** 查询画面扩展任务状态 */
export function getExpandResult(taskId: string) {
  return request.get<API.PictureExpandResultVO>('/picture/expand/result', { taskId })
}

/** 保存画面扩展结果为该图片当前展示内容 */
export function saveExpand(body: API.PictureExpandSaveRequest) {
  return request.post<API.PictureVO>('/picture/expand/save', body)
}

/**
 * AI 读图产出图片信息预填（只返回建议，不落库）。
 * 新建上传后自动调用；编辑页由「AI 重新读图」按钮手动调用。读不出内容时 data 为空。
 */
export function aiPrefillPictureInfo(body: API.PictureAiPrefillRequest) {
  return request.post<API.PictureAiPrefillVO>('/picture/ai/prefill', body)
}

/** 收藏图片（登录用户，重复收藏幂等），返回最新收藏状态与收藏量 */
export function collectPicture(pictureId: string) {
  return request.post<API.PictureCollectStatusVO>('/picture/collect', { pictureId })
}

/** 取消收藏图片（登录用户，未收藏时幂等），返回最新收藏状态与收藏量 */
export function cancelCollectPicture(pictureId: string) {
  return request.post<API.PictureCollectStatusVO>('/picture/collect/cancel', { pictureId })
}

/**
 * 批量查询「这批图片里我收藏了哪些」：列表页一次拿到整页星标状态，
 * 免去每张图块各发一次 /picture/collect/status。未登录返回空数组。
 * 星标属于浏览页的附属信息，失败时静默跳过，不弹全局提示打断看图。
 */
export function listCollectedPictureIds(pictureIds: string[]) {
  return request.post<string[]>('/picture/collect/status/batch', { pictureIds }, {
    skipErrorMessage: true,
  })
}

/** 分页查询我的收藏（登录用户），按收藏时间倒序，最近收藏的排在前面 */
export function listMyCollectPictureVOByPage(body: API.PageRequest = {}) {
  return request.post<API.Page<API.PictureVO>>('/picture/collect/list/page/vo', body)
}
