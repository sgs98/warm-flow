/**
 * 设计器凭证桥接（通用）：iframe 内的 XHR 无法自动携带宿主凭证（Cookie 被禁用 /
 * 跨域 / 网关注入等场景），父页面通过 postMessage 把任意请求头传入，这里保存在
 * 内存并由 axios 拦截器合并到每个 /warm-flow/** 请求。
 *
 * 消息契约（宿主自行决定头名与取值，设计器不感知具体认证体系）：
 *   父 -> 设计器：{ method: 'auth', headers: { '<头名>': '<值>', ... } }
 *   设计器 -> 父：{ method: 'auth-request' }   // 设计器加载后主动请求一次
 *
 * 空对象 / 缺失 headers 视为清除凭证（登出）。头名与格式完全由宿主定义，
 * 例如 sa-token 宿主发 { Authorization: 'Bearer xxx', clientid: 'yyy' }，
 * 网关注入型宿主可发 { X-Internal-User: 'alice' }。
 *
 * 安全约定：
 * - 仅接受 window.parent 发来的消息；
 * - 头只存内存，不写 URL / localStorage / sessionStorage，不进历史与日志；
 * - 凭证过期由父页面重新发送（同一 method: 'auth' 覆盖），设计器收到 401
 *   后会再次发出 auth-request。
 */

const runtimeAuth = {
  headers: {}
}

/**
 * 处理父页面发来的凭证消息。
 */
function onAuthMessage(e) {
  if (e.source !== window.parent) return
  const data = e.data
  if (!data || typeof data !== 'object' || data.method !== 'auth') return
  setRuntimeAuth(data.headers)
}

/**
 * 写入运行时凭证头（整体替换）。
 * @param {Object} headers 请求头映射；空值清除
 */
export function setRuntimeAuth(headers) {
  runtimeAuth.headers = (headers && typeof headers === 'object')
    ? Object.fromEntries(Object.entries(headers).filter(([k, v]) => k && typeof v === 'string'))
    : {}
}

/**
 * 读取运行时凭证头（axios 拦截器用）。
 * @returns {Object} 请求头映射
 */
export function getRuntimeAuth() {
  return { ...runtimeAuth.headers }
}

/** 是否已收到父页面的凭证（首个请求发出前用于等待，避免竞态 401） */
export function hasRuntimeAuth() {
  return Object.keys(runtimeAuth.headers).length > 0
}

/**
 * 等待凭证到达：iframe 加载即发首个业务请求，而 postMessage 往返是异步的，
 * 不等凭证会出现「首请求 401、刷新才好」的竞态。仅在嵌入模式（父窗口存在）
 * 且尚未收到凭证时等待；独立部署（无父窗口）凭证由 Cookie 等机制自带，直接放行。
 *
 * @param {number} timeout 最长等待毫秒数
 * @returns {Promise<void>} 凭证就绪或超时
 */
export function waitForRuntimeAuth(timeout = 1000) {
  if (window.parent === window || hasRuntimeAuth()) {
    return Promise.resolve()
  }
  return new Promise((resolve) => {
    const startedAt = Date.now()
    const timer = setInterval(() => {
      if (hasRuntimeAuth() || Date.now() - startedAt >= timeout) {
        clearInterval(timer)
        resolve()
      }
    }, 50)
  })
}

/**
 * 请求父页面提供凭证：设计器加载时调用一次；请求 401 后可再次调用。
 */
export function requestAuthFromParent() {
  if (window.parent !== window) {
    window.parent.postMessage({ method: 'auth-request' }, '*')
  }
}

/** 注册凭证消息监听（index.vue / flowChart.vue 挂载时调用） */
export function setupAuthListener() {
  window.addEventListener('message', onAuthMessage)
  requestAuthFromParent()
}

/** 移除凭证消息监听 */
export function cleanupAuthListener() {
  window.removeEventListener('message', onAuthMessage)
}
