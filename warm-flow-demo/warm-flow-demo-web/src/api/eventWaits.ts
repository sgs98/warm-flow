/**
 * 定时任务（事件等待记录）API：全局只读查询与状态统计。
 * <p>数据来自引擎 flow_event_wait，只做观测，不提供取消 / 立即触发 / 重新排期等写操作。</p>
 */
import { httpGet } from './http'
import type { EventWaitQueryParams, EventWaitRow, EventWaitSummary, PageResult } from '../types/workflow'

/** 分页查询事件等待记录，默认按下次触发时间升序。 */
export function pageEventWaits(params: EventWaitQueryParams): Promise<PageResult<EventWaitRow>> {
  return httpGet<PageResult<EventWaitRow>>('/event-waits', params)
}

/** 按同一组筛选条件统计各状态等待记录数量（状态条件不参与统计）。 */
export function getEventWaitSummary(params: EventWaitQueryParams): Promise<EventWaitSummary> {
  return httpGet<EventWaitSummary>('/event-waits/summary', params)
}
