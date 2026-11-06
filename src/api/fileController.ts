import { request } from '@/request'

/** 图片上传（登录用户可用，供头像等个人图片） */
export function uploadFile(file: File) {
  return request.upload<string>('/file/upload', file)
}

/** 测试文件上传（管理员） */
export function testUploadFile(file: File) {
  return request.upload<string>('/file/test/upload', file)
}

/** 测试文件流式上传（管理员） */
export function testUploadFileSteam(file: File) {
  return request.upload<string>('/file/test/uploadSteam', file)
}

/** 测试文件下载地址（管理员） */
export function getTestDownloadUrl(filepath: string) {
  return `/api/file/test/download/?filepath=${encodeURIComponent(filepath)}`
}
