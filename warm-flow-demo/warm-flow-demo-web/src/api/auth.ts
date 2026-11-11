/**
 * Demo 登录 API。
 *
 * 认证走 HttpOnly 会话 Cookie，浏览器在同源请求中自动携带；
 * 前端不再持有 token，也不再有 X-User-Name 请求头。
 */
import { httpGet, httpPost } from './http'
import type { DemoUser } from './users'

export type { DemoUser }

/** 登录请求。 */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录。 */
export function login(request: LoginRequest): Promise<DemoUser> {
  return httpPost<DemoUser>('/auth/login', request)
}

/** 当前登录用户。 */
export function me(): Promise<DemoUser> {
  return httpGet<DemoUser>('/auth/me')
}

/** 登出。 */
export function logout(): Promise<void> {
  return httpPost<void>('/auth/logout')
}

/** 登录页演示用户列表。 */
export function listLoginUsers(): Promise<DemoUser[]> {
  return httpGet<DemoUser[]>('/auth/users')
}
