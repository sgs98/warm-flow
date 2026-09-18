# Phase 2 步骤链设计

- 日期：2026-09-18
- 状态：**P2a+P2b+P2c 全部已实施**（2026-09-18，经实施计划批准，用户裁定 P2a/P2b 一批做、随后指示继续 P2c）——落地记录见 §7，含对决策 1/决策 4 的实施修订
- 范围模块：warm-flow-core
- 风险级别：L2（execute/revoke/terminate/updateHandlers/start 全部编排路径）
- 前置依赖：Phase 1（`FlowExecution` 执行作用域 + R1/R2/R3/R5 + B4-v1 guard 表）——已落地（commit a9b6424）
- 上游背景：[2026-09-18-flow-execution-design.md](./2026-09-18-flow-execution-design.md) 附录 A

## 1. 问题

Phase 1 收敛了「一次操作取什么数据」，但编排本身仍是巨石：

- `executeInternal`（TaskServiceImpl）单方法内联 8 类职责：变量合并/skipType 校验 → start 监听器 → 委派短路 → 权限 → 协作短路 → 路由解析 → 建任务/assignment 监听器 → 持久化/一票否决/收尾/finish 监听器；
- revoke/terminate/updateHandlers 各自手写「合并 → 监听器 → 权限/守卫 → 业务 → 持久化 → 收尾」骨架，靠复制对齐；
- `InsServiceImpl.start`（无 task 操作）游离于执行作用域之外，`InsServiceImpl:85-89` 手工逐字段拷贝 `WorkflowContext`——Phase 1 已列入本阶段处理。

## 2. 目标形态

**有序流水线 + 显式短路**（Servlet Filter / Netty Pipeline 式），不是经典 GoF 责任链——附录 A 的裁定仍然有效：委派与协作是短路点，其余步骤必经且有序，**顺序即状态机契约**。

```java
/** 包内步骤接口，无状态（可单例），状态全在 FlowExecution。 */
interface FlowStep {
    /** 执行本步骤；返回短路结果则终止链并透传给调用方。 */
    Optional<Instance> execute(FlowExecution execution);
}

/** 包内编排器：按操作装配的不可变步骤列表，逐个执行至短路或链尾。 */
final class FlowPipeline {
    static FlowPipeline executeChain();   // 办理链（含委派/协作短路点）
    static FlowPipeline revokeChain();    // 撤回链
    static FlowPipeline terminateChain(); // 终止链
    static FlowPipeline startChain();     // 发起链（P2c 接入）
    Instance run(FlowExecution execution);
}
```

步骤保持**粗粒度（6~8 个/操作）**，execute 链候选切分（边界在实施计划中定稿）：

| # | 步骤 | 现位置（executeInternal） |
|---|---|---|
| 1 | 准备（变量合并、skipType 必传校验、taskUsers 进作用域、combine 加载） | `:81-88` |
| 2 | start 监听器 | `:91` |
| 3 | 委派短路（handleDepute） | `:94-96` |
| 4 | 权限（checkAuth） | `:99` |
| 5 | 协作短路（cooperate：或签/会签/票签） | `:102-104` |
| 6 | 路由（resolve + defJson 元数据） | `:107-112` |
| 7 | 建任务（addTasks + 表达式办理人 + assignment 监听器） | `:115-123` |
| 8 | 持久化与收尾（updateFlowInfo + 一票否决 + handUndoneTask + finish 监听器） | `:126-134` |

## 3. 关键决策

1. **步骤无状态**：所有可变状态进 `FlowExecution`（Phase 1 已备好字段位）；步骤实例可复用，不为每次操作新建。
2. **短路语义显式化**：`Optional<Instance>` 非空即短路（委派、协作、会签暂存三处现状短路点），链终止并把实例透传给调用方——保持「短路路径返回 instance」的既有行为。
3. **不新增公共扩展点**：`FlowStep`/`FlowPipeline` 包内私有。引擎已有 Listener/表达式/网关三套 SPI 作为正门，开放内部步骤等于新增永久契约（附录 A 红线）。
4. **start 归一**：P2c 把 `InsService.start` 接入实例级 `FlowExecution`（loadInstance 同款入口但不伪造 task），`InsServiceImpl:85-89` 手工 context 拷贝随链化自然消亡。
5. **guard 表不动**：B4-v1 的 `FlowOp.checkGuards` 仍是加载期前置，步骤链是加载后的编排层，两层正交。
6. **JDK17**：无模式匹配 switch、无 record 透传公共面（包内 record 可用但不必须）。

## 4. 分阶段实施（每阶段独立可验收）

| 阶段 | 内容 | 验收门 |
|---|---|---|
| P2a | execute 链化（最大风险面，先行孤立验证） | 特征测试全绿 + 新增链序锁定测试 |
| P2b | revoke / terminate / updateHandlers 链化 | 同上 |
| P2c | start 接入实例级 execution + 手工拷贝消亡 | 同上 |

## 5. 不变量（验收标准）

- 公共签名零变化；`FlowStep`/`FlowPipeline` 包内私有；
- 异常消息与触发顺序不变（`TaskExecuteCharacteristicTest` 等锁定）；
- 监听器触发次数/时机不变（start 前设置 userList、terminate start 后设置等）；
- 持久化顺序不变（his 插入 → 删待办 → 删办理人 → setInsFinishInfo → 插待办 → 更新实例 → 插办理人）；
- 查询数与 R2/R3/R5 接入后的基线一致（操作内快照固定复用语义不回退）；
- 短路路径返回 instance 行为照旧。

## 6. 风险与收益（如实）

- **主要收益是可读性与可测试性**（巨石方法 → 可单测的有序步骤）+ start 归一，**非性能**——查询数、分配数基本不变；
- 最大风险：execute 链拆分时步骤边界切割错位（如 assignment 监听器与建任务的引用共享）——以既有特征测试为安全网，P2a 单独成批；
- 附录 A 警告「把 600 行类稀释成 20 个各自查库的 Step 文件」已被 Phase 1 解除（取数收口在 FlowExecution），步骤只做编排不做取数。

## 7. 实施记录（P2a+P2b，2026-09-18）

### 7.1 落地形态与对决策 1 的修订

- **每操作一个链装配类**而非每步骤一个文件：`FlowExecuteChain`（8 步）、`FlowRevokeChain`（6 步）、`FlowTerminateChain`（5 步）、`FlowUpdateHandlersChain`（5 步），步骤为链类私有方法、经方法引用装配进 `FlowPipeline.of(...)`——步骤名序列即链序的可执行文档，共 6 个新文件而非 20+。
- **【修订决策 1】操作参数（execute 的 skipType、updateHandlers 三元组）与链内工作段（pathWayData/nextNodes/addTasks、revoke 的 taskList/nodeMap）由每次调用新建的链实例字段承载**，不进 FlowExecution——后者保持阶段 A 的「聚合加载」单一职责，避免四操作的参数差异泄漏进共享作用域（updateHandlers 三参数在 execution 上无处安放即是信号）。代价：链实例不可复用、非线程安全、禁止静态化/缓存（四个链类 javadoc 显式锁死，不提供 static 工厂）；「步骤无状态」承诺以「步骤不跨操作携带状态」的形式保持。
- 机制层：`FlowStep`（包内接口，`Optional<Instance> execute(FlowExecution)`，非空即短路）+ `FlowPipeline`（`of(FlowStep...)` + `run`——首个非空短路透传，链尾返回 `execution.instance`；不捕获异常，短路用 `Optional.of` 非 `ofNullable`）。
- guard 表不动（加载期前置与链编排正交）；`TaskServiceImpl` 四入口瘦身为「断言 + 加载 + new 链 + run」，四个 internal 巨石方法删除；`oneVoteVeto` 随迁入办理链、`usersOfProcessedBy` 随迁入办理人调整链；`checkAuth`/`handUndoneTask`/`removeAndUser` 恰三个 private → 包内可见（压测核实无遗漏无多余）。

### 7.2 压测锁定的实施约束（独立全库核对后写入代码注释）

1. **别名方向相反**：execute 的 nextNodes 必须与 `pathWayData.getTargetNodes()` 同一引用（`retainJoinPath` 会 clear 后 addAll，下游可见清空后状态）；revoke 的 nextNodes 必须单独保存 `getNextByCheckGateway` 返回值（与 path 目标列表内容相等但对象不同）——两处禁止统一处理。
2. **updateHandlers 的 authGate 禁复用 checkAuth**：该操作从不 `setUserList`，`task.userList` 为 null 时 checkAuth 会静默放行且权限常量不同（NOT_AUTHORITY vs NULL_ROLE_NODE），:360-368 原样内联。
3. **加载时点**：execute 的 prepare 在 start 监听器前显式触发 `loadCombineNoDef()` 缓存（route/一票否决处取缓存引用）；updateHandlers 守卫顶部无条件 `loadTaskUsers()`（守卫失败路径查询数也保持）。
4. **revoke 的 persist 禁复用 `TaskHistoryHandler.updateFlowInfo`**：整表快照归档 vs 单任务归档、updateById 无 NOT_FOUNT_INSTANCE 断言，形似实异。
5. **terminate 的 authGate 改用 `execution.loadTaskUsers()`**（唯一等价改写）：同懒加载首次触发、同时点，注释「本链唯一办理人加载点」——未来新增派生读点会引入快照语义。
6. **现存边角原样搬运**：`getNextByCheckGateway` 可返回 null 时 `addAll(null)` NPE 是现存失败模式（不加判空）；委派/协作的 `!isIgnore() && handler.xxx` 求值顺序保留；`addTasks` 被 `setInsFinishInfo.removeIf` 原地删除后 endCreate 监听器看到删后同引用列表。

### 7.3 验证

- `mvn -pl warm-flow-core test` **135/135**：既有 132 个特征测试零修改通过（行为不变的核心证据）+ 新增 `FlowPipelineCharacteristicTest` 机制 3 用例（短路截断/链尾返回/空链）；
- `mvn -pl warm-flow-orm/warm-flow-mybatis/warm-flow-mybatis-core -am -DskipTests compile` 与仓库根 `mvn clean install -DskipTests`（21 模块）均通过；
- 异常堆栈顶层帧由 TaskServiceImpl 变为链类——测试只断言 getMessage，契约不受影响；跨生态回归仍依赖 warm-flow-test。

### 7.4 P2c 实施记录（start 接入，2026-09-18 用户指示「继续实施后续计划」）

- **落地形态（对决策 4 的实施修订）**：start 不走「loadInstance 同款入口」——发起期实例尚不存在，加载期无实例聚合可载。改为 `FlowExecution.loadStart(flowCode, intent)`（定义判空 → 定义图 → 开始节点 → 定义激活，逐条镜像原 `InsServiceImpl.start` 校验序；挂起守卫只看定义，消息为 NOT_DEFINITION_ACTIVITY，不共用 guard 表行）+ `FlowStartChain` 6 步（开始监听器 → 路由 → 实例与历史 → 建首待办 → 元数据与分派 → 持久化与完成）。
- **`instance` 字段放开 final**：`FlowExecution.instance` 改为包内可写——start 链在实例创建步骤回填一次（此前 start 监听器看到 null 实例、此后 assignment/endCreate 看到已创建实例，与原实现各监听点的实例可见性逐点一致）；其余四操作仍在构造期固定、无写入点。
- **手工拷贝消亡**：原 `InsServiceImpl:85-89` 的 taskContext 四字段拷贝删除——`addTask` 只读取 `instanceStatus`（逐字段拷贝与直传调用方上下文等值），改为直传 `intent`；办理链本就直接传 context，start 侧的防御性拷贝属冗余。
- **随迁**：`setStartInstance`/`setHisTask`/`saveFlowInfo` 三个私有助手移入 FlowStartChain；start 的 3 处内联 `ListenerVariable` 构造收敛为 `execution.contextListener(...)`（Phase 1 遗留的最后 3 处内联构造消失）；InsServiceImpl.start 瘦身为「断言 + loadStart + new 链 + run」。
- **完成结果约定**：start 链最后一步以 `Optional.of(instance)` 作为完成结果（实例为链内创建，链尾兜底 `execution.instance` 虽回填后可用，仍以显式返回为准）。
- **验证**：`mvn -pl warm-flow-core test` 135/135（既有测试零修改）；下游 mybatis-core 编译与 21 模块 install 通过。
