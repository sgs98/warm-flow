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
    const body = response?.data
    if (body && typeof body === 'object' && body.code != null) {
      if (body.code === 200) return body.data
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error: any) => {
    if (error?.silent || error?.message === 'UNAUTH') {
      return Promise.reject(error)
    }
    const msg = error?.response?.data?.message || error?.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

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
