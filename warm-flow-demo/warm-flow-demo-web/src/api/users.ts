/**
 * Demo 用户 API。
 */
import { httpGet } from './http'

/** Demo 用户信息。 */
export interface DemoUser {
  id: string
  userName: string
  realName: string
  roleType: string
  deptCode?: string
  deptName?: string
}

/** 查询全部 Demo 用户。 */
export function listUsers(): Promise<DemoUser[]> {
  return httpGet<DemoUser[]>('/users')
}
