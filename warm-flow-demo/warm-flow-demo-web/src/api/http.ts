import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  paramsSerializer: {
    indexes: null, // 数组参数序列化为 storageIds=a&storageIds=b（repeat 风格）
  },
})

http.interceptors.request.use((config) => {
  const user = localStorage.getItem('wf_user')
  if (user) {
    const headers = config.headers as { set?: (k: string, v: string) => void } & Record<string, unknown>
    if (typeof headers.set === 'function') headers.set('X-User-Name', user)
    else headers['X-User-Name'] = user
    return config
  }
  const url = config.url || ''
  if (/\/tasks\/(todo|done|copy)\b/.test(url)) {
    const err = new Error('UNAUTH')
    ;(err as Error & { silent?: boolean }).silent = true
    return Promise.reject(err)
  }
  return config
})

http.interceptors.response.use(
  (response: any) => {
    // 文件下载（流程定义导出）要读响应头里的文件名与原始 Blob，不做统一解包
    if (response?.config?.responseType === 'blob') return response
    const body = response?.data
    if (body && typeof body === 'object' && body.code != null) {
      if (body.code === 200) return body.data
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  async (error: any) => {
    if (error?.silent || error?.message === 'UNAUTH') {
      return Promise.reject(error)
    }
    const data = error?.response?.data
    const message = data instanceof Blob ? await blobMessage(data) : data?.message
    const msg = message || error?.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

/** 文件下载 / 上传出错时响应体是 Blob，解析出统一响应里的 message。 */
async function blobMessage(blob: Blob): Promise<string | undefined> {
  try {
    return (await JSON.parse(await blob.text()))?.message
  } catch {
    return undefined
  }
}

export function httpGet<T>(url: string, params?: Record<string, any>): Promise<T> {
  return http.get(url, { params }) as any
}
export function httpPost<T>(url: string, data?: any): Promise<T> {
  return http.post(url, data) as any
}
export function httpPut<T>(url: string, data?: any): Promise<T> {
  return http.put(url, data) as any
}
export function httpDelete<T>(url: string): Promise<T> {
  return http.delete(url) as any
}

/** 上传文件，用于流程定义导入。 */
export function httpUpload<T>(url: string, form: FormData): Promise<T> {
  return http.post(url, form) as any
}

/**
 * 下载文件，用于流程定义导出。
 * <p>文件名取后端 Content-Disposition，缺失时用调用方给的回退名；后端返回统一 json 错误体时按普通接口提示。</p>
 */
export async function httpDownload(url: string, fallbackFileName: string): Promise<string> {
  const response: any = await http.get(url, { responseType: 'blob' })
  const blob: Blob = response.data
  if (blob.type?.includes('application/json')) {
    const message = (await blobMessage(blob)) || '下载失败'
    ElMessage.error(message)
    throw new Error(message)
  }
  const fileName = parseFileName(response.headers?.['content-disposition']) || fallbackFileName
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(objectUrl)
  return fileName
}

/** 解析 Content-Disposition 文件名，兼容 filename*=utf-8''xxx 形式。 */
function parseFileName(disposition?: string): string | undefined {
  if (!disposition) return undefined
  const encoded = /filename\*=(?:utf-8|UTF-8)''([^;]+)/.exec(disposition)
  if (encoded?.[1]) return decodeURIComponent(encoded[1])
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain?.[1]
}

export interface PageVo<T> {
  total: number
  pageNum: number
  pageSize: number
  list: T[]
}

export interface DemoUser {
  id: string
  userName: string
  realName: string
  roleType: string
  deptCode?: string
  deptName?: string
}
