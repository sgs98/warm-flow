/**
 * 定时任务（事件等待记录）API：全局查询、状态统计与定时启动任务手动补发。
 * <p>数据来自引擎 flow_event_wait。手动补发只创建一次实例，不推进等待记录，
 * 定时任务的下次触发时间不受影响。</p>
 */
import { httpGet, httpPost } from './http'
import type { EventWaitQueryParams, EventWaitRow, EventWaitSummary, PageResult } from '../types/workflow'

/** 分页查询事件等待记录，默认按下次触发时间升序。 */
export function pageEventWaits(params: EventWaitQueryParams): Promise<PageResult<EventWaitRow>> {
  return httpGet<PageResult<EventWaitRow>>('/event-waits', params)
}

/** 按同一组筛选条件统计各状态等待记录数量（状态条件不参与统计）。 */
export function getEventWaitSummary(params: EventWaitQueryParams): Promise<EventWaitSummary> {
  return httpGet<EventWaitSummary>('/event-waits/summary', params)
}

/** 手动补发一次定时启动任务，返回新建实例主键；不影响该任务的下次触发时间。 */
export function triggerTimerStart(
  id: string | number,
  payload?: { businessId?: string; variables?: Record<string, unknown> },
): Promise<string> {
  return httpPost<string>(`/event-waits/${id}/trigger`, payload ?? {})
}
