# iframe-only 设计器迁移实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task。计划不包含 git commit，由维护者自行提交。

**Goal:** 以 `warm-flow-ui` 作为唯一设计器源码和 iframe 交付工程，完成 Demo iframe 联调后移除 `warm-flow-vue-designer`、npm workspace 依赖和 npm 专用配置。

**Architecture:** 保留 `warm-flow-ui` 的 Vue SPA、经典/仿钉钉设计器和 `/warm-flow-ui/**` 资源契约；构建产物同步到 `warm-flow-plugin-vue3-ui`。Demo 通过 iframe 加载后端 jar 提供的页面。

**Tech Stack:** Vue 3、Vite 5、Element Plus、LogicFlow、Yarn 1、pnpm workspace、Spring Boot UI plugin、Maven。

---

## Task 1：建立迁移基线

**Files:**

- Inspect: `warm-flow-ui/src/**`
- Inspect: `warm-flow-vue-designer/src/**`
- Inspect: `warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui/src/main/resources/warm-flow-ui/**`
- Create: `docs/iframe-designer-migration-baseline.md`

- [x] 运行 `cd warm-flow-ui && npm run build:prod`，记录 `dist/` 入口和资源清单。
- [x] 记录经典模式、仿钉钉模式、新建、编辑、只读、保存、办理人、监听器、节点扩展、按钮权限、流程图和 iframe 消息回归项。
- [x] 执行 `git status --short`，确认不覆盖用户已有改动。

## Task 2：把必要功能补回 `warm-flow-ui`

**Files:**

- Modify: `warm-flow-ui/src/api/**`
- Modify: `warm-flow-ui/src/views/flow-design/**`
- Modify: `warm-flow-ui/src/components/design/**`
- Inspect: `warm-flow-vue-designer/src/components/design/**`

- [x] 对照新设计器实现，确认节点扩展请求和响应信封处理已存在。
- [x] 确认节点属性含 `ButtonPermissionEnum` 权限页签，`type=4`、`multiple=true`。
- [x] 确认字典值为 `pop, trust, transfer, copy, back, addSign, subSign, termination, file`，默认开启 `copy/back/termination/file`。
- [x] 确认保存时生成 `[{"code":"ButtonPermissionEnum","value":"copy,back,termination,file"}]`，不改变节点 `ext` 契约。

## Task 3：完善 `warm-flow-ui` iframe 协议

**Files:**

- Modify: `warm-flow-ui/src/views/flow-design/index.vue`
- Modify: `warm-flow-ui/src/App.vue`
- Modify: `warm-flow-ui/src/router/**`
- Modify: `warm-flow-ui/src/utils/auth.js`
- Modify: `warm-flow-ui/src/api/**`

- [x] 保持现有定义 ID、页面类型、认证信息、后端地址等 URL 参数。
- [x] 缺少必需参数时展示明确错误，不渲染空白画布。
- [x] 保持保存、关闭的既有 `postMessage` 的 `method` 和字段含义。
- [x] 只处理约定的 Warm-Flow 消息，校验来源和数据结构。

## Task 4：构建并同步 jar 静态资源

**Files:**

- Modify: `warm-flow-ui/package.json`
- Modify: `warm-flow-ui/vite.config.js`
- Replace generated: `warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui/src/main/resources/warm-flow-ui/**`
- Optional create: `scripts/sync-warm-flow-ui.sh`

- [x] 保持 `build:prod` 为 iframe 生产构建入口；当前环境无 yarn，使用同一 Vite 脚本 `npm run build:prod`。
- [x] 同步前确认 `index.html` 和所有引用资源存在；失败时不覆盖插件现有资源。
- [x] 执行 `mvn -pl warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui -am -DskipTests clean package`。
- [x] 检查 jar 包含 `META-INF/resources/warm-flow-ui/index.html` 及全部依赖资源。

## Task 5：Demo 切换为 iframe

**Files:**

- Modify: `warm-flow-demo/warm-flow-demo-web/src/views/definitions/DefinitionEdit.vue`
- Modify: `warm-flow-demo/warm-flow-demo-web/src/router/index.ts`
- Modify: `warm-flow-demo/warm-flow-demo-web/package.json`
- Modify: `warm-flow-demo/README.md`
- Delete after scan: `warm-flow-demo/warm-flow-demo-web/src/api/provider.ts`

- [x] 删除 Demo 对 `FlowDesigner`、`setDataProvider`、`setUiAdapter` 和设计器 npm 样式的导入。
- [x] 用 iframe 容器加载 `/warm-flow-ui/index.html`，传递定义 ID、页面类型和认证信息。
- [x] 保留 Demo 外层标题、返回、加载态和错误态；通过 `window.message` 处理保存和关闭。
- [x] 从 `package.json` 删除 `@dromara/warm-flow-designer` 及不再使用的 LogicFlow 依赖。
- [x] 执行 `pnpm install && pnpm typecheck && pnpm build`。

## Task 6：移除 `warm-flow-vue-designer`

**Files:**

- Delete after reference scan: `warm-flow-vue-designer/**`
- Modify: `pnpm-workspace.yaml`
- Modify: `warm-flow-demo/README.md`
- Modify: `AGENTS.md`、模块规则和相关 README
- Remove: npm 专用 Demo、锁文件和构建脚本引用

- [x] 执行：

```bash
rg -n "warm-flow-vue-designer|@dromara/warm-flow-designer|workspace:\*|dist-lib|build:lib" --glob '!node_modules/**'
```

- [x] 确认源码、构建和 Demo 无生产引用后，从 workspace 移除设计器 npm 工程。
- [x] 删除 `warm-flow-vue-designer`，清理 pnpm workspace、锁文件和文档。
- [x] 再次执行引用扫描，活跃源码与配置无生产引用命中。

## Task 7：端到端回归

**Files:**

- Inspect: `warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui/**`
- Inspect: `warm-flow-demo/warm-flow-demo/**`
- Create: `docs/iframe-designer-regression.md`

- [x] 执行插件打包和 Demo 后端编译：

```bash
mvn -pl warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui -am -DskipTests package
cd warm-flow-demo/warm-flow-demo && mvn -DskipTests compile
```

- [ ] 验证 `curl -I http://localhost:8080/warm-flow-ui/index.html` 返回 200，入口引用的资源全部返回 200。
- [ ] 验证经典/仿钉钉、新建/编辑/只读、保存/关闭、办理人、监听器、按钮权限和流程图。
- [ ] 发起流程后验证按钮按节点权限显示；直接调用未授权动作时后端拒绝。
- [ ] 检查 Long ID、BusinessStatus 和现有后端接口无回归。
- [ ] 最后执行 `git diff --check`、`git status --short`，不执行 commit、push 或发布。
