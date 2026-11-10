# 回归问题记录

只记录可复现、可泛化的回归点；不要记录临时实例 ID。新增条目至少写清：不变量、最小拓扑与前置条件、复现动作、API/UI 期望、数据库必查、禁止状态、代码/特征测试锚点、适用范围和最近验证环境。一次性故障现场留在测试报告，不写入本文件。

## 并行/包容汇聚不能使用流程图历史状态

- 症状：并行分支办理后重复生成分支任务，或未完成全部实际分支就越过汇聚。
- 根因：流程图元数据中的节点已走过状态表示历史经过，不表示当前仍有待办。
- 检查：按实例查询 `flow_task`，只统计活动任务；办理当前任务后重新查询，再判断是否应汇聚。
- 代码关注点：`warm-flow-core` 的 `FlowPathResolver`、`NodeServiceImpl`、任务历史写入顺序。
- 复测要求：新建唯一业务号，避免使用已经逻辑删除的旧任务 ID；顺序办理和并发办理都要分别记录结果。

## 已逻辑删除任务不能作为复测输入

- 症状：使用旧任务 ID 复测造成重复推进或异常数据。
- 检查：任务查询必须带 `del_flag='0'`，并确认任务仍属于实例当前活动任务集合。
- 处理：保留现场，不删除测试数据；后续复测使用新实例。

## Demo 业务号必须符合数据库长度

- 症状：发起接口返回 500，日志出现 `Data too long for column 'business_id'`。
- 根因：`flow_instance.business_id` 当前为 `varchar(40)`，测试脚本生成的长业务号超过字段上限。
- 检查：发起前确认业务号不超过 40 个字符；优先使用短场景缩写和 Unix 秒级时间戳。
- 处理：不要修改数据库结构来迁就测试数据，缩短业务号后重新创建全新实例。

## 当前 Demo 定义不代表多条件包容网关

- 现状：现成的 `demo_gateway_classic` / `demo_gateway_mimic` 定义有两个条件出口（`gt@@day|3`、`lt@@day|3`）和两个无条件出口（「包容3」「包容4」）。
- 结论：两条条件出口对同一个 `day` 互斥，任何取值最多命中一条，所以只能证明「条件出口 + 无条件出口同时命中」，**不能**据此证明多个条件出口同时命中。
- 检查：`day=4` 应同时出现「包容day大于三」+「包容3」+「包容4」，`day=3`（两条条件都不命中）应只剩「包容3」+「包容4」；需要多条件同命中验证时，另建含两个可同时成立条件出口（如引用不同变量）的已发布定义，再按矩阵从新实例测试。
- 处理：不要把「只命中一条条件出口」当成引擎缺陷；期望值先按上面的语义算清再断言，也不要为了让流程多命中而改定义条件。

## 导入的定义未发布时不可用

- 症状：用 `POST /api/definitions/import` 导入案例 json 后直接发起，接口提示找不到 / 不可用定义；含 `TIMER_START` 的定义等不到自动发起，含 `MESSAGE_START` 的定义调 `POST /api/instances/message` 也建不出实例；库里 `flow_definition.is_publish='0'`。
- 根因：导入永远落成**新的未发布版本**；发起要求已发布版本，发布钩子（重建 `TIMER_START` 定义级等待、登记启动事件能力）也只在 `PUT /api/definitions/{id}/publish` 时执行。
- 检查：导入后确认 `is_publish='1'`；含定时启动的定义再按 `definition_id` 查 `flow_event_wait` 是否已有定义级 `TIMER_START` 等待行（`instance_id` 为空）。
- 处理：复测准备一律「导入 + 发布」，或用 `scripts/reimport-definitions.sh` 一次整理；不要为了能发起而手改 `flow_definition` 数据绕过发布。

## 启动实例状态不能被首任务状态覆盖

- 症状：启动接口成功，但 `flow_instance.flow_status` 被首节点任务状态写成 `pass`，草稿实例无法按业务状态展示。
- 根因：启动阶段复用了更新当前节点的公共逻辑，首任务状态参与了实例状态更新。
- 检查：启动后应为实例 `draft`、开始历史任务 `pass`、首节点活动任务 `pending` 且可继续办理；首次办理后实例再进入 `waiting`。实例和任务是独立状态维度，不能都断言为 `draft`。
- 处理：启动保存阶段保护调用方指定的实例状态，不扩大公共任务状态接口的职责。

## 实例、任务、协作和事件状态必须分维度验证

- 不变量：`FlowStatus`、`TaskStatus`、`CooperateType` 与事件等待状态是四套契约；宿主 `StatusMapper` 可独立映射实例、任务与协作编码。
- 检查：发起、办理、自动通过、终止、重启和协作场景同时核对 DB 原始编码、API label 与 UI fallback；新发起是 instance=`draft`、task=`pending`，自动通过历史是 `auto_pass`。
- 禁止：用任务状态推断实例终态、硬编码默认状态字符串验证自定义映射、未知状态导致列表崩溃或空白。
- 处理：状态断言锚定当前 mapper 与 DTO 投影，UI 对未知值提供稳定原值 fallback。

## 包容网关无条件出口与条件出口独立判断

- 语义：包容网关逐条判断出口；有条件出口仅在条件满足时进入，无条件出口始终进入，不作为“有条件命中时禁用”的兜底分支。
- 检查：`day=4` 等条件命中时应同时包含命中条件分支和全部无条件分支；条件不命中时应包含全部无条件分支。
- 处理：不要复用排他网关的“命中条件后只选一个”逻辑；每次从新实例查询 `flow_task` 核对活动分支集合。

## 并行任务批量脚本不能丢失第二行

- 症状：脚本使用 `read` 接收 SQL 多行结果时只保存第一个任务，导致只办理一个并行分支。
- 检查：使用 `while IFS= read -r` 逐行处理，或用 `jq -c` 对 API 返回数组逐项处理；办理前后都重新查询活动任务。

## 减签不能一次移除全部办理人

- 症状：当前任务有多个办理人时，一次提交全部 `reductionHandlers` 返回成功，`flow_user` 变为空。
- 根因：仅校验当前办理人数是否为 1，没有校验“当前人数 - 本次实际选中人数”是否小于 1。
- 检查：先加签出多个办理人，再一次选择全部办理人减签；接口应拒绝，且办理人集合不变。
- 处理：保留失败实例现场；修复时应按实际存在的待删除办理人去重后计算剩余人数。

## 撤回或终止后的任务不能作为当前待办展示

- 症状：待办接口已经查不到任务，但实例历史时间线仍把撤回或终止任务标记为“当前待办”。
- 根因：历史接口直接把实例关联任务全部转换为当前任务，没有排除 `cancel`、`termination`、`finish`、`invalid` 等终态任务。
- 检查：撤回或终止后同时查询待办接口和实例历史；历史中的终态操作应为非当前记录，活动任务集合应为空。
- 处理：历史时间线只转换非终态任务，保留草稿、退回待提交和待审核等可继续办理状态。

## 办理类操作需要请求级操作者权限（Demo 形态）

- 症状：`start`、`revoke` 正常，但 `pass`/`jump`/`reject`/`terminate`/`transfer`/`depute`/`add-signature`/`reduction-signature` 全部失败，返回「无法跳转到该节点,请检查当前用户是否有权限!」或「请检查当前用户是否有权限!」，任务与实例状态不变。
- 根因：`OperatorContext` 不再暴露 `permissions`，`WorkflowContext` 的权限只来自全局 `FlowEngine.permissionHandler()`。Demo 按请求确定操作者，若全局处理器拿不到当前用户，权限集合为空，`TaskServiceImpl.checkAuth` 与 `FlowUpdateHandlersChain.authGate` 直接判无权限。
- 检查：Demo 侧按请求写入当前用户（`X-User-Name` 拦截器 → `DemoUserContext` → `DemoPermissionHandler.permissions()` 返回该用户的 `permissionFlags`）；curl 复测必须带 `X-User-Name`，只带 body 里的 `user` 字段不够。
- 复测要求：修复后按矩阵重跑通过/驳回/终止/转办/委派/加减签；仅验证发起与撤回不足以证明办理链路可用。

## 请求头与 body 操作者不一致不能越权

- 不变量：Demo 的授权权限来自 `X-User-Name` 请求上下文；部分命令的 OperatorContext/历史审计操作者来自 body `user`。二者必须分别验证，不能互相替代。
- 复现：在隔离测试环境中让请求头与 body `user` 分别使用有权限和无权限身份，直接调用 pass/reject/terminate/transfer/depute/add-signature/reduction-signature。
- 检查：断言授权只受请求头 permissions 控制；历史审计字段按当前命令构造路径核对是否记录 body 操作者；所有拒绝操作的实例、任务、历史、用户、等待五类数据前后完全一致。UI 隐藏按钮不能作为唯一鉴权证据。
- 处理：权限负例始终直调 API，并把“是否允许办理”和“记录哪个操作者”拆成两个断言；请求头/body 不一致不是预设必拒条件。

## 排他网关条件变量缺失时抛错而非走无条件兜底

- 症状：发起时不传网关条件引用的变量（如 `day`），通过首节点后接口返回 400「跳转条件不能为空!」，实例停在首节点，不会落到无条件兜底出口。
- 边界区分：变量存在但不满足条件（如 `day=2`）时正确走无条件兜底；仅变量完全缺失时抛错。
- 检查：复测时用「无变量」与「变量存在但不命中」两个实例分别发起；无变量实例应至少给出可理解的业务提示，或按兜底出口流转。
- 处理：保留失败实例现场；定位归属（Core 条件求值对缺失变量的行为 vs Demo 应校验必填变量）后再改，不要为了让流程走通而改定义。

## 抄送列表把同任务的办理历史一并返回

- 症状：`GET /api/tasks/copy` 对同一任务的 `pass` 历史与 `copy` 历史都返回（EXISTS 按 `flow_user.associated = task_id` 关联，未过滤 `flow_his_task.flow_status='copy'`）。
- 影响：抄送人会在抄送列表看到该任务的办理记录，属展示语义问题；办理人隔离本身正确（非抄送人查询为 0）。
- 检查：办理时带 `copyUsers` 后，分别以抄送人和非抄送人查询抄送列表；抄送列表应只含 `flow_status='copy'` 的记录。
- 处理：Demo 查询侧过滤即可，不涉引擎契约。

## 隐式并行汇聚会首到达即生成后续节点

- 现象：并行分支直接汇入同一个中间节点（没有显式汇聚网关）时，完成任意一个分支就生成该汇聚节点，另一分支的待办随后被引擎清理。
- 判定：`FlowPathResolver.retainJoinPath` 只在本次流转路径经过并行/包容网关（node_type 4/5）时才做汇聚等待，隐式汇聚不适用，属既有语义（09-25 01:11 的旧记录与本次一致）。
- 检查：验证「完成最后一个实际分支才越过汇聚」必须使用带显式汇聚网关的定义；隐式汇聚场景只核对分支出口与最终节点，不要据此判定汇聚缺陷。

## 服务任务（node_type=6）漏触发节点监听器

- 症状：含服务任务的流程发起后，全局/节点监听器只收到服务任务的 `create`；服务节点自身的 `start`、`finish` 以及自动推进创建的后继节点 `create` 全部缺失，人工节点监听器配置在「服务任务之后的首个人工节点」上会静默不生效。
- 根因：自动节点推进（`AutoNodeAdvancer`）只做临时待办、历史与路由编排，未接入 `ListenerUtil`，绕过了正常办理链 `FlowExecuteChain` 的 start 与 endCreate 监听器。
- 检查：发起一条 `开始 → 服务任务 → 人工审批` 的流程，按全局监听器日志核对事件序列，应为 `start(服务任务) → finish(服务任务) → assignment(后继人工节点) → create(后继人工节点)`；服务任务临时待办无办理人，不产生 `flow_user`。
- 边界区分：事件顺序上，外部链路的 `finish(当前人工节点)/create(服务任务)` 仍在 `advanceAuto` 之后触发（设计如此），核对时不要据此判定重复或缺失。
- 处理：保留日志现场；修复应落在自动节点编排内补 `start`、`endCreateListener` 与后继人工节点的 `assignment`，不要为绕过问题在 Demo 监听器里特殊处理。

## 服务任务后继人工节点漏触发 assignment（办理人未展开）

- 症状：`开始 → 服务任务 → 人工审批` 发起后，后继人工节点只触发 `create`、不触发 `assignment`；Demo 全局监听器负责把 `user:xxx`/`dept:xxx` 展开为实际用户名，于是 `flow_user.processed_by` 落成 `user:approver1` 而不是 `approver1`，人工节点上的通知/展开类监听器静默失效。
- 根因：`AutoNodeAdvancer` 建后继待办时只补了 `endCreateListener`，缺少 `FlowExecuteChain#buildTasks` 中的 `executeAssignment`；且 `assignment` 必须在 `taskAddUsers` 之前触发，否则监听器对 `permissionList` 的改写无法随办理人落库。
- 检查：发起含服务任务且后继为人工节点的流程，`flow_user.processed_by` 必须是展开后的用户名；事件序列应为 `start(服务任务) → finish(服务任务) → assignment(后继人工节点) → create(后继人工节点)`。
- 边界区分：`assignment` 事件按人工办理链约定以**上游节点**为 `node`（此处是服务任务），被分派对象在 `nextTasks` 里（后继人工节点）；只看 `node` 会误判成「服务任务触发了 assignment」。发起链路的 `assignment`（`node=开始`）本来就会携带服务任务临时待办，属既有行为。
- 处理：修复落在 `AutoNodeAdvancer`，只对后继人工节点触发分派，不要改 `FlowStartChain` 的既有语义。

## 服务任务自动推进后流程图停在进入服务任务之前

- 症状：含服务任务的流程在人工节点提交后，实例详情流程图里服务任务节点一直显示"进行中/未完成"，后续人工节点也不显示"进行中"，看起来像卡在服务任务上；但任务列表已出现后继人工节点待办。
- 根因：`AutoNodeAdvancer` 只更新历史、待办与实例状态，未调用 `ChartService#skipMetadata` 合并流程图元数据，实例 `def_json` 快照停留在进入服务任务之前（提交人工节点时标记的"下一个节点=服务任务=待办理"）。
- 检查：提交人工节点后直查 `flow_instance.def_json`：本跳服务任务应为 `2`（已办理）、后继人工节点应为 `1`（待办理）；连续多个服务任务要逐跳核对，不能只看最后一个。
- 边界区分：服务任务历史是 `flow_status=2`（通过）/`skip_type=NONE` 且无办理人（`AUTO_PASS=3` 已移除，自动审批与服务任务统一记「通过」）；"办理历史"条数与流程图着色是两套数据，不要用办理历史推断节点状态。
- 处理：修复落在 `AutoNodeAdvancer` 每跳解析后继之后补 `skipMetadata`，与办理链 `route()` 语义一致；不要在前端或 Demo 侧做状态补偿。

## Spring 集成下内置定时器不启动（init 早于 setFlowConfig）

- 症状：开启 `warm-flow.timer-enabled=true` 后，启动日志没有「内置定时器已启动」，`flow_event_wait` 的定时记录一直停在 `WAITING`，定时启动 / 定时捕获 / 定时边界事件永不触发；而 core 单元测试（含定时器测试）全绿。
- 根因：`WarmFlow.init()` 内部触发定时器启动，但启动判断读取的是全局 `FlowEngine.getFlowConfig()`；Spring starter 在 `warm-flow.init()` **之后** 才 `FlowEngine.setFlowConfig(...)`，于是全局配置为 `null`，启动被静默跳过。
- 检查：必须以 Spring 集成方式启动 Demo 复测（不能只跑 core 单测），确认启动日志出现「内置定时器已启动」，再验证带定时事件的流程能到期推进。
- 边界区分：core 单测通过不能证明集成可用——单测通常在 `start()` 前已注册全局配置，掩盖了这个顺序问题。
- 处理：`WarmFlow.init()` 启动定时器时显式传入自身配置，不依赖全局配置注册顺序；修完同时回归 core 单测与 Demo 定时流程。

## 事件一次性成功后的等待行清理口径取决于删除模式

- 症状：事件一次性成功后，`flow_event_wait` 查不到物理行，但库里残留的软删行 `status` 仍显示 `RUNNING`，容易误判为「执行结果未提交」。
- 根因：MyBatis-Plus 实体上的 `@TableLogic` 让 `mapper.delete(...)` 变成 `del_flag='1'` 的逻辑删除，删除语句不会回写 `status`。
- 检查：**先确认宿主删除模式**。Demo 以 `warm-flow.logic-delete=false` 运行，等待行是**物理删除**，查不到行即已消费；`logic-delete=true`（默认）的宿主才需按 `del_flag`（活动=`0`）判定，不要看 `status`，也不要以物理行是否存在为准。
- 边界区分：这是引擎逻辑删除的统一约定，不是事件特例；`WarmFlowPostInitTableInfoHandler` 覆盖全部 9 张流程表（含 `flow_event_wait` / `flow_form`）。需要物理清除的宿主按保留期自行清理。
- 处理：测试与判定按宿主实际删除模式的口径；不要为通过测试去改引擎删除语义。

## 删除实例 / 定义后残留指向已删宿主的等待行

- 症状：Demo「删除实例」或 `InsService.remove(ids)` 删掉实例后，`flow_event_wait` 仍留有该 `instance_id` 的行（`WAITING` 或 `CANCELLED`）；删除流程定义后同样残留定义级等待，等待事件列表能看到宿主已不存在的记录。
- 根因：等待记录早期只被"取消"（`status=CANCELLED`，不删行），Demo 的物理删除 Mapper 只清 `flow_user / flow_task / flow_his_task / flow_instance`，漏了等待表；`AutoNodeAdvancer.removeTempTask` 删除服务任务临时待办时也没有取消附着其上的边界等待（边界事件可挂在服务节点上），留下会被定时器误触发的活动等待。
- 检查：删除实例后按 `instance_id`、删除定义后按 `definition_id` 查 `flow_event_wait`，应为 0 行；带边界事件或消息 / 定时捕获的实例最容易复现。
- 边界区分：**实例 / 定义仍存在**的场景（挂起、撤回、终止、节点重达）语义是"取消"而非删除，`CANCELLED` 行会保留，这是既有约定，不要按"必须没有行"判定；只有宿主行被删除时才要求等待行一并清理，且逻辑 / 物理跟随引擎删除模式。
- 处理（已修复）：`InsServiceImpl.toRemoveTask` 改为按 `instance_id` 随实例同批删除（`eventWaitService.remove(entity)`）；`DefServiceImpl.removeDef` 删除定义级等待；`AutoNodeAdvancer.removeTempTask` 删除临时待办前先 `cancelByAttachedTaskId`。core 侧由 `EventRuntimeCharacteristicTest` 的 `insRemoveDeletesInstanceEventWaits` / `removeDefDeletesDefinitionEventWaits` / `serviceTempTaskRemovalCancelsBoundaryWait` 守护，ORM 侧由 `LogicDeleteModeTest` 守护按实体条件删除的逻辑 / 物理两种模式。

## triggerMessage 的消息载荷被静默丢弃

- 症状：`InsService.triggerMessage(instanceId, eventName, payload, context)` 触发消息捕获后流程正确推进到后继节点，但传入的 `payload` 没有进入实例变量（`flow_instance.variable` 仍为空），`flow_event_wait.payload` 也为空；接口返回成功，属静默丢数据。
- 根因：`triggerMessage` 把 `payload` 合并进一个临时 `WorkflowContext`，随后 `processClaimedMessage(target)` 既没有接收该 context，也没有把 payload 写入等待记录的 `payload` 列；而运行时是由 `EventRuntimeHandler.executionContext(wait, caller)` 从 `wait.getPayload()` 读取并合并为流程变量，于是 payload 两条路径都没落地。
- 检查：触发消息捕获时带非空 `payload`，核对 `flow_event_wait.payload` 与 `flow_instance.variable` 都应包含这些键；只看接口成功、流程是否推进不足以发现。
- 边界区分：`triggerMessage` 的 javadoc 与设计 §9.2 都明确「将 payload 合并为流程变量」，因此这是实现缺口，不是「暂不支持」；`startByMessage` 的 payload 需一并核对。
- 处理（已修复）：`InsService.triggerMessage` 现于抢占前把 payload 按 `id + version + status in (WAITING, RETRY)` 落库到等待记录 `payload` 列（新增 `FlowEventWaitDao.bindPayload`，不递增版本号），运行时 `EventRuntimeHandler.executionContext(wait, caller)` 据此合并为流程变量；该检查保留为回归守护，不要在 Demo / 调用方自行改写实例变量绕过。

## triggerMessage 的调用方上下文（操作人/租户）未生效

- 症状：`triggerMessage(..., context)` 传入的 `context.handler` 没有成为本次推进的审计操作人——被中断任务的历史 `approver` 记成系统兜底（`system`/`systemHandler`），而非调用方指定的用户；`context.tenantId` 同理被忽略。
- 根因：`triggerMessage` 的 `context` 只在（已删除的）payload 合并块里被用过；`processClaimedMessage(target)` → `EventWaitProcessor` → `EventTaskHandler.handle(wait)` 只把等待记录交给处理器，`EventRuntimeHandler` 始终用系统兜底构造上下文，调用方上下文从未透传。
- 检查：消息边界（`MESSAGE_BOUNDARY`）由 `POST /api/instances/{id}/messages` 携带 `X-User-Name: approver-x` 触发后，核对被中断节点的历史 `flow_his_task.approver` 应为 `approver-x`；只看流程是否推进、任务是否中断不足以发现。
- 边界区分：`startByMessage` 把 `context` 传进了 `FlowExecution`，操作人正常；只有 `triggerMessage` 路径丢失，不要据此判断整个事件特性都不支持上下文。
- 处理（已修复）：`EventTaskHandler` 新增 default 方法 `handle(EventWait, WorkflowContext)`，`processClaimedMessage` 增加带上下文的透传重载，`triggerMessage` 传入调用方 `context`；`EventRuntimeHandler.executionContext(wait, caller)` 中调用方 `handler`/`tenantId` 优先、未指定时回退系统兜底。定时调度路径无调用方上下文，行为不变。

## 监听器在 start 事件抛错会因外层链找不到任务而失败

- 症状：节点监听器挂在 `start` 事件、命中失败时调用 `TaskService.throwError`，边界事件虽被触发，但办理接口最终抛 `FlowException: 未找到待办任务!`。
- 根因：`throwError` 在内层完成中断与边界路由（删除当前任务、创建边界出口任务）；而 `start` 监听器在办理链中途触发，链尾 `persistAndFinalize` 仍按原任务推进，此时任务已被删除。
- 检查：判定型监听器应挂 `create` 事件——节点创建时判定，此时边界等待已建好（`registerBoundaryWaits`），命中则中断任务、不进入办理链；变量不命中则正常产生待办。
- 边界区分：`finish` 事件同样不可用（任务已归档，`throwError(taskId)` 找不到任务）。core 特征测试 `ListenerThrowErrorCharacteristicTest` 同时覆盖 `create` 正常收口与 `start` 报错两组行为。
- 处理：判定型抛错一律用 `create` 事件；这也是「失败即进边界、成功正常办理、无需手动按钮」的推荐姿势。

## 终止结束事件的实例状态曾被写成已完成

- 症状：`TERMINATE_END` 到达后流程正确终止、并行分支待办被中断，但 `flow_instance.flow_status` 落成「已完成」（demo 展示 `finish`），统计口径无法区分「正常办结」与「严重异常终止」。
- 根因：终止语义此前只体现在分支待办的中断与结束事件留痕上，实例状态仍由固定 END 的收口统一写「已完成」；收口链没有把「终止」传递到实例。
- 检查：`veto=1` 复测终止结束后，实例状态应为**终止**（demo `termination`，内置码 4），普通完成与消息结束仍为「已完成」；同时核对 `task_id=0` 留痕历史（message「流程由该终止结束事件终止」）存在。
- 边界区分：宿主手动指定的任务状态不得污染实例状态——落点为 END 时实例状态只认「任务结束编码→已完成」或「终止」，其余一律回落已完成，避免任务/实例两套编码串列。
- 处理（已修复）：`EventArrivalDispatcher` 把 `instanceStatus(FlowStatus.TERMINATE)` 写进执行 intent，`TaskServiceImpl.instanceStatusOf` 在 END 落点沿用该语义；core 特征测试 `EndEventCleanupCharacteristicTest` 守护（含宿主自定义编码与手动任务状态两个防串列用例）。

## 定时启动实例的初始状态曾是待提交

- 症状：定时启动定义到期后自动创建实例，`flow_instance.flow_status` 落成 `draft`（待提交），待办列表与业务侧按「草稿」过滤的查询看不到这单。
- 根因：定时启动没有发起人提交环节，实例状态沿用了普通发起链的初始「待提交」语义。
- 检查：定时启动触发生成首个待办后，实例状态应为**审批中**（Demo `waiting`）；检查实际夹具配置的节点默认变量。当前 `demo_timer_start` 配置 `veto=0`/`channel=daily`，而 `demo_event_daily_check` 的每日定时启动节点没有这些默认值。
- 边界区分：手动发起的草稿实例仍是 `draft`，两条路径语义不同属设计如此；不要以「发起后应统一为草稿」为由改回。
- 处理（已修复）：`EventRuntimeHandler.timerStart` 触发时把实例状态置为审批中；core 特征测试 `EventRuntimeCharacteristicTest` 守护实例状态与默认变量两项断言。

## 终态实例重启不变量（working-tree only，直到能力正式提交）

### 只允许三种终态且实例不能有现存待办

- 不变量：只有映射后的 `FINISHED`、`TERMINATE`、`INVALID` 可重启；定义和实例必须激活；实例没有活动任务。
- 负例：draft/waiting/cancel/back、自定义其它终态，以及“终态但仍有任务”全部拒绝。
- 必查：除唯一新增的重启审计行外，调用前后 `flow_instance`、`flow_task`、`flow_his_task`、`flow_user`、`flow_event_wait` 快照完全一致，不能为了重启先静默删除旧任务。

### 目标必须属于实例快照的普通人工节点

- 不变量：目标属于同一实例 `def_json` 快照，是 `BETWEEN` 且 `eventType` 为空；会签、或签、票签、顺序签是合法人工协作模式。
- 负例：定义后来新增但快照没有的节点、另一版本/定义、start/end/gateway/service/event 全部拒绝。
- 必查：失败不改变实例当前位置、状态、历史、任务、用户或等待。

### 显式审批人必须归一化且旧历史逐字段不变

- 不变量：handlers 经 `PermissionHandler.convertPermissions` 后再次过滤空值和去重；最终至少一名具体用户。restart 只新增当前任务/用户和一条重启审计，不重写或补造旧历史。
- 必查：新任务为 `pending`；`flow_user.processed_by` 是具体用户名；重启前后旧历史的 id/taskId/status/approver/collaborator/message/timestamps/variables 全等；审计行 `flow_status=restart`、`task_id=-1`、来源节点为重启前节点、目标节点为重启目标、办理人为操作人。
- 负例：空列表、全空白、转换后为空、重复标识和未知权限按当前校验明确处理，不得成功后留下无办理人任务。

### 正常到达副作用必须完整恢复

- 不变量：旧活动等待取消；目标节点边界等待绑定新 taskId 重建；节点默认变量只补缺失键；assignment 在办理人落库前执行，create 随后执行；自动抄送和流程图 TODO 与正常到达一致。
- 必查：监听器/持久化任一步失败时整笔回滚，不留 task/user/wait/instance 部分写入；旧历史仍不变。
- 代码锚点：restart 编排应复用正常边界等待、默认变量、监听器、抄送和 chart 语义，而不是 Demo 单独补偿。
- 状态锚点：审计状态显式 `taskStatus` 优先，否则取 `StatusMapper.taskStatus(RESTART)`，mapper 返回 null 时回落内置 `restart`；不得把 null 写进非空的 `flow_his_task.flow_status`。

### 可选 flowStatus 必须有稳定回落

- 不变量：调用方显式传值时写入该实例状态；未传或空值时使用 `StatusMapper.instanceStatus(WAITING)`。
- 必查：默认 mapper 与自定义 mapper 各一例；任务状态仍是 `pending`，不得被实例自定义状态污染。

### 同实例并发重启只能有一个赢家

- 不变量：宿主事务内对实例行加锁并覆盖“检查无活动任务→创建任务/用户→更新实例”的完整区间。
- 复现：两个独立事务同时 restart 同一终态实例，不能用单线程顺序调用替代。
- 必查：恰好一个成功，另一个在获得锁后看到新任务并拒绝；最终只有一份活动任务、一组审批用户、一次实例更新，旧历史无变化。
- Demo 锚点：`SELECT ... FOR UPDATE` 必须在 `@Transactional` 方法内、调用引擎 facade 之前执行；Spring 事务本身不等于并发串行化。

## 组签不变量（GRP-*）

### 组成员必须在首次写入前展开为具体用户

- 不变量：`flow_user` 每行 `processed_by` 是具体用户名，`seq_no` 是组下标（并行组签同样写入）；同组多人共享同一 `seq_no`。
- 负例：任一组成员的 `resolveGroupHandlers` 展开为空时，发起 / 办理 / 撤回 / 事件 / 服务后继必须在首次 `flow_task`、`flow_user`、`flow_event_wait` 写入前失败；失败跳自身零写入。
- 边界：服务处理器执行与跨服务跳的宿主副作用不在 core 回滚范围，整条调用链的原子性由宿主事务承担；不要用单跳测试宣称多级服务链整体原子。
- 代码锚点：`PermissionHandler.resolveGroupHandlers`、`ArrivalPlan`、`EventArrivalDispatcher`、`UserServiceImpl`。

### 组完成判定按整组清退，不能按人计票

- 不变量：通过时删除当前办理人命中的所有组槽位行；顺序模式只命中最小 `seq_no` 组，并行模式命中全部匹配组；仍有组时写一条 `pass` 签名历史并停留，最后一组完成才流转。
- 驳回：任一当前组成员驳回即清空全部组，按驳回路由流转。
- 检查：不要用 `flow_task` 行数或单行 `flow_user` 判断完成，必须按 `seq_no` 分组核对剩余行。
- 代码锚点：`TaskCooperationHandler#cooperateGroup`、`SequenceSignUtil#isCurrentGroup`。

### 顺序组签的可见性与办理权限一致

- 不变量：当前组任一成员可见、可办；后续组不可见，直调返回无权限。core `TodoQueryUtil` 与 Demo `selectTodoPage` 两条查询路径都必须按「匹配行最小 `seq_no` = 活动行最小 `seq_no`」过滤。
- 检查：同时核对待办接口结果与 `flow_user` 的 `seq_no`，不能只用其中一条路径证明。
- 兼容：普通顺序会签仍按「最小顺序位的那一名办理人」判定，不能被组签规则改写。

### 组签转办是槽位整体替换

- 不变量：删除当前组全部成员行，为目标新增 `type=transfer`、同 `seq_no` 的行；目标可多人（组内或签）；二次转办规则相同；历史 `approver` 为转出人、`collaborator` 为目标、`message` 原样保留。
- 负例：目标已在任务任何活动行、顺序组签非当前组、普通节点多目标、空目标、重复目标全部拒绝且五类数据不变。
- 检查：转办后原组成员不可见、受转人可见；组标识不单独落库，不要从历史里找组 ID。

### 组签禁用动作与重启排除必须服务端兜底

- 不变量：加签、减签、委托、任意跳转、自动审批、终态重启 / 重置到组签节点全部由服务端拒绝；Demo 按钮隐藏只是提示。
- 检查：直调 HTTP API，不只看 UI；失败后比较实例、任务、历史、用户、等待五类快照。
- 代码锚点：`FlowUpdateHandlersChain`、`FlowExecuteChain`（PASS + targetNodeCode）、`AutoPassAdvancer`、`InstanceRestartCoordinator`。

### 旧版本引擎没有安全降级

- 不变量：旧引擎把组签节点当作无办理人的或签节点，知道 taskId 的调用方可以越过组规则。
- 处理：发布说明与运维必须要求「组签定义只发布到支持组签的引擎版本」；测试报告写明引擎版本，不能声称权限为空会安全失败。
