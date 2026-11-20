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

/** 驳回发起节点位于并行/包容分支内时按原流程重走；已汇聚的返回区间允许直返。 */
export function isParallelReplayInterval(graph = {}, sourceId) {
  return isParallelBranchNode(graph, sourceId)
}

/** 从起始节点沿通过线能否到达目标节点（排他网关选路用；节点类型由区间遍历再校验）。 */
function reaches(edges, from, to) {
  const pass = edge => edge.properties?.skipType === 'PASS'
  const visited = new Set()
  const queue = [from]
  while (queue.length) {
    const code = queue.shift()
    if (!code || visited.has(code)) continue
    visited.add(code)
    if (code === to) return true
    for (const edge of edges) {
      if (edge.sourceNodeId === code && pass(edge)) queue.push(edge.targetNodeId)
    }
  }
  return false
}

/** 节点是否位于尚未汇聚的并行/包容分支内，兼容嵌套网关与含环图。 */
export function isParallelBranchNode(graph = {}, nodeId) {
  return parallelBranchNodes(graph).has(nodeId)
}

function parallelBranchNodes(graph = {}) {
  const nodes = graph.nodes || [], edges = graph.edges || []
  const pass = edge => edge.properties?.skipType === 'PASS'
  const byId = new Map(nodes.map(n => [n.id, n]))
  const incoming = new Map()
  const outgoing = new Map()
  for (const edge of edges) {
    if (!pass(edge)) continue
    if (!incoming.has(edge.targetNodeId)) incoming.set(edge.targetNodeId, [])
    incoming.get(edge.targetNodeId).push(edge)
    if (!outgoing.has(edge.sourceNodeId)) outgoing.set(edge.sourceNodeId, [])
    outgoing.get(edge.sourceNodeId).push(edge)
  }
  const forks = nodes.filter(node => ['parallel', 'inclusive'].includes(node.type)
    && (outgoing.get(node.id) || []).length > 1)
  const branchNodes = new Set()
  if (!forks.length) return branchNodes
  const dominators = postDominators(byId, incoming, outgoing)
  let terminalDominators = null
  for (const fork of forks) {
    let current = dominators
    if (!current.get(fork.id).size) {
      const entries = postDominators(byId, outgoing, incoming, new Set([fork.id]), false, true)
      const headers = new Set(edges.filter(edge => pass(edge) && entries.get(edge.sourceNodeId)?.has(edge.targetNodeId))
        .map(edge => edge.targetNodeId))
      const exits = new Set([...headers].filter(header => ![...headers].some(other => other !== header && entries.get(header)?.has(other))
        && (outgoing.get(fork.id) || []).every(edge => reaches(edges, edge.targetNodeId, header))))
      if (exits.size) {
        current = postDominators(byId, incoming, outgoing, exits)
      } else {
        if (terminalDominators === null) {
          terminalDominators = postDominators(byId, incoming, outgoing, terminalComponentExits(byId, incoming, outgoing))
        }
        current = terminalDominators
      }
    }
    let common = null
    for (const edge of outgoing.get(fork.id) || []) {
      const next = current.get(edge.targetNodeId) || new Set()
      common = common === null ? new Set(next) : new Set([...common].filter(code => next.has(code)))
    }
    const joins = new Set([...common].filter(code => code !== fork.id
      && ['parallel', 'inclusive'].includes(byId.get(code).type) && (incoming.get(code) || []).length > 1))
    const visited = new Set()
    const queue = (outgoing.get(fork.id) || []).map(edge => edge.targetNodeId)
    while (queue.length) {
      const code = queue.shift()
      if (!byId.has(code) || visited.has(code) || code === fork.id || joins.has(code)) continue
      visited.add(code)
      branchNodes.add(code)
      for (const edge of outgoing.get(code) || []) queue.push(edge.targetNodeId)
    }
  }
  return branchNodes
}

function terminalComponentExits(nodes, incoming, outgoing) {
  const visited = new Set(), order = [], path = []
  for (const code of nodes.keys()) {
    if (visited.has(code)) continue
    visited.add(code)
    path.push({ code, successors: (outgoing.get(code) || [])[Symbol.iterator]() })
    while (path.length) {
      const current = path[path.length - 1]
      const next = current.successors.next()
      if (next.done) {
        order.push(path.pop().code)
        continue
      }
      const target = next.value.targetNodeId
      if (nodes.has(target) && !visited.has(target)) {
        visited.add(target)
        path.push({ code: target, successors: (outgoing.get(target) || [])[Symbol.iterator]() })
      }
    }
  }
  const assigned = new Set(), exits = new Set()
  for (const code of order.reverse()) {
    if (assigned.has(code)) continue
    assigned.add(code)
    const component = new Set(), pending = [code]
    while (pending.length) {
      const current = pending.pop()
      component.add(current)
      for (const edge of incoming.get(current) || []) {
        const previous = edge.sourceNodeId
        if (nodes.has(previous) && !assigned.has(previous)) {
          assigned.add(previous)
          pending.push(previous)
        }
      }
    }
    if ([...component].every(current => (outgoing.get(current) || []).every(edge => component.has(edge.targetNodeId)))) {
      for (const current of component) exits.add(current)
    }
  }
  return exits
}

function postDominators(nodes, incoming, outgoing, extraExits = new Set(), includeEnds = true, reverse = false) {
  const exits = new Set(extraExits)
  if (includeEnds) {
    for (const code of nodes.keys()) {
      if (!(outgoing.get(code) || []).length) exits.add(code)
    }
  }
  const reachesExit = new Set()
  const queue = [...exits]
  while (queue.length) {
    const code = queue.shift()
    if (reachesExit.has(code)) continue
    reachesExit.add(code)
    for (const edge of incoming.get(code) || []) {
      const previous = reverse ? edge.targetNodeId : edge.sourceNodeId
      if (nodes.has(previous)) queue.push(previous)
    }
  }
  const dominators = new Map([...nodes.keys()].map(code => [code, !reachesExit.has(code) ? new Set()
    : exits.has(code) ? new Set([code]) : new Set(reachesExit)]))
  queue.push(...reachesExit)
  const queued = new Set(reachesExit)
  while (queue.length) {
    const code = queue.shift()
    queued.delete(code)
    const successors = outgoing.get(code) || []
    if (!successors.length || exits.has(code)) continue
    let shared = null
    for (const edge of successors) {
      const nextCode = reverse ? edge.sourceNodeId : edge.targetNodeId
      if (reverse && !reachesExit.has(nextCode)) continue
      const next = dominators.get(nextCode) || new Set()
      if (shared === null) {
        shared = new Set(next)
      } else {
        for (const candidate of shared) {
          if (!next.has(candidate)) shared.delete(candidate)
        }
      }
    }
    if (shared === null) continue
    shared.add(code)
    const previous = dominators.get(code)
    if (shared.size !== previous.size || [...shared].some(candidate => !previous.has(candidate))) {
      dominators.set(code, shared)
      for (const edge of incoming.get(code) || []) {
        const before = reverse ? edge.targetNodeId : edge.sourceNodeId
        if (reachesExit.has(before) && !queued.has(before)) {
          queued.add(before)
          queue.push(before)
        }
      }
    }
  }
  return dominators
}

/** target → source 的任意 PASS 路径是否经过并行 / 包容网关。 */
function crossesParallel(byId, outgoing, target, source) {
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
    for (const edge of outgoing.get(code) || []) queue.push([edge.targetNodeId, crossed])
  }
  return false
}

function nodesReachingSource(incoming, source) {
  const reachable = new Set(), pending = [source]
  while (pending.length) {
    const code = pending.shift()
    if (reachable.has(code)) continue
    reachable.add(code)
    for (const edge of incoming.get(code) || []) pending.push(edge.sourceNodeId)
  }
  return reachable
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
  const branchNodes = parallelBranchNodes(graph)
  const byId = new Map(nodes.map(n => [n.id, n]))
  const pass = edge => edge.properties?.skipType === 'PASS'
  const outgoing = new Map(), incoming = new Map(), rejects = new Map()
  const attachedNodes = new Set(nodes.map(node => node.properties?.attachedToNodeCode).filter(Boolean))
  for (const edge of edges) {
    if (pass(edge)) {
      if (!outgoing.has(edge.sourceNodeId)) outgoing.set(edge.sourceNodeId, [])
      outgoing.get(edge.sourceNodeId).push(edge)
      if (!incoming.has(edge.targetNodeId)) incoming.set(edge.targetNodeId, [])
      incoming.get(edge.targetNodeId).push(edge)
    } else if (edge.properties?.skipType === 'REJECT') {
      if (!rejects.has(edge.sourceNodeId)) rejects.set(edge.sourceNodeId, [])
      rejects.get(edge.sourceNodeId).push(edge)
    }
  }
  for (const source of enabled) {
    const reject = rejects.get(source.id) || []
    const target = source.properties?.anyNodeSkip || (reject.length === 1 ? reject[0].targetNodeId : null)
    try {
      // 没有静态目标时允许保存：驳回目标可由办理人运行时指定，路径合法性由引擎在
      // 驳回事务内校验（validateRejectReturnPath）。仅静态目标存在时提前预检路径。
      if (!target) continue
      if (target === source.id) throw new Error('必须返回之前的人工节点')
      if (source.type !== 'between' || source.properties?.eventType) throw new Error('驳回发起节点必须是普通人工节点')
      // 目标必须是普通人工节点（网关只允许作为区间内的中转）
      const targetNode = byId.get(target)
      if (!targetNode || targetNode.type !== 'between' || targetNode.properties?.eventType) {
        throw new Error('驳回目标必须是普通人工节点')
      }
      if (branchNodes.has(source.id)) continue
      const reachingSource = nodesReachingSource(incoming, source.id)
      if (crossesParallel(byId, outgoing, target, source.id)) {
        const interval = new Set(), pending = [target]
        let reachedSource = false
        while (pending.length) {
          const code = pending.shift()
          if (interval.has(code)) continue
          interval.add(code)
          const node = byId.get(code), properties = node?.properties || {}
          if (!node || node.type === 'service' || properties.eventType
              || attachedNodes.has(code)) {
            throw new Error('返回区间不支持服务任务、事件或边界事件')
          }
          if (code === source.id) {
            reachedSource = true
            continue
          }
          const next = outgoing.get(code) || []
          const reaching = next.filter(edge => reachingSource.has(edge.targetNodeId))
          if (node.type === 'serial') {
            if (reaching.length !== 1) throw new Error('排他网关存在多条或没有通往驳回节点的分支')
          } else if (['parallel', 'inclusive'].includes(node.type)) {
            if (!reaching.length || reaching.length !== next.length) throw new Error('并行/包容返回分支必须完整汇聚')
          } else if (node.type !== 'between' || next.length !== 1 || reaching.length !== 1 || next[0].properties?.skipCondition?.trim()) {
            throw new Error('人工返回路径必须唯一且无条件')
          }
          pending.push(...reaching.map(edge => edge.targetNodeId))
        }
        if (!reachedSource) throw new Error('目标必须是之前的人工节点')
        continue
      }
      let code = target
      const visited = new Set()
      while (true) {
        const node = byId.get(code), props = node?.properties || {}
        if (!node || visited.has(code)) throw new Error('返回区间包含循环或缺失节点')
        visited.add(code)
        // 会签 / 票签 / 顺签 / 组签允许被直返跳过；排他网关为单令牌选路、直返不执行区间内节点，
        // 也允许（要求唯一一条通往驳回节点的分支）；并行/包容网关、服务任务、事件与边界事件仍拒绝
        const isExclusiveGateway = node.type === 'serial'
        if (!isExclusiveGateway && (node.type !== 'between' || props.eventType
            || attachedNodes.has(code))) {
          throw new Error('返回区间不支持并行/包容网关、服务任务、事件或边界事件')
        }
        if (code === source.id) break
        const next = outgoing.get(code) || []
        if (isExclusiveGateway) {
          // 直返不执行网关：只要求恰好一条分支能到达驳回节点
          const reaching = next.filter(edge => reachingSource.has(edge.targetNodeId))
          if (reaching.length !== 1) throw new Error('排他网关存在多条或没有通往驳回节点的分支')
          code = reaching[0].targetNodeId
        } else {
          if (next.length !== 1 || next[0].properties?.skipCondition?.trim()) throw new Error('返回区间必须是唯一无条件通过路径')
          code = next[0].targetNodeId
        }
        if ((incoming.get(code) || []).length !== 1) throw new Error('返回区间不能含分支汇聚')
      }
    } catch (error) { push(source, `驳回直返：${error.message}`) }
  }
  return errors
}
