import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { message } from 'ant-design-vue'

/**
 * 请求配置扩展
 */
export interface RequestConfig extends AxiosRequestConfig {
  /** 为 true 时，业务错误码 / 网络异常不弹出全局提示，由调用方自行处理 */
  skipErrorMessage?: boolean
}

/**
 * axios 实例
 *
 * - baseURL 使用 `/api`：开发环境由 Vite 代理转发到后端 `http://localhost:8080/api`，
 *   生产环境与后端同域部署即可，避免跨域。
 * - withCredentials：后端登录态基于 Session Cookie（user_login），必须开启，
 *   否则 /user/get/login、/space/mine、空间分析等接口会返回 40100 未登录。
 */
const instance = axios.create({
  baseURL: '/api',
  timeout: 60000,
  withCredentials: true,
})

/**
 * 响应拦截器：统一解包 BaseResponse
 * - 返回业务封装对象 { code, data, message }，调用方只需判断 code === 0
 * - 二进制流等非标准响应原样返回
 */
instance.interceptors.response.use(
  (response) => {
    const res = response.data
    // 文件流等非 JSON 封装，直接返回
    if (res == null || typeof res !== 'object' || typeof res.code === 'undefined') {
      return res
    }
    if (res.code !== 0) {
      const config = (response.config || {}) as RequestConfig
      if (!config.skipErrorMessage) {
        message.error(res.message || '请求失败')
      }
    }
    return res
  },
  (error) => {
    const config = (error?.config || {}) as RequestConfig
    if (!config.skipErrorMessage) {
      const status = error?.response?.status
      if (status) {
        message.error(`请求失败（HTTP ${status}）`)
      } else {
        message.error('网络异常，请确认后端服务已在 8080 端口启动')
      }
    }
    return Promise.reject(error)
  },
)

/**
 * 统一请求方法：返回值已解包为 BaseResponse<T>
 */
export const request = {
  get<T = unknown>(url: string, params?: Record<string, unknown>, config?: RequestConfig) {
    return instance.get(url, { params, ...config }) as unknown as Promise<API.BaseResponse<T>>
  },
  post<T = unknown>(url: string, data?: unknown, config?: RequestConfig) {
    return instance.post(url, data, config) as unknown as Promise<API.BaseResponse<T>>
  },
  /**
   * 表单上传：file 走 @RequestPart("file")，其余字段为普通表单字段
   */
  upload<T = unknown>(
    url: string,
    file: File | Blob,
    extra?: Record<string, unknown>,
    config?: RequestConfig,
  ) {
    return request.uploadFiles<T>(url, { file }, extra, config)
  },
  /**
   * 多部件表单上传：files 的键即后端 @RequestPart 的名字。
   * 数组值会展开为同名多字段（如 tags=a&tags=b），与 Spring 的 List 绑定一致。
   */
  uploadFiles<T = unknown>(
    url: string,
    files: Record<string, File | Blob>,
    extra?: Record<string, unknown>,
    config?: RequestConfig,
  ) {
    const formData = new FormData()
    Object.entries(files).forEach(([key, file]) => {
      formData.append(key, file)
    })
    if (extra) {
      Object.entries(extra).forEach(([key, value]) => {
        if (value === undefined || value === null || value === '') {
          return
        }
        if (Array.isArray(value)) {
          value.forEach((item) => formData.append(key, String(item)))
        } else {
          formData.append(key, String(value))
        }
      })
    }
    return instance.post(url, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      ...config,
    }) as unknown as Promise<API.BaseResponse<T>>
  },
}

export default instance
