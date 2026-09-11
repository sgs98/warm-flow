# Warm-Flow 全栈集成测试项目实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建一个独立的 Spring Boot + Vue + Element Plus 业务型集成测试项目，完整验证 Warm-Flow 后端流程能力与设计器 npm/iframe 两种交付链路。

**Architecture:** 新项目放在 `warm-flow-demo/`，不加入正式 Maven 反应堆，不修改 Warm-Flow 公共 API。后端提供流程定义、实例、待办、历史和用户测试 API，前端提供真实业务操作页面；npm 设计器作为主流程设计入口，iframe 页面作为独立交付链路验证入口。现有 `warm-flow-designer-demo` 在新项目验收前保留，避免丢失当前构建基线。

**Tech Stack:** Spring Boot 3、MyBatis-Plus、MySQL 8、Java 8 源码兼容、Vue 3、Vite、TypeScript、Element Plus、`@dromara/warm-flow-designer`、npm/pnpm。

---

## 文件边界

**后端：** `warm-flow-demo/warm-flow-demo/`

- `pom.xml`：独立 Maven 工程，仅依赖当前 Warm-Flow starter、Web、校验和 MySQL 驱动。
- `src/main/java/.../IntegrationApplication.java`：Spring Boot 启动入口。
- `config/`：Warm-Flow、MyBatis-Plus、跨域和 Jackson 配置。
- `controller/`：流程定义、实例、任务、用户 API。
- `service/`：业务编排和 DTO 映射，不直接把数据库实体暴露给前端。
- `dto/`、`vo/`：请求和响应模型。
- `src/main/resources/sql/`：初始化表结构和测试数据。
- `src/test/`：Controller 参数校验、流程 API 和状态流转集成测试。

**前端：** `warm-flow-demo/warm-flow-demo-web/`

- `src/api/`：按业务域封装 HTTP 请求。
- `src/layout/`：后台布局、导航和全局反馈。
- `src/views/`：流程定义、流程设计、实例、待办、已办、详情页面。
- `src/components/`：流程状态、任务操作、历史时间线等复用组件。
- `src/router/`、`src/stores/`、`src/types/`：路由、状态和接口类型。
- `src/views/flow-designer/`：npm 设计器宿主页面与 iframe 验证页面。

**项目文档：**

- `warm-flow-demo/README.md`：启动、数据库初始化、账号和验证流程。
- `docs/frontend-flow-regression.md`：补充真实后端联调场景和验收记录。
- `docs/optimization-verification.md`：增加集成项目构建命令和结果。

### Task 1: 创建独立项目骨架

**Files:**
- Create: `warm-flow-demo/README.md`
- Create: `warm-flow-demo/.gitignore`
- Create: `warm-flow-demo/warm-flow-demo/pom.xml`
- Create: `warm-flow-demo/warm-flow-demo-web/package.json`
- Create: `warm-flow-demo/warm-flow-demo-web/vite.config.ts`

- [ ] **Step 1: 建立目录和最小配置**

后端使用 `spring-boot-starter-parent` 3.x、`warm-flow-mybatis-plus-sb3-starter`，版本通过根项目当前版本属性传入；前端固定 Vue 3、Vite、TypeScript、Element Plus 和设计器 npm 包版本，不修改根项目依赖。

- [ ] **Step 2: 添加启动说明**

README 明确数据库地址、初始化 SQL、后端启动命令 `mvn spring-boot:run`、前端启动命令 `pnpm dev`，并注明该目录不参与正式发布。

- [ ] **Step 3: 验证骨架**

运行 `mvn -DskipTests compile` 和 `pnpm install --frozen-lockfile`；预期后端编译成功、前端依赖安装成功。

### Task 2: 配置后端和测试数据库

**Files:**
- Create: `backend/src/main/java/org/dromara/warm/integration/IntegrationApplication.java`
- Create: `backend/src/main/java/org/dromara/warm/integration/config/WebConfig.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-local.yml`
- Create: `backend/src/main/resources/sql/schema.sql`
- Create: `backend/src/main/resources/sql/data.sql`

- [ ] **Step 1: 配置数据源和 Warm-Flow**

默认连接 `jdbc:mysql://localhost:3306/warm_flow_integration`，允许通过环境变量覆盖；启用 `warm-flow.enabled=true`、逻辑删除和 MyBatis-Plus 分页，不修改引擎表结构。

- [ ] **Step 2: 配置跨域和统一响应**

仅允许本地前端开发源 `http://localhost:5173`，统一返回 `{code, message, data}`，异常返回 400/404/500 对应 HTTP 状态。

- [ ] **Step 3: 初始化最小测试数据**

插入管理员、审批人、流程分类和一条三节点审批流程；SQL 必须与当前 MySQL 全量脚本字段保持一致，不执行删除或重写现有仓库 SQL。

- [ ] **Step 4: 验证启动**

执行 SQL 后运行后端，访问 `GET /api/health`，预期返回成功响应并能建立数据库连接。

### Task 3: 实现流程定义 API

**Files:**
- Create: `backend/src/main/java/org/dromara/warm/integration/controller/DefinitionController.java`
- Create: `backend/src/main/java/org/dromara/warm/integration/service/DefinitionFacade.java`
- Create: `backend/src/main/java/org/dromara/warm/integration/dto/DefinitionSaveRequest.java`
- Create: `backend/src/main/java/org/dromara/warm/integration/vo/DefinitionSummaryVO.java`
- Test: `backend/src/test/java/org/dromara/warm/integration/DefinitionControllerTest.java`

- [ ] **Step 1: 定义接口契约**

实现分页查询、详情、保存、发布、复制和删除：`GET /api/definitions`、`GET /api/definitions/{id}`、`POST /api/definitions`、`PUT /api/definitions/{id}/publish`、`POST /api/definitions/{id}/copy`、`DELETE /api/definitions/{id}`。

- [ ] **Step 2: 保持流程 JSON 原样传递**

保存请求中的 `flowCode`、`flowName`、`modelValue`、`nodeList` 和 `skipCondition` 原样交给 Warm-Flow 服务，不在集成项目中重写转换逻辑。

- [ ] **Step 3: 补参数校验和异常测试**

覆盖空编码、空名称、重复编码、无效 definitionId 和已发布流程修改；测试断言 HTTP 状态和统一响应字段。

### Task 4: 实现实例和任务 API

**Files:**
- Create: `backend/src/main/java/org/dromara/warm/integration/controller/InstanceController.java`
- Create: `backend/src/main/java/org/dromara/warm/integration/controller/TaskController.java`
- Create: `backend/src/main/java/org/dromara/warm/integration/service/WorkflowFacade.java`
- Create: `backend/src/main/java/org/dromara/warm/integration/vo/TaskVO.java`
- Create: `backend/src/main/java/org/dromara/warm/integration/vo/HistoryVO.java`
- Test: `backend/src/test/java/org/dromara/warm/integration/WorkflowFacadeTest.java`

- [ ] **Step 1: 实现实例发起和详情**

提供 `POST /api/instances`、`GET /api/instances/{id}`、`GET /api/instances/{id}/history`。

- [ ] **Step 2: 实现待办和已办查询**

提供 `GET /api/tasks/todo`、`GET /api/tasks/done`，支持分页、流程编码筛选和当前用户筛选。

- [ ] **Step 3: 实现任务操作**

提供通过、退回、转办、委派、加签、减签、撤回和终止 API；请求字段映射到现有 `FlowParams`，不新增或修改核心接口。

- [ ] **Step 4: 编写状态流转测试**

至少覆盖通过、退回、转办、委派、加签、减签和终止，断言实例状态、待办数量和历史记录数量。

### Task 5: 创建 Vue 业务后台

**Files:**
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/layout/AppLayout.vue`
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/stores/session.ts`
- Create: `frontend/src/styles/index.scss`

- [ ] **Step 1: 配置 Element Plus 和 HTTP 客户端**

统一使用 Element Plus，配置请求前缀、加载态、错误提示和 401/403 分支；不引入第二套 UI 库。

- [ ] **Step 2: 建立后台布局和路由**

菜单包含流程定义、流程实例、我的待办、我的已办和用户测试；桌面端和窄屏保持可用，不做营销型首页。

- [ ] **Step 3: 建立类型和 API 封装**

为定义、实例、任务、历史和分页响应建立 TypeScript 类型，所有页面只能通过 `src/api/` 调用后端。

### Task 6: 实现流程定义和设计器页面

**Files:**
- Create: `frontend/src/views/definitions/DefinitionList.vue`
- Create: `frontend/src/views/definitions/DefinitionEdit.vue`
- Create: `frontend/src/views/flow-designer/NpmDesignerPage.vue`
- Create: `frontend/src/views/flow-designer/IframeDesignerPage.vue`
- Create: `frontend/src/components/definitions/DefinitionTable.vue`
- Create: `frontend/src/api/definitions.ts`

- [ ] **Step 1: 实现定义列表**

支持分页、关键字筛选、状态展示、新建、编辑、发布、复制和删除；处理加载态、空态和接口错误。

- [ ] **Step 2: 接入 npm 设计器**

通过 `@dromara/warm-flow-designer` 主入口和 Element Plus 子入口接入，完成流程 JSON 初始加载、保存回写、发布前校验和只读预览。

- [ ] **Step 3: 接入 iframe 页面**

使用 `warm-flow-ui` 构建产物，通过 iframe 加载设计器，明确 postMessage 的加载、保存和只读消息协议，并增加来源校验。

- [ ] **Step 4: 验证双链路一致性**

同一份流程 JSON 分别通过 npm 和 iframe 加载、编辑、保存，再比较 `flowCode`、节点编码、边起止编码和 `skipCondition`。

### Task 7: 实现实例、待办和历史页面

**Files:**
- Create: `frontend/src/views/instances/InstanceList.vue`
- Create: `frontend/src/views/instances/InstanceDetail.vue`
- Create: `frontend/src/views/tasks/TodoList.vue`
- Create: `frontend/src/views/tasks/DoneList.vue`
- Create: `frontend/src/components/tasks/TaskActionDialog.vue`
- Create: `frontend/src/components/instances/HistoryTimeline.vue`
- Create: `frontend/src/api/workflow.ts`

^- [x] **Step 1: 实现实例列表和发起流程**

支持按流程、状态、发起人筛选，进入详情并从已发布流程发起实例。

^- [x] **Step 2: 实现待办操作**

操作弹窗必须处理表单绑定、办理意见、提交态、成功刷新、失败提示和重复提交保护。

^- [x] **Step 3: 实现历史时间线**

展示节点、办理人、动作、意见、时间和状态，缺失字段显示稳定空态，不抛出渲染异常。

### Task 8: 完成端到端验证和文档

**Files:**
- Create: `frontend/e2e/workflow.spec.ts`
- Create: `backend/src/test/resources/application-test.yml`
- Modify: `warm-flow-demo/README.md`
- Modify: `docs/frontend-flow-regression.md`
- Modify: `docs/optimization-verification.md`

- [ ] **Step 1: 编写后端集成测试**

使用独立测试数据库，按“建流程 -> 发布 -> 发起 -> 待办通过 -> 查看历史”的顺序验证完整链路，并覆盖退回、转办和终止。

- [ ] **Step 2: 编写前端浏览器测试**

使用 Playwright 验证 npm 页面和 iframe 页面各一条完整链路，断言无控制台错误、保存成功提示出现、列表状态刷新。

- [ ] **Step 3: 执行构建门禁**

运行后端 `mvn -DskipTests package`、前端 `pnpm typecheck`、`pnpm build`、现有设计器 round-trip、现有 Demo 构建和 iframe 构建。

- [ ] **Step 4: 记录风险和清理决策**

记录数据库前置条件、bundle 体积警告和未执行项。只有新项目完成全部验收后，才单独评估是否删除旧的 `warm-flow-designer-demo`，本计划不直接删除。

## 验收标准

- Spring Boot 后端可连接 MySQL，并完成流程定义、实例、任务和历史 API。
- Vue 前端仅使用 Element Plus，所有业务页面可通过真实 API 操作。
- npm 和 iframe 两种设计器链路均可加载、编辑、保存和只读查看同一流程 JSON。
- 通过、退回、转办、委派、加签、减签、终止至少有后端测试保护。
- 前端 typecheck、生产构建、后端编译和浏览器关键路径验证通过。
- 不修改 Warm-Flow 公共 API、实体字段、状态枚举、数据库正式脚本和部署配置。
- 不执行 `git commit` 或 `git push`，由用户自行提交。
