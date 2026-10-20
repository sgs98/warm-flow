/**
 * 保存前流程图检查
 *
 * 规则与后端发布校验「结构部分」（`FlowConfigUtil.validateGraph` +
 * `DefServiceImpl.checkFlowLegal`）对齐：目的不是替代后端校验，而是在点保存时就把
 * 必然导致「发布失败 / 流程跑不下去」的结构问题指出来，避免用户画完、发布时才发现。
 *
 * 只实现**从画布数据（nodes + edges）即可判定**的结构规则；事件节点的字段级校验
 * （事件类型合法性、定时参数、启动 / 结束事件拓扑）依赖后端 `EventType` 语义，
 * 由属性面板自身的 rules 与后端校验负责，这里不重复实现，避免两处漂移。
 */

/** 逻辑跳转类型：通过 */
const SKIP_TYPE_PASS = 'PASS'

/**
 * 取节点展示名：优先画布文本，其次节点名属性，最后回落节点编码
 * @param {Object} node 画布节点数据
 * @returns {string} 展示名
 */
const labelOf = (node) => {
  if (!node) {
    return '未知节点'
  }
  const text = node.text && typeof node.text === 'object' ? node.text.value : node.text
  return text || node.properties?.nodeName || node.id || '未命名节点'
}

/**
 * 检查流程图结构
 *
 * @param {Object} graphData `lf.getGraphData()` 的数据
 * @returns {Array<{id: string|null, name: string, message: string}>}
 *          问题列表；id 为空表示流程级问题（画布上无法定位到具体节点）
 */
export const checkFlowNodes = (graphData) => {
  const nodes = graphData?.nodes || []
  const edges = graphData?.edges || []
  const errors = []
  const push = (node, message) => errors.push({
    id: node?.id || null,
    name: node ? labelOf(node) : '流程',
    message
  })

  // 未绘制流程图（后端 NOT_DRAW_FLOW_ERROR）
  if (nodes.length === 0) {
    push(null, '未绘制流程图，不可发布')
    return errors
  }

  const nodeById = new Map(nodes.map(node => [node.id, node]))
  // 出线 / 入线索引：一次遍历供后续多个规则复用
  const outgoing = new Map()
  const incoming = new Map()
  edges.forEach(edge => {
    outgoing.set(edge.sourceNodeId, [...(outgoing.get(edge.sourceNodeId) || []), edge])
    incoming.set(edge.targetNodeId, [...(incoming.get(edge.targetNodeId) || []), edge])
  })

  // 节点编码缺失（后端 LOST_NODE_CODE）：画布节点 id 即节点编码
  nodes.forEach(node => {
    if (!String(node.id ?? '').trim()) {
      push(node, '节点编码缺失')
    }
  })

  // 开始 / 结束节点数量（后端 LOST_START_NODE / MUL_START_NODE）
  const startNodes = nodes.filter(node => node.type === 'start')
  const endNodes = nodes.filter(node => node.type === 'end')
  if (startNodes.length === 0) {
    push(null, '缺少开始节点')
  } else if (startNodes.length > 1) {
    push(null, `存在 ${startNodes.length} 个开始节点，同一流程只能有一个`)
  }
  if (endNodes.length === 0) {
    push(null, '缺少结束节点')
  }

  // 固定节点连线约束（后端 FIXED_NODE_LINE_ERROR）
  startNodes.forEach(node => {
    if ((incoming.get(node.id) || []).length > 0) {
      push(node, '开始节点不允许有入线')
    }
  })
  endNodes.forEach(node => {
    if ((outgoing.get(node.id) || []).length > 0) {
      push(node, '结束节点不允许有出线')
    }
  })

  // 事件属性承载节点约束（后端 FIXED_NODE_NO_EVENT / EVENT_NODE_TYPE_INVALID）
  nodes.forEach(node => {
    if (!node.properties?.eventType) {
      return
    }
    if (['start', 'end'].includes(node.type)) {
      push(node, '固定开始 / 结束节点不允许配置事件属性')
    } else if (!['between'].includes(node.type)) {
      push(node, '事件属性只能配置在中间节点上')
    }
  })

  // 连线有效性（后端 FLOW_HAVE_USELESS_SKIP / NULL_NODE_CODE）
  edges.forEach(edge => {
    const source = nodeById.get(edge.sourceNodeId)
    const target = nodeById.get(edge.targetNodeId)
    if (!source) {
      push(target, '存在无用的跳转：连线起点已不存在')
    }
    if (!target) {
      push(source, '存在无效的跳转：连线目标节点已不存在')
    }
  })

  nodes.forEach(node => {
    const out = outgoing.get(node.id) || []
    const isEnd = node.type === 'end'

    // 死路：非结束节点没有出线，流程会停在这里（后端运行时 NULL_SKIP_TYPE 的成因）
    if (!isEnd && out.length === 0) {
      push(node, '没有后续节点，流程会停在这里')
      return
    }

    // 服务任务节点的出线只能是通过线（后端 SERVICE_TASK_ONLY_PASS）
    if (node.type === 'service' && out.some(edge => edge.properties?.skipType !== SKIP_TYPE_PASS)) {
      push(node, '服务任务节点的出线只能是审批通过线')
    }

    // 不可同时通过 / 退回到多个中间节点，必须先流转到网关（后端 MUL_SKIP_BETWEEN）
    // 与后端 FlowConfigUtil.checkSkipNode 严格对齐：只统计「中间节点 → 中间节点」的出线。
    // 网关（互斥 serial / 并行 parallel / 包容 inclusive）扇出到多个中间节点是合法拓扑，
    // 早先这里只判断了目标节点类型，把网关自身当成源也计入，导致任何多分支网关都误报、保存被挡。
    const toBetween = node.type === 'between'
      ? out.filter(edge => nodeById.get(edge.targetNodeId)?.type === 'between')
      : []
    const passCount = toBetween.filter(edge => edge.properties?.skipType === SKIP_TYPE_PASS).length
    const rejectCount = toBetween.length - passCount
    if (passCount > 1 || rejectCount > 1) {
      push(node, '不可同时通过或退回到多个中间节点，需先经过网关')
    }
  })

  return errors
}
