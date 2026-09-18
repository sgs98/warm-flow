# FlowExecution 执行作用域设计（Phase 1）

- 日期：2026-09-18
- 状态：**Phase 1 已实施（第二轮）+ R2/R3/R5 已接入**——同日经实施计划批准从零重新落地（第一轮因超出当时授权回滚，回滚备份已删除），随后经用户指示接入 R2/R3/R5。落地记录见 §13.5 / §13.6；特征测试套件 126/126 全绿
- 范围模块：warm-flow-core
- 风险级别：L2（涉及流程状态机编排路径，行为不变性为验收标准）

## 1. 背景与问题诊断

### 1.1 上下文传递混乱

一次流程操作（execute / revoke / terminate / updateHandlers）当前需要穿过 7 种"上下文"形状：

| 形状 | 位置 | 职责 |
|---|---|---|
| `WorkflowContext` | `workflow/context/`（公共 API） | 调用方意图（handler、permissions、variables、ignore 等 12 字段） |
| `TaskServiceImpl.R` | `service/impl/TaskServiceImpl.java:534` | task/instance/definition/nowNode 四元组，public 字段 |
| `FlowCombine` | `dto/` | definition + allNodes + allSkips |
| `PathWayData` | `dto/` | 路径累积器，被多层 add/remove |
| `ListenerVariable` | `listener/`（6 个 telescoping 构造器） | definition/instance/node/task/variables 的再投影，core 内构造约 10 处 |
| `TaskCooperationRuleEvaluator.Context` | `service/impl/` | 8 个位置参数 |
| `OperatorContext` | `workflow/context/` | 经 `command.fillContext()` 合入 WorkflowContext |

具体病灶：

1. 同一份数据的三重投影：`ListenerVariable` 每次从 `R` + `WorkflowContext` 抄位置参数，纯搬运代码；
2. 可变共享状态当传声筒：`TaskServiceImpl.java:292` 用 `context.setInstanceStatus(...)` 把中间结果写回调用方 context，下游再读——副作用通道；
3. 合并不变量靠复制粘贴：`MapUtil.mergeAll(instance 变量, context 变量)` 在 `TaskServiceImpl.java:81/163/266/366` 出现四遍（实施时核对：updateHandlers 亦有一处，原稿漏记），合并顺序是隐式契约；
4. `PathWayData` 所有权不明：`NodeServiceImpl.java:213/264/276`、`FlowPathResolver.java:86`、`TaskServiceImpl.java:191` 均在增删其内部列表；
5. `InsServiceImpl.java:85-89` 手工逐字段拷贝 `WorkflowContext`（Phase 2 处理，本设计不含）。

### 1.2 重复查询

一次普通 complete（单办理人、串行网关）写库前即有 8 次 SELECT（Q1 task → Q2 instance → Q3 definition → Q4 nowNode → Q5 办理人 → Q6/Q7 全量节点+连线 → Q8 收尾待办）。以下为冗余候选清单：R1 已核实为低风险，R2-R5 以验证为前置，R6 推迟。

| # | 冗余 | 证据 |
|---|---|---|
| R1 | terminate 同一任务连查两次 | `TaskServiceImpl.java:247` `getById(taskId)` 后 `:253` `getAndCheck(task.getId())` 内部再 `getById` |
| R2 | 并行/包容汇聚时定义图整表重查 | `FlowPathResolver.java:75` 走 `previousNodeList(definitionId,…)` → `NodeServiceImpl.java:144-145` 重查全量节点+连线；而 `suffixNodeList` 有 Combine 重载（`NodeServiceImpl.java:104`）、`previousNodeList` 没有——API 不对称直接导致重复 |
| R3 | revoke 同一语句 26 行内打两遍 | `TaskServiceImpl.java:170` 与 `:196`（`getByInsId` 的实现就是后者本身，见 `:443-445`） |
| R4 | reject 流程背靠背重查实例待办 | `oneVoteVeto` `TaskServiceImpl.java:583` 与 `handUndoneTask` `:606`，语句一字不差 |
| R5 | 办理人表一操多次往返 | `TaskServiceImpl.java:86`（无类型）、`TaskCooperationHandler.java:79`（3 类型）、`TaskServiceImpl.java:376`（getPermission，3 类型） |
| R6 | nowNode 单查后全量节点又含它 | `FlowTaskContextLoader.java:39` vs `DefServiceImpl.java:168`（**推迟不修**：动查询顺序会改变异常触发次序） |

关键事实：`UserType` 当前枚举仅有 3 个值（APPROVAL/TRANSFER/DEPUTE，`enums/UserType.java`），但这本身不足以证明“无类型全集查询 + 内存过滤”与“3 类型 SQL 查询”在所有 ORM、排序、重复、租户和脏数据场景下逐字节等价；R5 必须以回归测试为前置条件。

### 1.3 根因

缺少执行作用域（unit-of-work）对象：没有统一存放"本次操作已加载的聚合"，每个协作者通过 `FlowEngine.xxxService()` 静态门面各自再取一遍，静态服务定位遍布 core 各服务实现。

## 2. 总体方案与命名决策

- **分两阶段**：Phase 1 = 执行作用域 + 查询去重（本设计）；Phase 2 = 在其上把编排表达为有序步骤链（见附录 A）。
- **命名**：新对象叫 `FlowExecution`，不叫 `FlowContext`。规则：**意图叫 context，过程叫 execution**。`WorkflowContext` 已占住"调用方意图"语义且是公共 API，再引入一个名字含 Context 的对象会复刻"两种上下文靠记忆区分"的现状。`FlowExecution` 亦与 Flowable/jBPM 的领域词汇（`DelegateExecution` 等）一致，且与 `FlowCombine` 命名风格统一。
- **形态**：`final class`（包内私有），非 record——它是演化中的工作状态，不是不可变快照。

## 3. FlowExecution 类设计

位置：`org.dromara.warm.flow.core.service.impl.FlowExecution`，包内可见，零公共面。

Phase 1 不把执行作用域暴露为 SPI，也不承诺“整个方法期间所有数据都来自同一快照”。监听器、表达式和框架扩展都可能在回调期间读写数据库；缓存只在明确的安全边界内复用，回调边界之后默认只标记失效、不自动重载，是否重查由既有调用点决定（§5.1）。

### 3.1 字段

| 段 | 字段 | 类型 | 写入者 / 时机 |
|---|---|---|---|
| 身份 | `task` | `Task` | `loadTask()`；仅任务级操作存在 |
| 身份 | `instance` | `Instance` | `loadTask()` 或 `loadInstance()` |
| 身份 | `definition` | `Definition` | 与现有 loader 相同的校验顺序加载 |
| 身份 | `nowNode` | `Node` | 任务级操作加载；实例级 revoke 不强行伪造单一 nowNode |
| 身份 | `intent` | `WorkflowContext` | 调用方传入；引擎尽量不写，监听器仍可能通过公共 API 修改 |
| 一次加载 | `combine` | `FlowCombine` | 按操作选择 `getFlowCombineNoDef` / `getFlowCombine(definition)`；缓存/失效唯一口径见 §5.1 |
| 按需读取 | `taskUsers` | `List<User>` | 只在原调用点按需读取；不在通用 `load()` 中隐式查询 |
| 按需读取 | `mergedVariables` | `Map<String,Object>` | 在原有合并点显式计算；监听器替换 variables Map 后旧派生值只失效，是否重算遵循现有调用点（§4） |
| 快照 | `instanceTasks` | `List<Task>` | `snapshotTasks()` 无则查；`tasksRemoved()` 写跟踪剔除；`refreshTasks()` 持久化后强制重查 |
| 工作 | `path` | `PathWayData` | `FlowPathResolver.resolve()` 之后归 execution 所有 |
| 工作 | `nextTasks` / `nextUsers` | `List<...>` | 建任务 / 持久化步骤 |

### 3.2 骨架

```java
final class FlowExecution {
    // 身份段 / 一次加载段 / 快照段 / 工作段（见 3.1 表）

    /** 吸收 FlowTaskContextLoader，校验顺序逐条保留 */
    static FlowExecution loadTask(Task task);

    /** 实例级操作使用，不要求存在唯一当前任务 */
    static FlowExecution loadInstance(Long instanceId, WorkflowContext intent);

    /** 一次操作内只加载一次，操作内固定复用；缓存/失效唯一口径见 §5.1 */
    FlowCombine loadCombine();

    /** 在既有调用点合并变量，并按兼容要求同步 intent；不是任意 accessor 的隐式副作用 */
    Map<String, Object> mergeVariablesAtCurrentPoint();

    /** 监听器/表达式回调完成后，使受影响的派生缓存失效 */
    void invalidateAfterCallback();

    List<Task> snapshotTasks();
    void tasksRemoved(List<Task> removed);
    void refreshTasks();

    /** 从 execution 派生；调用方显式选择是否附带 context，保留现有事件语义 */
    ListenerVariable listener(Task task, Node node, boolean attachContext);
    ListenerVariable listener(Task task, Node node, List<Node> nextNodes, List<Task> nextTasks
        , boolean attachContext);
}
```

- `loadTask()` 校验顺序逐条镜像 `FlowTaskContextLoader.java:30-40`：task 空 → instance 空 → definition 空 → 激活态 → 终态 → 结束节点 → nowNode 空。
- `loadInstance()` 逐条镜像 `revokeInternal` 现有校验顺序，不得增删或重排：默认状态写回（`:157-159`）→ 实例查询/判空（`:161-162`）→ 变量合并（`:163`）→ 定义查询/判空（`:164-165`）→ 激活态（`:166`）→ 终态（`:167`）→ 结束节点（`:168`），异常消息与触发次序不变；保留多待办语义，不把实例级操作强行套成单任务操作。`loadInstance()` 不自动加载 `instanceTasks`；revoke 的第一次 `taskList` 查询和第二次 `curTaskList` 查询仍由原编排点分别决定，以保留监听器前后的查询时序。
- `FlowTaskContextLoader` 删除（包内私有，仅 `TaskServiceImpl` 引用）；`TaskServiceImpl.R` 删除；`getAndCheck` 两个重载返回 `FlowExecution`。
- `ListenerVariable` 类本身不动（公共 API），只是 core 内 10 处构造改走工厂。
- `TaskServiceImpl.load(taskId)` 仍然是表单读取路径，不得因为 `FlowExecution` 自动查询办理人或定义图而增加副作用查询。
- listener 工厂必须保留现有列表引用和原地修改时机：`nextNodes`、`nextTasks` 不能默认复制；`ListenerUtil.endCreateListener()` 对同一个 `ListenerVariable` 的逐节点修改必须继续生效。
- listener 工厂**每次调用必须新建实例**：`endCreateListener`（`ListenerUtil.java:58-61`）以游标方式原地修改传入对象，缓存或复用工厂返回值会跨站点污染。
- `snapshotTasks()` 对外只返回 execution 所有的只读视图；删除通过 `tasksRemoved()` 按 ID 跟踪，不能让调用方直接修改缓存集合。若底层删除结果不确定，必须刷新而不是凭本地集合推断数据库状态。

## 4. 上下文写纪律

1. 引擎编排代码原则上不直接修改 `intent`（`WorkflowContext`），但 Phase 1 不能把它当作真正不可变对象；公共监听器可能通过 `ListenerVariable.getContext()` 调用 setter。保留以下兼容写回：
   - `revokeInternal:157-159` 的默认值补写 → `execution.defaultInstanceStatus(CANCEL)`（为空才写）；
   - `terminateInternal:292` 的回写 → `execution.setInstanceStatusComputed(...)`。
2. 变量合并收敛到显式调用点，而不是任意 accessor 的隐式副作用。保持现有合并时机，并保留 `context.setVariables(merged)` 写回；若监听器替换了 variables Map，只允许使 execution 的派生缓存失效，不能继续使用旧缓存，也不得新增隐式的实例变量重新合并。后续是否重新 merge，必须严格遵循现有调用点。
3. 回调边界：start / assignment / finish / create 监听器以及表达式执行完成后，`taskUsers`、实例待办快照和变量派生值默认视为可能过期；`combine` 按 §5.1 的固定复用规则处理，不纳入本通用失效规则。
4. `PathWayData` 所有权：`resolve()` 之后仅路径相关代码与持久化读取可变动；`revokeInternal:191` 的 `addAll` 移入 resolver 对应位置并注明所有权。不得改变现有列表引用和原地修改时机，监听器可观察行为以此为准。

### 4.1 上下文治理边界

本文件只记录候选的 2.0 改造方向，不把文档中的历史说明视为本轮用户授权。Phase 1 仍按现有公共契约约束设计；若要进入 2.0，必须在版本发布决策中再次明确授权、范围和迁移方案。

原始三档边界（兼容约束下）：

| 档 | 对象 | 说明 |
|---|---|---|
| ✅ Phase 1 治愈 | `R`（删除）、`RuleEvaluator.Context`、变量合并 ×3、`ListenerVariable` 拼装 ×10、context 写回散布 | 均为包内私有，零契约成本 |
| ⚠️ → §12 候选 | `WorkflowContext`、`PathWayData`、`ListenerVariable` 构造器、`WorkflowCommand.fillContext` | 需要单独的 2.0 迁移设计和明确版本授权 |
| ❌ 不在本设计轴上 | 命名治理（`DefService`/`IWarmService`/`NOT_FOUNT`，独立 PR 轨道）、`FlowEngine` 静态调用（DI 轴，另立设计）、存储层 Integer key（数据模型非 API） | 刻意不塞进同一刀 |

## 5. 冗余查询消除明细

| # | 改法 | 收益 | 风险与缓解 |
|---|---|---|---|
| R1 | `terminate()` 改调**已存在的** `getAndCheck(Task)` 重载（`TaskServiceImpl.java:527`），一行改动 | −1 SELECT | 已确认的低风险优化；不跨监听器缓存 |
| R2 | `NodeService` 新增 `previousNodeList(String nodeCode, FlowCombine combine)`；仅在定义图未经过可写回调、且当前操作明确允许复用时使用 | 汇聚路径理论上 −2 SELECT | 现状重查发生在回调之后，会拾取监听器直改节点/连线表的写库结果；改为复用 combine 即丢失该可见性，必须通过“监听器改图后汇聚路径仍等价”的反例测试后接入 |
| R3 | revoke 的两次待办查询只在 start listener 前后没有可能影响任务表的扩展点时复用快照；否则维持第二次查询 | 理论上 −1 SELECT | 当前 revoke 对每个任务执行监听器，默认按“可能有写”处理，不能以无写为前提 |
| R4 | 一次快照 + **写跟踪**：删除成功后才经 `execution.tasksRemoved(removed)` 剔除；删除结果不确定或回调可能写库时直接 `refreshTasks()` | reject 可能 −1 次查询 | 写跟踪不是充分条件，必须叠加回调失效和事务失败处理 |
| R5 | 先保留现有按类型查询；只有在验证排序、重复、租户、逻辑删除和未知 type 行为一致后，才允许从全集用户列表派生视图 | 会签/updateHandlers 可能减少查询 | “UserType 只有 3 个值”不足以证明结果逐字节等价；MyBatis 与 MyBatis-Plus 都要验证 |
| R6 | Phase 1 推迟 | — | nowNode 查询顺序和异常触发顺序仍保持现状 |

### 5.1 缓存与回调失效规则

执行作用域不能越过公共扩展点假设数据库状态不变。以下边界之后默认执行失效。这里的“失效”只表示禁止新增优化继续使用旧派生缓存，不自动 reload，也不得改变既有调用点的查询时机：

| 边界 | 默认失效对象 | 说明 |
|---|---|---|
| start / assignment / finish / create listener | `taskUsers`、`instanceTasks`、变量派生值（combine 不在此列，见下方唯一口径） | 监听器可调用 Bean、表达式或业务服务写库 |
| 网关/条件表达式 | 变量派生值 | 表达式可能修改传入 Map 或触发外部逻辑 |
| `removeAndUser` | `instanceTasks` | 仅数据库删除成功后更新写跟踪；失败时强制刷新。`deleteByTaskIds` 只删办理人表（如 `TaskServiceImpl.java:299`），不触碰任务快照，不列入本表 |
| 事务异常或返回值不确定 | 所有可变快照 | 不以本地集合推断数据库最终状态 |

Phase 1 的默认安全策略是“宁可重查，不复用未经证明仍然有效的缓存”。R2-R5 只有在对应 characterization test 通过后才可启用。

**combine 的缓存/失效唯一口径**（本节表格、§3.1 字段表、§3.2 骨架中的相关表述均以本段为准）：

1. 一次操作内只加载一次（入口 `loadCombine()`），操作内所有消费点复用同一引用——这就是现状语义（execute 在 `TaskServiceImpl.java:87` 加载、跨 start 监听器 `:90`、`resolve` `:107` 消费）；
2. **不因回调失效，也不在回调后重载**：`ListenerVariable` 只暴露 definition/instance/node/task/variable，combine 的节点/连线**列表**在任何路径都不可达；“同一内存对象跨回调携带”即现状行为。回调后重载反而改变行为（会拾取监听器直改节点/连线表的写库结果）。因此失效表不含 combine，骨架也不设 `invalidateCombine()`——无触发点即不设机制。注意 `Definition` **对象**在 revoke/start 与 `ListenerVariable` 共享引用，监听器修改 Definition 对象属同一内存可见——现状即如此，Phase 1 不改变也不利用该通道；
3. 变量则相反：监听器持有 map 引用；监听器原地修改时继续使用同一引用，监听器替换 Map 时只使旧派生缓存失效，后续是否重新合并严格遵循原有调用点。**combine（监听器不可达，缓存安全、重载有害）与 variables（监听器可达，缓存有害、不能隐式重算）的不对称必须体现在实现中**；
4. GlobalListener 经 `ListenerUtil.executeListener` 全事件广播，同按监听器处理；
5. 该口径不豁免 R2：`previousNodeList` 的现状是回调**之后**的重查（`NodeServiceImpl.java:144-145`），替换为复用 combine 属于“新增缓存跨回调”，仍须等价性测试放行。

`nextTasks` 的生命周期也必须写清：assignment listener 看到的是 `addTask()` 产生的原始列表；`setInsFinishInfo()` 可能原地移除结束任务；finish/create listener 继续看到现有代码经过该步骤后的同一列表引用。

### 5.2 Phase 1 默认启用范围

为避免理论收益被误当成实施硬指标，首个实现批次默认只启用：

- 执行作用域对象收敛，不改变既有查询时序；
- R1 terminate 重复查询消除；
- R4 仅在删除成功、事务结果明确且没有回调写库歧义时启用。

R2、R3、R5 默认关闭，分别通过定义图修改、revoke 多任务/监听器写库、办理人数据等价性测试后，再单独开启。查询数量是观测指标，不是 Phase 1 的行为契约。

> 2026-09-18 更新：同日经用户指示「继续接入」，R2/R3/R5 已全部接入——等价性问题以「操作内快照固定复用」重定义并写入契约（监听器中途写库不进入本次操作视图），由 `ExecutionSnapshotCharacteristicTest` 锁定，见 §13.6。跨生态（真库排序/租户/逻辑删除）回归仍依赖 warm-flow-test。

## 6. 实施顺序（每步独立可编译）

| 步骤 | 内容 | 涉及文件 |
|---|---|---|
| W1 | 新建 `FlowExecution` 的任务级/实例级加载骨架；保留 `FlowTaskContextLoader` 校验语义，先不启用跨回调缓存 | `FlowExecution` + `TaskServiceImpl` |
| W2 | 删除 `R` 与 `FlowTaskContextLoader`（`loadTask()` 落地后 loader 即无引用），将 execute / terminate / updateHandlers 迁移到 `loadTask()`；revoke 单独迁移到 `loadInstance()`，不伪造唯一 task | `TaskServiceImpl` +1 |
| W3 | 变量合并、ListenerVariable 工厂和回调失效边界；逐个保持 listener 的 null/non-null context 语义及列表引用 | `TaskServiceImpl`、`ListenerUtil` 相关调用点 |
| W4 | R1 低风险去重；R4 写跟踪仅在删除成功后更新，并补事务失败/回调写库分支 | `TaskServiceImpl` |
| W5 | 新增 Combine 重载；仅在失效规则和测试允许时接入 R2 | `NodeService`、`NodeServiceImpl`、`FlowPathResolver` |
| W6 | 评估 R3/R5；验证失败则保持原查询，不为了数字强行缓存 | `TaskServiceImpl`、`TaskCooperationHandler` |
| W7 | 验证（§8） | — |

## 7. 行为不变性清单（验收标准）

- 公共签名零变化（Phase 1 唯一增量：`previousNodeList` 新重载——W5 无条件新增该 API，受测试门控的只是 R2 缓存的接入，不是重载本身）；
- 异常消息与**触发顺序**不变；
- 监听器触发次数与时机不变（start / assignment / finish / create）;
- listener 前后 context、变量 Map、nextNodes、nextTasks、`task.userList` 的 null/引用语义与**设置时机**不变——execute 在 start 监听器**前**设置（`TaskServiceImpl.java:86`），terminate 在 start 监听器**后**、finish 监听器前设置（`:271`，`checkAuth` `:272` 消费），监听器可经 `ListenerVariable.getTask().getUserList()` 观察；
- 持久化顺序不变：his 插入 → 删待办 → 删办理人 → `setInsFinishInfo` → 插待办 → 更新实例 → 插办理人（`TaskHistoryHandler.java:34-45` 现序）；
- 事务边界不变（仍在 `TaskService` 公共方法一层，starter 切面不受影响）；
- 短路路径返回 instance 的行为照旧。

## 8. 验证方式

1. 分模块编译：`mvn -pl warm-flow-core -am -DskipTests compile`；下游 `mvn -pl warm-flow-orm/warm-flow-mybatis/warm-flow-mybatis-core -am -DskipTests compile`；最后 `mvn clean install -DskipTests`；
2. 实施时经用户批准，`warm-flow-core` 已新增 `src/test` 特征测试套件（JUnit 5 + 内存 DAO 脚手架，纯 JVM 无 Spring/DB，见 §13）；跨生态行为回归仍依赖独立仓库 **warm-flow-test**（需另行运行）；
3. 查询数前后对比：`warm-flow-demo` 开 SQL 日志，覆盖 complete（简单/会签/并行汇聚）、reject、revoke、terminate、转办；查询数只作为观测结果，不写成固定行为契约。
4. 在 `warm-flow-test` 增加回归：监听器在两次查询之间新增/删除任务、监听器替换 variables、监听器直改节点/连线表后走汇聚路径（R2 门控用例）、revoke 多活动任务、reject 一票否决、租户/逻辑删除、未知 user type、listener context 为 null 与非 null、MyBatis/MyBatis-Plus 结果一致性。

## 9. 预期收益（如实）

| 操作 | 当前基线 | Phase 1 目标 |
|---|---|---|
| complete·串行单办理人 | 以日志实测 | 先保持行为；不承诺减少 |
| complete·会签 | 以日志实测 | R5 通过等价性测试后再评估 |
| complete·并行/包容汇聚 | 以日志实测 | R2 通过“监听器改图后汇聚仍等价”反例测试后再评估 |
| reject | 以日志实测 | R4 通过删除/事务测试后再评估 |
| revoke | 以日志实测 | 默认保留第二次查询；R3 仅在安全边界明确后启用 |
| terminate(taskId) | 以日志实测 | R1 预期减少 1 次 SELECT |
| transfer/delegate/加减签 | 以日志实测 | R5 不通过则保持原查询 |

结构性收益大于数字：7 种上下文投影收敛为 1 个包内作用域，同时明确“调用方意图”和“引擎工作状态”的边界；Phase 1 不宣称 intent 已真正不可变。

## 10. 风险登记

| 风险 | 缓解 |
|---|---|
| listener/表达式在查询间写库导致缓存过期 | 回调边界只标记派生缓存不可用于新增优化，不自动重载；未经测试不复用 task/user/definition 快照 |
| R4 快照过期触发 `NOT_FOUNT_TASK` 断言 | 删除成功后再写跟踪；删除失败、事务异常和外部写库统一刷新 |
| combine 复用与监听器直改节点/连线表的可见性差异 | 操作内固定复用即现状语义（见 §5.1 唯一口径）；R2 以等价性测试门控接入，未放行前保持重查 |
| revoke 被错误建模为单任务操作 | 使用 `loadInstance()` 和实例级待办快照，保留多任务监听器循环 |
| `FlowEngine.taskService()` 自调用残留 | Phase 1 刻意不动 DI 结构，避免与本次目标纠缠 |

## 11. 可扩展性评估

先区分两种"扩展"：**引擎内部演进**（本设计的目标）与**第三方插件式扩展**（刻意不提供——引擎已有 Listener/表达式/网关三套公共 SPI 作为正门，开放内部作用域等于新增永久契约，且用户插错位置会破坏状态机语义）。

| 未来场景 | 评估 | 依据 |
|---|---|---|
| Phase 2 步骤链 | ✅ 直接铺好 | execution 即未来 Step 的统一入参，Phase 2 只加编排层 |
| 新增流程操作（拿回/催办等） | ⚠️ | 按任务级或实例级选择 `loadTask()` / `loadInstance()`，不能假设所有操作有唯一 task |
| 新增缓存聚合 | ✅ 模式可复制 | 懒加载字段 + 访问器三行一个；包内类加字段零契约成本 |
| 新增删除路径 | ⚠️ 一条纪律 | 删除必须走 `removeAndUser` 单入口，快照跟踪自动生效 |
| start 等无 task 操作 | ⚠️ 已知缺口 | Phase 2 再接入实例级 execution；本阶段不把 start 伪装成任务级操作 |
| 换存储 / 新 ORM | ✅ | 取数收口在 `loadTask()/loadInstance()/loadCombine()/snapshotTasks()` |
| 监听器变量丰富化 | ✅ | 构造收敛到工厂，加派生字段只改一处 |
| 可测试性 | ✅ 顺带收益 | 协作者接收 execution，可用假数据直测，不必拉起引擎 |
| 异步/并发推进 | ⚠️ 不支持 | execution 为单线程独占工作台，与现状假设一致；异步续跑需另行设计 |

不解决的扩展轴：多引擎实例隔离、`FlowEngine` 静态注册表全局态、事务作用域——Phase 1 既不恶化也不治愈。

## 12. 大版本契约改造候选（2.0 轨道，另行审批）

本节不是 Phase 1 的实施授权。当前仓库仍按公共 API 向后兼容规则维护；只有明确的 2.0 版本决策、迁移窗口和发布批准完成后，才可执行以下破坏性改造。前置仍是阶段 A（`FlowExecution`）先落地。

### 12.1 契约层改造项

| 项 | 目标形态 |
|---|---|
| `WorkflowContext` | 类名保留，形状重造为不可变（record 或 final + builder）；删除 setter 前必须先定义监听器如何读取/返回变量和状态。当前代码已经有 `OperatorContext`，且 `WorkflowServiceImpl.context()` 已完成 operator 到 context 的转换，不能把这部分当作未实施工作 |
| 引擎写回 intent 的两处妥协（§4） | 直接删除：terminate/revoke 的中间状态只写 `FlowExecution`，调用方 context 永不被引擎污染 |
| `ListenerVariable` | 改为不可变前必须设计 listener 的事件结果模型；当前 `ListenerUtil` 会原地修改 node/task/nextNodes/nextTasks/variable，不能只删除 setter。6 个构造器和 setter 的迁移影响需单独盘点 |
| `PathWayData` | 整体内部化：除 `NodeService` 外，还必须处理公共 `ChartService.startMetadata/skipMetadata`、`ChartServiceImpl` 及外部实现者 |
| 触碰到的签名 | `String skipType` 换 `SkipType` 必须盘点 `TaskService`、`HisTaskService`、`NodeService` 及各 ORM/plugin 调用方；不能只修改“本来会碰到”的少数签名而留下同一语义的混用 |
| `InsServiceImpl:85` 手工拷贝 | 随 intent 不可变及 builder 填充机制改造自然消亡 |
| `FlowStatusMachine` 形态 | **B4 转移表化**，已拆分为独立设计：[2026-09-18-flow-status-machine-design.md](./2026-09-18-flow-status-machine-design.md)（含外部 FSM 库弃用论证与 JDK17 实现约束） |

### 12.2 阶段划分（修订）

- **阶段 A（内部层）**：即本设计 W1~W7。异常消息、触发顺序、监听器可观察的引用和回调边界仍是验收标准；R6 不属于本阶段默认范围。
- **阶段 B（契约层）**：B1 `WorkflowContext` 不可变 + command 填充机制改造；B2 `ListenerVariable` 不可变 + 事件结果模型/工厂；B3 `PathWayData` 内部化 + `NodeService`/`ChartService` 签名瘦身 + 枚举化；B4 `FlowStatusMachine` 转移表化（独立设计文档，见 §12.1 链接）。
- **阶段 C（清理）**：删除构造器/回写残留、R6、demo 与文档同步。

实施顺序 A → B → C：A 是 B 的地基，且 A 零契约风险可先行合入。

### 12.3 迁移清单

1. 获得明确的 2.0.0 版本决策后，再让仓库内全模块（4 个 starter、plugin-ui、plugin-modes）同版本联动；
2. `warm-flow-demo`、`docs/`、README 破坏性变更清单同步；
3. warm-flow-test 全量回归（不可省略，2.0 的唯一行为安全网）；
4. 若确实进入 2.0，再在独立变更中同步 AGENTS.md、README 和迁移指南；本设计文档本身不改变项目规则。

### 12.4 刻意不同时做（虽已解锁）

命名治理（`DefService`/`IWarmService`/`NOT_FOUNT`/`KenGen`）、`FlowEngine` 静态注册表 DI 化、存储层枚举化——与上下文改造无依赖关系，混入会使 2.0 的 diff 不可审，各自独立轨道。

## 13. 实施记录（Phase 1，2026-09-18）

### 13.1 落地内容

- **W1~W3**：`FlowExecution`（包内 final 类）落地，吸收 `TaskServiceImpl.R` 与 `FlowTaskContextLoader`（两者已删除，`getAndCheck` 两重载一并移除）；execute / terminate / updateHandlers / load / deleteByInsIds 走 `loadTask()`，revoke 走 `loadInstance()`，校验顺序逐行对齐原实现；变量合并 ×4 收敛为 `mergeVariables()`；ListenerVariable 构造收敛为 `rawListener` / `contextListener`（含 7 参重载），updateHandlers 的 finish 监听器保持不带 context 的既有语义（`rawListener`）。
- **W4**：R1 已落地——terminate 复用门面已加载的任务对象，任务主键查询 2→1；R4 评估后**不接线**（见 13.2）。
- **W5**：`NodeService.previousNodeList(String, FlowCombine)` 重载已新增（与 `suffixNodeList` 对称），R2 未接入任何调用点，`FlowPathResolver` 维持重查。
- **W6**：R3/R5 维持原查询，未做改动。

### 13.2 偏差记录

- `snapshotTasks()` / `tasksRemoved()` / `refreshTasks()` / `invalidateAfterCallback()` **未实现**：R4 的前提（同一次操作内两处同语句查询背靠背重复）被特征测试证伪——`oneVoteVeto` 仅在 reject 分支查询，`handUndoneTask` 仅在实例到达 end 节点时查询，二者互斥，同一操作内不会都执行（`RejectCharacteristicTest.locksTaskQueryBaseline_rejectPathQueriesOnce` 锁定 reject 路径任务列表查询恒为 ×1）。无 Phase 1 消费者，按 §5.2/W6 纪律不为数字引入写跟踪状态机。若未来出现 reject 直达 end 的跳转场景再评估。
- R1 的实现取 `FlowExecution.loadTask(task)` 复用门面已查对象（原稿建议改调 `getAndCheck(Task)` 重载；W2 迁移后该重载已随 `getAndCheck` 删除，语义等同，−1 SELECT 一致）。

### 13.3 实施中实测发现的行为（与原稿假设不同，已由测试锁定为基线）

1. execute 对 end 节点任务无前置拦截：skipType 缺失时抛 `NULL_SKIP_TYPE`，而非 `FLOW_FINISH`；
2. revoke 发起人校验仅在 `!context.isIgnorePermission()` 时执行（比对 `instance.getCreateBy()` 与 `context.getHandler()`）；撤回目标为开始节点的下一节点（apply），非开始节点本身；
3. updateHandlers 转办守卫语义＝目标办理人已是本任务受让人（TRANSFER）即拒绝（`IS_ALREADY_TRANSFER`）；
4. `oneVoteVeto` 与 `handUndoneTask`（多待办终止收尾）对残留待办均为**纯删除**：不产生历史记录（注释称"转历史任务"，实现不符）；
5. 空集合跳过 saveBatch：持久化顺序实测为 his 单条 save → deleteByIds → deleteByTaskIds → updateById → 收尾查询，无 `saveBatch[n=0]` 标记；
6. 会签（ratio=100）驳回走 `removeRestList` 立即生效并清退其余办理人，且**不产生签票历史**（仅流转历史）；首票通过则暂存（签票历史 + 剔除办理人 + 监听器只到 start 短路）；
7. 并行汇聚等待：先完成分支不推进实例（addTasks 为空时 `setInsFinishInfo` 仅更新时间与变量），汇聚判定把**办理中任务**计入活动前置（`activePreviousTasks` 在删待办前判定）；
8. start 不按 businessId 去重；发起后实例停留在 TOBESUBMIT、首待办为 APPROVAL（启动状态由调用方决定，不被首任务状态反向覆盖）；委派办理（受托人）短路回还办案权且不流转；
9. （第二批补充）引擎在变量合并点**原地写回** `context.getVariables()` 返回的 Map：调用方传 `Map.of` 等不可变 Map 会在合并点抛 `UnsupportedOperationException`——现状即契约，监听器/调用方需传可变 Map；
10. （第二批补充）网关条件引用的变量缺失时抛 `NULL_CONDITION_VALUE`（“跳转条件不能为空!”），而非按条件未命中回退默认出口；core 内置条件语法为 `类型@@变量|值`（`@@` 分隔类型、`|` 分隔变量与值）；
11. （第二批补充）委派期间办理权完全转移：待办办理人视图仅剩受托人，委托人 user 行不保留，受托人办理后才回还；
12. （第二批补充）票签表达式变量 `passNum` 口径 = 本次投票前已归档的通过票数（不含当前票）；`rejectCount=N` 的通过侧判据是 `已通过+1 > 总人数-N`（通过多数可提前结束）；
13. （第二批补充）`InsServiceImpl.unActive` 对已挂起实例重复挂起时，守卫消息复用 `INSTANCE_ALREADY_ACTIVITY`（“当前流程实例已经激活”，文案与场景不符，附录 B 已知问题，已由测试锁定）；
14. （第三批补充）`ChartStatus` 自定义颜色是 JVM 级静态累积状态：`initCustomColor` 只向静态 Map put、无清除入口，`WarmFlow.init()` 换配置或传空列表均无法撤销已设置的自定义色；取色回退顺序为 classics/mimic → 普通自定义 → 枚举默认色。测试 `warmFlowInitAndChartCustomColor` 触发该写入后对同 JVM 后续颜色断言构成顺序依赖（当前套件内无冲突），新增默认色断言时须置于该测试之前或改用独立 JVM。

### 13.4 测试资产与验证

- `warm-flow-core` 新增 `src/test`（用户批准的仓库约定偏离），JUnit 5（junit-jupiter，版本随 spring-boot-dependencies BOM；替换原 junit4 依赖）。纯 JVM 脚手架：内存 DAO 按 SQL 行语义实现（读返回列拷贝、userList 等瞬态字段随查询重置、save 回填 id、updateById 非空列合并）+ FrameInvoker 手工装配，无 Spring/DB。
- 57 个特征测试（覆盖 15 个测试类；改造前先锁定为绿，再实施 W1~W5，全程保持绿；唯一变化为 R1 授权的 terminate 查询 2→1）：
  - start 5：入参校验、定义查找/挂起、初始状态（实例 TOBESUBMIT/首待办 APPROVAL/发起历史/全局监听器序）、businessId 不去重；
  - execute 10：校验级联顺序、监听器序列与 userList 时序、持久化顺序、查询基线；
  - 会签 3 / 票签 2：首票暂存（签票历史+短路）、末票流转、会签驳回立即生效、通过率 50% 与 passCount=2 规则、未投人清退；
  - 委派 3：守卫、受托人办理回还短路（DEPUTE 双历史按 skipType 区分）、委托人续办流转；
  - 加减签与权限门 4：协作守卫（空对象/重复加签/最后一人不可减签）、NOT_AUTHORITY、协作历史落库；
  - 并行网关 3：分叉双待办、汇聚等待（含定义图重查基线——R2 接入的观测锚点）、末支汇合完成；
  - terminate 4 + 多待办收尾 2：单任务终止时序/终态/查询基线、多待办静默清理、无待办校验；
  - revoke 5：发起人门、终态拦截、撤回语义、双查询基线（R3）、无待办拦截；
  - reject 3：无驳回线校验、一票否决清理语义、驳回路径查询基线（R4 前提证伪依据）；
  - 路径跳转边界 3：直达 end、网关禁跳（TAR_NOT_GATEWAY）、开始节点禁跳；
  - 表单读取 3：load 无副作用查询锁、节点自定义表单返回、hisLoad 只读；
  - 实例级删除 3：deleteByInsIds 激活校验与清理、remove 全级联。
- 大改前加厚批次（当前 123 个 / 27 个测试类——回滚基线 122 个先跑在原始代码上锁定改造前行为，阶段 A 第二轮落地后随 R1 基线翻转与重载测试恢复为 123 个并保持全绿；JaCoCo 实测 core 行覆盖 49.6% → 86.6%、分支 36.0% → 66.2%；剩余缺口集中在 StringUtils、ListenerVariable、ClassUtil、ChartStatus、StreamUtils 与 UserType/PublishStatus/Page 等低风险工具面）：
  - 网关路由 5：互斥网关条件命中选线 / 未命中回退无条件默认出口 / 条件变量缺失抛 `NULL_CONDITION_VALUE`（不按未命中处理）；包容网关无条件出口恒激活 + 条件出口命中才加入。core 内置条件语法为 `eq@@变量|值`，条件策略在 `ExpressionUtil` 静态注册，无需 SPI；
  - 票签规则 3：`rejectCount=N` 驳回达数流转 + 通过票多数提前分支；`default@@` 表达式规则（注册 default 前缀票签策略桩），表达式可见 `passNum` = 本次投票前已归档的通过票数；
  - DefService 生命周期 12：空图发布拦截、发布挤掉同编码已发布兄弟版本（未使用→未发布、已使用→失效）、取消发布守卫、定义挂起/激活往返与守卫、删除级联（节点+连线）、复制全图且版本递增、copyDef 未知 id 抛 NPE（锁定附录 B 已知死校验缺陷）、insertFlow 版本生成（数字递增与非数字时间戳 `_1` 两分支）、getAllDataDefinition/queryDesign/exportJson→importJson 往返、saveDef 新增/全量更新/onlyNodeSkip/缺开始节点/无用连线校验；
  - workflow 命令门面 11：start/complete/reject/jump/transfer/delegate/terminate（实例级+任务级）/revoke/加签减签的 Command→Context 转换与结果视图（含批量办理人查询）、五类入参守卫、operator 缺省回退 PermissionHandler（经 FrameInvoker）；
  - 实例服务 4：实例激活/挂起往返与守卫（重复挂起消息复用 `INSTANCE_ALREADY_ACTIVITY`，附录 B 已知问题已锁定）、挂起实例阻断办理（`NOT_ACTIVITY`）、removeVariables 删键持久化、getByDefId/listByDefIds；
  - 条件表达式 6：eq/gt/ge/lt/le/ne/like/notLike 的数值与字符串语义、变量缺失抛错、空表达式与未知前缀返回 false、普通办理人字符串直传。
  - 第二批补充表单服务生命周期、节点 Combine 前置查询、办理人多类型查询、历史任务查询/按实例删除（7 个用例）。
  - API 覆盖补充：通用 `IWarmService` CRUD/批量/排序/分页、Def/Node/Form/HisTask/User 查询重载、FlowEngine 门面、ChartService、WarmFlow/SPI、三种 SnowFlake ID、工具类与枚举辅助方法、ApiResult/FlowPage 等 DTO 合约。
  - 当前验证命令已通过：`mvn -pl warm-flow-core test`（阶段 A 第二轮落地后 123/123）；此前 `mvn -pl warm-flow-orm/warm-flow-mybatis/warm-flow-mybatis-core -am -DskipTests compile` 与仓库根 `mvn clean install -DskipTests`（21 模块 BUILD SUCCESS）亦已通过。
- 未尽事项（已了结）：R2/R3/R5 原拟等 warm-flow-test 或 demo+H2 环境补齐等价性门控后评估接入——2026-09-18 经用户指示直接接入，等价性以操作快照语义重定义并由特征测试锁定（见 §13.6）；跨生态行为回归仍依赖 warm-flow-test。

### 13.5 第二轮实施记录（2026-09-18，经计划批准）

- **背景**：第一轮实施曾应用户要求回滚（补丁备份随后被用户删除），在 122 个特征测试锁定改造前行为后，经实施计划批准（范围裁定：只做阶段 A，B4 仅设计）从零重写。
- **落地内容**：`FlowExecution`（包内 final 类）重建——`loadTask(Task, WorkflowContext)` 逐条镜像原 `FlowTaskContextLoader` 七步校验序；`loadInstance` 逐条镜像 `revokeInternal`「默认状态仅空时补 CANCEL → 实例 → 变量合并 → 定义 → 激活 → 终态 → 结束节点」次序；`loadCombine()`（revoke 用，含定义）/ `loadCombineNoDef()`（execute 用）按既有调用点各自懒加载一次；`mergeVariables()` 收敛 4 处合并点并保持向 intent 原地写回；`rawListener` / `contextListener`（含 7 参重载）工厂替换 core 内 10 处构造（`load` 读取路径无 intent，保留 1 处内联构造）。`R` 与 `FlowTaskContextLoader` 删除；execute / terminate / updateHandlers / load 走 `loadTask`，revoke 走 `loadInstance`。
- **与第一轮的刻意差异（瘦身）**：不再实现 `snapshotTasks` / `tasksRemoved` / `refreshTasks` / `invalidateAfterCallback`（§13.2 已证 R4 无消费者，不为数字引入死脚手架）；`loadTask` 增加 intent 参数（§3.2 骨架原为单参——intent 归属执行作用域，读取路径传 null）。
- **R1**：terminate 复用门面已加载任务对象，任务主键查询 2→1（`TerminateCharacteristicTest` 基线随翻转为 1 次）。R2/R3/R5 维持原查询；`previousNodeList(String, FlowCombine)` 重载恢复新增，仍无调用点接入（`FlowPathResolver` 维持重查）。
- **验证**：`mvn -pl warm-flow-core test` 123/123；`mvn -pl warm-flow-orm/warm-flow-mybatis/warm-flow-mybatis-core -am -DskipTests compile`；仓库根 `mvn clean install -DskipTests`（21 模块）。实施计划存档于 `docs/superpowers/plans/2026-09-18-flow-execution-phase-a.md`。

### 13.6 R2/R3/R5 接入记录（2026-09-18，用户指示「继续接入」）

- **接入内容**：
  - **R2**：`FlowPathResolver.retainJoinPath` 汇聚前置判定改走 `previousNodeList(nodeCode, flowCombine)`，复用操作内定义图（`getFlowCombineNoDef` 与 `prefixOrSuffixNodes(definitionId,…)` 装配的是同一对 `getByDefId` 查询，数据面等价，唯一差异即查询时点）。汇聚路径节点全量查询 3→2、连线 2→1。
  - **R3**：`revokeInternal` 撤回清理复用监听器执行前的待办快照（`taskList`），删除回调后的二次任务列表查询（2→1）；`NOT_FOUND_FLOW_TASK` 校验保留在原位置，改判快照。
  - **R5**：`FlowExecution` 新增 `loadTaskUsers()`（办理人全集懒加载，execute 路径与 `task.userList` 共享同一引用）与 `usersOfTypes(String...)`（类型视图内存派生）；`TaskCooperationHandler.cooperate` 待办视图、`updateHandlersInternal` 四个协作守卫（`usersOfProcessedBy` 辅助）与权限门全部改从全集派生。按类型/按办理人条件查询不再出现：updateHandlers 办理人查询 2→1，会签计数路径 2→1。
- **语义变化（有意，用户授权）**：三处原为「回调后重查」，接入后统一为「操作内快照固定复用」——监听器在 start 触发点中途写库（新增待办/办理人/定义图分支）不再进入本次操作视图。由此路由、汇聚判定、权限校验、会签计数、撤回清理在同一操作内使用同一份数据（改造前路由用快照、汇聚/计数/清理用重查，本身不一致）。新语义由 `ExecutionSnapshotCharacteristicTest` 三个用例锁定（revoke 清理快照 / 会签计数快照 / 汇聚判定快照），每例带前提断言证明中途写库确已发生，杜绝空转通过。
- **测试对账**：三处查询基线随接入翻转——`ParallelGatewayCharacteristicTest`（节点 3→2、连线 2→1）、`RevokeCharacteristicTest`（任务列表 2→1）、`UpdateHandlersCharacteristicTest`（按类型查询基线翻转为无类型全集派生）；其余 120 个测试零改动通过。
- **验证**：`mvn -pl warm-flow-core test` 126/126；仓库根 `mvn clean install -DskipTests`（21 模块 BUILD SUCCESS）。跨生态（MyBatis/MyBatis-Plus 真库排序、租户、逻辑删除）回归仍依赖 warm-flow-test。
- **测试基建**：`RecordingGlobalListener` 构造器 private→protected（可子类化）；新增 `MutatingGlobalListener`（start 触发点库写钩子）。注意：harness 构造时即把全局监听器固化进 `FlowEngine` 字段，替换注册后必须重跑 `FlowEngine.initGlobalListener(null)` 才生效（本轮 R5/R2 用例曾因此空转通过，由 R3 用例戳穿后修正）。

## 附录 A：Phase 2 展望（步骤链）

> 2026-09-18 更新：P2a+P2b+P2c（execute/revoke/terminate/updateHandlers/start 五操作链化）已全部实施——[2026-09-18-flow-phase2-step-chain-design.md](./2026-09-18-flow-phase2-step-chain-design.md) §7 落地记录（含 §7.4 start 接入与 `InsServiceImpl:85-89` 手工拷贝消亡）。

- 形态为**有序流水线 + 显式短路**（Servlet Filter / Netty Pipeline 式），不是经典 GoF 责任链——委派（`:94`）与协作（`:102`）是短路点，其余步骤必经且有序，顺序即状态机契约；
- 前置条件：Phase 1 的 `FlowExecution` 先落地，否则只是把 600 行类稀释成 20 个各自查库的 Step 文件；
- 步骤保持粗粒度（6~7 个）、包内可见、**不新增公共扩展点**（引擎已有 Listener/表达式/网关三套 SPI，开放内部步骤等于新增永久契约）；
- `InsService.start`（无 task 的操作）与其手工 context 拷贝（`InsServiceImpl.java:85-89`）在 Phase 2 一并接入。

## 附录 B：关联诊断（2026-09-18 全库体检摘要）

Phase 1 之外已确认的问题及处置状态：

- ✅ **已修复（2026-09-18，同日第二批小修）**：
  - `DefServiceImpl.copyDef` 死校验（NPE 先于断言）——判空提前到 `copy()` 解引用前，未知 id 现报 `NOT_FOUNT_DEF`；原锁定 NPE 的测试随翻转为 `copyDef_unknownId_throwsNotFoundDef`（§13.4 批次描述中的「抛 NPE」为历史记录）；
  - sqlserver 脚本死列 `handler_type`/`handler_path`（实体在 1.8.4 已删字段，MySQL 有对应 DROP 升级脚本，sqlserver 全量脚本漏同步）——删除 `flow_node` 两列定义及其 `sp_addextendedproperty` 注释块；既有 sqlserver 库中的两列为无映射死列（全 NULL），可留可手动 DROP，无需新升级脚本；
  - 4 个 starter `FlowAutoConfig`（mybatis/mybatis-plus × sb3/sb4）补 Apache 2.0 license header。
- ✅ **已修复（2026-09-18，第三批）**：`FlowEngine` 死缓存（`FlowEngine.java:42-51`）——定性为「想做缓存但从未生效」的脚手架：10 个 `static final = null` 字段使 `getObj` 快路径分支永不生效，每次取服务实际都走 `FrameInvoker.getBean`。修复取**死代码清除**而非真缓存（真缓存会在重注册场景复刻 globalListener 固化坑）：删除 10 个死字段，10 个访问器直连 `FrameInvoker.getBean`，行为逐字节等价（`getObj(null, X)` ≡ `getBean(X)`）；`getObj` 为公共 API 且语义自洽（候选非空即用），保留并补 javadoc。
- ✅ **已修复（2026-09-18，第四批，经用户批准）**：`InsServiceImpl.unActive:236` 错误常量——重复挂起改报新增常量 `INSTANCE_ALREADY_SUSPENDED`（「当前流程实例已经挂起」，与既有 `DEFINITION_ALREADY_SUSPENDED` 命名对齐），锁定测试随翻转；`INSTANCE_ALREADY_ACTIVITY` 语义回归「已经激活」单义。
- ⏳ **仍挂账**：sb3/sb4 starter 99% 复制（结构性去重，另行轨道）。

