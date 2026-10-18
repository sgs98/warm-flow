/**
 * 事件 API：消息启动/触发、错误抛出、等待事件与发布记录查询。
 */
import { httpGet, httpPost } from './http'
import type {
  ErrorTriggerPayload,
  MessageStartPayload,
  MessageTriggerPayload,
  WaitingEvent,
} from '../types/workflow'

/** 消息启动流程（MESSAGE_START），返回新实例主键。 */
export function startInstanceByMessage(payload: MessageStartPayload): Promise<string> {
  return httpPost<string>('/instances/message', payload)
}

/** 触发实例的消息捕获事件（MESSAGE_CATCH / MESSAGE_BOUNDARY），返回实例主键。 */
export function triggerInstanceMessage(id: string | number, payload: MessageTriggerPayload): Promise<string> {
  return httpPost<string>(`/instances/${id}/messages`, payload)
}

/** 查询实例等待中的事件（含下次触发时间）。 */
export function getInstanceWaitingEvents(id: string | number): Promise<WaitingEvent[]> {
  return httpGet<WaitingEvent[]>(`/instances/${id}/events`)
}

/** 查询最近的消息发布记录（MESSAGE_THROW / MESSAGE_END，最新在前）。 */
export function getPublishedMessages(): Promise<string[]> {
  return httpGet<string[]>('/events/published')
}

/** 实例级抛出流程错误，触发 ERROR_CATCH（错误码留空表示通配）。 */
export function throwInstanceError(id: string | number, payload: ErrorTriggerPayload): Promise<boolean> {
  return httpPost<boolean>(`/instances/${id}/errors`, payload)
}

/** 任务级抛出流程错误，触发附着该任务的 ERROR_BOUNDARY。 */
export function throwTaskError(taskId: string | number, payload: ErrorTriggerPayload): Promise<boolean> {
  return httpPost<boolean>(`/tasks/${taskId}/errors`, payload)
}
