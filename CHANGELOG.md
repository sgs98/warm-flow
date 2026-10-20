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

- 新增节点级抄送（`node_config.copy`）：`CopyConfig` 解析 `{"copy":{"handlers":[...]}}`，在待办落库的 5 个位置（发起 / 办理 / 撤回 / 自动节点推进 / 事件推进）自动抄送；另提供 `TaskService#copy` 与门面 `WorkflowService#copy(CopyCommand)` 供办理中手动追加。抄送只写 `flow_his_task`（`flow_status=copy`、`cooperate_type=none`）与 `flow_user`（`type=4`）留痕，不产生待办、不改实例 / 任务状态、不触发监听器。抄送人支持发起人占位 `warmFlowInitiator`；**抄送必须落到具体人**：新增 SPI `PermissionHandler#resolveCopyHandlers`（默认回落 `convertPermissions`），宿主把 `user:zhangsan`、`dept:001` 等标识展开成真实用户，展开不到人的标识直接丢弃；手动抄送时若一个都落不到人则抛 `FlowException`，不再静默成功。自动抄送直接接收调用方的实例对象而非按主键回查——发起链的抄送发生在实例落库之前，回查会丢掉发起人。新增特征测试 `CopyCharacteristicTest`。
- 抄送留痕的生命周期与审计更正：抄送人记录（`flow_user.type=4`）改挂**抄送历史主键**（`flow_his_task.id`）而非待办主键——待办办理完 / 撤回 / 终止会被连同办理人整批删除，按待办 id 关联会让抄送在办结瞬间从「我的抄送」里消失；实例删除时按历史主键一并清理，不留孤儿。抄送历史不再写 `approver`（否则触发抄送的人「已办」里会凭空多出一条抄送记录，谁抄送看 `create_by`），`create_time` 改记抄送时刻而非待办创建时间（办理中追加抄送时两者可能相差很久）。流程结束时统计「已归档待办」排除抄送行，避免带抄送的未办待办被误判为已归档而漏记失效历史。`flow_user` 的 `type` / `associated` 注释与四套全量脚本、实体 javadoc 同步（新增「4待办任务的抄送人」）。
- 设计器抄送面板随节点切换重载：属性面板复用同一个 `form` 对象，原先只在初始化时回显一次，切到别的节点还显示上一个节点的抄送人，点「选择」确认会把上一个节点的抄送人写进当前节点；改为监听抄送人标识变化重载。抄送分组同时只在会产生待办的节点出现，事件节点（画布上是圆圈，易被误认为网关）不显示。
- 抄送收尾（demo 联调暴露的问题）：① **办结后补抄送不再报 `NOT_FOUNT_TASK`** —— 办理完成待办行即被删除，`TaskService#copy` 改为按归档历史还原节点信息（取该待办最近一条非抄送历史），「办完顺手抄送」不必抢在办理之前；② **同一待办对同一人幂等** —— 已抄送过的人不再重复留痕（读抄送历史的 `collaborator` 快照），全部重复时返回 `null`，自动抄送静默跳过、手动抄送按无新增处理，重试或「节点自动抄送 + 手动抄送同一人」都不会产生重复记录；③ demo 抄送列表的「流程状态」改取**实例状态**（原先显示「抄送」，那是留痕类型不是流程进展），按页 `getByIds` 批量回查实例，不做 N+1；④ 流程详情时间线的抄送行显示「抄送给 xxx」，不再出现「办理人：-」；⑤ demo 办理时的抄送注册到**事务提交之后**（`TransactionSynchronization#afterCommit`）执行，抄送自身失败只告警，不会回滚已提交的办理。节点级自动抄送仍与流转同事务——它属于「到达节点」这件事，理应一起成功或一起回滚。⑥ **门面抄送同样支持办结后补抄送**：`WorkflowService#copy` 原先自己先 `getById` 断言待办存在，把 `TaskService#copy` 的归档还原兜底挡在外面（单元测试直接调服务所以没暴露，demo 走门面才现形）；改为不前置断言，实例信息由抄送留痕带回，`CopyCharacteristicTest#facadeCopy_afterTaskCompleted_remainsAvailable` 锁定该路径。⑦ **demo 手动抄送人与节点配置同构**：`copyUsers` 支持 `user:xxx` / `dept:xxx` 前缀，先展开成具体用户再校验，展开不到人的标识报「抄送人不存在」并回滚本次办理，避免同一份抄送人写法在节点上和办理时两套语义。
- 状态与协作三维拆分：实例状态（`FlowStatus`）、任务状态（新增 `TaskStatus`）、协作动作（`CooperateType`）分开建模，分别落 `flow_instance.flow_status`、`flow_task` / `flow_his_task.flow_status`、`flow_his_task.cooperate_type`；实例状态不再复制待办状态码，改由落点语义推导（结束→已完成、退回→已退回、其余→审批中），避免宿主自定义映射后两套编码串列。
- **状态编码改为小写英文**：三个维度是不同表、独立编码空间，落库编码直接用英文（如 `finish` / `pass` / `transfer`），不再用数字，落库即可自解释（2.1.0 未发布，无存量数据需迁移，仅同步列类型与注释）。
  - 实例 `FlowStatus`：`draft` 待提交、`waiting` 审批中、`termination` 终止、`invalid` 作废、`cancel` 撤销、`finish` 已完成、`back` 已退回、`restart` 重启；移除「暂存」「取回」（取回属任务维度，由 `take_back` 表达）。
  - 任务 `TaskStatus`：`pending` 待审批、`pass` 通过、`auto_pass` 自动审批、`back` 退回、`invalid` 失效、`take_back` 拿回、`copy` 抄送；移除「结束」（结束事件留痕统一按「通过」归档）。
  - 协作 `CooperateType`：`none` 无、`transfer` 转办、`delegate` 委派、`add_signer` 加签、`remove_signer` 减签；`flow_his_task.cooperate_type` 列类型随之由 `tinyint` 改为 `varchar(20)`（默认值 `none`）。该列只记<b>协作动作</b>，三个原本混在里面的语义已拆走：会签 / 票签是节点的多人审批模式（由 `node_ratio` 决定，判断仍用 `CooperateType.isCountersign(...)` 等静态方法），`COUNTERSIGN` / `VOTE` 常量移除；抄送只告知、不改任务归属，`COPY` 常量移除，改由 `HisTaskService#setCopyHisTask(task, context, collaborators)` 构造，结果列记 `copy`、动作列记 `none`，避免两个枚举出现同名值。常量同时改为语义更准的英文：`APPROVAL`→`NONE`（值本就是「无」）、`DEPUTE`→`DELEGATE`、`ADD_SIGNATURE`→`ADD_SIGNER`、`REDUCTION_SIGNATURE`→`REMOVE_SIGNER`（加/减签是给当前节点增减签署人，不是「签名」，也不必然等于会签——是否会签由节点比例决定），接口签名 `Integer cooperateType` 一并改为 `String`。
- 修复两个维度编码重叠导致的误判：`TaskServiceImpl.instanceStatusOf` 原先用「落点任务码 == 实例终止码」识别终止，同号时会把普通结束误判成终止；改为按执行意图（`WorkflowContext.flowStatus`）判定。`StatusMapper.label(String)` 明确只按实例维度解析，新增 `taskLabel(String)` 与 `label(String, boolean)` 支持任务维度。
- 新增状态映射扩展点 `StatusMapper`（`instanceStatus` / `taskStatus` / `cooperateStatus` / `isTerminal` / `label`）：宿主用自己的状态枚举接管三个维度的落库编码与终态判定，各操作点不再需要手动传 `flowStatus` / `taskStatus`；默认实现沿用内置编码，升级不需要迁移数据。注册方式 `FlowEngine.setStatusMapper(...)` 或配置 `warm-flow.status-mapper-path`。demo 的编码已与引擎对齐，因此移除了 `DemoStatusMapper`，直接使用默认实现。
- 状态默认值全部内置于引擎：发起 / 通过 / 退回 / 撤回 / 终止的状态由引擎按操作语义推导（提交或通过 → `pass`、退回 → `back`、撤回 → `cancel`、终止 → `termination`），业务层只调用 `start` / `complete` / `reject` / `revoke` / `terminate`，不必再 `setTaskStatus(...)`。抄送补齐为内置能力：新增 `HisTaskService#setCopyHisTask`，状态直接按 `StatusMapper.taskStatus(TaskStatus.COPY)` 取，业务层不必再手写 `copy`；协作动作仍在 `StatusMapper.cooperateStatus` 里统一回落 `pending`（待审批）。需要自有编码时只覆写 `StatusMapper` 对应方法即可覆盖内置默认，调用方无需改动。
- `FlowStatus` 精简为实例状态（移除 `PASS` / `INVALID` / `TASK_BACK`，任务维度改用新增的 `TaskStatus`）；自动审批历史记 `auto_pass`（自动审批），服务任务历史记「通过」。
- 结束收口语义补全：流程结束时仍存在的待办不再静默删除，改按「失效」（`TaskStatus.INVALID`）归档进 `flow_his_task`（保留节点、办理人、创建时间，便于审计）；已归档的待办（本次办理 / 终止的那条）自动去重，不重复归档。
- 退回一票否决补失效留痕：退回目标节点之后仍存在的其他并行待办（如兄弟分支）此前被直接删除、不留任何历史，办理人的待办凭空消失且无法审计；现与流程结束收口同构，删除前先按「失效」（`TaskStatus.INVALID`，备注「退回一票否决，待办自动失效」）归档进 `flow_his_task`，节点、办理人、创建时间可查。特征测试 `RejectCharacteristicTest#oneVoteVeto_archivesSuffixSiblingTasksAsInvalid`、`ParallelRejectCharacteristicTest` 锁定三种并行退回形态。
- 协作留痕状态改为动作自身编码（行为变更）：`StatusMapper#cooperateStatus` 内置默认不再把转办 / 委派 / 加签 / 减签的历史留痕统一写成「待审批」（`pending`）——伪待办会在宿主审批记录里渲染成一条不存在的待办（如加签留痕显示为「并行二 待审批」）。现默认写协作类型编码 `transfer` / `delegate` / `add_signer` / `remove_signer`，`NONE` 兜底仍为 `pending`；抄送不受影响（固定 `copy`）。宿主字典需能显示这四个编码。`StatusMapperCharacteristicTest#cooperateStatus_hasBuiltInDefaults` 同步更新。
- 普通完成链路补齐事件等待清理：实例到达结束节点即取消本实例的 `flow_event_wait` 记录，避免流程已完成后仍有僵尸等待被外部消息或定时器唤醒；终止结束、显式终止路径行为不变（原本就会清理）。
- 结束事件留痕：`TERMINATE_END` / `MESSAGE_END` 到达时在 `flow_his_task` 写一条结束事件记录（`task_id` 以 `0` 为哨兵、`flow_status` 按「通过」`pass` 归档），备注区分「流程由该终止结束事件终止」与「流程由该结束事件节点结束」，按 `node_code` 即可筛出「因严重异常终止」的单子。
- 终止结束的实例状态修正：`TERMINATE_END` 到达后实例状态由「已完成」改为「终止」（`termination`，自定义映射取 `StatusMapper.instanceStatus(FlowStatus.TERMINATE)`），消息结束与普通完成仍为「已完成」；结构上仍收口到固定 `END` 节点，语义上不再与正常办结混淆。
- 节点默认流程变量提到节点级并全节点开放：数据位置由 `node_config.event.variables` 改为 `node_config.variables`（旧格式仍兼容读取，存量 JSON 无需迁移），语义为「缺失时的兜底默认值」——流程走到该节点才补充，办理时传入的同名变量优先，随后写入实例变量；设计器在开始 / 中间 / 网关 / 服务任务 / 结束五类节点的「默认变量」中配，不再局限于事件节点。
- 定时启动的实例状态修正：触发后实例直接处于「审批中」（此前为「待提交」）——定时启动没有发起人提交环节，首个待办即审批任务。
- 撤回的实例状态修正（破坏性，行为变更）：`revoke` 后实例状态取调用意图（`FlowExecution.loadInstance` 预置的 `FlowStatus.CANCEL`），不再由落点待办推导成「审批中」。此前撤回把待办落回申请人后实例仍是 `waiting`，宿主监听器再按「唯一待办在申请人节点」把它覆盖成 `back`，于是同一次撤销在三个维度上给出三个词：归档 `cancel`、待办 `cancel`、实例「已退回」。撤回的语义是申请人把流程收回去，实例维度应与归档留痕同为 `cancel`；回到申请人节点只是位置信息。退回（`backProcess`）不受影响，仍是 `back`。
- 撤销移出内置终态（行为变更）：`FlowStatus.TERMINAL_KEYS` 只保留 `finish` / `termination` / `invalid`，撤销后的实例不再被 `EXECUTE` / `TERMINATE` / `UPDATE_HANDLERS` / `LOAD` / `REVOKE` 的 `NOT_TERMINAL` 守卫拦截——撤回会在发起节点重建待办，申请人修改后可直接重新提交，不会再报「流程已完成！」。宿主若确实把撤销视为终态，仍可在 `StatusMapper#isTerminal` 中自行声明。`FlowStatusMachineCharacteristicTest#matrix_cancel_isNotTerminal` 锁定该语义。
- 抄送留痕不再被调用方状态覆盖（修复）：`setCopyHisTask` 的结果列固定为 `copy`。此前它与转办 / 委派共用「手动状态优先」的取值，而办理链上的**节点到达自动抄送**与办理命令共享同一个上下文——调用方一旦指定 `taskStatus`，抄送历史就被标成 `pass`：审批记录把抄送显示成「通过」污染留档，宿主按 `copy` 查抄送历史也查不到，被抄送人点开待办详情直接 403。转办 / 委派等协作留痕的「手动状态优先」契约不变（由 `StatusMapperCharacteristicTest#manualStatusWins` 锁定），需要自定义编码仍走 `StatusMapper`；新增特征测试 `CopyCharacteristicTest#copy_ignoresCallerTaskStatus`（先复现为红，修复后 314 个测试全绿）。

### 设计器

- 修复保存前检查误拦多分支网关（`flowCheck.js`）：「不可同时通过或退回到多个中间节点」这条原先只判断连线的**目标**节点类型，没判断**源**节点类型，于是互斥 / 并行 / 包容网关的正常扇出也被判成违规，弹窗只有「知道了」，任何带多分支网关的流程都保存不了。现与后端 `FlowConfigUtil.checkSkipNode` 严格对齐——只统计「中间节点 → 中间节点」的出线，网关扇出不再计数。
- 经典与仿钉钉设计器开放 11 类事件配置（事件类型、消息名称、错误码、定时策略与时间值、附着任务节点、中断开关），保存后重新打开 JSON 保持一致。
- 开始 / 中间 / 网关 / 服务任务 / 结束五类节点的基础信息区新增「默认变量」键值编辑器（数字 / 布尔自动类型推断），此前该能力只挂在启动事件上。
- 办理人设置新增「发起人」快捷按钮（写入 `warmFlowInitiator` 标识），办理人名称回显对引擎无法解析的标识补充占位行。
- 中间节点基础设置新增「抄送」分组：复用人员选择器写入 `node_config.copy.handlers`，抄送人以标签展示、可单个移除，并有「发起人」快捷按钮；未配置时不发名称回显请求。`copy` 同步纳入引擎保留键，扩展属性 KV 编辑器不再把它当自定义属性、编辑其它 KV 时也不会丢。该分组只在会产生待办的节点上出现——选中事件类型后的中间 / 边界事件节点（画布上是圆圈，易被误认为网关）不显示抄送，网关面板则从始至终没有该配置。

### Demo

- 流程实例列表支持勾选多条批量物理删除（`DELETE /api/instances?ids=...`，返回实际删除数量），与单条删除一致清理流程用户、待办与历史任务。
- Demo 前端分层重构：新增 `api/`（definitions / instances / tasks / events / users 五个领域模块）、`types/workflow.ts`（统一展示模型）、`composables/usePagedList`（分页列表逻辑复用），全部视图与组件不再直接拼 HTTP 调用；`X-User-Name` 由 axios 拦截器统一注入。
- Demo 后端分层重构：1171 行的 `WorkflowServiceImpl` 拆分为 `WorkflowSupport`（共享缓存与 VO 组装）+ `InstanceAppService`（发起 / 查询 / 撤回 / 终止 / 删除）+ `TaskAppService`（待办已办抄送 / 按钮权限 / 通过退回转办委派加签减签），Controller 与对外 API 路径保持不变。
- 事件测试页（`/events`）：重构为通用外部事件模拟器——发起回归实例列表（仅消息启动案例保留「消息启动」按钮），实例操作按钮由等待事件数据驱动（有消息等待出现「触发消息」弹窗，不再按流程硬编码）。错误边界案例改为「财务执行付款」节点 `create` 监听器 `DemoPayFailListener` 读流程变量 `payAmount`、超过限额 1000 即抛 `PAY_FAILED` 自动进边界，前端不再提供「模拟抛错」按钮。
- 新增定时任务观测页（`/timers`）与配套只读接口：`GET /api/event-waits`（分页查询 `flow_event_wait`，默认只查定时类事件、按下次触发时间升序，支持状态 / 事件类型 / 流程编码 / 业务 ID / 节点编码 / 事件名 / 下次触发与创建时间区间组合过滤）与 `GET /api/event-waits/summary`（同组筛选条件下的各状态计数）；页面提供状态计数快捷过滤、条件区、详情抽屉、查看实例，并对消息类等待「触发消息」、错误类等待「抛错」（复用既有事件触发接口），不提供取消 / 立即触发 / 重新排期等写操作。
- 各列表页表格补充统一空状态提示（el-empty）。
- 业务层不再手动传任务状态：发起、通过、退回、撤回、终止全部交给引擎按操作语义推导，抄送改走 `setCopyHisTask`（不再手写 `copy`），Demo 只按引擎枚举读展示文案。
- 修复实例/待办状态展示裸数字的存量缺陷：VO 组装只按 demo 字符串枚举查文案，未命中时回落原始值，导致列表直接显示引擎写入的裸码；现状态文案统一由引擎 `FlowStatus` / `TaskStatus` 提供（demo 侧业务状态枚举与任务状态枚举已删除），实例详情/列表均正常显示「审批中」等。
- 修复实例列表「继续办理/重新提交」按钮从不显示的存量缺陷：按钮判断依赖 demo 业务码（draft/back/cancel），与引擎写入的任务状态码不匹配；现 `InstanceVo` 透出当前待办任务状态码 `taskStatusKey`（`pending` 待审批 / `back` 退回），按钮改按实例状态（`draft` / `back` / `cancel`）显隐、按 `taskStatusKey` 是否 `back` 区分「继续办理」与「重新提交」。

### 数据库

- 新增表 `flow_event_wait`；`flow_node` 新增 `event_type`、`event_name`、`attached_to_node_code`、`interrupting` 4 列；修正 `flow_node.node_config` 列注释（自定义扩展属性实际在 `node.ext`）。四套全量脚本（MySQL / Oracle / PostgreSQL / SQL Server）已同步，并按 MySQL 基线逐表逐列比对（列名集合、列顺序、可空性、默认值、字符长度）确认无差异；MySQL 增量升级脚本见 [sql/mysql/v1-upgrade/warm-flow_2.1.0.sql](./sql/mysql/v1-upgrade/warm-flow_2.1.0.sql)。
- 承上完成三方言的结构收口，共 38 处（只改全量脚本，存量库不受影响）。可空性 16 处：Oracle 11 处、SQL Server 4 处、PostgreSQL 1 处，为引擎必写列补 `NOT NULL`（`flow_his_task.{skip_type, flow_status, cooperate_type}`、`flow_instance.{node_code, node_type, flow_status, activity_status}`、`flow_task.{node_code, node_type, flow_status}`、`flow_definition.activity_status`，`flow_definition.is_publish` 仅 SQL Server 需补）。默认值 13 处：SQL Server 的 `flow_definition` / `flow_node` / `flow_skip` / `flow_instance` / `flow_task` 五表 `create_by` + `update_by` 与 `flow_user.update_by` 共 11 处补 `DEFAULT('')`，Oracle / PostgreSQL 的 `flow_user.create_by` 去掉 `DEFAULT ''` 以与 MySQL 一致。长度 9 处：Oracle `listener_path` 500 → 400（`flow_definition`、`flow_node`），SQL Server `flow_his_task` 的 `node_code` / `node_name` 与 `target_node_code` / `target_node_name` 长度互换纠正，三方言 `flow_user.create_by` 64 → 80。另有 PostgreSQL `flow_his_task` 列顺序按 MySQL 重排。列类型按各方言习惯映射（`bigint` / `NUMBER(20)` / `int8`、`varchar` / `VARCHAR2` / `nvarchar`、`text` / `CLOB` / `nvarchar(max)`），二级索引四方言等量（MySQL 以表内 `KEY` 声明，其余以 `CREATE INDEX` 声明，索引名沿用各自既有写法）。
- 全量脚本修正两处存量问题：SQL Server 的 `flow_node` 补齐 `listener_type` / `listener_path` 两列（其余三方言已有）；Oracle 删除 `FLOW_NODE.HANDLER_TYPE` / `HANDLER_PATH` 两条孤儿注释（表无此两列，执行脚本会报 ORA-00904）。
- 同步状态列注释到三维语义：四套全量脚本中 `flow_instance.flow_status` 改为「实例状态（draft待提交 waiting审批中 termination终止 invalid作废 cancel撤销 finish已完成 back已退回 restart重启）」，`flow_task.flow_status` 改为「任务状态（pending待审批 pass通过 auto_pass自动审批 back退回 invalid失效 take_back拿回 copy抄送；任务结果，勿套用实例状态编码）」，`flow_his_task.flow_status` 用同一串并追加「唯一例外是撤回归档写 cancel」——撤回链 `FlowExecution.loadInstance` 会把上下文实例状态预置为 `FlowStatus.CANCEL`，历史状态经 `customStatus(taskStatus, flowStatus)` 回落取值、`skipStatus` 又只在自定义值为空时才推导 PASS/BACK，所以即使调用方不显式指定任务状态，被撤回归档的那条历史记录仍会落 `cancel`；它是任务维度唯一会出现的实例码，宿主字典必须为它留一行标签，否则审批记录显示裸码。此前三处共用一套旧混合注释（含已移除的「2审批通过」），两个维度同号不同义，极易误读。`cooperate_type` 注释同步纠正为「1无」（此前写「1审批」）并注明是行为维度。MySQL 升级脚本给出对应的 `MODIFY COLUMN` 语句（仅改注释、不动类型与数据）。

### 升级提示

- 事件能力默认关闭：升级需执行对应数据库脚本，并显式配置 `warm-flow.timer-enabled=true` 才会启动定时扫描线程；未开启或未建表时既有流程行为不变。
- 事务边界：事件到达会同时写任务、实例与等待记录，消息触发后的流程推进亦然，宿主必须用 ORM / JDBC 事务包裹相关调用；`MESSAGE_THROW` / `MESSAGE_END` 的发布器调用与流程状态更新在同一提交边界内，需要可靠投递时请在发布器实现中接入 Outbox。
- 数据库方言：定时扫描的固定条数查询在 Oracle 下使用 `ROWNUM`（MyBatis）/ `OFFSET ... FETCH NEXT`（MyBatis-Plus）改写，Oracle 需 12c 及以上；MySQL、PostgreSQL、SQLServer 使用 `LIMIT`。
- 状态枚举拆分（破坏性）：`FlowStatus` 只保留实例维度（移除 `PASS` / `TASK_BACK`），任务维度改用新增的 `TaskStatus`；扩展点 `FlowStatusMapper` 更名为 `StatusMapper`，配置项 `flow-status-mapper-path` 改为 `status-mapper-path`。**落库编码改为小写英文**（实例 `draft` / `waiting` / `termination` / `invalid` / `cancel` / `finish` / `back` / `restart`，任务 `pending` / `pass` / `auto_pass` / `back` / `invalid` / `take_back` / `copy`），不再使用数字；引擎的增量脚本 `sql/mysql/v1-upgrade/warm-flow_2.1.0.sql` 只同步列类型与注释、**不含任何数据迁移**（2.1.0 未发布，引擎视角无存量）。但已经按 2.0.1 落库的宿主（如 RuoYi-Vue-Plus）库里有数字码存量，`ALTER` 之后它们会变成字符串 `'1'`，引擎按新编码查不到，需自行 `UPDATE`：实例 `0→draft` `1→waiting` `4→termination` `5→invalid` `6→cancel` `8→finish` `9→back` `12→restart`，已移除的 `7 取回→waiting`（取回属任务维度）、`13 暂存→draft`；任务 `1→pending` `2→pass` `9→back` `10→invalid` `11→take_back`，已移除的 `8 结束→pass`（结束留痕统一按通过归档）；协作 `0/1→none` `2→transfer` `3→delegate` `6→add_signer` `7→remove_signer`，已移除的 `4 会签 / 5 票签→none`（两者改由 `node_ratio` 表达，迁移后该维度信息丢失）。宿主按数字码写的查询 / 统计 SQL 与报表需同步调整。**存量迁移脚本见 [sql/mysql/v1-upgrade/warm-flow_2.0.1_data_to_2.1.0.sql](./sql/mysql/v1-upgrade/warm-flow_2.0.1_data_to_2.1.0.sql)**（可选，仅已按 2.0.1 落库的宿主需要；含审计、备份、映射、复验与回滚，执行前须经数据负责人批准并在测试库先跑）。
- 扩展点与契约包路径收口（破坏性，**无兼容层**）：2.0.1 的 `core.handler` / `core.listener` 两个包已不存在，宿主 `import` 与配置里的类路径需按下表改。判定规则是「真正的扩展点进 `spi`，引擎内部策略进 `strategy`，值对象进 `dto`」：

  | 2.0.1 | 2.1.0 |
  | --- | --- |
  | `core.handler.PermissionHandler` | `core.spi.PermissionHandler` |
  | `core.handler.DataFillHandler` | `core.spi.DataFillHandler` |
  | `core.handler.TenantHandler` | `core.spi.TenantHandler` |
  | `core.handler.DefaultHandlerStrategy` | `core.strategy.DefaultHandlerStrategy` |
  | `core.listener.GlobalListener` | `core.spi.GlobalListener` |
  | `core.listener.Listener` | `core.spi.Listener` |
  | `core.listener.ListenerVariable` | `core.spi.ListenerVariable` |
  | `core.listener.ValueHolder` | `core.dto.ValueHolder` |

  另：`warm-flow.global-listener-path` 配置项已移除，全局监听器改由容器 Bean 获取（`FlowEngine.initGlobalListener()` → `FrameInvoker.getBean(GlobalListener.class)`），实现类注册为 Bean 即生效，**容器内只允许一个 `GlobalListener` Bean**。
- 行为变更：流程结束时未办待办改为按「失效」归档进历史（此前静默删除），历史列表会多出失效记录；结束事件（终止结束 / 消息结束）另写一条 `task_id=0` 的留痕记录，按 `node_code` 可筛出异常终止的实例。
- 行为变更：终止结束事件（`TERMINATE_END`）的实例状态由「已完成」改为「终止」（`termination`），消息结束与普通完成不变；统计口径若需包含终止单请显式计入。
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
