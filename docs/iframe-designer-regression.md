# iframe 设计器回归记录

## 已完成

- `warm-flow-ui` 生产构建成功。
- `warm-flow-ui` 产物同步到 `warm-flow-plugin-vue3-ui`。
- 插件 clean 打包成功。
- jar 中仅包含当前 4 个静态资源，`index.html` 引用完整。
- Demo 前端类型检查和生产构建成功。
- Demo 后端编译成功。
- Demo 前端已改为 iframe 集成，并代理 `/warm-flow` 与 `/warm-flow-ui`。
- 根目录 pnpm workspace 与旧 npm 工程 `node_modules` 已移除。

## 未完成

本机没有 MySQL 客户端，且 3306、8080 端口均未监听，因此未执行以下真机回归：

- `curl -I http://localhost:8080/warm-flow-ui/index.html`
- 经典模式与仿钉钉模式渲染。
- 新建、编辑、只读、保存、关闭回传。
- 办理人选择与回显。
- 监听器配置。
- 节点扩展与按钮权限默认值。
- 流程图查看。
- 发起流程后的按钮权限与后端拒绝未授权动作。

## 后续执行条件

准备 MySQL 8 并初始化 `warm-flow` 数据库后，按以下顺序回归：

1. 启动 `warm-flow-demo` 后端。
2. 启动 `warm-flow-demo-web` 前端。
3. 打开 `/definitions/design` 与 `/definitions/design/{id}`。
4. 按「未完成」清单逐项验证。
