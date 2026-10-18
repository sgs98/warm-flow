# 更新日志

本文件记录 warm-flow（sgs98 二次开发版）的重要版本变更。使用文档、在线更新日志与升级指南见 [sgs98.github.io/warm-flow-doc](https://sgs98.github.io/warm-flow-doc/)。

## 2.1.0（未发布）

### 引擎

- 落地发起人办理标识（`FlowCons.WARMFLOWINITIATOR`）：节点办理人配置为 `warmFlowInitiator` 时，创建待办统一替换为流程实例发起人（`createBy`），实现"谁发起、谁提交"；实例缺少发起人（如系统自动发起）时保留原标识。新增特征测试 `InitiatorPermissionCharacteristicTest`。
- 新增服务任务节点（`SERVICE`）：支持 `class` / `delegateExpression` / `expression` 三种处理器配置，节点配置经 `ServiceTaskConfig` 统一解析并在保存期校验。
- 新增定义级自动审批（`def_config.autoPass`）：开启后满足条件的审批节点自动通过，设计器支持节点扩展属性编辑。
- 新增流程事件能力（设计详见 `docs/workflow-event-design.md`，使用指南见文档站「高级功能 → 流程事件与内置定时器」）：定时启动 / 中间定时捕获 / 定时边界 / 终止结束、消息启动 / 中间消息捕获 / 消息边界 / 消息抛出 / 消息结束、错误捕获 / 错误边界共 11 类事件。
- 定时事件底座 `TimerEngine`：支持 `AFTER` / `FIXED` / `POINT_IN_TIME` 三种触发策略、CAS 抢占与租约、过期恢复、失败退避重试与 `DEAD`；`WAITING` / `RETRY` 双游标分页扫描，单条事件异常不终止扫描循环。
- 消息事件：`Message Start` 按 `flowCode + eventName` 定位，消息捕获与消息边界按 `instanceId + eventName` 定位并经 `claimMessage` 抢占；`MESSAGE_THROW` / `MESSAGE_END` 经 `EventPublisher` 扩展点调用宿主发布器，未注册发布器时告警跳过。
- 错误事件：错误经宿主 API 显式抛出——`TaskService.throwError` 中断附着任务并走错误边界出口，`InsService.throwError` 路由到实例级错误捕获；错误码精确匹配优先于通配（留空通配），无匹配时抛「流程错误未被捕获」异常并整体回滚，错误等待不参与定时调度与重试。
- 无普通任务的事件等待实例支持查询（`WorkflowService.waitingEvents`）、撤回与终止；终止结束、实例终止、作废、撤回与级联删除统一清理等待记录；定时启动等待随定义发布 / 取消发布 / 挂起 / 激活同步重建与取消。
- 事件等待记录新增只读查询 API（`WorkflowService.pageEventWaits` / `listEventWaits` / `countEventWaits` / `getEventWait`，数据侧 `FlowEventWaitDao` 同步提供）：条件对象 `EventWaitQuery` 可按 `definitionId(s)` / `instanceId(s)` / `nodeCode` / `eventName` / `attachedTaskId` / `eventTypes` / `triggerTypes` / `statuses` / 下次触发时间与创建时间区间自由组合，`businessId` / `flowCode` 由引擎解析为实例 / 定义主键后下推（可与主键条件互斥校验）；排序字段走白名单、非法值直接报错，默认只查定时类事件、按下次触发时间升序，解析均基于查询条件副本，不改写调用方入参。

- 状态与协作三维拆分：实例状态（`FlowStatus`）、任务状态（新增 `TaskStatus`）、协作动作（`CooperateType`）分开建模，分别落 `flow_instance.flow_status`、`flow_task` / `flow_his_task.flow_status`、`flow_his_task.cooperate_type`；实例状态不再复制待办状态码，改由落点语义推导（结束→已完成、退回→已退回、其余→审批中），避免宿主自定义映射后两套编码串列。
- 新增状态映射扩展点 `StatusMapper`（`instanceStatus` / `taskStatus` / `cooperateStatus` / `isTerminal` / `label`）：宿主用自己的状态枚举接管三个维度的落库编码与终态判定，各操作点不再需要手动传 `flowStatus` / `taskStatus`；默认实现沿用内置编码，升级不需要迁移数据。注册方式 `FlowEngine.setStatusMapper(...)` 或配置 `warm-flow.status-mapper-path`。
- `FlowStatus` 精简为实例状态（移除 `PASS` / `INVALID` / `TASK_BACK`，任务维度改用新增的 `TaskStatus`）；自动审批与服务任务历史统一记「通过」，不再写入 `AUTO_PASS`。
- 结束收口语义补全：流程结束时仍存在的待办不再静默删除，改按「失效」（`TaskStatus.INVALID`）归档进 `flow_his_task`（保留节点、办理人、创建时间，便于审计）；已归档的待办（本次办理 / 终止的那条）自动去重，不重复归档。
- 普通完成链路补齐事件等待清理：实例到达结束节点即取消本实例的 `flow_event_wait` 记录，避免流程已完成后仍有僵尸等待被外部消息或定时器唤醒；终止结束、显式终止路径行为不变（原本就会清理）。
- 结束事件留痕：`TERMINATE_END` / `MESSAGE_END` 到达时在 `flow_his_task` 写一条结束事件记录（`task_id` 以 `0` 为哨兵、`flow_status` 为「结束」），备注区分「流程由该终止结束事件终止」与「流程由该结束事件节点结束」，按 `node_code` 即可筛出「因严重异常终止」的单子。
- 终止结束的实例状态修正：`TERMINATE_END` 到达后实例状态由「已完成」改为「终止」（内置码 `4`，自定义映射取 `StatusMapper.instanceStatus(FlowStatus.TERMINATE)`），消息结束与普通完成仍为「已完成」；结构上仍收口到固定 `END` 节点，语义上不再与正常办结混淆。
- 节点默认流程变量提到节点级并全节点开放：数据位置由 `node_config.event.variables` 改为 `node_config.variables`（旧格式仍兼容读取，存量 JSON 无需迁移），语义为「缺失时的兜底默认值」——流程走到该节点才补充，办理时传入的同名变量优先，随后写入实例变量；设计器在开始 / 中间 / 网关 / 服务任务 / 结束五类节点的「默认变量」中配，不再局限于事件节点。
- 定时启动的实例状态修正：触发后实例直接处于「审批中」（此前为「待提交」）——定时启动没有发起人提交环节，首个待办即审批任务。

### 设计器

- 经典与仿钉钉设计器开放 11 类事件配置（事件类型、消息名称、错误码、定时策略与时间值、附着任务节点、中断开关），保存后重新打开 JSON 保持一致。
- 开始 / 中间 / 网关 / 服务任务 / 结束五类节点的基础信息区新增「默认变量」键值编辑器（数字 / 布尔自动类型推断），此前该能力只挂在启动事件上。
- 办理人设置新增「发起人」快捷按钮（写入 `warmFlowInitiator` 标识），办理人名称回显对引擎无法解析的标识补充占位行。

### Demo

- 流程实例列表支持勾选多条批量物理删除（`DELETE /api/instances?ids=...`，返回实际删除数量），与单条删除一致清理流程用户、待办与历史任务。
- Demo 前端分层重构：新增 `api/`（definitions / instances / tasks / events / users 五个领域模块）、`types/workflow.ts`（统一展示模型）、`composables/usePagedList`（分页列表逻辑复用），全部视图与组件不再直接拼 HTTP 调用；`X-User-Name` 由 axios 拦截器统一注入。
- Demo 后端分层重构：1171 行的 `WorkflowServiceImpl` 拆分为 `WorkflowSupport`（共享缓存与 VO 组装）+ `InstanceAppService`（发起 / 查询 / 撤回 / 终止 / 删除）+ `TaskAppService`（待办已办抄送 / 按钮权限 / 通过退回转办委派加签减签），Controller 与对外 API 路径保持不变。
- 事件测试页（`/events`）：重构为通用外部事件模拟器——发起回归实例列表（仅消息启动案例保留「消息启动」按钮），实例操作按钮由等待事件数据驱动（有消息等待出现「触发消息」弹窗，不再按流程硬编码）。错误边界案例改为「财务执行付款」节点 `create` 监听器 `DemoPayFailListener` 读流程变量 `payAmount`、超过限额 1000 即抛 `PAY_FAILED` 自动进边界，前端不再提供「模拟抛错」按钮。
- 新增定时任务观测页（`/timers`）与配套只读接口：`GET /api/event-waits`（分页查询 `flow_event_wait`，默认只查定时类事件、按下次触发时间升序，支持状态 / 事件类型 / 流程编码 / 业务 ID / 节点编码 / 事件名 / 下次触发与创建时间区间组合过滤）与 `GET /api/event-waits/summary`（同组筛选条件下的各状态计数）；页面提供状态计数快捷过滤、条件区、详情抽屉、查看实例，并对消息类等待「触发消息」、错误类等待「抛错」（复用既有事件触发接口），不提供取消 / 立即触发 / 重新排期等写操作。
- 接入状态映射扩展点 `DemoStatusMapper`：实例状态映射到 `BusinessStatusEnum`、任务状态映射到 `TaskStatusEnum`、协作动作映射为任务状态，业务层不再在发起 / 撤回 / 终止 / 办理 / 退回 / 转办 / 委派 / 加签 / 减签处手动传状态。
- 各列表页表格补充统一空状态提示（el-empty）。
- 修复实例/待办状态展示裸数字的存量缺陷：实例状态由引擎写入数字码（如 1=审批中），VO 组装只查了 demo 字符串枚举 `BusinessStatusEnum`，未命中时回落原始值导致列表显示「1」；现补充引擎 `FlowStatus` 文案兜底，实例详情/列表均正常显示「审批中」等。
- 修复实例列表「继续办理/重新提交」按钮从不显示的存量缺陷：按钮判断依赖 demo 字符串业务码（draft/back/cancel），与引擎写入的任务状态码不匹配；现 `InstanceVo` 透出当前待办任务状态码 `taskStatusKey`（0 待提交 / 9 已退回），按钮按此显隐。

### 数据库

- 新增表 `flow_event_wait`；`flow_node` 新增 `event_type`、`event_name`、`attached_to_node_code`、`interrupting` 4 列；修正 `flow_node.node_config` 列注释（自定义扩展属性实际在 `node.ext`）。四套全量脚本（MySQL / Oracle / PostgreSQL / SQL Server）已同步，且经脚本逐表逐列比对结构一致；MySQL 增量升级脚本见 [sql/mysql/v1-upgrade/warm-flow_2.1.0.sql](./sql/mysql/v1-upgrade/warm-flow_2.1.0.sql)。
- 全量脚本修正两处存量问题：SQL Server 的 `flow_node` 补齐 `listener_type` / `listener_path` 两列（其余三方言已有）；Oracle 删除 `FLOW_NODE.HANDLER_TYPE` / `HANDLER_PATH` 两条孤儿注释（表无此两列，执行脚本会报 ORA-00904）。
- 同步状态列注释到三维语义：四套全量脚本中 `flow_instance.flow_status` 改为「实例状态（0待提交 1审批中 4终止 5作废 6撤销 7取回 8已完成 9已退回 12重启 13暂存）」，`flow_task` / `flow_his_task.flow_status` 改为「任务状态（1待办理 2通过 8结束 9退回 10失效 11拿回）」，并标注「勿套用实例状态编码」——此前三处共用一套旧混合注释（含已移除的「2审批通过」），且 `8` 在实例表是「已完成」、在任务表是「结束」，极易误读。`cooperate_type` 注释同步纠正为「1无」（此前写「1审批」）并注明是行为维度。MySQL 升级脚本给出对应的 `MODIFY COLUMN` 语句（仅改注释、不动类型与数据，存量库可跳过）。

### 升级提示

- 事件能力默认关闭：升级需执行对应数据库脚本，并显式配置 `warm-flow.timer-enabled=true` 才会启动定时扫描线程；未开启或未建表时既有流程行为不变。
- 事务边界：事件到达会同时写任务、实例与等待记录，消息触发后的流程推进亦然，宿主必须用 ORM / JDBC 事务包裹相关调用；`MESSAGE_THROW` / `MESSAGE_END` 的发布器调用与流程状态更新在同一提交边界内，需要可靠投递时请在发布器实现中接入 Outbox。
- 数据库方言：定时扫描的固定条数查询在 Oracle 下使用 `ROWNUM`（MyBatis）/ `OFFSET ... FETCH NEXT`（MyBatis-Plus）改写，Oracle 需 12c 及以上；MySQL、PostgreSQL、SQLServer 使用 `LIMIT`。
- 状态枚举拆分（破坏性）：`FlowStatus.PASS` / `FlowStatus.INVALID` / `FlowStatus.TASK_BACK` 已移除，任务维度改用 `TaskStatus` 对应常量；扩展点 `FlowStatusMapper` 更名为 `StatusMapper`，配置项 `flow-status-mapper-path` 改为 `status-mapper-path`。落库编码未变（`0`/`1`/`2`/`4`~`13`），存量数据无需迁移，只需调整代码引用。
- 行为变更：流程结束时未办待办改为按「失效」归档进历史（此前静默删除），历史列表会多出失效记录；结束事件（终止结束 / 消息结束）另写一条 `task_id=0` 的留痕记录，按 `node_code` 可筛出异常终止的实例。
- 行为变更：终止结束事件（`TERMINATE_END`）的实例状态由「已完成」改为「终止」（内置码 `4`），消息结束与普通完成不变；统计口径若需包含终止单请显式计入。
- 行为变更：普通完成链路现在会取消本实例的事件等待记录（此前只有终止 / 撤回 / 作废会清理），完成后不再残留僵尸等待。

## 2.0.1（2026-10-12）

### 引擎

- 发布流程定义前校验持久化流程图，结构非法的定义不允许进入已发布状态。
- 已发布或已失效的流程定义不再允许保存修改，需复制新版本后编辑，与设计器只读规则对齐。
- 设计器保存与发布校验均由服务端从目标节点派生连线节点类型，不再信任客户端回传值，防止绕过中间节点多出口限制。
- 保存与导入的结构校验合并为共享校验核（`FlowConfigUtil.validateGraph`），两条路径规则保持一致。
- 新增 `copyDefReturnId`：复制流程定义并返回新定义主键；复制产物显式置为未发布草稿，不再依赖数据库默认值。
- `unPublish` 对不存在的流程定义报「流程定义不存在」，不再静默返回 false，与 `publish` 行为对齐。

### 数据库

- `flow_instance.node_code` 长度由 `varchar(40)` 扩至 `varchar(100)`，与节点编码长度对齐；四套全量脚本已同步，MySQL 升级脚本见 [sql/mysql/v1-upgrade/warm-flow_2.0.1.sql](./sql/mysql/v1-upgrade/warm-flow_2.0.1.sql)。

### 升级提示

- 行为变化：已发布或已失效的流程定义不可再经 `saveDef` 就地改图（返回「请复制新版本后编辑」），发布前会校验持久化流程图结构；依赖就地编辑已发布定义的集成方需改为复制新版本。
- 表结构：升级到 2.0.1 前执行对应数据库脚本，MySQL 库需将 `flow_instance.node_code` 扩至 `varchar(100)`。

## 2.0.0（2026-10-03）

首个以 `io.github.sgs98` 坐标对外发布的版本，基于 [dromara/warm-flow](https://gitee.com/dromara/warm-flow) 二次开发维护。

### 构建与生态

- JDK 基线提升至 17，兼容 Java 17 / 21。
- 移除 Spring Boot 2 生态线，支持 Spring Boot 3.5 / 4.0，MyBatis、MyBatis-Plus 均提供 `sb3` / `sb4` starter。
- Maven 坐标由 `org.dromara.warm` 迁移为 `io.github.sgs98`，升级前请阅读[升级指南](https://sgs98.github.io/warm-flow-doc/master/other/upgrade_guide.html)。
- 发布通道支持 Maven Central（Central Portal）与 GitHub Packages。

### 引擎

- 重构流程引擎任务流转：引入 `FlowExecution` 执行作用域 + guard 转移表，统一工作流上下文与命令执行链路。
- 五个流程操作（`start` / `execute` / `revoke` / `terminate` / `updateHandlers`）链化，完善流程操作门面与引擎协作能力。
- 新增实例流程历史图，支持按流程定义查询无实例的流程图数据。
- 监听器新增事件类型上下文，完善监听器事件语义。
- 逻辑删除默认值调整为 1（与 MyBatis-Plus 对齐），支持切换为物理删除模式。
- 移除流程节点版本字段，统一分页查询，清理零引用死代码与编译警告。
- 优化节点图遍历与网关路径解析，优化 MyBatis-Plus 批处理保存，提升大流程处理效率。
- 修复发起流程时首任务状态取值错误等缺陷。

### 设计器（warm-flow-ui）

- 原生支持经典与仿钉钉双模式，并支持画布内一键互转。
- 属性抽屉改为可折叠分组，监听器配置收进基础设置。
- 新增拖拽磁吸对齐，修复拖动节点误弹属性抽屉。
- 网关文案统一为「包容网关」。
- 修复双模型互转的撤销错乱、网关标签重叠、折点脱开、流程图办理人显示等问题。

### Demo 与测试

- 自带 `warm-flow-demo` 演示工程（Spring Boot 3.5 + Vue 3），优化流程办理工作台，新增导入导出示例。
- 补充核心 API 单元测试，增强核心稳定性。

### 数据库

- 提供 MySQL / Oracle / PostgreSQL / SQL Server 四套全量建表脚本。
- MySQL 版本升级脚本见 [sql/mysql/v1-upgrade](./sql/mysql/v1-upgrade)。

### 升级提示

- 坐标变更：依赖需从 `org.dromara.warm:*` 改为 `io.github.sgs98:*`，版本号按实际发布为准。
- Spring Boot 2 用户需先升级到 Spring Boot 3.5 / 4.0 且 JDK 17+。
