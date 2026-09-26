/**
 * 拖拽磁吸。
 *
 * LogicFlow 的对齐线只负责“画线”、不改变节点坐标，且 snapToGrid 会把坐标量化到网格倍数，
 * 两者叠加就是“看着对齐了、松手又差几像素”的原因。这里在拖拽结束时自己求对齐目标的精确坐标。
 */

// 与 LogicFlow 初始化参数 snaplineEpsilon 取同一值，否则会出现“有线但吸不上”或“无线却跳位”
export const SNAP_EPSILON = 8

/**
 * 计算节点松手后应吸附到的坐标。
 * 优先级与 LogicFlow 对齐线一致：先中心对齐，中心未命中再按包围盒边缘对齐。
 *
 * @param lf LogicFlow 实例
 * @param nodeId 被拖动的节点 id
 * @param epsilon 吸附容差（画布坐标）
 * @returns {{x: number, y: number} | null} 命中时返回目标坐标，未命中返回 null
 */
export function getMagnetPosition(lf, nodeId, epsilon = SNAP_EPSILON) {
  const self = lf.getNodeModelById(nodeId)
  if (!self) {
    return null
  }
  const others = lf.graphModel.nodes.filter((node) => node.id !== nodeId)
  if (!others.length) {
    return null
  }
  const x = getMagnetCoordinate(self, others, 'x', epsilon)
  const y = getMagnetCoordinate(self, others, 'y', epsilon)
  if (x === null && y === null) {
    return null
  }
  return { x: x === null ? self.x : x, y: y === null ? self.y : y }
}

/**
 * 单轴求最近的对齐目标，返回吸附后该轴的节点中心坐标。
 */
function getMagnetCoordinate(self, others, axis, epsilon) {
  const sizeKey = axis === 'x' ? 'width' : 'height'
  const center = self[axis]
  const half = self[sizeKey] / 2

  let best = null
  const accept = (delta, target) => {
    if (Math.abs(delta) <= epsilon && (!best || Math.abs(delta) < Math.abs(best.delta))) {
      best = { delta, target }
    }
  }

  // 1) 中心对齐
  for (const node of others) {
    accept(center - node[axis], node[axis])
  }
  if (best) {
    return best.target
  }

  // 2) 中心没命中，再让我的左/右边去贴它的左/右边
  for (const node of others) {
    const myEdges = [center - half, center + half]
    const itsEdges = [node[axis] - node[sizeKey] / 2, node[axis] + node[sizeKey] / 2]
    for (const myEdge of myEdges) {
      for (const itsEdge of itsEdges) {
        accept(myEdge - itsEdge, center + (itsEdge - myEdge))
      }
    }
  }
  return best ? best.target : null
}
