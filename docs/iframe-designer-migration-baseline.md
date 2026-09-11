# iframe 设计器迁移基线

## 构建基线

- 时间：2026-09-11
- 命令：`cd warm-flow-ui && npm run build:prod`
- 说明：本机未安装 `yarn`，使用同一 `package.json` 中的 `vite build` 脚本执行；构建成功。
- 产物：`dist/index.html`、`dist/css/index-CWht0cS7.css`、`dist/js/index-FF9PBj-0.js`、`dist/ico/favicon-FqvijpIH.ico`。

## 功能基线

- 经典模式、仿钉钉模式、基础信息、画布编辑、节点属性、跳转条件、监听器、节点扩展均由 `warm-flow-ui/src/views/flow-design/index.vue` 与 `src/components/design/**` 提供。
- 节点扩展接口为 `/warm-flow/node-ext`，属性面板支持 `type=4` 与 `multiple=true`。
- 按钮权限扩展码为 `ButtonPermissionEnum`，字典包含 `pop, trust, transfer, copy, back, addSign, subSign, termination, file`。
- 保存时 `logicFlowJsonToWarmFlow` 会将节点 `ext` 序列化为 JSON 字符串，保持既有节点扩展契约。
- iframe 主题参数与 `postMessage` 主题切换已由 `useDark` 提供。
- 保存成功后设计器向父页面发送 `{ method: "close" }`。

## 已识别缺口

- `App.vue` 对 `FlowChart`、`form`、`formCreate` 等页面的必需 `id` 缺少明确错误态。
- `useDark` 的消息监听未校验消息来源与数据结构。
- Demo 前端 iframe 页面已存在，但 Vite 未代理 `/warm-flow` 与 `/warm-flow-ui`。
- Demo 前端入口仍安装 npm 设计器与 Provider，`package.json` 仍依赖 `@dromara/warm-flow-designer`。

## 回归清单

- 经典模式与仿钉钉模式渲染。
- 新建、编辑、只读、保存、关闭回传。
- 办理人选择与回显。
- 监听器配置。
- 节点扩展与按钮权限默认值。
- 流程图查看。
- 插件 jar 静态资源完整性。
