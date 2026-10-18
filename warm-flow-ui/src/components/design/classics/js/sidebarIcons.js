/**
 * 侧边栏节点图标定义
 * 形状与画布节点 (classics/js/*.js) 保持完全一致；
 * 配色与画布「设计态语义色」(applyClassicDesignColor) 一致，避免发灰、提升辨识度。
 * 所有图标均为内联 SVG 字符串，可直接通过 v-html 渲染
 */

/** 各节点类型语义色（与画布设计态完全一致） */
const COLOR = {
  start: '103,194,58',     // 绿：起点
  between: '64,158,255',   // 蓝：审批
  end: '245,108,108',      // 红：终止
  serial: '245,158,11',    // 橙：互斥
  parallel: '19,194,194',  // 青：并行
  inclusive: '146,84,222', // 紫：包含
  service: '79,70,229',    // 靛蓝：服务任务
  event: '217,70,239',     // 品红：事件（定时 / 消息 / 终止）
}

const stroke = (rgb) => `rgb(${rgb})`
const rgba = (rgb, a) => `rgba(${rgb},${a})`

/** 图标背景色 — 用 CSS 变量支持暗黑模式自动切换 */
const ICON_BG = 'var(--wf-icon-bg, #ffffff)'

/** 齿轮图标路径（Material settings，viewBox 0 0 24 24），服务任务节点图标符号 */
const GEAR_PATH = 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z'

/** 闪电图标路径（Material bolt，viewBox 0 0 24 24），事件节点图标符号 */
const BOLT_PATH = 'M11 21h-1l1-7H7.5c-.58 0-.57-.32-.38-.66.19-.34.05-.08.07-.12C8.48 10.94 10.42 7.54 13 3h1l-1 7h3.5c.49 0 .56.33.47.51l-.07.15C12.96 17.55 11 21 11 21z'

/**
 * 开始节点：圆环 + 内核实心渐变圆（与 start.js 一致）
 */
export const startIcon = `
<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <radialGradient id="si-core" cx="35%" cy="35%" r="65%">
      <stop offset="0%" stop-color="${stroke(COLOR.start)}"/>
      <stop offset="70%" stop-color="${rgba(COLOR.start, 0.4)}"/>
    </radialGradient>
  </defs>
  <circle cx="50" cy="50" r="42" fill="${ICON_BG}" stroke="${stroke(COLOR.start)}" stroke-width="2.5"/>
  <circle cx="50" cy="50" r="30" fill="url(#si-core)"/>
</svg>`

/**
 * 中间节点：圆角矩形卡片 + 审批图标（与 between.js 一致）
 */
export const betweenIcon = `
<svg viewBox="0 0 100 80" xmlns="http://www.w3.org/2000/svg">
  <rect x="5" y="5" width="90" height="70" rx="8" fill="${ICON_BG}" stroke="${stroke(COLOR.between)}" stroke-width="1.5"/>
  <!-- 左上角审批小图标（放大，偏右下） -->
  <rect x="12" y="10" width="32" height="26" rx="5" ry="5" fill="${rgba(COLOR.between, 0.08)}" stroke="${stroke(COLOR.between)}" stroke-width="1.2"/>
  <circle cx="28" cy="23" r="5.5" fill="${rgba(COLOR.between, 0.15)}" stroke="${stroke(COLOR.between)}" stroke-width="1"/>
  <path d="M24 23l4 4l6-6" fill="none" stroke="${stroke(COLOR.between)}" stroke-width="1.5" stroke-linecap="square" stroke-linejoin="miter"/>
</svg>`

/**
 * 结束节点：双环圆 — 粗外环 + 细内环（与 end.js 一致）
 */
export const endIcon = `
<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
  <circle cx="50" cy="50" r="42" fill="${ICON_BG}" stroke="${stroke(COLOR.end)}" stroke-width="3.5"/>
  <circle cx="50" cy="50" r="30" fill="none" stroke="${rgba(COLOR.end, 0.25)}" stroke-width="1"/>
</svg>`

/**
 * 服务任务节点：圆角矩形卡片 + 齿轮图标（与 service.js 一致）
 */
export const serviceIcon = `
<svg viewBox="0 0 100 80" xmlns="http://www.w3.org/2000/svg">
  <rect x="5" y="5" width="90" height="70" rx="8" fill="${ICON_BG}" stroke="${stroke(COLOR.service)}" stroke-width="1.5"/>
  <rect x="12" y="10" width="32" height="26" rx="5" ry="5" fill="${rgba(COLOR.service, 0.08)}" stroke="${stroke(COLOR.service)}" stroke-width="1.2"/>
  <path transform="translate(19,17) scale(0.75)" d="${GEAR_PATH}" fill="${stroke(COLOR.service)}"/>
</svg>`

/**
 * 事件节点：圆角矩形卡片 + 闪电（触发）图标（与事件节点画布标识一致）
 */
export const eventIcon = `
<svg viewBox="0 0 100 80" xmlns="http://www.w3.org/2000/svg">
  <rect x="5" y="5" width="90" height="70" rx="8" fill="${ICON_BG}" stroke="${stroke(COLOR.event)}" stroke-width="1.5"/>
  <rect x="12" y="10" width="32" height="26" rx="5" ry="5" fill="${rgba(COLOR.event, 0.08)}" stroke="${stroke(COLOR.event)}" stroke-width="1.2"/>
  <path transform="translate(19,16) scale(0.62)" d="${BOLT_PATH}" fill="${stroke(COLOR.event)}"/>
</svg>`

/** 事件节点卡片公共外框：卡片 + 左上角触发徽标底座，glyph 为徽标区内部图形 */
const eventCard = (glyph) => `
<svg viewBox="0 0 100 80" xmlns="http://www.w3.org/2000/svg">
  <rect x="5" y="5" width="90" height="70" rx="8" fill="${ICON_BG}" stroke="${stroke(COLOR.event)}" stroke-width="1.5"/>
  <rect x="12" y="10" width="32" height="26" rx="5" ry="5" fill="${rgba(COLOR.event, 0.08)}" stroke="${stroke(COLOR.event)}" stroke-width="1.2"/>
  ${glyph}
</svg>`

/**
 * 定时事件：卡片 + 时钟字形（徽标区内 32x26，中心 28,23）
 */
export const timerEventIcon = eventCard(`
  <circle cx="28" cy="23" r="8" fill="none" stroke="${stroke(COLOR.event)}" stroke-width="1.8"/>
  <path d="M28 18.5v4.8l3.4 2" fill="none" stroke="${stroke(COLOR.event)}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="28" cy="23" r="1" fill="${stroke(COLOR.event)}"/>
`)

/**
 * 消息事件：卡片 + 信封字形
 */
export const messageEventIcon = eventCard(`
  <rect x="19.5" y="16.5" width="17" height="13" rx="2" fill="none" stroke="${stroke(COLOR.event)}" stroke-width="1.8"/>
  <path d="M20.5 18l7.5 5.5 7.5-5.5" fill="none" stroke="${stroke(COLOR.event)}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
`)

/**
 * 错误事件：卡片 + 错误标记（圆内叉）
 */
export const errorEventIcon = eventCard(`
  <circle cx="28" cy="23" r="8" fill="none" stroke="${stroke(COLOR.event)}" stroke-width="1.8"/>
  <path d="M24.8 19.8l6.4 6.4M31.2 19.8l-6.4 6.4" fill="none" stroke="${stroke(COLOR.event)}" stroke-width="1.8" stroke-linecap="round"/>
`)

/**
 * 边界事件：卡片 + 外环包闪电（附着 / 中断语义）
 */
export const boundaryEventIcon = eventCard(`
  <circle cx="28" cy="23" r="9.5" fill="none" stroke="${stroke(COLOR.event)}" stroke-width="1.2" stroke-dasharray="2.4 1.8"/>
  <path transform="translate(22.5,17.5) scale(0.46)" d="${BOLT_PATH}" fill="${stroke(COLOR.event)}"/>
`)

// ========== 网关节点公共部分 ==========

/** 正方形外框（50x50 坐标系），绕中心点旋转45度，尺寸确保旋转后四角不超出viewBox */
const gwRect = `<rect x="8" y="8" width="34" height="34" rx="5" transform="rotate(45 25 25)"`

/**
 * 互斥网关：正方形 + X + 四端圆点
 */
export const serialIcon = `
<svg viewBox="0 0 50 50" xmlns="http://www.w3.org/2000/svg">
  ${gwRect} fill="${ICON_BG}" stroke="${stroke(COLOR.serial)}" stroke-width="2"/>
  <!-- X 图标（直线，无圆角端点） -->
  <path d="M17 17 L33 33 M33 17 L17 33" fill="none" stroke="${stroke(COLOR.serial)}" stroke-width="2.5" stroke-linecap="square" stroke-linejoin="miter"/>
  <!-- 四端装饰圆点 -->
  <circle cx="14.5" cy="14.5" r="1.3" fill="${stroke(COLOR.serial)}"/>
  <circle cx="34.5" cy="14.5" r="1.3" fill="${stroke(COLOR.serial)}"/>
  <circle cx="34.5" cy="34.5" r="1.3" fill="${stroke(COLOR.serial)}"/>
  <circle cx="14.5" cy="34.5" r="1.3" fill="${stroke(COLOR.serial)}"/>
</svg>`

/**
 * 并行网关：正方形 + 十字 + 四端圆点
 */
export const parallelIcon = `
<svg viewBox="0 0 50 50" xmlns="http://www.w3.org/2000/svg">
  ${gwRect} fill="${ICON_BG}" stroke="${stroke(COLOR.parallel)}" stroke-width="2"/>
  <!-- 十字图标（直线） -->
  <path d="M15 25 L35 25" fill="none" stroke="${stroke(COLOR.parallel)}" stroke-width="3" stroke-linecap="square"/>
  <path d="M25 15 L25 35" fill="none" stroke="${stroke(COLOR.parallel)}" stroke-width="3" stroke-linecap="square"/>
  <!-- 四端装饰圆点 -->
  <circle cx="25" cy="12" r="1.5" fill="${stroke(COLOR.parallel)}"/>
  <circle cx="38" cy="25" r="1.5" fill="${stroke(COLOR.parallel)}"/>
  <circle cx="25" cy="38" r="1.5" fill="${stroke(COLOR.parallel)}"/>
  <circle cx="12" cy="25" r="1.5" fill="${stroke(COLOR.parallel)}"/>
</svg>`

/**
 * 包容网关：正方形 + 同心双圆 + 中心点
 */
export const inclusiveIcon = `
<svg viewBox="0 0 50 50" xmlns="http://www.w3.org/2000/svg">
  ${gwRect} fill="${ICON_BG}" stroke="${stroke(COLOR.inclusive)}" stroke-width="2"/>
  <!-- 同心双圆 -->
  <circle cx="25" cy="25" r="11" fill="none" stroke="${stroke(COLOR.inclusive)}" stroke-width="2"/>
  <circle cx="25" cy="25" r="5.5" fill="${rgba(COLOR.inclusive, 0.15)}" stroke="${stroke(COLOR.inclusive)}" stroke-width="1.5"/>
  <!-- 中心点缀 -->
  <circle cx="25" cy="25" r="1.8" fill="${stroke(COLOR.inclusive)}"/>
</svg>`

/**
 * 节点图标映射表
 * key: node type, value: svg string
 */
export const nodeIcons = {
  start: startIcon,
  between: betweenIcon,
  end: endIcon,
  serial: serialIcon,
  parallel: parallelIcon,
  inclusive: inclusiveIcon,
  service: serviceIcon,
}

// ========== 网关分组入口 ==========

/**
 * 网关分组入口图标：菱形 + 分支符号（点击/悬停展开网关子菜单）
 */
export const gatewayIcon = `
<svg viewBox="0 0 50 50" xmlns="http://www.w3.org/2000/svg">
  ${gwRect} fill="${ICON_BG}" stroke="${stroke(COLOR.serial)}" stroke-width="2"/>
  <circle cx="25" cy="16.5" r="2" fill="${stroke(COLOR.serial)}"/>
  <path d="M25 18.5 L17.5 30 M25 18.5 L32.5 30" fill="none" stroke="${stroke(COLOR.serial)}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="17.5" cy="31" r="1.6" fill="${stroke(COLOR.serial)}"/>
  <circle cx="32.5" cy="31" r="1.6" fill="${stroke(COLOR.serial)}"/>
</svg>`
