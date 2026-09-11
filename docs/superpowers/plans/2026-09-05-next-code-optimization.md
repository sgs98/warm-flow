# Next Code Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保持 Warm-Flow 公共 API、流程 JSON、状态机语义和 iframe/npm 交付方式不变的前提下，完成前端结构拆分、类型收敛、双模式回归和后端任务服务内部重构。

**Architecture:** 计划拆成三个可独立验收的工作流。前端先把 `FlowDesigner.vue` 与 `useLogicFlowCanvas.ts` 按职责拆成 composable 和纯函数模块；随后以固定流程 JSON 和双模式场景做回归；最后在外部状态机测试可用的前提下，把 `TaskServiceImpl` 的状态校验、历史写入和计数协调提取为内部类。所有新文件只承载内部实现，不修改 `FlowEngine`、`TaskService`、实体字段、状态枚举、接口返回结构或 npm exports。

**Tech Stack:** Vue 3.3、TypeScript、Vite 5、LogicFlow、Element Plus、pnpm workspace、Java 8、Maven、外部 `warm-flow-test`。

---

## 工作流一：前端结构与类型

### Task 1: 完成流程图领域类型接入

**Files:**
- Modify: `warm-flow-vue-designer/src/types/flow.ts`
- Modify: `warm-flow-vue-designer/src/components/design/common/js/tool.ts`
- Modify: `warm-flow-vue-designer/src/composables/useLogicFlowCanvas.ts`
- Modify: `warm-flow-vue-designer/src/designer/types.ts`

- [x] **Step 1: 扩展领域类型**

在 `src/types/flow.ts` 增加并导出以下字段，未知扩展字段统一放在索引签名中：

```ts
export interface LogicFlowTextData { value?: string; x?: number; y?: number }
export interface LogicFlowNodeData { id: string; type: string; x?: number; y?: number; text?: LogicFlowTextData; properties?: Record<string, any> }
export interface LogicFlowEdgeData { id?: string | number; type: string; sourceNodeId: string; targetNodeId: string; text?: LogicFlowTextData; properties?: Record<string, any>; pointsList?: Array<{ x: number; y: number }> }
export interface FlowGraphData { nodes: LogicFlowNodeData[]; edges: LogicFlowEdgeData[]; [key: string]: any }
```

- [x] **Step 2: 给转换函数加签名**

将 `json2LogicFlowJson` 的输入/输出改为 `FlowDefinitionData -> FlowGraphData`，将 `logicFlowJsonToWarmFlow` 的输入改为 `FlowGraphData`；对后端允许为空的字段保留可选属性，不改变序列化字段名。

- [x] **Step 3: 收窄画布 composable 的 JSON 类型**

将 `UseLogicFlowCanvasOptions.logicJson` 从 `Ref<Record<string, any>>` 改为 `Ref<FlowGraphData & Record<string, any>>`，调用方初始化对象必须仍包含 `nodes` 和 `edges`。

- [x] **Step 4: 验证类型和构建**

运行：`cd warm-flow-vue-designer && pnpm typecheck && pnpm build:lib`。

预期：两个命令退出码均为 `0`，`dist-lib/` 只生成主入口、`element-plus.es.js`、CSS 和声明文件。

### Task 2: 提取流程定义加载逻辑

**Files:**
- Create: `warm-flow-vue-designer/src/composables/useFlowDefinition.ts`
- Modify: `warm-flow-vue-designer/src/components/design/FlowDesigner.vue`
- Test: `docs/frontend-flow-regression.md`

- [x] **Step 1: 固定 composable 接口**

创建 `useFlowDefinition(options)`，接口至少包含：

```ts
interface UseFlowDefinitionOptions {
  props: FlowDesignerProps
  logicJson: Ref<Record<string, any>>
  jsonString: Ref<string>
  applyGraph: (definition: Record<string, any>) => void
}
```

返回 `loadDefinition`、`applyDefinition`、`getInitialSource` 三个函数。`getInitialSource` 保持优先级：受控 `json` > `initialJson` > `definitionId` > 新建默认数据。

- [x] **Step 2: 迁移加载代码**

把 `FlowDesigner.vue` 中 `applyDefinition`、`definitionId` 请求和初始 JSON 分支迁移到 composable；保存事件、props 名称和加载失败提示保持原样。

- [x] **Step 3: 删除容器内重复实现**

确认 `FlowDesigner.vue` 只保留模板编排和 composable 调用，不能同时存在旧的 `applyDefinition` 实现。

- [x] **Step 4: 回归验证**

在 Element Plus Demo 验证：新建流程、`initialJson` 加载、`definitionId` 加载、只读预览、刷新回显；再运行 `pnpm typecheck`、`pnpm build:lib`、Demo `pnpm build`。

### Task 3: 提取保存与画布事件逻辑

**Files:**
- Create: `warm-flow-vue-designer/src/composables/useFlowSave.ts`
- Create: `warm-flow-vue-designer/src/composables/useFlowEvents.ts`
- Modify: `warm-flow-vue-designer/src/components/design/FlowDesigner.vue`

- [x] **Step 1: 创建保存 composable**

`useFlowSave` 接收 `validateBaseInfo`、`getFlowJson`、`beforeSave`、`saveJson` 回调，返回 `saveJsonModel`、`validate`、`resetDirty`。必须保留 `before-save` 的同步 `preventDefault()` 和 `setJson()` 语义。

- [ ] **Step 2: 创建事件 composable**

`useFlowEvents` 接收 `lf`、`logicJson`、`disabled` 和事件 emit 函数，返回节点点击、画布变更、撤销/重做和步骤切换处理器。事件 payload 使用现有 `designer/types.ts` 类型。

- [x] **Step 3: 替换 FlowDesigner 内联函数**

逐个替换保存、节点点击、步骤切换和 dirty 状态处理；每替换一个函数后运行 `pnpm typecheck`，避免一次性失去错误定位。

- [x] **Step 4: 回归验证**

覆盖保存成功、保存失败、表单校验失败、`before-save.preventDefault()`、节点点击、撤销/重做和 `change` 事件；运行库和 Demo 构建。

### Task 4: 拆分 LogicFlow 画布内部实现

**Files:**
- Create: `warm-flow-vue-designer/src/composables/logicflow/graphConverter.ts`
- Create: `warm-flow-vue-designer/src/composables/logicflow/canvasOptions.ts`
- Create: `warm-flow-vue-designer/src/composables/logicflow/canvasEvents.ts`
- Modify: `warm-flow-vue-designer/src/composables/useLogicFlowCanvas.ts`

- [x] **Step 1: 提取 graphConverter**

迁移节点/边导入、导出和元数据复制；新增单元级 round-trip 检查：输入流程 JSON 经转换后再导出，`flowCode`、`flowName`、节点编码、边起止编码和 `skipCondition` 必须保持一致。

- [x] **Step 2: 提取 canvasOptions**

集中处理 `grid`、`keyboard`、`container`、静默模式和 `lfOptions` 合并规则；`container` 必须始终由组件内部值覆盖，禁止 props 传入值替换容器。

- [x] **Step 3: 提取 canvasEvents**

集中处理 LogicFlow 事件注册、历史监听和卸载清理；同一个 composable 生命周期内不得重复绑定相同事件。

- [x] **Step 4: 验证双模式画布**

经典和仿钉钉模式分别验证新增节点、连线、条件跳转、网关、只读、撤销/重做和导出；运行 `pnpm typecheck`、`pnpm build:lib`、Demo `pnpm build`。

---

## 工作流二：发布与回归

### Task 5: 固化前端双模式自动检查

**Files:**
- Create: `warm-flow-vue-designer/scripts/check-flow-roundtrip.mjs`
- Modify: `warm-flow-vue-designer/package.json`
- Modify: `docs/frontend-flow-regression.md`

- [x] **Step 1: 增加 round-trip 检查脚本**

脚本读取 `docs/frontend-flow-regression.md` 中的 JSON fixture，调用构建后的转换模块，断言节点数量、边数量、节点编码、起止节点和条件字段一致；不启动浏览器、不访问真实后端。

- [x] **Step 2: 增加 npm script**

在 `package.json` 增加 `"test:flow-roundtrip": "node scripts/check-flow-roundtrip.mjs"`，不修改已有 `typecheck`、`build:lib` 命令。

- [x] **Step 3: 执行发布门禁**

运行：

```bash
pnpm typecheck
pnpm test:flow-roundtrip
pnpm build:lib
cd ../warm-flow-designer-demo/warm-flow-ep-designer-demo && pnpm build
cd ../../warm-flow-ui && npm run build:prod
```

- [x] **Step 4: 记录 bundle 风险**

记录当前 Demo 和 iframe bundle 超过 500 kB 的警告，不通过调高阈值隐藏问题；如拆包单独开任务，不与功能拆分混做。

### Task 6: 清理文档中的历史适配器误导

**Files:**
- Modify: `warm-flow-vue-designer/todo.md`
- Modify: `docs/superpowers/plans/2026-09-04-element-plus-only-ui.md`

- [x] **Step 1: 标记历史文档**

在文档开头明确其为历史记录，当前支持范围只有 Element Plus；保留审计所需的历史删除记录，但不让扫描命令把历史说明误判为生产残留。

- [x] **Step 2: 验证生产源码扫描**

执行：`rg -n --hidden -g '!**/node_modules/**' -g '!**/target/**' -g '!docs/superpowers/**' -g '!warm-flow-vue-designer/todo.md' 'ant-design-vue|naive-ui|antdvAdapter|naiveAdapter' .`。

预期：无生产源码、POM、发布入口命中。

---

## 工作流三：后端任务服务

### Task 7: 建立 TaskServiceImpl 重构前基线

**Files:**
- Verify: `warm-flow-core/src/main/java/org/dromara/warm/flow/core/service/impl/TaskServiceImpl.java`
- Create: `docs/task-service-refactor-baseline.md`

- [x] **Step 1: 记录公共方法和写操作**

列出 `TaskServiceImpl` 每个 public 方法调用的 DAO 写操作、实例/任务/历史任务状态变化和异常类型；只读审计，不改源码。

- [x] **Step 2: 固化编译基线**

运行 `mvn -pl warm-flow-core -am -DskipTests compile`，将 JDK、Maven 版本和结果记录到基线文档。

- [x] **Step 3: 检查外部测试仓库**

确认本地是否存在 `warm-flow-test`；不存在时把 Task 8-10 标记为阻塞前置，不进行状态机内部提取。

### Task 8: 提取纯状态校验内部类

**Files:**
- Create: `warm-flow-core/src/main/java/org/dromara/warm/flow/core/service/impl/task/TaskStateValidator.java`
- Modify: `warm-flow-core/src/main/java/org/dromara/warm/flow/core/service/impl/TaskServiceImpl.java`

- [ ] **Step 1: 定义包内接口**

新类只接收任务、实例、办理参数和现有服务依赖，返回 void 或原有校验结果；不得暴露为新的公共服务 Bean。

- [ ] **Step 2: 迁移重复校验**

只迁移当前状态、实例状态、办理人权限和重复办理检查；异常类型、异常消息和校验顺序保持不变。

- [ ] **Step 3: 编译并执行状态测试**

先运行 core 编译；若 `warm-flow-test` 可用，再运行通过、退回、转办、终止和重复办理测试。任一失败立即停止后续提取。

### Task 9: 提取历史任务与计数协调

**Files:**
- Create: `warm-flow-core/src/main/java/org/dromara/warm/flow/core/service/impl/task/TaskHistoryWriter.java`
- Create: `warm-flow-core/src/main/java/org/dromara/warm/flow/core/service/impl/task/TaskCounterCoordinator.java`
- Modify: `warm-flow-core/src/main/java/org/dromara/warm/flow/core/service/impl/TaskServiceImpl.java`

- [ ] **Step 1: 提取历史任务写入**

只迁移 HisTask 创建、字段复制和批量保存；保持主任务更新与历史写入在同一原有事务路径。

- [ ] **Step 2: 提取会签/票签计数**

只迁移通过数、完成数、回退恢复和网关汇聚计算；不改变状态枚举、DAO 调用顺序和事务边界。

- [ ] **Step 3: 执行完整状态机回归**

覆盖通过、退回、撤回、转办、委派、加签、减签、会签、票签、互斥网关、并行网关、包含网关和终止。

- [ ] **Step 4: 编译 ORM 接缝**

编译 core、MyBatis core、MyBatis-Plus core、Easy-Query core 及代表 starter，确认内部类没有引入框架依赖。

### Task 10: 完成发布验收

**Files:**
- Modify: `docs/optimization-verification.md`
- Modify: `docs/superpowers/plans/2026-09-05-next-code-optimization.md`

- [x] **Step 1: 执行全量质量门禁**

运行 npm 库 typecheck/build、Demo build、iframe build、Maven validate、core 编译、ORM/插件代表编译和 `git diff --check`。

- [x] **Step 2: 检查发布产物**

确认 npm `exports` 对应文件、`dist-lib` 主入口/Element Plus 子入口/CSS/声明文件，以及 iframe `dist` 资源均存在。

- [x] **Step 3: 记录未验证项**

若外部状态机测试或浏览器自动化不可用，明确记录命令、失败原因、影响范围和后续执行条件，不把计划强行标记为完成。

- [x] **Step 4: 更新计划状态**

只有对应命令和测试证据齐全时才勾选步骤；禁止通过删除检查、吞异常或修改阈值制造通过结果。
