/**
 * 流程实例 API。
 * <p>当前登录用户由 axios 拦截器统一注入 {@code X-User-Name} 请求头。</p>
 */
import { httpDelete, httpGet, httpPost } from './http'
import type { HistoryItem, InstanceRow, PageResult } from '../types/workflow'

/** 发起流程实例，返回首个待办任务信息。 */
export function startInstance(payload: {
  flowCode: string
  businessId?: string
  variables?: Record<string, unknown>
}) {
  return httpPost<{ instanceId: string; taskId?: string; nodeName?: string }>('/instances', payload)
}

/** 分页查询流程实例（默认按当前登录用户过滤）。 */
export function pageInstances(params: {
  pageNum?: number
  pageSize?: number
  definitionId?: string | number
  flowStatus?: string
  businessId?: string
}): Promise<PageResult<InstanceRow>> {
  return httpGet<PageResult<InstanceRow>>('/instances', params)
}

/** 查询流程实例详情。 */
export function getInstance(id: string | number): Promise<InstanceRow> {
  return httpGet<InstanceRow>(`/instances/${id}`)
}

/** 查询实例审批历史（含当前活动待办，最新在前）。 */
export function getInstanceHistory(id: string | number): Promise<HistoryItem[]> {
  return httpGet<HistoryItem[]>(`/instances/${id}/history`)
}

/** 删除流程实例及其关联数据。 */
export function removeInstance(id: string | number): Promise<void> {
  return httpDelete<void>(`/instances/${id}`)
}

/** 批量删除流程实例，返回实际删除数量。 */
export function removeInstances(ids: Array<string | number>): Promise<number> {
  return httpDelete<number>('/instances', { ids })
}

/** 撤回本人发起的流程实例。 */
export function revokeInstance(id: string | number): Promise<void> {
  return httpPost<void>(`/instances/${id}/revoke`)
}

/** 终止流程实例。 */
export function terminateInstance(id: string | number): Promise<void> {
  return httpPost<void>(`/instances/${id}/termination`)
}
