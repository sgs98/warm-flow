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
import {isGateWay, isLabelOnNode} from '@/components/design/common/js/tool.js'

const LAYER_HEIGHT = 140
const MIN_GAP = 300
const CENTER_X = 700
const TOP_Y = 100
// 经典网关的标签画在菱形下方（与 tool.js 旧数据回退约定一致）
const GATEWAY_LABEL_OFFSET = 40

// 仿钉钉各节点类型的尺寸（边线路由与锚点计算依赖）
const MIMIC_SIZE = {
  start: { width: 76, height: 40 },
  end: { width: 76, height: 40 },
  between: { width: 260, height: 76 },
  serial: { width: 36, height: 36 },
  parallel: { width: 36, height: 36 },
  inclusive: { width: 36, height: 36 }
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
  const maxLayer = nodes.length
  const queue = [start.id]
  layer.set(start.id, 0)
  while (queue.length > 0) {
    const current = queue.shift()
    const nextLayer = layer.get(current) + 1
    children.get(current).forEach(childId => {
      if (!layer.has(childId)) {
        layer.set(childId, nextLayer)
        queue.push(childId)
      } else if (layer.get(childId) < nextLayer && nextLayer <= maxLayer) {
        // 汇聚回边取最长路径；异常环路受 maxLayer 限制，避免转换无限循环。
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
      // LogicFlow 会同时读取节点自身尺寸和 properties 尺寸；转换时两处必须保持一致，
      // 否则保存后重新加载可能按旧尺寸计算文本与边线锚点。
      node.width = size.width
      node.height = size.height
      node.properties = node.properties || {}
      node.properties.width = size.width
      node.properties.height = size.height
    }
  })
  return true
}

/**
 * 把仿钉钉节点数据归一到经典模式：清尺寸 + 回正网关标签。
 *
 * 尺寸：经典模式的节点尺寸本该由注册元素（矩形 / 菱形 / 圆形）决定，但 RectNodeModel.setAttributes
 * 会把 properties.width/height 重新应用回节点尺寸；「仿钉钉 → 经典」若不清理，
 * 审批卡会保持仿钉钉的 260x76，与经典默认的 100x80 不一致。
 *
 * 标签：仿钉钉把节点文本放在节点中心，转回经典后网关标签会压住菱形（经典网关标签约定在
 * 菱形下方 40px）；经典模式标签不可拖拽，回正不会覆盖用户意图。
 *
 * @param {Array} nodes LogicFlow 节点数组（原地修改）
 */
export const normalizeClassicNodes = nodes => {
  if (!nodes || nodes.length === 0) {
    return
  }
  nodes.forEach(node => {
    delete node.width
    delete node.height
    if (node.properties) {
      delete node.properties.width
      delete node.properties.height
    }
    if (isGateWay(node.type) && node.text && node.text.value) {
      node.text.x = node.x
      node.text.y = node.y + GATEWAY_LABEL_OFFSET
    }
  })
}

/**
 * 重置画布历史，把转换后的画布状态作为新的撤销基准。
 *
 * 模式转换会通过 updateEdges 删除并重建全部连线，这些中间状态会被 LogicFlow 记进历史；
 * 转换后按「上一步」会退回「节点已是新模型、连线还是旧模型路由」的错乱状态。
 * 这里在转换完成后重建基准：转换本身不可撤销，之后的编辑仍可正常撤销与重做。
 *
 * @param {Object} lf LogicFlow 实例
 */
export const resetHistory = lf => {
  const history = lf?.history
  if (!history || typeof history.destroy !== 'function' || typeof history.watch !== 'function') {
    return
  }
  // destroy 清空 undos/redos/curData 并停掉旧观察器，watch 以当前画布重新建立基准
  history.destroy()
  history.watch(lf.graphModel)
}

/**
 * 校正连线标签坐标（设计器不允许拖拽标签，坐标一律由连线几何算出，重置不会丢失用户意图）。
 *
 * @param {Object} lf LogicFlow 实例
 * @param {boolean} force true=按当前连线几何全部重算（布局转换后旧坐标已属于上一个布局）；
 *                        false=只修正压在节点上的标签，避免标签文字与节点文字叠加
 */
export const resetEdgeTextPositions = (lf, force = false) => {
  if (!lf || !lf.graphModel) {
    return
  }
  const nodeBoxes = lf.graphModel.nodes.map(node => ({
    x: node.x,
    y: node.y,
    width: node.width,
    height: node.height
  }))
  lf.graphModel.edges.forEach(edge => {
    const text = edge.text
    if (!text || !text.value || !Number.isFinite(text.x) || !Number.isFinite(text.y)) {
      return
    }
    if (force || isLabelOnNode(text, nodeBoxes)) {
      edge.resetTextPosition()
    }
  })
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
