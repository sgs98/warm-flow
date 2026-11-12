export const RETURN_TO_REJECTOR = 'RETURN_TO_REJECTOR'
const own = (object, key) => Object.prototype.hasOwnProperty.call(object, key)
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value)

/** 受管驳回规则：reject 为对象且带 afterApproval；同名业务字段须在升级前迁移。 */
export function isManagedRejectSection(value) {
  return object(value) && own(value, 'afterApproval')
}

export function parseRejectReturnNodeConfig(raw) {
  return JSON.parse(raw)
}

export function readRejectReturn(config = {}) {
  const result = { mode: 'REPLAY', conflict: false, error: '' }
  if (typeof config === 'string') {
    try { config = parseRejectReturnNodeConfig(config) }
    catch (error) { return { ...result, error: `nodeConfig 无法解析：${error.message}` } }
  }
  const reject = config?.reject
  if (!isManagedRejectSection(reject)) {
    result.conflict = own(config || {}, 'reject')
    return result
  }
  if (reject.afterApproval == null || reject.afterApproval === 'REPLAY') {
    return result
  }
  if (reject.afterApproval === RETURN_TO_REJECTOR) {
    result.mode = RETURN_TO_REJECTOR
  } else {
    result.error = 'reject.afterApproval 必须为 REPLAY 或 RETURN_TO_REJECTOR，请修正节点配置'
  }
  return result
}

export function writeRejectReturn(config = {}, mode) {
  if (!['REPLAY', RETURN_TO_REJECTOR].includes(mode)) throw new Error('未知驳回后续模式')
  const state = readRejectReturn(config)
  if (state.error) throw new Error(state.error)
  const updated = { ...config }
  if (mode === RETURN_TO_REJECTOR) {
    if (state.conflict) throw new Error('旧业务属性 reject 与驳回规则冲突，请先在扩展属性中迁移到其它键')
    updated.reject = { ...config.reject, afterApproval: RETURN_TO_REJECTOR }
  } else if (isManagedRejectSection(config.reject)) {
    const { afterApproval, ...remaining } = config.reject
    if (Object.keys(remaining).length) updated.reject = remaining
    else delete updated.reject
  }
  return updated
}

/** 当前节点的返回区间是否跨并行 / 包容网关（跨则运行期按整体退回重走）。 */
export function isParallelReplayInterval(graph = {}, sourceId) {
  const nodes = graph.nodes || [], edges = graph.edges || []
  const source = nodes.find(n => n.id === sourceId)
  if (!source) return false
  const reject = edges.filter(e => e.sourceNodeId === sourceId && e.properties?.skipType === 'REJECT')
  const target = source.properties?.anyNodeSkip || (reject.length === 1 ? reject[0].targetNodeId : null)
  return !!target && target !== sourceId && crossesParallel(nodes, edges, target, sourceId)
}

/** target → source 的任意 PASS 路径是否经过并行 / 包容网关。 */
function crossesParallel(nodes, edges, target, source) {
  const pass = edge => edge.properties?.skipType === 'PASS'
  const byId = new Map(nodes.map(n => [n.id, n]))
  const seen = new Map()
  const queue = [[target, false]]
  while (queue.length) {
    const [code, hasGateway] = queue.shift()
    if (!code) continue
    const key = `${code}:${hasGateway}`
    if (seen.has(key)) continue
    seen.set(key, true)
    const node = byId.get(code)
    const crossed = hasGateway || node?.type === 'parallel' || node?.type === 'inclusive'
    if (code === source) {
      if (crossed) return true
      continue
    }
    for (const edge of edges) {
      if (edge.sourceNodeId === code && pass(edge)) queue.push([edge.targetNodeId, crossed])
    }
  }
  return false
}

/** 画布保存前提示；后端仍是严格校验的最终边界。 */
export function rejectReturnGraphErrors(graph = {}) {
  const nodes = graph.nodes || [], edges = graph.edges || [], errors = []
  const configs = new Map()
  const push = (node, message) => errors.push({ id: node.id, name: node.text?.value || node.id, message })
  for (const node of nodes) {
    try {
      const raw = node.properties?.nodeConfig
      const parsed = typeof raw === 'string' && raw.trim() ? parseRejectReturnNodeConfig(raw) : raw || {}
      if (!object(parsed)) throw new Error('nodeConfig 必须为 JSON 对象')
      configs.set(node.id, parsed)
      const state = readRejectReturn(parsed)
      if (state.error) push(node, state.error)
    } catch (error) { push(node, `节点配置无法解析：${error.message}`) }
  }
  const enabled = nodes.filter(n => readRejectReturn(configs.get(n.id)).mode === RETURN_TO_REJECTOR)
  if (!enabled.length) return errors
  const byId = new Map(nodes.map(n => [n.id, n]))
  const pass = edge => edge.properties?.skipType === 'PASS'
  for (const source of enabled) {
    const reject = edges.filter(e => e.sourceNodeId === source.id && e.properties?.skipType === 'REJECT')
    const target = source.properties?.anyNodeSkip || (reject.length === 1 ? reject[0].targetNodeId : null)
    try {
      if (!target || target === source.id) throw new Error('需要唯一且早于本节点的实际驳回目标')
      // 并行 / 包容网关区间按「整体退回重走」处理：配置合法但不启用直返
      if (crossesParallel(nodes, edges, target, source.id)) continue
      let code = target
      const visited = new Set()
      while (true) {
        const node = byId.get(code), props = node?.properties || {}
        if (!node || visited.has(code)) throw new Error('返回区间包含循环或缺失节点')
        visited.add(code)
        // 会签 / 票签 / 顺签 / 组签允许被直返跳过；串行网关、服务任务、事件与边界事件仍拒绝
        if (node.type !== 'between' || props.eventType
            || nodes.some(n => n.properties?.attachedToNodeCode === code)) {
          throw new Error('返回区间不支持串行网关、服务任务、事件或边界事件')
        }
        if (code === source.id) break
        const next = edges.filter(e => e.sourceNodeId === code && pass(e))
        if (next.length !== 1 || next[0].properties?.skipCondition?.trim()) throw new Error('返回区间必须是唯一无条件通过路径')
        code = next[0].targetNodeId
        if (edges.filter(e => e.targetNodeId === code && pass(e)).length !== 1) throw new Error('返回区间不能含分支汇聚')
      }
    } catch (error) { push(source, `驳回直返：${error.message}`) }
  }
  return errors
}
