import { request } from '@/request'

/** 上传动态壁纸：视频与前端抽好的封面在同一次请求内提交 */
export function uploadVideo(file: File, coverFile: File, body?: API.VideoUploadRequest) {
  return request.uploadFiles<API.VideoVO>('/video/upload', { file, coverFile }, {
    id: body?.id,
    name: body?.name,
    introduction: body?.introduction,
    category: body?.category,
    tags: body?.tags,
    spaceId: body?.spaceId,
    duration: body?.duration,
  })
}

/** 删除视频 */
export function deleteVideo(body: API.DeleteRequest) {
  return request.post<boolean>('/video/delete', body)
}

/** 编辑视频元信息（名称/简介/分类/标签，不含画面编辑） */
export function editVideo(body: API.VideoEditRequest) {
  return request.post<boolean>('/video/edit', body)
}

/** 分页查询视频（原始实体，管理员） */
export function listVideoByPage(body: API.VideoQueryRequest) {
  return request.post<API.Page<API.Video>>('/video/list/page', body)
}

/** 分页查询视频视图 */
export function listVideoVOByPage(body: API.VideoQueryRequest) {
  return request.post<API.Page<API.VideoVO>>('/video/list/page/vo', body)
}

/** 根据 id 获取视频视图 */
export function getVideoVOById(id: string) {
  return request.get<API.VideoVO>('/video/get/vo', { id })
}

/** 审核视频（管理员） */
export function reviewVideo(body: API.VideoReviewRequest) {
  return request.post<boolean>('/video/review', body)
}

/**
 * 下载视频：后端计数后 302 到带文件名的预签名地址。
 * <p>
 * 与图片不同，这里不把响应读成 Blob：视频动辄几十 MB，读进内存再转 objectURL
 * 只是白白占一份拷贝。目标地址自带 Content-Disposition: attachment，
 * 直接导航即可触发下载且不会离开当前页面。
 */
export function downloadVideoById(videoId: string) {
  window.location.href = `/api/video/download/${videoId}`
}
