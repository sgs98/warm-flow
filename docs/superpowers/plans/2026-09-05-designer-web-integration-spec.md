# Warm-Flow Demo 前端设计器接入 Spec

> 工作 spec（实现前评审用）。对应 `docs/superpowers/plans/2026-09-05-fullstack-integration-test.md` 的 Task 6/7，聚焦「npm 设计器」接入、后端补丁与 Task7 页面；iframe 经决策延期为独立任务。任务勾选按证据推进。

## 目标

在 `warm-flow-demo` 内把流程“设计→保存→发布→发起→办理→历史”真正闭环：定义列表可进入 npm 设计器新建/编辑并保存到本地 `warm-flow` 库；办理人可选择多个 demo 用户；流程实例/待办/已办/历史页面基于真实 API。不修改 Warm-Flow 公共 API、表结构与正式脚本。

## 已定决策与现状

- 数据库：本地 MySQL(Docker `mysql:8.0.42`) 库 `warm-flow`，`root/root`，引擎 7 表已有；demo 表 `demo_user`/`demo_category` 已建并灌数据（admin + approver1/2/3）。
- 多用户模型：节点办理人 `permissionFlag` 用 `@@` 连接多个 `user_name`；操作者身份 = `user_name`，经 `X-User-Name` 请求头传入，引擎以 `handler + permissionFlag` 鉴权。后端 Task1-4 已真机跑通。
- 前端：`warm-flow-demo-web`（Vue3+Element Plus+Pinia+TS）骨架完成，`pnpm typecheck/build` 通过；`http.ts` 已统一解包 `{code,message,data}` 并注入 `X-User-Name`。
- 设计器 npm 包 `@dromara/warm-flow-designer` 未发布 npm，经 pnpm workspace 引用本地 `warm-flow-vue-designer`；**接入前必须执行 `pnpm --filter @dromara/warm-flow-designer build:lib` 产出 `dist-lib`**。

## 设计器数据契约（已核实）

设计器默认 `DataProvider`(`httpProvider.ts`) 调用以下端点并**期望返回已解包的业务数据**（即我们的 `httpGet/httpPost` 返回值，不含 `{code,...}` 外壳）：

| 设计器方法 | 默认端点 | 我们接入 |
| --- | --- | --- |
| `saveJson(data, onlyNodeSkip)` | POST `warm-flow/save-json` | 映射到 demo 保存接口（见下） |
| `queryDef(id?)` | GET `warm-flow/query-def[/{id}]` | 新增 `GET /api/designer/query-def[/{id}]`（id 空返回空白 CLASSICS 默认，非空返回详情） |
| `queryFlowChart(id)` | GET `warm-flow/query-flow-chart/{id}` | 实例运行图（Task7 详情页用） |
| `handlerType/handlerResult/handlerFeedback/handlerDict` | `/warm-flow/handler-*` | 新增 demo 办理人系列端点 |
| `publishedList/nodeExt/listenerList/config` | 各自端点 | 返回稳定默认/空集 |

- 注入方式：host 页在 `main.ts`/入口调用 `setDataProvider({...})`（见 `src/data/provider.ts`，`setDataProvider` 会用 `Object.assign` 合并到默认实现，可只覆盖部分方法）。Demo 需**全量覆盖**以走 `/api`。
- 精确响应结构（办理人选择器返回的 `HandlerSelectVo`、保存后返回的 definitionId 等）在实现阶段**对齐 `mockProvider.ts` 与 plugin-ui `vo`**，不得臆造字段名；`queryDef(id=null)` 返回空白定义用于新建。

## 后端补丁（`warm-flow-demo/warm-flow-demo`）

- 新增 `DesignerController`（`/api/designer`）：
  - `GET /query-def[/{id}]`：`id` 空 → 空白 DefJson（`modelValue=CLASSICS`、`formCustom=N`）；非空 → `defService.queryDesign(id)`。
  - `POST /save`：`{ data, onlyNodeSkip }` → `defService.saveDef`，返回新/更新的 `definitionId`。
  - `GET /flow-chart/{instanceId}`（预留）：返回实例 defJson + 状态着色（可用 `chartService`）。
- 新增办理人系列端点（`/api/designer` 或复用 `/api`）：`handler-type`（返回 `["user"]` 单 tab）、`handler-result`（分页返回 demo 用户，字段对齐 `HandlerSelectVo`）、`handler-feedback`（按 storageId 回显姓名）、`handler-dict`（返回默认表达式 3 项，复刻 plugin-ui 默认）。
- `node-ext`、`listener-list`、`published-form`：返回空集/空列表，保证选择器不崩。
- 统一异常沿用 `GlobalExceptionHandler`。

## 前端接入（`warm-flow-demo-web`）

- `src/api/provider.ts`：实现 `setDataProvider` 覆盖（saveJson/queryDef/handler*/…），全部映射到 `/api/designer`，复用 `http.ts` 与 `X-User-Name`。
- 新增 `DefinitionEdit.vue`（路由 `/definitions/:id?`）：embed `FlowDesigner`；
  - 新建/编辑：由 `queryDef(id)` 决定初始；`disabled` 控制只读（发布预览可复用）。
  - 保存：走 designer `save()`/`before-save` 语义 → provider.saveJson；保存成功回写 `definitionId`，跳转/停留在列表。
  - 校验：`structureValidator` 或 designer 内置校验拦截未连线/空名。
- 定义列表 `DefinitionList.vue` 增加「新建 / 设计 / 编辑」入口并跳转 DefinitionEdit。
- 需要的公共包构建顺序：`@dromara/warm-flow-designer build:lib` 先行。

## iframe 链路（决策变更）

- **已否决 Option A**：`warm-flow-plugin-ui-sb-web` 仅适配 Spring Boot 2(javax)，demo 为 SB3(jakarta)，无 SB3 变体，不能直接挂。iframe 在本 demo 内**延期**，独立后续任务，另出设计。，启用其标准 `/warm-flow-ui/**` 静态资源与后端接口（`WarmFlowUiController/WarmFlowController`），前端 `IframeDesignerPage.vue` 用 `<iframe src="/warm-flow-ui/index.html...">` 加载，走官方 iframe 交付链路。需业务自实现的 `HandlerSelectService` 等提供 demo 用户数据。
- 备选 B（不改）：给 `warm-flow-ui` 加宿主“加载/保存/只读”postMessage 协议 —— 触碰库交付物，需另立评审，**本 spec 不采用**。
- 验收：npm 与 iframe 用同一份流程 JSON 均能加载/编辑/保存；比较 `flowCode`、节点编码、边起止、`skipCondition` 一致。

## Task 6/7 任务清单

- [x] Task 6.0 预构建设计器：`pnpm build:lib`（warm-flow-vue-designer）（已产出 dist-lib）
- [x] Task 6.1 后端 DesignerController + 办理人/表单/节点扩展默认端点，`mvn compile` 通过并 curl 冒烟
- [x] Task 6.2 前端 `api/provider.ts` 注入；`DefinitionEdit`(npm) + 列表入口；`typecheck`/`build` 通过（typecheck 0 错、build 成功；浏览器联调待验）
- [x] Task 6.3（决策：延期）——实测 plugin-ui-sb-web 仅 SB2(javax)，demo 为 SB3(jakarta)，Option A 不可行；iframe 作为独立后续任务另行设计，不在本 demo 内做
- [x] Task 7.1 实例详情 + 历史时间线（真实 API）
- [x] Task 7.2 待办操作弹窗：通过/退回/转办/委派/加签/减签 + 提交态/防重复/错误提示
- [ ] Task 8.1 补充后端流程状态测试、前端 e2e(Playwright) 关键路径
- [ ] Task 8.2 构建门禁 + README/验证记录更新

## 本轮查缺补漏记录（2026-09-05）

- 修复 `WorkflowFacade.transfer/depute`：引擎 `transfer/depute` 读 `flowParams.addHandlers`（见 `TaskServiceImpl`），原实现传 `nextHandler` 导致“转办/委派对象不能为空”。改为 `fp.addHandlers(...)`。
- 修复 `DemoTaskQueryMapper.selectTodoPage`：原只查 `flow_user.type='1'`（审批），转办(type=2)/委派(type=3)后新办理人的待办不可见。改为仅按激活行 `del_flag='0'` 匹配，并在真机验证转办/委派后目标办理人待办可见且 assignees 正确。
- 修复待办“办理人”列：`assigneesOf` 由只取审批人改为 `APPROVAL/TRANSFER/DEPUTE`。
- `DefinitionList.vue` 补充“复制”操作（`POST /definitions/{id}/copy`）。
- 对齐文档：README/schema 数据库名统一为 `warm-flow`（原写 `warm_flow_demo`）。
- 结论与边界（真机验证）：退回(REJECT)要求审批节点配置 REJECT 跳转边；demo 默认单级纯通过流程不支持退回（引擎报“未找到跳转类型匹配的目标节点”，属流程建模而非代码缺陷）。终止需当前节点办理人执行，撤回需发起人执行（均符合引擎鉴权）。
- 前端 typecheck 0 错、`pnpm build` 通过；后端 `mvn -DskipTests compile` 通过。浏览器自动化(Playwright)与后端集成测试未在本轮落地，见 Task 8.1。

## 验收标准

- 定义可在 npm 设计器中新建/编辑/保存（含多用户办理人）到本地库；发布后可从列表发起实例。
- 实例详情/历史、待办/已办基于真实 API；办理操作覆盖通过/退回/转办/委派/加签/减签并处理后端提示。
- （iframe 链路已决策延期，见上，不在本 demo 验收。）
- 后端 `mvn -DskipTests compile`、前端 `pnpm typecheck`/`pnpm build` 通过；能真机 curl/浏览器冒烟的部分给出证据，不能的部分（如浏览器自动化）如实记录。
- 不改 Warm-Flow 公共 API、表结构、正式 SQL 与部署配置。

## 非目标 / 风险

- 不在本 spec 内改动 `warm-flow-ui` 或给设计器加 postMessage（iframe 选 Option A）。
- 办理人选择器/历史时间线等精确字段结构需对齐 plugin-ui `vo` 与 `mockProvider`，若发现与设计器内部期望不一致，按“最小修改、先证据后合并”处理。
- 浏览器自动化在当前环境可能无法执行；Playwright 结果按证据如实记录，不造假。
