/** 按业务状态编码 / 名称映射 Element Plus Tag 类型。 */
export function statusTagType(
  status?: string,
  name?: string,
): 'success' | 'warning' | 'danger' | 'info' {
  const s = (status || '').toLowerCase()
  const n = name || ''
  if (s === 'finish' || n.includes('完成') || n.includes('通过')) return 'success'
  if (s === 'waiting' || n.includes('待审核') || n.includes('审批') || n.includes('办理')) return 'warning'
  if (s === 'back' || n.includes('退回') || n.includes('驳回')) return 'danger'
  if (s === 'termination' || n.includes('终止')) return 'danger'
  if (s === 'invalid' || n.includes('作废')) return 'info'
  if (s === 'cancel' || n.includes('撤销')) return 'info'
  if (s === 'draft' || n.includes('草稿')) return 'info'
  return 'info'
}
