# Overall Code Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变 Warm-Flow 公共 API、流程状态语义和 iframe/npm 交付方式的前提下，提升构建质量、减少前端源码分叉、收敛类型风险并降低核心服务维护复杂度。

**Architecture:** 本计划拆成五个可独立验收的子项目，按“先建立质量门禁，再处理前端结构，最后处理后端内部重构”的顺序执行。所有重构只提取内部实现，保留 `FlowEngine`、Service 接口、DTO/VO、流程 JSON 和现有资源路径；`warm-flow-ui` 继续作为稳定 iframe 基线，`warm-flow-vue-designer` 继续作为 npm 组件库。

**Tech Stack:** Java 8、Maven、Spring Boot 2/3/4 适配模块、Vue 3.3.9、TypeScript、Vite 5、LogicFlow、Element Plus、外部 `warm-flow-test` 测试仓库。

---

## 子项目一：构建与类型质量门禁

### Task 1: 建立可失败的前端类型检查

**Files:**
- Modify: `warm-flow-vue-designer/package.json`
- Create: `warm-flow-vue-designer/scripts/typecheck.mjs`
- Verify: `warm-flow-vue-designer/tsconfig.json`

- [x] **Step 1: 增加独立类型检查命令**

  在 `package.json` 增加 `typecheck` 脚本，执行 `vue-tsc --noEmit -p tsconfig.json`；将 `build:lib` 保持为产物构建，不用构建成功掩盖类型错误。

- [x] **Step 2: 先记录当前错误基线**

  执行 `pnpm typecheck`，保存错误数量和文件列表，重点覆盖 `useLogicFlowCanvas.ts`、`common/js/tool.ts`、节点视图和 `FlowDesignerHeader.vue` 的声明错误。

- [x] **Step 3: 按模块修复类型错误**

  为 LogicFlow 配置、节点/边转换结果、样式对象和 `StepItem` 补显式类型；将 `setCommonStyle`、`applyCssVariables` 等已有函数的可选参数与调用方统一，禁止通过 `as any` 批量压制错误。

- [x] **Step 4: 验证类型门禁**

  执行 `pnpm typecheck`，预期退出码为 0；再执行 `pnpm build:lib`，预期只生成主入口、`element-plus.es.js` 和样式。

### Task 2: 增加 Maven 与前端基础检查说明

**Files:**
- Modify: `warm-flow-vue-designer/AGENTS.md`
- Modify: `warm-flow-vue-designer/README.md`

- [x] **Step 1: 记录正式验证命令**

  将 `pnpm typecheck`、`pnpm build:lib`、Element Plus Demo 构建和 `mvn -q -DskipTests validate` 写入模块验证章节，明确 dts 生成告警不能视为类型检查通过。

- [x] **Step 2: 验证文档路径无失效入口**

  执行 `rg -n 'antdv|naive|ant-design-vue|naive-ui|build:antdv|build:naive'`，排除历史归档记录后不得出现已删除 npm 入口。

## 子项目二：前端双源码治理

### Task 3: 固化 iframe 与 npm 的职责边界

**Files:**
- Modify: `warm-flow-ui/AGENTS.md`
- Modify: `warm-flow-vue-designer/AGENTS.md`
- Modify: `README.md`
- Create: `docs/frontend-source-boundary.md`

- [x] **Step 1: 定义唯一变更规则**

  在 `docs/frontend-source-boundary.md` 明确：`warm-flow-ui` 只接收 iframe/webjar 关键修复；npm 新功能先进入 `warm-flow-vue-designer`；涉及流程 JSON、节点状态、接口字段的改动必须在两条链路分别验证。

- [x] **Step 2: 建立关键文件差异清单**

  记录两套工程的共享逻辑文件：节点模型/视图、`common/js/tool`、流程 API、主题变量和核心组件；不要求机械保持文件相同，而是明确每次修改要检查的对应文件。

- [x] **Step 3: 增加人工可执行的同步检查**

  提供命令 `diff -qr warm-flow-ui/src warm-flow-vue-designer/src` 的筛选说明，只对共享逻辑文件做审查，不比较 TS/JS 后缀、npm 专属 composable 和 iframe 专属 form 页面。

### Task 4: 抽取共享前端逻辑前的回归基线

**Files:**
- Verify: `warm-flow-ui/src/**`
- Verify: `warm-flow-vue-designer/src/**`
- Test: 外部 `warm-flow-test` 与 Element Plus Demo

- [x] **Step 1: 固化流程 JSON 样例**

  使用开始节点、普通节点、并行网关、条件跳转、会签/票签和结束节点各一份样例，验证 iframe 与 npm 导出的 JSON 字段一致。

- [x] **Step 2: 固化关键交互清单**

  验证加载定义、保存、只读预览、节点属性编辑、跳转条件、用户选择、撤销/重做和异常提示；任何一个场景失败时停止抽取共享逻辑。

## 子项目三：TypeScript 类型收敛

### Task 5: 建立流程图领域类型

**Files:**
- Create: `warm-flow-vue-designer/src/types/flow.ts`
- Modify: `warm-flow-vue-designer/src/designer/types.ts`
- Modify: `warm-flow-vue-designer/src/composables/useLogicFlowCanvas.ts`
- Modify: `warm-flow-vue-designer/src/components/design/FlowDesigner.vue`
- Modify: `warm-flow-vue-designer/src/components/design/common/js/tool.ts`

- [x] **Step 1: 定义稳定领域类型**

  增加 `FlowDefinitionData`、`FlowNodeData`、`FlowEdgeData`、`FlowGraphData`、`NodeProperties` 和 `FlowApiResponse<T>`，对未知扩展字段使用 `Record<string, unknown>`，不把整个对象继续定义为 `any`。

- [ ] **Step 2: 替换流程图核心链路中的 any**

  将 `logicJson`、节点/边转换、`getGraphData`、`initialJson` 和 `json` 相关签名替换为上述类型；对后端扩展字段在 API 边界做一次类型收窄。

- [x] **Step 3: 保持公共 API 兼容**

  不修改现有 prop 名称、事件名称和运行时 JSON 字段；类型只提供更精确的声明，允许扩展字段继续通过 `Record<string, unknown>` 传递。

- [x] **Step 4: 验证类型和构建**

  执行 `pnpm typecheck`、`pnpm build:lib`，再在 Element Plus Demo 中验证加载、编辑、保存和导出。

### Task 6: 收敛 DataProvider 与用户选择器类型

**Files:**
- Modify: `warm-flow-vue-designer/src/data/provider.ts`
- Modify: `warm-flow-vue-designer/src/data/httpProvider.ts`
- Modify: `warm-flow-vue-designer/src/data/mockProvider.ts`
- Modify: `warm-flow-vue-designer/src/composables/useUserPicker.ts`
- Modify: `warm-flow-vue-designer/src/components/design/common/vue/UserPicker*.vue`

- [x] **Step 1: 为接口响应建立最小结构类型**

  为统一响应声明 `code/msg/data`，为办理人、树节点、监听器、表单路径和分页结果声明最小字段；保留业务自定义字段扩展。

- [x] **Step 2: 同步 mock 与 http provider**

  让 mock 返回值完全满足同一接口类型，避免 mock 成功而真实接口编译或运行失败。

- [ ] **Step 3: 移除用户选择器中的无约束 any**

  为树节点、选中项、查询条件、权限行和日期范围补类型，并保留空值/加载态/异常态处理。

## 子项目四：前端大文件拆分

### Task 7: 拆分 FlowDesigner 编排逻辑

**Files:**
- Modify: `warm-flow-vue-designer/src/components/design/FlowDesigner.vue`
- Create: `warm-flow-vue-designer/src/composables/useFlowDefinition.ts`
- Create: `warm-flow-vue-designer/src/composables/useFlowSave.ts`
- Create: `warm-flow-vue-designer/src/composables/useFlowEvents.ts`

- [ ] **Step 1: 提取定义加载与回显**

  将 `definitionId`、`initialJson`、`json` 优先级处理和 `applyDefinition` 迁移到 `useFlowDefinition`，保持加载失败、空态和只读态行为不变。

- [ ] **Step 2: 提取保存与校验**

  将保存、`before-save`、`validate-error`、`dirty`、`resetDirty` 和结构校验协调迁移到 `useFlowSave`，保持现有事件 payload 不变。

- [ ] **Step 3: 提取节点/画布事件**

  将节点点击、画布 change、拖拽、撤销/重做和工具栏事件迁移到 `useFlowEvents`，组件只保留模板编排和公开 expose。

- [ ] **Step 4: 回归验证**

  执行 `pnpm typecheck`、`pnpm build:lib`，并在 Demo 手动验证新建、编辑、只读预览、保存失败和浏览器刷新回显。

### Task 8: 拆分 LogicFlow 画布内部实现

**Files:**
- Modify: `warm-flow-vue-designer/src/composables/useLogicFlowCanvas.ts`
- Create: `warm-flow-vue-designer/src/composables/logicflow/graphConverter.ts`
- Create: `warm-flow-vue-designer/src/composables/logicflow/canvasOptions.ts`
- Create: `warm-flow-vue-designer/src/composables/logicflow/canvasEvents.ts`

- [ ] **Step 1: 提取 JSON 与 LogicFlow 双向转换**

  将节点/边转换和定义元数据复制集中到 `graphConverter.ts`，确保导出字段与现有流程 JSON 完全一致。

- [ ] **Step 2: 提取初始化配置**

  将 grid、keyboard、container、只读模式和 `lfOptions` 合并规则集中到 `canvasOptions.ts`，保证 `container` 始终由组件内部覆盖。

- [ ] **Step 3: 提取事件绑定与清理**

  将 LogicFlow 事件监听、历史变更监听和卸载清理集中到 `canvasEvents.ts`，避免重复绑定。

- [ ] **Step 4: 验证双模式流程图**

  在经典和仿钉钉模式分别验证节点拖拽、连线、条件跳转、网关、只读预览、撤销/重做和导出图片。

## 子项目五：后端任务服务内部重构与测试

### Task 9: 拆分 TaskServiceImpl 内部职责

**Files:**
- Modify: `warm-flow-core/src/main/java/org/dromara/warm/flow/core/service/impl/TaskServiceImpl.java`
- Create: `warm-flow-core/src/main/java/org/dromara/warm/flow/core/service/impl/task/TaskStateValidator.java`
- Create: `warm-flow-core/src/main/java/org/dromara/warm/flow/core/service/impl/task/TaskCounterCoordinator.java`
- Create: `warm-flow-core/src/main/java/org/dromara/warm/flow/core/service/impl/task/TaskHistoryWriter.java`

- [ ] **Step 1: 标记公共边界和事务边界**

  先列出 `TaskService` 的公共方法、每个方法涉及的 DAO 写操作和事务要求；新类只能承载内部实现，不新增对外 Bean 契约。

- [ ] **Step 2: 提取状态校验**

  将任务当前状态、实例状态、办理人权限和重复办理检查集中到 `TaskStateValidator`，校验失败继续抛出原有异常类型和消息语义。

- [ ] **Step 3: 提取历史任务写入**

  将 HisTask 创建、批量保存和关联字段复制集中到 `TaskHistoryWriter`，确保主任务更新与历史写入仍在同一事务路径。

- [ ] **Step 4: 提取会签/票签计数协调**

  将通过人数、完成数、回退后计数恢复和并行节点汇聚集中到 `TaskCounterCoordinator`，不改变状态枚举、计数规则和 DAO 调用顺序。

- [ ] **Step 5: 编译并执行外部状态机测试**

  执行 `mvn -q -pl warm-flow-core -am -DskipTests compile`；在 `warm-flow-test` 中覆盖通过、退回、转办、终止、加签、减签、会签、票签、互斥网关和并行网关。

### Task 10: 补充项目级回归验证

**Files:**
- Verify: `warm-flow-core/**`
- Verify: `warm-flow-orm/**`
- Verify: `warm-flow-plugin/**`
- Verify: `warm-flow-ui/**`
- Verify: `warm-flow-vue-designer/**`

- [x] **Step 1: 执行后端矩阵编译**

  执行 `mvn -q -DskipTests validate`，并编译 MyBatis、MyBatis-Plus、Easy-Query 的 core 与 SB/SB3/SB4 starter 代表模块。

- [x] **Step 2: 执行两条前端构建链路**

  分别执行 `warm-flow-ui` 的 `npm run build:prod` 和 `warm-flow-vue-designer` 的 `pnpm build:lib`。

- [x] **Step 3: 检查发布产物**

  确认 iframe 内嵌资源可被 Maven UI 插件打包；确认 npm `exports`、类型声明、主 bundle 和 Element Plus 子入口路径全部存在。

- [x] **Step 4: 记录残余风险**

  将未能在本仓库验证的流程状态机行为、外部测试仓库依赖和既有类型告警记录在发布说明中，不用关闭检查或吞掉错误来获得“通过”。

## Compatibility and Risk Notes

- 不删除或重命名 `FlowEngine`、`TaskService`、DTO/VO 字段、流程 JSON 字段和 HTTP 路径。
- `warm-flow-ui` 与 `warm-flow-vue-designer` 的职责保持双轨，源码合并必须另立项目评审，不能在本计划中直接改为单工程。
- `TaskServiceImpl` 拆分涉及流程状态机，任何行为差异都需要外部 `warm-flow-test` 验证后才能合并。
- `pnpm build:lib` 当前存在原有 dts 类型错误输出；在 Task 1 完成前不能把构建成功等同于类型质量达标。
