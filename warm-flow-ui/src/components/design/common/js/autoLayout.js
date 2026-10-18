/**
 * 经典模式「一键整理」：把自由摆放的节点重排为分层树状布局。
 *
 * 与 relayout.js 的 relayoutToMimic 的区别：
 * - 输入是画布上实时的节点（含各自真实宽高），不是待转换的 json；
 * - 排布方向恒为「左 → 右」：经典模式的模板与示例流程都是「开始 → 结束」自左向右。
 *   曾经按「画布现有连线走向」推断横竖，但设计器模型可以来回切换——切仿钉钉会把节点
 *   排成竖排，切回经典并不还原坐标，于是整理结果被这些旧坐标继续带成竖排（经典模式
 *   却是上下结构）。方向由设计器模型决定（本模块只服务经典模式），不看旧坐标。
 * - 同列（层）先按双亲重心排序降低连线交叉，再整体回正到「双亲重心均值」：
 *   扇出的兄弟节点对称落在父节点两侧，汇聚节点自然回到主轴，主干不会逐层漂移；
 * - 列宽与间距按真实节点尺寸累加，大小节点混排时不会参差不齐；
 * - 先剔除回边（退回线 REJECT 形成）再分层，否则回边会把主线一层层顶下去，
 *   结束节点会被排到主干中间（这是「最长路径分层」遇上环时的典型塌陷）。
 *
 * @author warm
 * @since 2026/10/18
 */

/** 沿流程方向的起始坐标（首列节点中心） */
const FLOW_ORIGIN = 120
/** 相邻两列（层）之间的空白 */
const LAYER_GAP = 70
/** 同列相邻节点之间的空白 */
const NODE_GAP = 60
/** 垂直流程方向的起始坐标（最外侧节点的边缘） */
const CROSS_ORIGIN = 80
/** 取不到尺寸时的兜底宽高（与经典模式默认节点尺寸一致） */
const FALLBACK_WIDTH = 100
const FALLBACK_HEIGHT = 80

const sizeOf = (node, key, fallback) => {
  const value = node?.[key]
  return Number.isFinite(value) && value > 0 ? value : fallback
}

const mean = values => values.reduce((sum, value) => sum + value, 0) / values.length

const edgeKey = (sourceId, targetId) => `${sourceId}\u0000${targetId}`

/**
 * 找出回边：DFS 三色标记，指向「仍在当前搜索栈上」的节点即为回边。
 *
 * @param {Array} nodeIds 节点 id（顺序决定遍历起点）
 * @param {Map} children 邻接表
 * @returns {Set} 回边的 key 集合
 */
const findBackEdges = (nodeIds, children) => {
  const backEdges = new Set()
  const state = new Map(nodeIds.map(id => [id, 0])) // 0 未访问 / 1 在栈上 / 2 已完成
  nodeIds.forEach(rootId => {
    if (state.get(rootId) !== 0) {
      return
    }
    const stack = [{ id: rootId, index: 0 }]
    state.set(rootId, 1)
    while (stack.length > 0) {
      const frame = stack[stack.length - 1]
      const childIds = children.get(frame.id) || []
      if (frame.index >= childIds.length) {
        state.set(frame.id, 2)
        stack.pop()
        continue
      }
      const childId = childIds[frame.index++]
      const childState = state.get(childId)
      if (childState === 1) {
        backEdges.add(edgeKey(frame.id, childId))
      } else if (childState === 0) {
        state.set(childId, 1)
        stack.push({ id: childId, index: 0 })
      }
    }
  })
  return backEdges
}

/** 按前向边做无向连通分量，保持节点在入参里的先后顺序 */
const collectComponents = (nodes, linkedBy) => {
  const seen = new Set()
  const components = []
  nodes.forEach(node => {
    if (seen.has(node.id)) {
      return
    }
    const component = []
    const queue = [node.id]
    seen.add(node.id)
    while (queue.length > 0) {
      const current = queue.shift()
      component.push(current)
      ;(linkedBy.get(current) || []).forEach(nextId => {
        if (!seen.has(nextId)) {
          seen.add(nextId)
          queue.push(nextId)
        }
      })
    }
    components.push(component)
  })
  return components
}

/**
 * 重心法减少同列连线交叉：向下扫描按双亲位置排，向上扫描按子节点位置排。
 *
 * 排序键是邻居在自身列内的相对位置（归一化到 0..1），无邻居的节点保持原位；
 * 排序稳定、冲突时保留上一轮次序，避免来回抖动。
 *
 * @param {Map} columns 层号 → 节点 id 数组（原地排序）
 * @param {Array} layerKeys 层号升序
 * @param {Map} forwardParents id → 双亲 id
 * @param {Map} forwardChildren id → 子节点 id
 */
const orderByBarycenter = (columns, layerKeys, forwardParents, forwardChildren) => {
  const order = new Map()
  const refresh = ids => {
    ids.forEach((id, index) => order.set(id, (index + 1) / (ids.length + 1)))
  }
  columns.forEach(refresh)
  for (let round = 0; round < 4; round++) {
    const downward = round % 2 === 0
    const keys = downward ? layerKeys : [...layerKeys].reverse()
    const neighbors = downward ? forwardParents : forwardChildren
    keys.forEach(key => {
      const ids = columns.get(key)
      if (ids.length <= 1) {
        return
      }
      const current = new Map(ids.map((id, index) => [id, index]))
      const weight = new Map()
      ids.forEach(id => {
        const positions = (neighbors.get(id) || [])
          .map(neighborId => order.get(neighborId))
          .filter(value => Number.isFinite(value))
        weight.set(id, positions.length > 0 ? mean(positions) : order.get(id))
      })
      ids.sort((a, b) => (weight.get(a) - weight.get(b)) || (current.get(a) - current.get(b)))
      refresh(ids)
    })
  }
}

/**
 * 计算分层布局坐标。
 *
 * @param {Array} nodes LogicFlow 节点模型或节点 json（读取 id / type / x / y / width / height）
 * @param {Array} edges LogicFlow 连线（读取 sourceNodeId / targetNodeId）
 * @returns {Array} [{id, x, y}] 节点新的中心坐标；输入为空时返回空数组
 */
export const computeLayeredLayout = (nodes, edges) => {
  if (!Array.isArray(nodes) || nodes.length === 0) {
    return []
  }
  const nodeById = new Map(nodes.map(node => [node.id, node]))
  const nodeIndex = new Map(nodes.map((node, index) => [node.id, index]))
  const children = new Map(nodes.map(node => [node.id, []]))
  const linkedBy = new Map(nodes.map(node => [node.id, []]))
  const edgeKeys = new Set()

  // 1) 建邻接：跳过自环与悬空边，同一条边只记一次
  ;(edges || []).forEach(edge => {
    const sourceId = edge?.sourceNodeId
    const targetId = edge?.targetNodeId
    if (
      !nodeById.has(sourceId) ||
      !nodeById.has(targetId) ||
      sourceId === targetId ||
      edgeKeys.has(edgeKey(sourceId, targetId))
    ) {
      return
    }
    edgeKeys.add(edgeKey(sourceId, targetId))
    children.get(sourceId).push(targetId)
    linkedBy.get(sourceId).push(targetId)
    linkedBy.get(targetId).push(sourceId)
  })

  // 2) 剔除回边：只按前向边分层，退回线不会参与分层
  const backEdges = findBackEdges(nodes.map(node => node.id), children)
  const forwardChildren = new Map(nodes.map(node => [node.id, []]))
  const forwardParents = new Map(nodes.map(node => [node.id, []]))
  edgeKeys.forEach(key => {
    if (backEdges.has(key)) {
      return
    }
    const [sourceId, targetId] = key.split('\u0000')
    forwardChildren.get(sourceId).push(targetId)
    forwardParents.get(targetId).push(sourceId)
  })

  // 3) 连通分量：含 start 的主分量排最前，其余分量接在它后面（孤立节点不会挤进首列）
  const startNode = nodes.find(node => node.type === 'start')
  const components = collectComponents(nodes, linkedBy)
  if (startNode) {
    const index = components.findIndex(component => component.includes(startNode.id))
    if (index > 0) {
      components.unshift(components.splice(index, 1)[0])
    }
  }

  const layer = new Map()
  let layerOffset = 0
  components.forEach(componentIds => {
    // 分量内起点：无前向入边的节点，一个都没有（纯环）时退化为分量首个节点
    let roots = componentIds.filter(id => forwardParents.get(id).length === 0)
    if (roots.length === 0) {
      roots = [componentIds[0]]
    }
    const maxLayer = componentIds.length
    const queue = []
    roots.forEach(id => {
      layer.set(id, layerOffset)
      queue.push(id)
    })
    // 最长路径分层：懒更新保证汇聚节点落在最深的双亲之后；去掉回边后必然收敛
    while (queue.length > 0) {
      const current = queue.shift()
      const nextLayer = layer.get(current) + 1
      forwardChildren.get(current).forEach(childId => {
        if (!layer.has(childId)) {
          layer.set(childId, nextLayer)
          queue.push(childId)
        } else if (layer.get(childId) < nextLayer && nextLayer - layerOffset <= maxLayer) {
          layer.set(childId, nextLayer)
          queue.push(childId)
        }
      })
    }
    let localMax = layerOffset
    componentIds.forEach(id => {
      if (layer.has(id) && layer.get(id) > localMax) {
        localMax = layer.get(id)
      }
    })
    componentIds.forEach(id => {
      if (!layer.has(id)) {
        layer.set(id, localMax)
      }
    })
    layerOffset = localMax + 1
  })

  // 4) 排布方向固定为左 → 右：flow = x 方向坐标，cross = y 方向坐标
  const flowOf = node => Number(node.x) || 0
  const crossOf = node => Number(node.y) || 0
  const flowSizeOf = node => sizeOf(node, 'width', FALLBACK_WIDTH)
  const crossSizeOf = node => sizeOf(node, 'height', FALLBACK_HEIGHT)

  // 5) 按层分列，列内先按原始坐标排序，保住用户在画布上的先后次序
  const columns = new Map()
  nodes.forEach(node => {
    const key = layer.get(node.id)
    if (!columns.has(key)) {
      columns.set(key, [])
    }
    columns.get(key).push(node.id)
  })
  const layerKeys = [...columns.keys()].sort((a, b) => a - b)
  columns.forEach(ids => {
    ids.sort((a, b) => (crossOf(nodeById.get(a)) - crossOf(nodeById.get(b))) || (nodeIndex.get(a) - nodeIndex.get(b)))
  })

  // 6) 重心法迭代排序：让兄弟分支的次序尽量少交叉
  orderByBarycenter(columns, layerKeys, forwardParents, forwardChildren)

  // 7) 垂直方向的坐标：双亲重心 → 列内按真实尺寸消解重叠 → 整列回正到重心均值
  const crossPos = new Map()
  layerKeys.forEach(key => {
    const ids = columns.get(key)
    const desired = new Map()
    ids.forEach(id => {
      const parentCross = (forwardParents.get(id) || [])
        .map(parentId => crossPos.get(parentId))
        .filter(value => Number.isFinite(value))
      // 单亲时等于双亲坐标（主干自然是一条直线），多亲时取重心（汇聚对称收口）
      desired.set(id, parentCross.length > 0 ? mean(parentCross) : crossOf(nodeById.get(id)))
    })
    let cursorEdge = -Infinity
    const placed = new Map()
    ids.forEach(id => {
      const halfHeight = crossSizeOf(nodeById.get(id)) / 2
      const want = desired.get(id)
      const center = cursorEdge === -Infinity ? want : Math.max(want, cursorEdge + NODE_GAP + halfHeight)
      placed.set(id, center)
      cursorEdge = center + halfHeight
    })
    // 整列平移（而非逐点吸附）：扇出的兄弟对称落在父节点两侧，主轴不会被推歪
    const offset = mean(ids.map(id => desired.get(id))) - mean(ids.map(id => placed.get(id)))
    ids.forEach(id => crossPos.set(id, placed.get(id) + offset))
  })

  // 8) 沿流程方向的坐标：每列宽度取该列最宽的节点，逐列累加
  const flowPos = new Map()
  let flowCursor = FLOW_ORIGIN
  layerKeys.forEach(key => {
    const ids = columns.get(key)
    const columnSize = ids.reduce((max, id) => Math.max(max, flowSizeOf(nodeById.get(id))), 0)
    const center = flowCursor + columnSize / 2
    ids.forEach(id => flowPos.set(id, center))
    flowCursor += columnSize + LAYER_GAP
  })

  // 9) 整体归位：最外侧节点从 CROSS_ORIGIN 开始
  let minCross = Infinity
  nodes.forEach(node => {
    minCross = Math.min(minCross, crossPos.get(node.id) - crossSizeOf(node) / 2)
  })
  const crossShift = Number.isFinite(minCross) ? CROSS_ORIGIN - minCross : 0

  return nodes.map(node => {
    const flow = Math.round(flowPos.get(node.id))
    const cross = Math.round(crossPos.get(node.id) + crossShift)
    return {
      id: node.id,
      x: flow,
      y: cross,
    }
  })
}

/**
 * 把布局坐标写回画布。
 *
 * 只移动节点：连线端点锚在节点上，LogicFlow 会随节点移动重新计算正交路由；
 * 连线标签由调用方用 resetEdgeTextPositions(lf, true) 按新几何统一重算。
 *
 * @param {Object} lf LogicFlow 实例
 * @param {Array} positions computeLayeredLayout 的结果
 * @returns {boolean} 是否有节点被移动
 */
export const applyLayeredLayout = (lf, positions) => {
  if (!lf || !lf.graphModel || !Array.isArray(positions) || positions.length === 0) {
    return false
  }
  let moved = false
  positions.forEach(position => {
    if (!lf.graphModel.getNodeModelById(position.id)) {
      return
    }
    // isIgnoreRule=true：整体重排不需要逐点走「移动后是否压住其它节点」的单点规则
    lf.graphModel.moveNode2Coordinate(position.id, position.x, position.y, true)
    moved = true
  })
  return moved
}

/**
 * 为每条「前进边」计算一条清爽的正交折线，并给出条件标签的落点。
 *
 * 默认路由在「同列内回折 + 分支上下错开」的图上会绕出 5 段以上的折线、标签落在路径中段，
 * 于是这里显式指定折线：从源节点右侧出、在「两列中点 x」处做竖直段、在目标节点左侧入，
 * 标签贴在起点右侧、落在第一段水平线上，避免绕远与标签飘到中段。
 *
 * 非前进边（回退线 REJECT 等：目标在源左侧或同列）交给 LogicFlow 默认路由，不强行改造。
 *
 * @param {Array} positions computeLayeredLayout 的结果 [{id, x, y}]（节点中心坐标）
 * @param {Map} sizeById 节点 id → {width, height}（画布上节点的真实尺寸）
 * @param {Array} edges [{id, sourceNodeId, targetNodeId}]
 * @returns {Map} 边 id → { points: [{x, y}...], label: {x, y} | null }
 */
export const computeEdgeRoutes = (positions, sizeById, edges) => {
  const posById = new Map(positions.map(p => [p.id, p]))
  const routes = new Map()
  edges.forEach(edge => {
    const s = posById.get(edge.sourceNodeId)
    const t = posById.get(edge.targetNodeId)
    if (!s || !t) {
      return
    }
    const ss = sizeById.get(edge.sourceNodeId) || { width: FALLBACK_WIDTH, height: FALLBACK_HEIGHT }
    const ts = sizeById.get(edge.targetNodeId) || { width: FALLBACK_WIDTH, height: FALLBACK_HEIGHT }
    const sRight = s.x + ss.width / 2
    const tLeft = t.x - ts.width / 2
    // 非前进边：不改，留给默认路由
    if (tLeft + ts.width / 2 <= sRight + ss.width / 2) {
      return
    }
    const midX = Math.round((sRight + tLeft) / 2)
    const sy = Math.round(s.y)
    const ty = Math.round(t.y)
    const points = [
      { x: Math.round(sRight), y: sy },
      { x: midX, y: sy },
      { x: midX, y: ty },
      { x: Math.round(tLeft), y: ty },
    ]
    // 标签：贴起点右侧，落在第一段水平线上方一点（条件分支名如 day>3 自然落在分支起点）
    const label = { x: Math.round(sRight + 14), y: Math.round(s.y - 12) }
    routes.set(edge.id, { points, label })
  })
  return routes
}
