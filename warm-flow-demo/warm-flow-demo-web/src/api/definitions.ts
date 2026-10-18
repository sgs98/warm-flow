/**
 * 流程定义 API。
 */
import { httpDelete, httpDownload, httpGet, httpPost, httpPut, httpUpload, type PageVo } from './http'
import type { DefinitionOption } from '../types/workflow'

/** 分页查询流程定义。 */
export function listDefinitions(params: {
  pageNum?: number
  pageSize?: number
  keyword?: string
  category?: string
  isPublish?: number
}): Promise<PageVo<DefinitionOption>> {
  return httpGet<PageVo<DefinitionOption>>('/definitions', params)
}

/** 查询流程定义设计数据。 */
export function getDefinition(id: string | number): Promise<Record<string, any>> {
  return httpGet<Record<string, any>>(`/definitions/${id}`)
}

/** 保存流程定义设计数据。 */
export function saveDefinition(defJson: unknown): Promise<string> {
  return httpPost<string>('/definitions', defJson)
}

/** 发布流程定义。 */
export function publishDefinition(id: string | number): Promise<void> {
  return httpPut<void>(`/definitions/${id}/publish`)
}

/** 复制流程定义，返回新定义主键。 */
export function copyDefinition(id: string | number): Promise<string> {
  return httpPost<string>(`/definitions/${id}/copy`)
}

/** 导出流程定义 json 文件。 */
export function exportDefinition(id: string | number, fallbackFileName: string): Promise<string> {
  return httpDownload(`/definitions/${id}/export`, fallbackFileName)
}

/** 导入流程定义 json 文件，返回新定义主键。 */
export function importDefinition(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  return httpUpload<string>('/definitions/import', form)
}

/** 删除流程定义。 */
export function removeDefinition(id: string | number): Promise<void> {
  return httpDelete<void>(`/definitions/${id}`)
}
