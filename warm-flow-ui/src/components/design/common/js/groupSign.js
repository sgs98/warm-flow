/**
 * 组签配置纯函数：只读写 nodeConfig.groupSign 命名空间。
 *
 * 组定义随 nodeConfig 透传保存，本模块不接触画布、组件状态或后端接口，
 * 便于在 Node 内置测试中验证「保留其它配置、只改 groupSign」的契约。
 */

export const GROUP_SIGN_PARALLEL = 'parallel'
export const GROUP_SIGN_SEQUENCE = 'sequence'

const GROUP_ID_PATTERN = /^[A-Za-z0-9_-]{1,32}$/

/** 统一顺序值：只有 sequence 是顺序，其余（含缺失、非法值、大小写差异）都按并行处理 */
export function normalizeOrder(order) {
  return String(order || '').trim().toLowerCase() === GROUP_SIGN_SEQUENCE
    ? GROUP_SIGN_SEQUENCE : GROUP_SIGN_PARALLEL
}

/** 规范化单个组：id/name 转字符串，members 只保留非空字符串 */
function normalizeGroup(raw) {
  const group = raw && typeof raw === 'object' ? raw : {}
  const members = Array.isArray(group.members)
    ? group.members.filter(member => typeof member === 'string' && member.trim() !== '').map(member => member.trim())
    : []
  return {
    id: group.id === undefined || group.id === null ? '' : String(group.id),
    name: group.name === undefined || group.name === null ? '' : String(group.name),
    members
  }
}

/**
 * 读取组签配置。没有 groupSign 段或结构非法时返回空组列表 + 并行顺序。
 * 兼容旧定义顶层 groups / groupOrder：它们属于自定义 KV，不在这里解释。
 */
export function normalizeGroupSign(nodeConfig) {
  const config = nodeConfig && typeof nodeConfig === 'object' ? nodeConfig : {}
  const section = config.groupSign && typeof config.groupSign === 'object' ? config.groupSign : {}
  return {
    order: normalizeOrder(section.order),
    groups: Array.isArray(section.groups) ? section.groups.map(normalizeGroup) : []
  }
}

/** 只替换 groupSign 段，其它顶层键（variables/serviceTask/event/copy/自定义 KV）原样保留 */
export function serializeGroupSign(nodeConfig, sign) {
  const config = nodeConfig && typeof nodeConfig === 'object' ? { ...nodeConfig } : {}
  const groups = (sign && Array.isArray(sign.groups) ? sign.groups : []).map(group => {
    const serialized = {
      id: String(group.id || '').trim(),
      members: (group.members || []).filter(member => typeof member === 'string' && member.trim() !== '')
        .map(member => member.trim())
    }
    const name = String(group.name || '').trim()
    if (name) serialized.name = name
    return serialized
  })
  config.groupSign = { groups, order: normalizeOrder(sign && sign.order) }
  return config
}

/** 删除 groupSign 段；切换到非组签模式时使用，避免残留组配置 */
export function removeGroupSign(nodeConfig) {
  const config = nodeConfig && typeof nodeConfig === 'object' ? { ...nodeConfig } : {}
  delete config.groupSign
  return config
}

/** 卡片/收起摘要：组签 · N 组 · 并行/顺序 */
export function groupSignSummary(nodeConfig) {
  const sign = normalizeGroupSign(nodeConfig)
  const order = sign.order === GROUP_SIGN_SEQUENCE ? '顺序' : '并行'
  return sign.groups.length > 0 ? `组签 · ${sign.groups.length} 组 · ${order}` : '组签 · 未配置'
}

/**
 * 校验组签结构，返回面向用户的错误文案。
 * 发布前的严格校验仍在后端；这里只拦截设计器内明显无效的配置。
 */
export function validateGroupSign(sign) {
  const errors = []
  const groups = sign && Array.isArray(sign.groups) ? sign.groups : []
  if (groups.length === 0) {
    errors.push('至少配置一个审批组')
    return errors
  }
  const ids = new Set()
  groups.forEach((group, index) => {
    const label = `第 ${index + 1} 组`
    const id = String(group.id || '').trim()
    if (!id) {
      errors.push(`${label}缺少组标识`)
    } else if (!GROUP_ID_PATTERN.test(id)) {
      errors.push(`组标识「${id}」只能包含字母、数字、下划线和中划线，长度 1-32`)
    } else if (ids.has(id)) {
      errors.push(`组标识「${id}」重复`)
    }
    ids.add(id)
    const members = (group.members || []).filter(member => String(member || '').trim() !== '')
    if (members.length === 0) {
      errors.push(`${label}至少选择一名成员`)
    }
  })
  return errors
}
