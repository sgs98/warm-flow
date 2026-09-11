# 优化计划验证记录

## 已验证

- iframe 工程：`cd warm-flow-ui && npm run build:prod`
- 插件资源同步：`cd warm-flow-ui && npm run sync:plugin`
- 插件打包：`mvn -pl warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui -am -DskipTests clean package`
- Demo 前端：`cd warm-flow-demo/warm-flow-demo-web && pnpm typecheck && pnpm build`
- Demo 后端：`cd warm-flow-demo/warm-flow-demo && mvn -DskipTests compile`
- jar 资源检查：仅包含当前 `index.html`、CSS、JS、favicon，且入口引用均存在
- 引用扫描：生产源码、workspace 与 Demo 不再引用 `@dromara/warm-flow-designer`
- 根目录不再保留 pnpm workspace `node_modules`；Demo 前端改为本地依赖安装

## 未验证

- 本机没有 `mysql` / `mysqladmin` 客户端，且 3306、8080 端口未监听，未执行真实数据库、HTTP 与浏览器端到端回归。
- 未验证经典/仿钉钉、新建/编辑/只读、保存/关闭、办理人、监听器、按钮权限和流程图的真机交互。
- Vite 报告 Demo 和 iframe bundle 超过 500 kB，属于体积警告，不影响构建结果；代码拆包仍是后续独立优化项。
