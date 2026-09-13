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
