/**
 * 任务 API：待办/已办/抄送查询与协作操作。
 */
import { httpGet, httpPost } from './http'
import type { ButtonPermission, DemoUserOption, PageResult, TaskActionPayload, TaskNextNode, TaskRow } from '../types/workflow'

/** 分页查询当前用户待办。 */
export function pageTodoTasks(params: { pageNum?: number; pageSize?: number }): Promise<PageResult<TaskRow>> {
  return httpGet<PageResult<TaskRow>>('/tasks/todo', params)
}

/** 分页查询当前用户已办。 */
export function pageDoneTasks(params: { pageNum?: number; pageSize?: number }): Promise<PageResult<TaskRow>> {
  return httpGet<PageResult<TaskRow>>('/tasks/done', params)
}

/** 分页查询当前用户收到的抄送。 */
export function pageCopyTasks(params: { pageNum?: number; pageSize?: number }): Promise<PageResult<TaskRow>> {
  return httpGet<PageResult<TaskRow>>('/tasks/copy', params)
}

/** 查询待办任务节点可操作按钮权限。 */
export function getButtonPermissions(taskId: string | number): Promise<ButtonPermission[]> {
  return httpGet<ButtonPermission[]>(`/tasks/${taskId}/button-permissions`)
}

/** 查询待办任务可达的下一审批节点（含可选办理人）。 */
export function getNextNodes(taskId: string | number): Promise<TaskNextNode[]> {
  return httpGet<TaskNextNode[]>(`/tasks/${taskId}/next-nodes`)
}

/** 查询待办任务可退回的历史审批节点。 */
export function getBackNodes(taskId: string | number): Promise<TaskNextNode[]> {
  return httpGet<TaskNextNode[]>(`/tasks/${taskId}/back-nodes`)
}

/** 查询加签可选办理人。 */
export function getAddSignatureHandlers(instanceId: string | number): Promise<DemoUserOption[]> {
  return httpGet<DemoUserOption[]>(
    `/tasks/instances/${instanceId}/add-signature-handlers`)
}

/** 查询可减签办理人。 */
export function getReductionSignatureHandlers(instanceId: string | number): Promise<DemoUserOption[]> {
  return httpGet<DemoUserOption[]>(
    `/tasks/instances/${instanceId}/reduction-signature-handlers`)
}

/** 通过待办任务。 */
export function passTask(payload: TaskActionPayload) {
  return httpPost('/tasks/pass', payload)
}

/** 退回待办任务到指定节点。 */
export function rejectTask(payload: TaskActionPayload) {
  return httpPost('/tasks/reject', payload)
}

/** 转办待办任务。 */
export function transferTask(payload: TaskActionPayload): Promise<void> {
  return httpPost<void>('/tasks/transfer', payload)
}

/** 委派待办任务。 */
export function deputeTask(payload: TaskActionPayload): Promise<void> {
  return httpPost<void>('/tasks/depute', payload)
}

/** 加签。 */
export function addSignature(payload: TaskActionPayload): Promise<void> {
  return httpPost<void>('/tasks/add-signature', payload)
}

/** 减签。 */
export function reductionSignature(payload: TaskActionPayload): Promise<void> {
  return httpPost<void>('/tasks/reduction-signature', payload)
}
