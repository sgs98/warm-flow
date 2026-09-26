/**
 * 设计器模式转换的坐标重排工具。
 *
 * 经典(CLASSICS) → 仿钉钉(MIMIC) 需要把自由布局重排为上下结构：
 * - y 按拓扑分层（最长路径），层高 LAYER_HEIGHT；
 * - x 先取「双亲均值」（汇聚节点自然回到中轴），再按层以均值为中心、
 *   MIN_GAP 间距展开消解碰撞；
 * - 同时补齐 properties.width/height（仿钉钉边线路由依赖这些尺寸）。
 *
 * 仿钉钉 → 经典 不需要重排（经典是自由布局，竖排坐标可直接用）。
 *
 * @author warm
 * @since 2026/9/26
 */

const LAYER_HEIGHT = 140
const MIN_GAP = 300
const CENTER_X = 700
const TOP_Y = 100

// 仿钉钉各节点类型的尺寸（边线路由与锚点计算依赖）
const MIMIC_SIZE = {
  0: { width: 76, height: 40 },
  2: { width: 76, height: 40 },
  1: { width: 260, height: 76 },
  3: { width: 36, height: 36 },
  4: { width: 36, height: 36 },
  5: { width: 36, height: 36 }
}

/**
 * 将节点/连线重排为仿钉钉上下结构布局。
 *
 * @param {Array} nodes LogicFlow 节点数组（原地修改 x/y/text/properties）
 * @param {Array} edges LogicFlow 连线数组（用于拓扑分层，不修改）
 * @returns {boolean} 是否成功重排（找不到开始节点时返回 false）
 */
export const relayoutToMimic = (nodes, edges) => {
  if (!nodes || nodes.length === 0) {
    return false
  }
  const nodeById = new Map(nodes.map(node => [node.id, node]))
  const start = nodes.find(node => node.type === 'start')
  if (!start) {
    return false
  }

  // 邻接表
  const parents = new Map(nodes.map(node => [node.id, []]))
  const children = new Map(nodes.map(node => [node.id, []]))
  edges.forEach(edge => {
    if (nodeById.has(edge.sourceNodeId) && nodeById.has(edge.targetNodeId)) {
      parents.get(edge.targetNodeId).push(edge.sourceNodeId)
      children.get(edge.sourceNodeId).push(edge.targetNodeId)
    }
  })

  // 1) 拓扑分层：y = 最长路径层数 × 层高（防环：只允许沿未定层节点前进）
  const layer = new Map()
  const queue = [start.id]
  layer.set(start.id, 0)
  while (queue.length > 0) {
    const current = queue.shift()
    const nextLayer = layer.get(current) + 1
    children.get(current).forEach(childId => {
      if (!layer.has(childId)) {
        layer.set(childId, nextLayer)
        queue.push(childId)
      } else if (layer.get(childId) < nextLayer) {
        // 环或汇聚回边：取最长路径，重新入队其子节点
        layer.set(childId, nextLayer)
        queue.push(childId)
      }
    })
  }

  // 2) 拓扑序（按层升序、同层稳定），x 先取双亲均值
  const ordered = [...nodes].sort((a, b) => (layer.get(a.id) ?? 0) - (layer.get(b.id) ?? 0))
  ordered.forEach(node => {
    const parentIds = parents.get(node.id)
    if (!parentIds || parentIds.length === 0) {
      node.x = node === start ? CENTER_X : (node.x ?? CENTER_X)
    } else {
      const parentXs = parentIds.map(id => nodeById.get(id).x).filter(x => x !== undefined)
      node.x = parentXs.length > 0
        ? parentXs.reduce((sum, x) => sum + x, 0) / parentXs.length
        : CENTER_X
    }
    node.y = TOP_Y + (layer.get(node.id) ?? 0) * LAYER_HEIGHT
  })

  // 3) 逐层消解碰撞：保持该层均值不动，以 MIN_GAP 均匀展开
  const byLayer = new Map()
  ordered.forEach(node => {
    const key = layer.get(node.id) ?? 0
    if (!byLayer.has(key)) {
      byLayer.set(key, [])
    }
    byLayer.get(key).push(node)
  })
  ;[...byLayer.keys()].sort((a, b) => a - b).forEach(key => {
    const row = byLayer.get(key)
    if (row.length <= 1) {
      return
    }
    row.sort((a, b) => a.x - b.x)
    const mean = row.reduce((sum, node) => sum + node.x, 0) / row.length
    row.forEach((node, index) => {
      node.x = mean + (index - (row.length - 1) / 2) * MIN_GAP
    })
  })

  // 4) 文本坐标与仿钉钉尺寸补齐
  ordered.forEach(node => {
    if (node.text) {
      node.text.x = node.x
      node.text.y = node.y
    }
    const size = MIMIC_SIZE[node.type]
    if (size) {
      node.properties = node.properties || {}
      node.properties.width = size.width
      node.properties.height = size.height
    }
  })
  return true
}

/**
 * 丢弃连线上保存的旧折点（坐标重排后旧折点不再贴合新位置），
 * 仿钉钉模式下由 updateEdges 按新坐标重建边路径。
 *
 * @param {Array} edges LogicFlow 连线数组（原地修改）
 */
export const clearEdgePoints = edges => {
  edges.forEach(edge => {
    delete edge.pointsList
    delete edge.startPoint
    delete edge.endPoint
  })
}
