# Element Plus Only UI Implementation Plan

> 历史执行计划，仅用于审计删除过程；当前生产支持范围以 `warm-flow-vue-designer/AGENTS.md` 和 README 为准，只有 Element Plus。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 保留 iframe 与 npm 两条交付链路，但统一只支持 Element Plus，移除 Ant Design Vue 与 Naive UI 适配器、示例和构建入口。

**Architecture:** iframe 链路继续由 `warm-flow-ui` 构建并将 Element Plus 打进 `warm-flow-plugin-vue3-ui`。npm 组件库继续保留 `UiAdapter` 抽象和 `element-plus` 子入口，主入口保持 UI 无关；删除 antdv/naive 子入口及其 demo，避免改变 iframe 现有集成方式。

**Tech Stack:** Vue 3.3.9、Vite 5、Element Plus 2.4.3、TypeScript、pnpm/yarn、Maven 资源插件。

---

### Task 1: 固定 npm 组件库的 Element Plus 唯一适配器

**Files:**
- Modify: `warm-flow-vue-designer/package.json`
- Modify: `warm-flow-vue-designer/vite.lib.config.js`
- Modify: `warm-flow-vue-designer/README.md`
- Modify: `warm-flow-vue-designer/AGENTS.md`
- Keep: `warm-flow-vue-designer/src/ui/elementPlusAdapter.ts`
- Keep: `warm-flow-vue-designer/src/ui/uiAdapter.ts`
- Delete: `warm-flow-vue-designer/src/ui/antdvAdapter.ts`
- Delete: `warm-flow-vue-designer/src/ui/naiveAdapter.ts`
- Delete: `warm-flow-vue-designer/vite.antdv.config.js`
- Delete: `warm-flow-vue-designer/vite.naive.config.js`

- [ ] **Step 1: 删除 npm 包导出和构建入口**

  从 `package.json` 的 `exports` 删除 `./antdv`、`./naive`；从 `build:lib` 删除两个对应构建命令；删除 `build:antdv`、`build:naive`；从 `peerDependencies`、`peerDependenciesMeta`、`devDependencies` 删除 `ant-design-vue` 和 `naive-ui`，保留 `element-plus`。

- [ ] **Step 2: 清理主库构建配置中的多适配器描述**

  在 `vite.lib.config.js` 中移除对 `antdvAdapter` 和 `naiveAdapter` 的 dts/入口说明，保留主入口和 Element Plus 子入口的构建逻辑；不得把 Element Plus 直接打入主入口 bundle。

- [ ] **Step 3: 删除两套适配器实现**

  删除 `src/ui/antdvAdapter.ts`、`src/ui/naiveAdapter.ts`，确认 `src/` 内除 `elementPlusAdapter.ts` 外不再 import 具体 UI 库。

- [ ] **Step 4: 更新 npm 组件库文档与模块规则**

  将 README 中“三选一”“antdv/naive 子入口”“三套适配器”改为仅说明 Element Plus；将 `AGENTS.md` 的适配器矩阵、exports、构建命令和维护约束同步为单适配器。

### Task 2: 精简消费 Demo

**Files:**
- Modify: `warm-flow-designer-demo/README.md`
- Keep: `warm-flow-designer-demo/warm-flow-ep-designer-demo/**`
- Delete: `warm-flow-designer-demo/warm-flow-antdv4-designer-demo/**`
- Delete: `warm-flow-designer-demo/warm-flow-naive-designer-demo/**`

- [ ] **Step 1: 删除 Ant Design Vue 和 Naive UI demo**

  删除两个 demo 的源码、配置、README 和 package manifest。删除前确认它们只通过 `@dromara/warm-flow-designer/antdv` 或 `/naive` 消费库，没有被 Maven 或其他生产代码引用。

- [ ] **Step 2: 更新 demo 总说明**

  将 demo 列表改为只保留 `warm-flow-ep-designer-demo`，删除 5181/5182 端口、三 UI 对照说明和对应启动命令，保留 Element Plus 的第三方 npm 消费示例。

### Task 3: 同步工程规则和文档

**Files:**
- Modify: `warm-flow-vue-designer/AGENTS.md`
- Modify: `warm-flow-vue-designer/README.md`
- Modify: `warm-flow-designer-demo/README.md`
- Modify: `README.md`（仅在存在多 UI/npm 适配器说明时）
- Modify: `.cursor/rules/warm-flow.mdc`（仅在存在多 UI 说明时）

- [ ] **Step 1: 全文清理失效入口**

  使用 `rg -n 'antdv|naive|ant-design-vue|naive-ui|build:antdv|build:naive'` 检查源码、配置和文档，删除指向已删除文件的路径；保留与业务代码中普通 `div`、`native` 等无关词的合法命中。

- [ ] **Step 2: 保持 iframe 说明不变**

  不修改 `warm-flow-ui` 的 Element Plus iframe 实现、`warm-flow-plugin-vue3-ui` 的资源路径和 `/warm-flow-ui/**` 后端映射，避免影响现有 jar 集成。

### Task 4: 重建并同步 npm 与 iframe 产物

**Files:**
- Generated: `warm-flow-vue-designer/dist-lib/**`
- Generated: `warm-flow-ui/dist/**`
- Modify: `warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui/src/main/resources/warm-flow-ui/**`

- [ ] **Step 1: 构建 npm 组件库**

  在 `warm-flow-vue-designer` 执行 `pnpm build:lib`，预期只生成主入口、Element Plus 子入口和样式产物，不生成 `antdv.es.js` 或 `naive.es.js`。

- [ ] **Step 2: 构建 iframe 前端**

  在 `warm-flow-ui` 执行 `npm run build:prod`，确认生产构建成功且产物仍包含 Element Plus。

- [ ] **Step 3: 同步 jar 内嵌资源**

  将 `warm-flow-ui/dist/index.html`、`dist/css/*`、`dist/js/*` 同步到 `warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui/src/main/resources/warm-flow-ui/`，删除旧 hash 文件，确保 `index.html` 引用的文件实际存在。

### Task 5: 验证发布链路与残留

**Files:**
- Verify: `pom.xml` and all Maven module `pom.xml`
- Verify: `warm-flow-vue-designer/package.json`
- Verify: `warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui/src/main/resources/warm-flow-ui/**`

- [ ] **Step 1: 检查失效引用**

  执行 `rg -n --hidden -g '!**/node_modules/**' -g '!**/target/**' -g '!**/.qoder/**' 'antdv|naive|ant-design-vue|naive-ui' .`，预期无生产源码、POM、发布文档命中。

- [ ] **Step 2: 编译 Maven 关键模块**

  执行 `mvn -q -DskipTests validate` 和 `mvn -q -pl warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui -am -DskipTests compile`，预期均成功。

- [ ] **Step 3: 验证 npm 消费示例**

  在 `warm-flow-designer-demo/warm-flow-ep-designer-demo` 执行 `pnpm build`，预期 Element Plus demo 构建成功。

- [ ] **Step 4: 检查资源和产物**

  确认 `warm-flow-vue-designer/dist-lib` 只有主入口、`element-plus.es.js`、样式和类型声明；确认内嵌资源目录无 `antdv`、`naive` 文件；确认生产代码中无相关字符串。

- [ ] **Step 5: 清理测试生成物**

  经用户确认后再删除 `target/`、`node_modules/`、`dist/`、`dist-lib/` 等可重建目录；不要删除源码、文档或用户已有修改。

### Compatibility and Risk Notes

- 这是 npm 对外 exports 的破坏性变更，使用 `@dromara/warm-flow-designer/antdv` 或 `/naive` 的下游项目会构建失败，发布前必须在版本说明中明确。
- iframe 现有 URL、静态资源路径、Element Plus 版本和 Maven 模块保持不变。
- 保留 `UiAdapter` 抽象和 `setUiAdapter` API，未来恢复其他 UI 时不需要重写设计器核心。
- `.qoder/repowiki` 为被忽略的本地生成文档，不纳入发布验证；如要求全磁盘零残留，需另行确认后清理。
