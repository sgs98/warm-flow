---
name: warm-flow-demo-testing
description: 使用 Warm-Flow Demo 对流程定义、网关、草稿变量、任务协作、事件与内置定时器做可重复的端到端回归测试；适用于复测流程引擎改动或排查 Demo 流程行为，不替代独立测试仓库的多数据库测试。
---

# Warm-Flow Demo 流程测试

## 目标

通过 Demo 的 HTTP API 和数据库活动任务验证真实流程行为，覆盖：发起、草稿、变量、通过、驳回、转办、委托、加签、减签、撤回、终止、抄送，以及排他、并行、包容网关，和事件节点与内置定时器（定时启动 / 中间定时捕获 / 定时边界 / 消息抛出）。测试必须给出每个场景的输入、实际结果、数据库状态和未验证项。

## 测试前检查

1. 阅读仓库根 `AGENTS.md`，再阅读 `warm-flow-demo/README.md` 和 Demo 所在模块规则。
2. 检查 Demo 启动配置、流程 API、任务 API、`BusinessStatusEnum` 和现有流程定义，不假设固定流程编码或固定节点 ID。
3. 检查 MySQL/Demo 是否已经运行。优先复用现有环境；不要重建数据库、执行 DROP、批量 DELETE 或清空流程表。
4. 若需启动 Demo，优先使用未占用端口，例如 `8081`，并记录 URL、数据库和进程会话。
5. 每条测试使用唯一且不超过 40 个字符的业务号，例如 `WF-D-<unix秒>`；不要把纳秒时间戳、完整日期和长场景名全部拼入业务号。测试创建的数据默认保留，除非用户明确要求清理。
6. 测事件 / 定时器前，确认 Demo 依赖的 `warm-flow` 版本已包含待测代码：Demo 一般依赖本地 `~/.m2` 的同版本坐标，先在仓库根 `mvn clean install -DskipTests` 安装当前反应堆，再启动 Demo；不要假设 Demo 会自动使用工作区源码。
7. 测事件 / 定时器前，确认数据库已应用事件结构（`flow_node` 的 `event_type` / `event_name` / `attached_to_node_code` / `interrupting` 四列与 `flow_event_wait` 表）：对已存在的库执行对应版本升级脚本（如 `sql/mysql/v1-upgrade/warm-flow_<version>.sql`），不要重新建库或删表。
8. 内置定时器默认关闭。启动 Demo 时用启动参数开启，不改 Demo 配置文件，例如 `-Dspring-boot.run.arguments="--warm-flow.timer-enabled=true --warm-flow.timer-scan-interval=1000 --warm-flow.timer-lease-seconds=30"`；启动完成日志应出现「Warm-Flow 内置定时器已启动」，否则定时事件不会触发。
9. 办理类请求（`pass`/`reject`/`jump`/`terminate`/转办/委托/加减签）必须带请求头 `X-User-Name`，只带 body 里的 `user` 字段不够（Demo 按请求头装配 `PermissionHandler`）；发起用 `admin`，办理用节点办理人。

## 内置流程定义（复测数据从哪来）

Demo 的流程定义**不由 SQL 灌入**（`warm-flow-demo/src/main/resources/sql/data.sql` 头部已声明），统一放在 `warm-flow-demo/warm-flow-demo/src/main/resources/json/`，**文件名 = 定义里的 `flowCode`**。导入接口永远生成**新的未发布版本**，必须再发布才能用于发起 / 定时启动 / 消息启动；换库或定义被改乱时用 `scripts/reimport-definitions.sh` 一次性整理（见下）。

| json / flowCode | 覆盖能力 |
| --- | --- |
| `demo_gateway_classic`、`demo_gateway_mimic` | 排他 + 并行 + 包容网关；两文件结构相同，只差设计器模式（`CLASSICS` / `MIMIC`），网关复测主用定义 |
| `demo_service_task` | 服务任务三种执行方式（class / delegateExpression / expression）+ 网关 + 并行 |
| `demo_event_refund` | 定时边界 + 消息结束（`refund.finish`） |
| `demo_event_risk_wait` | 消息启动（`order.paid`）+ 消息捕获（`risk.result`） |
| `demo_event_cooldown` | 定时捕获 + 消息抛出（`membership.cancelled`） |
| `demo_event_pay_fail` | 错误边界 + 判定型监听器抛错（`payAmount` 超限 1000） |
| `demo_event_daily_check` | 定时启动 + 终止结束打断并行分支（`veto=1`；`veto=0` 正常汇聚）；定时启动节点带默认变量 `veto=0`/`channel=daily`（触发时并入实例变量） |
| `demo_event_notify` | 消息启动（`membership.cancelled`），由 `demo_event_cooldown` 抛出的消息驱动 |

维护约定：

- 导出文件名形如 `流程编码_版本号.json`；**收进仓库时去掉版本后缀**，一个 flowCode 只保留最新一份文件，文件名必须等于 `flowCode`（改名时同步改文件里的 `flowCode` / `flowName`）。
- 文件里的 `version` 是导出快照；导入时引擎按同编码递增重新编号，不要手改它来「控版本」。
- 发布同一 `flowCode` 的新版本会把旧版本转为未发布（已被实例用过则转为已失效），并重建该版本的 `TIMER_START` 定义级等待；删除定义会级联清理节点 / 连线 / 定义级等待，但**已被实例使用的定义会被拒绝删除**。
- 整理脚本：`CONFIRM=YES bash scripts/reimport-definitions.sh`（导出备份 → 全删 → 按 json 目录逐个导入并发布；存在流程实例时拒绝执行）。默认打 `http://localhost:8080`，可用 `BASE=` / `JSONDIR=` / `DUMPDIR=` 覆盖。

## 核心测试顺序

先用流程定义查询接口获取已发布定义和节点/跳转配置，再按以下顺序执行。每一步都重新查询活动任务，不使用已经逻辑删除或已完成的任务 ID。Shell 脚本读取任务集合时必须逐行遍历或使用 `jq -c` 循环，不能用 `read` 一次接收多行结果。

### 1. 发起与草稿

- 发起时不填写审批说明，确认请求成功。
- 带 `variables` 发起，确认实例详情能完整回显变量。
- 确认实例为 Demo 业务状态 `draft`，启动历史任务为 `pass`，首节点任务可继续办理，且没有提前生成后续审批节点。
- 关闭/不提交首节点后，重新从实例列表打开，确认仍能继续办理。

### 2. 普通办理与网关

- 通过首节点，确认排他网关只生成第一个满足条件的分支；无条件出口只能作为兜底。
- 使用 `day=4`、`day=2`、`day=3` 等明确变量分别验证条件分支，不能只看接口返回，要查询 `flow_task`。
- 并行网关应一次生成全部出口任务；完成一个分支时不能提前越过汇聚；完成最后一个实际分支后才创建汇聚后的任务。
- 包容网关：逐条判断出口——**无条件出口始终生效**，有条件出口只在条件命中时生效（`InclusiveGatewayStrategy`）；不要套用排他网关「命中条件就禁用兜底出口」的语义。
- 包容网关至少覆盖三组变量：两个条件出口各命中一次、两条条件都不命中（此时只剩无条件出口）；每组都从新实例开始，不能从上一个实例的残留任务推断结果。
- 现成的 `demo_gateway_classic` / `demo_gateway_mimic` 两条条件出口互斥，只能证明「条件出口 + 无条件出口同时命中」，证明不了多个条件出口同时命中（见 `references/regression-cases.md`）。
- 记录每次流转前后的活动任务节点集合，重点检查不能把普通/排他网关当成包容网关。

### 3. 任务协作

- 转办：必须选择目标用户；确认原办理人不再拥有当前转办待办，目标用户拥有待办，历史记录有转办信息。
- 委托：必须选择目标用户；确认受托人办理后按引擎语义回到原审批链路，检查历史任务。
- 加签：候选人必须排除当前节点已有办理人和当前办理人；增加后查询 `flow_user` 确认新增权限。
- 减签：候选人必须来自当前节点已有办理人；不能减掉全部办理人，至少保留一人；确认删除的是选中的办理人。
- 减签边界必须至少测一次“同时选择全部当前办理人”，接口应拒绝且办理人集合保持不变；不能只测试逐个减签。
- 驳回：使用流程中真实存在的 REJECT 跳转；确认目标节点、历史状态和剩余待办正确。
- 撤回：使用发起人操作；确认实例业务状态、历史任务和新待办符合 Demo 规则。
- 终止：使用当前节点办理人操作；确认实例进入终态、剩余待办被正确处理。
- 抄送：办理时带 `copyUsers`，确认抄送列表可查询且不改变审批办理人。
- 完成链路：通过所有实际活动任务后，确认实例状态为 `finish`，活动任务为空，历史任务包含最后节点；且该实例的 `flow_event_wait` 已无活动等待行（普通完成链路也会取消本实例的事件等待，防僵尸等待被事后唤醒）。

### 4. 事件与内置定时器

事件节点统一用 `BETWEEN`（`node_type=1`）承载，事件类型持久化在 `flow_node.event_type`，定时参数在 `node_config` 的 `event` 段（`timerType` = `AFTER`/`FIXED`/`POINT_IN_TIME`，`timerValue` 为 ISO-8601 时长或时间点）。**节点默认变量**是 `node_config` 顶层的 `variables`（任何节点都能配，旧写法 `event.variables` 自动回落兼容）：发布时随等待记录落 `payload`，触发时并入实例变量，办理传入的同名变量优先。事件不生成办理任务；需要等待的事件落一行 `flow_event_wait`，由内置定时器或消息回调推进。定时是异步的：断言前按扫描间隔等待（sleep ≥ 定时时长 + 1~2 个扫描间隔），不要立即断言。

- 定时捕获（`TIMER_CATCH`）：`开始 → 人工A → 定时捕获(AFTER PT3S) → 人工B → 结束`。办理人工A 后应生成 1 行 `flow_event_wait`（`WAITING`/`TIMER_CATCH`，`next_trigger_at ≈ now+3s`），且**活动待办为空**（实例停在事件节点）；到期后等待行被删除（Demo 为物理删除模式，行直接消失；引擎逻辑删除模式下为 `del_flag='1'`）、生成人工B 待办、历史含人工A。
- 定时启动（`TIMER_START`）：`开始 → 定时启动事件(AFTER PT3S) → 人工 → 结束`。**发布定义时**即生成定义级等待行（`instance_id` 为空）；手动发起应被拒绝（提示"该流程由启动事件触发，不支持手动启动"）；到期后引擎自动创建实例（`business_id` 形如 `T<等待行id>_<毫秒>`）并生成首个待办。**实例状态应为审批中**（demo `waiting`，内置码 1，不是 `draft`）——定时启动没有发起人提交环节；节点默认变量（如 `demo_event_daily_check` 的 `veto=0`/`channel=daily`）应并入实例变量，后续条件网关不因变量缺失报「跳转条件不能为空」。
- 定时边界（`TIMER_BOUNDARY`，第一阶段必须为中断型 `interrupting=true`，`attached_to_node_code` 指向宿主任务节点）：`开始 → 人工A(+定时边界 AFTER PT3S → 人工C) → 结束`。发起后除人工A 待办外还应有 1 行边界等待（`attached_task_id` = 人工A 的任务id）；不办理人工A 直到到期，应中断人工A（历史 `flow_status=10` 失效，`message` 为定时边界中断提示）并生成人工C 待办。
- 消息抛出 / 结束（`MESSAGE_THROW`/`MESSAGE_END`）：`人工A → 消息抛出 → 结束`。宿主未注册 `EventPublisher` 时到达即告警跳过发布但**继续推进**，不得阻塞流程；核对实例到达后续节点而非卡死。`MESSAGE_END` 到达时另写一条结束事件留痕历史（见下「结束事件留痕」）。
- 消息捕获（`MESSAGE_CATCH`）：`开始 → 人工A → 消息捕获(eventName=X) → 人工B → 结束`。办理人工A 后生成 `WAITING/MESSAGE_CATCH`（`event_name=X`）、无活动待办；`GET /api/instances/{id}/events` 能查到；`POST /api/instances/{id}/messages {eventName:X}` 触发，消息名不符应被拒绝（流程不变），命中后生成人工B。触发时带的 `payload` 必须并入实例变量（核对 `flow_instance.variable`），并落库到 `flow_event_wait.payload`（Demo 物理删除模式下触发成功后该行被物理删除；引擎逻辑删除模式下该行 `del_flag='1'` 且载荷保留，供重试/租约恢复读取）——只看接口成功、流程是否推进不足以发现载荷被静默丢弃。
- 消息边界（`MESSAGE_BOUNDARY`）：人工A 挂消息边界（`attachedToNodeCode`=人工A、`interrupting=true`、`eventName`=消息名）→ 人工C。发起后除人工A 待办外生成边界等待（`attached_task_id`）；`POST /api/instances/{id}/messages {eventName}` 命中后中断人工A（历史 `flow_status=10`）并生成人工C。**审计操作人**：触发时 `X-User-Name` 应写入中断历史的 `approver`（如 `approver-x`），而不是系统兜底（`system`/`systemHandler`）。
- 错误捕获（`ERROR_CATCH`）：`开始 → 人工A → 错误捕获(eventName=P001) → 人工B → 结束`。办理人工A 后生成 `WAITING/ERROR_CATCH`（`event_name` 为错误码，空=通配）；`POST /api/instances/{id}/errors {errorCode}`，错误码不匹配返回「流程错误未被捕获」且流程不变，命中后生成人工B。
- 错误边界（`ERROR_BOUNDARY`）：人工A 挂错误边界（`attachedToNodeCode`=人工A、`interrupting=true`、`eventName`=错误码）→ 人工C。发起后除人工A 待办外生成边界等待（`attached_task_id`）；`POST /api/tasks/{taskId}/errors {errorCode}` 命中后中断人工A（历史 `flow_status=10`）并生成人工C。另有**监听器判定变体**：人工A 挂 `create` 事件监听器，监听器读流程变量命中时调 `TaskService.throwError`，则人工A 不产生待办即被中断、直接进边界出口（demo `demo_event_pay_fail` 用 `payAmount` 超限 1000）。判定型监听器不要挂 `start`/`finish` 事件，否则链尾找不到任务而报错（见 regression-cases）。
- 消息启动（`MESSAGE_START`）：`开始 → 消息启动(eventName=X) → 人工 → 结束`。发布后该流程**不能手动发起**，由 `POST /api/instances/message {flowCode,businessId,eventName,variables}` 创建实例（消息名与节点 `eventName` 不符应被拒绝）；它不落 `flow_event_wait`，核对重点是实例由消息创建且后继待办生成（现成定义：`demo_event_risk_wait` 的 `order.paid`、`demo_event_notify` 的 `membership.cancelled`）。
- 终止结束（`TERMINATE_END`）：节点到达即把实例状态置为**终止**（demo 映射 `termination`，内置码 4；普通完成与消息结束仍是「已完成」），**并行分支上其它活动待办按「失效」归档**（历史 `flow_status=10`，`message` 为「流程结束时待办未办理，自动失效」）。复测用 `demo_event_daily_check`：办理「风控否决判定」时带 `veto=1`，质检分支任务应被失效归档、实例状态为终止；带 `veto=0` 时两分支正常汇聚、实例为已完成。
- 结束事件留痕：`TERMINATE_END` / `MESSAGE_END` 到达时在 `flow_his_task` 写一条**结束事件留痕**——`task_id=0` 哨兵、`flow_status=8`（结束；demo 自定义映射下落业务码 `pass`，时间线标签显示「通过」，「结束」语义由 message 承载）、办理人为系统兜底，`message` 区分「流程由该终止结束事件终止」与「流程由该结束事件节点结束」；按 `node_code` 可筛出异常终止的实例。断言终态场景时别忘了核对这条记录的存在与文案。
- 边界校验：事件节点的能力门禁在发布时校验（`DefServiceImpl.publish`），草稿保存不校验运行时能力；启动类事件与固定 `START` 的一致性、边界事件必须绑定宿主节点等结构规则在发布时报错。

## 数据库核对

只读核对与当前场景相关的记录：

- `flow_instance`：实例状态、节点、变量、创建人。
- `flow_task`：只把 `del_flag='0'` 且未完成的记录视为活动待办。
- `flow_his_task`：办理、驳回、转办、委托、加减签、终止等历史记录；结束事件留痕（`task_id=0`）也在这里，按 `node_code` + `task_id=0` 定位。
- `flow_user`：按任务关联查询审批、转办、委托、抄送办理人。
- `flow_event_wait`：事件等待记录；只把 `del_flag='0'` 视为活动等待，核对 `status`/`event_type`/`next_trigger_at`/`retry_at`/`attached_task_id`/`repeat_count`。定义级定时启动等待的 `instance_id` 为空。
- 等待记录的**全局只读视图**（不按实例查时用）：`GET /api/event-waits`（默认只查定时类、按 `next_trigger_at` 升序；`timerOnly=false` 含消息 / 错误类；支持 `definitionId`/`instanceId`/`businessId`/`flowCode`/`status`/`eventType` 过滤）、`GET /api/event-waits/summary`（各状态计数）；设计器「定时任务」菜单（`/timers`）是同一份数据的页面。参数与字段见 references/test-matrix.md。

不要用流程图“已经过节点”状态替代当前待办状态判断。并行/包容汇聚必须以当前实例真实活动任务为依据；这是本项目曾经出现过的回归点。

事件一次性成功后的等待行清理口径取决于宿主的删除模式：**Demo 以 `warm-flow.logic-delete=false`（物理删除）运行**，等待行会被**直接物理删除**，查不到行即已消费；若宿主用默认 `logic-delete=true`，则为软删除（`del_flag='1'`，`status` 可能仍停在 `RUNNING`），此时判定「已消费」要看 `del_flag`，不要因为查不到物理行就断定异常。

## 判定与失败处理

- API 成功不等于流程正确，必须同时核对后续活动任务和历史数据。
- 发现失败时先保留现场，记录实例 ID、任务 ID、流程定义版本、变量、请求体、响应和 SQL 查询结果。
- 先定位是 Demo 参数/业务映射、Core 路径/状态、ORM 持久化还是数据库环境问题，再决定是否修改代码。
- 不为了让测试通过而改测试数据、跳过节点、使用已删除任务或吞掉异常。
- 事件 / 定时器场景若「等待行长期不消费」：先看启动日志是否出现「内置定时器已启动」，确认 `timer-enabled` 真的生效、`flow_event_wait` 表存在（表缺失时引擎只报一次错误日志后自动停用扫描，属 fail-soft，不会周期刷屏）。
- 定时 / 异步断言必须给足时间（sleep ≥ 定时时长 + 1~2 个扫描间隔）；租约过期恢复、重试退避、`DEAD` 等并发与容错语义优先在 core 单元测试覆盖，Demo 集成只保证正常触发路径。
- 若发现可复现缺陷，更新本 Skill 或 `references/regression-cases.md`，只加入能泛化到后续复测的检查项；不要写入一次性的 ID。

## 参考资料

- 详细接口、请求样例、数据库查询和场景记录模板见 [references/test-matrix.md](references/test-matrix.md)。
- 新增回归问题时维护 [references/regression-cases.md](references/regression-cases.md)。
- 一键整理复测定义（导出备份 → 全删 → 导入 → 发布）：[scripts/reimport-definitions.sh](scripts/reimport-definitions.sh)，用法见「内置流程定义」。
