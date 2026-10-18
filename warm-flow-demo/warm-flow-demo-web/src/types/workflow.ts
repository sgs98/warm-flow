/** 流程实例在 Demo 页面中的展示模型。 */
export interface WorkflowInstance {
  id: string
  definitionId?: string
  flowName: string
  businessId: string
  variables?: Record<string, unknown>
  nodeCode?: string
  nodeName: string
  taskId?: string
  flowStatusName: string
  businessStatus?: string
  businessStatusName: string
  createBy: string
  createTime?: string
}

/** 流程历史记录在 Demo 页面中的展示模型。 */
export interface WorkflowHistoryItem {
  id: string
  instanceId?: string
  nodeName: string
  nodeCode: string
  targetNodeCode?: string
  targetNodeName: string
  approver: string
  skipType?: string
  flowStatusName: string
  businessStatus?: string
  businessStatusName: string
  current: boolean
  taskStatusName: string
  message: string
  createTime: string
}

/** 待办任务在办理工作台中的展示模型。 */
export interface WorkflowTask {
  id: string
  instanceId: string
  definitionId?: string
  flowName: string
  nodeName: string
  nodeCode?: string
  businessId: string
  assignees?: string[]
  variables?: Record<string, unknown>
  createTime: string
}

/* ==================== 通用 ==================== */

/** Demo 统一分页响应。 */
export interface PageResult<T> {
  total: number
  pageNum: number
  pageSize: number
  list: T[]
}

/** 流程定义在列表/下拉中的展示模型。 */
export interface DefinitionOption {
  /** 流程定义主键。 */
  id: string
  /** 流程编码。 */
  flowCode: string
  /** 流程名称。 */
  flowName: string
  /** 流程分类。 */
  category?: string
  /** 发布状态。 */
  isPublish?: number
  /** 版本号。 */
  version?: string
}

/** 流程定义列表页完整行模型。 */
export interface DefinitionRow {
  /** 流程定义主键。 */
  id: string
  /** 流程编码。 */
  flowCode: string
  /** 流程名称。 */
  flowName: string
  /** 模型值。 */
  modelValue: string
  /** 版本号。 */
  version: string
  /** 发布状态：0 未发布 / 1 已发布 / 9 失效。 */
  isPublish: number
  /** 激活状态：1 激活 / 其他挂起。 */
  activityStatus: number
  /** 业务扩展字段。 */
  ext?: string | null
  /** 定义配置 json（含 autoPass）。 */
  defConfig?: string | null
  /** 更新时间。 */
  updateTime: string
}

/** 任务协作按钮权限。 */
export interface ButtonPermission {
  code: string
  name: string
  show: boolean
}

/** 办理弹窗中下一审批节点及可选办理人。 */
export interface TaskNextNode {
  nodeCode: string
  nodeName: string
  permissionFlags: string[]
  selectableUsers: DemoUserOption[]
}

/** 协作选人（加签/减签/下一节点办理人）。 */
export interface DemoUserOption {
  userName: string
  realName: string
}

/* ==================== 实例 ==================== */

/** 流程实例列表/详情展示模型。 */
export interface InstanceRow {
  id: string
  definitionId: string
  flowName: string
  businessId: string
  variables?: Record<string, unknown>
  nodeCode: string
  nodeName: string
  taskId?: string
  /** 当前待办任务状态编码（引擎码：0 待提交 / 9 已退回）。 */
  taskStatusKey?: string
  nodeType?: number
  flowStatusKey?: string
  flowStatusName: string
  businessStatus: string
  businessStatusName: string
  createBy: string
  activityStatus?: number
  createTime: string
  updateTime?: string
}

/* ==================== 任务 ==================== */

/** 待办/已办/抄送任务展示模型。 */
export interface TaskRow {
  id: string
  instanceId: string
  flowName: string
  nodeName: string
  businessId: string
  assignees?: string[]
  flowStatusName?: string
  flowStatusKey?: string
  businessStatus?: string
  businessStatusName?: string
  createTime: string
  message?: string
}

/** 办理请求体（通过/退回/转办/委派/加签/减签共用）。 */
export interface TaskActionPayload {
  user: string
  taskId: string
  message?: string
  nodeCode?: string
  variables?: Record<string, unknown>
  nextHandlers?: string[]
  nextHandlerMap?: Record<string, string[]>
  addHandlers?: string[]
  reductionHandlers?: string[]
  copyUsers?: string[]
}

/* ==================== 事件 ==================== */

/** 实例等待中的事件视图。 */
export interface WaitingEvent {
  eventType: string
  eventName?: string
  nodeCode: string
  /** 边界事件关联的待办任务 ID（非边界事件为空）。 */
  attachedTaskId?: string
  nextTriggerAt?: string
  status: string
}

/** 消息启动请求体。 */
export interface MessageStartPayload {
  flowCode: string
  businessId: string
  eventName: string
  variables?: Record<string, unknown>
}

/** 消息触发请求体。 */
export interface MessageTriggerPayload {
  eventName: string
  payload?: Record<string, unknown>
}

/* ==================== 定时任务（事件等待记录） ==================== */

/** 事件等待记录行模型，对应引擎 flow_event_wait。 */
export interface EventWaitRow {
  /** 等待记录主键。 */
  id: string
  /** 流程定义主键。 */
  definitionId?: string
  /** 流程名称。 */
  flowName?: string
  /** 流程实例主键。 */
  instanceId?: string
  /** 业务流程主键。 */
  businessId?: string
  /** 事件节点编码。 */
  nodeCode?: string
  /** 事件类型持久化值（TIMER_CATCH 等）。 */
  eventType: string
  /** 事件类型文案。 */
  eventTypeName?: string
  /** 事件名称：消息名 / 错误码，错误边界留空表示通配。 */
  eventName?: string
  /** 边界事件关联的待办任务主键。 */
  attachedTaskId?: string
  /** 定时触发策略（AFTER / FIXED / POINT_IN_TIME）。 */
  triggerType?: string
  /** 定时表达式或间隔值。 */
  triggerInterval?: string
  /** 下次触发时间。 */
  nextTriggerAt?: string
  /** 时区。 */
  timezone?: string
  /** 已触发次数。 */
  repeatCount?: number
  /** 最大触发次数。 */
  maxRepeatCount?: number
  /** 等待状态持久化值（WAITING 等）。 */
  status: string
  /** 等待状态文案。 */
  statusName?: string
  /** 已重试次数。 */
  retryCount?: number
  /** 最大重试次数。 */
  maxRetryCount?: number
  /** 下次重试时间。 */
  retryAt?: string
  /** 抢占持有者。 */
  lockOwner?: string
  /** 抢占租约到期时间。 */
  lockUntil?: string
  /** 乐观锁版本号。 */
  version?: number
  /** 事件载荷 JSON。 */
  payload?: string
  /** 最近一次失败原因。 */
  lastError?: string
  /** 创建时间。 */
  createTime?: string
  /** 更新时间。 */
  updateTime?: string
}

/** 各状态等待记录数量，与列表共用筛选条件（状态条件除外）。 */
export interface EventWaitSummary {
  waiting: number
  retry: number
  running: number
  dead: number
  cancelled: number
}

/** 定时任务查询参数，与引擎 EventWaitQuery 的过滤维度一一对应。 */
export interface EventWaitQueryParams {
  pageNum?: number
  pageSize?: number
  /** 等待状态多选，留空不限。 */
  statuses?: string[]
  /** 事件类型多选，留空时按 timerOnly 取默认范围。 */
  eventTypes?: string[]
  /** 是否只查定时类事件，默认 true。 */
  timerOnly?: boolean
  flowCode?: string
  businessId?: string
  nodeCode?: string
  /** 事件名称（消息名 / 错误码）。 */
  eventName?: string
  nextTriggerAtFrom?: string
  nextTriggerAtTo?: string
  createTimeFrom?: string
  createTimeTo?: string
}

/** 流程错误触发请求体。 */
export interface ErrorTriggerPayload {
  errorCode?: string
  message?: string
}

/** 历史记录别名，供 API 层使用。 */
export type HistoryItem = WorkflowHistoryItem
