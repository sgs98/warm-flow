export const RETURN_TO_REJECTOR = 'RETURN_TO_REJECTOR'
export const REJECT_RETURN_RUNTIME_KEY = '__rejectReturnRuntime'
const own = (object, key) => Object.prototype.hasOwnProperty.call(object, key)
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value)

export function isManagedRejectSection(value) {
  return object(value) && own(value, 'schemaVersion')
}

// 只保留协议版本的原数值语义；其它节点属性仍走既有 JSON.parse 行为。
export function parseRejectReturnNodeConfig(raw) {
  const parsed = JSON.parse(raw)
  if (!object(parsed)) return parsed
  const tokenPattern = /"(?:\\.|[^"\\])*"|-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?/g
  const strings = [...raw.matchAll(tokenPattern)].filter(match => match[0].startsWith('"')).map(match => JSON.parse(match[0]))
  let prefix = '__rejectNumber__'
  while (strings.some(text => text.startsWith(prefix))) prefix += '_'
  const numbers = new Map()
  const masked = raw.replace(tokenPattern, token => {
    if (token.startsWith('"')) return token
    const key = prefix + numbers.size
    numbers.set(key, token)
    return JSON.stringify(key)
  })
  const exact = JSON.parse(masked)
  for (const [section, field] of [['reject', 'schemaVersion'], [REJECT_RETURN_RUNTIME_KEY, 'version']]) {
    if (!object(exact[section])) continue
    const token = numbers.get(exact[section][field])
    if (token !== undefined && !exactVersionOne(token)) {
      // 非法版本保留原 token 为可见错误值，不能先舍入成 1 再保存。
      parsed[section][field] = token
    }
  }
  return parsed
}

function exactVersionOne(token) {
  const [mantissa, exponent = '0'] = token.toLowerCase().split('e')
  const [integer, fraction = ''] = mantissa.split('.')
  const digits = (integer + fraction).replace(/^0+/, '')
  const significant = digits.replace(/0+$/, '')
  return significant === '1' && BigInt(exponent) - BigInt(fraction.length) + BigInt(digits.length - significant.length) === 0n
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
  if (typeof reject.schemaVersion !== 'number' || reject.schemaVersion !== 1) {
    result.error = 'reject.schemaVersion 必须是数值整数 1，请修正导入的节点配置'
  } else if (reject.afterApproval == null || reject.afterApproval === 'REPLAY') {
    return result
  } else if (reject.afterApproval === RETURN_TO_REJECTOR) {
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
    if (own(config, REJECT_RETURN_RUNTIME_KEY)) throw new Error('节点含实例运行关联，请使用设计定义，不能覆盖运行或业务数据')
    updated.reject = { ...config.reject, schemaVersion: 1, afterApproval: RETURN_TO_REJECTOR }
  } else if (isManagedRejectSection(config.reject)) {
    const { afterApproval, ...remaining } = config.reject
    if (Object.keys(remaining).length === 1) delete updated.reject
    else updated.reject = remaining
  }
  return updated
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
      const runtime = parsed[REJECT_RETURN_RUNTIME_KEY]
      if (object(runtime) && own(runtime, 'version')) push(node, '这是实例运行图，请改用设计定义；运行关联不能导入共享节点')
    } catch (error) { push(node, `节点配置无法解析：${error.message}`) }
  }
  const enabled = nodes.filter(n => readRejectReturn(configs.get(n.id)).mode === RETURN_TO_REJECTOR)
  if (!enabled.length) return errors
  for (const node of nodes) {
    if (own(configs.get(node.id) || {}, REJECT_RETURN_RUNTIME_KEY)) push(node, '直返定义不能包含实例运行段或同名业务属性，请先迁移')
  }
  const byId = new Map(nodes.map(n => [n.id, n]))
  const pass = edge => edge.properties?.skipType === 'PASS'
  for (const source of enabled) {
    const reject = edges.filter(e => e.sourceNodeId === source.id && e.properties?.skipType === 'REJECT')
    const target = source.properties?.anyNodeSkip || (reject.length === 1 ? reject[0].targetNodeId : null)
    try {
      if (!target || target === source.id) throw new Error('需要唯一且早于本节点的实际驳回目标')
      let code = target
      const visited = new Set()
      while (true) {
        const node = byId.get(code), props = node?.properties || {}, cfg = configs.get(code) || {}
        if (!node || visited.has(code)) throw new Error('返回区间包含循环或缺失节点')
        visited.add(code)
        if (node.type !== 'between' || props.eventType || (props.nodeRatio != null && Number(props.nodeRatio) !== 0)
            || ['sequence', 'group'].includes(props.cooperateType) || own(cfg, 'groupSign')
            || nodes.some(n => n.properties?.attachedToNodeCode === code)) {
          throw new Error('首版仅支持无事件、无边界事件的普通单人/或签线性人工区间')
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
