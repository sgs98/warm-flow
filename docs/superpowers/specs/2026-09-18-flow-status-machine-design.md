# FlowStatusMachine 转移表设计（2.0 候选 · B4）

- 日期：2026-09-18
- 状态：**v1（guard 半边）已实施**——经研究（见 §6）与用户批准，guard 表化以行为不变改造落地（`FlowOp` + `FlowStatusMachine.checkGuards`），不按 2.0 门控；裁决半边（`taskStatus`/`skipStatus`/`defaultStatus`/`customStatus` 表化）与 `FlowOp` 公开化维持 2.0 候选。实施记录见 §7
- 范围模块：warm-flow-core
- 风险级别：L2（流程状态机语义）
- 前置依赖：阶段 A（`FlowExecution` 执行作用域）先落地——已落地

## 1. 现状

`FlowStatusMachine`（`service/impl/FlowStatusMachine.java`，包内私有）目前是静态工具类而非状态机：

| 成员 | 职责 |
|---|---|
| `TERMINAL_STATUS` + `isTerminal` | 5 个标准终态的集合判断（FINISHED / TERMINATE / NULLIFY / CANCEL / INVALID） |
| `customStatus` | 历史任务自定义状态优先于流程实例状态——业务自定义状态透传的现有通道 |
| `taskStatus(nodeType, skipType)` | if 链裁决新待办任务默认状态（开始→TOBESUBMIT / 结束→FINISHED / 驳回→REJECT / 其余→APPROVAL） |
| `skipStatus` / `defaultStatus` | 自定义状态优先的默认值裁决 |

操作合法性 guard 散落在各 `*Internal` 编排方法中（如 `revokeInternal:166-168` 的激活态 / 终态 / 结束节点三连断言、`terminateInternal:289` 的默认状态裁决），同一语义的判断在多个操作之间靠复制保持一致，新增操作时无机制保证对齐。

## 2. 目标形态（B4 转移表化）

把 guard（操作合法性）与默认状态裁决收拢为声明式转移表：

- **维度**：`FlowOp`（execute / revoke / terminate / updateHandlers / skip …）× 出发状态（实例 flowStatus，含 `nodeType` × `skipType` 派生维度）；
- **表项**：guard（该操作是否允许从该状态出发）+ 默认状态裁决（目标 task / his / instance 状态）；
- **非法转移集中抛 `FlowException`**，异常文案与现有散落断言一致（验收标准）；
- **业务自定义状态透传不受影响**：`customStatus` 通道保留，表只裁决默认值，不闭集化状态空间。

## 3. 不引入外部 FSM 库

- **状态集开放**：业务自定义状态经 `customStatus` 透传，引擎侧不可枚举闭集；Spring Statemachine / Stateless4j / Squirrel 等通用 FSM 库的核心模型是闭集状态 + 内存事件分发；
- **转移数据驱动**：合法性取决于操作类型 × 节点类型 × 跳转类型 × 实例状态的组合，且表结构需随操作集演进；
- **副作用是事务性持久化**：转移触发的是 his / instance / task 多表事务写入，不是内存状态通知；
- **core 零依赖红线**：`warm-flow-core` 仅 slf4j + lombok；
- **行业先例**：Activiti / Flowable / Camunda / jBPM 的状态推进全部自研，无引入通用 FSM 库的先例。

## 4. JDK17 实现约束

switch 模式匹配在 JDK17 为 preview（21 才 final），实现限传统 switch / Map 表驱动 / sealed 类型（sealed 类本身在 17 已 final，可用）。

## 5. 授权与验收

本文仅为候选设计，不构成实施授权。实施前置：阶段 A（`FlowExecution`）落地 + 明确的 2.0 版本决策（范围、迁移窗口、发布批准）。验收标准：异常消息与触发顺序不变、guard 语义与现状逐条等价；warm-flow-test 全量回归不可省略。

> 2026-09-18 更新：v1（§7）拆分了授权边界——guard 半边为行为不变、零公共面的内部改造，按阶段 A 同级处理（经用户单独批准「做」后实施）；本节 2.0 门控继续约束裁决半边与 FlowOp 公开化。

## 6. 研究结论（2026-09-18，v1 实施前置）

- **阶段 A 已治愈一半病灶**：§1 所述「guard 散落靠复制保持一致」在阶段 A 后已大幅收敛——execute/terminate/updateHandlers/load 走 `FlowExecution.loadTask`、revoke 走 `loadInstance`，状态三连断言只剩两份拷贝；真正散落的仅 `deleteByInsIds` 内联激活断言。
- **guard 分类学**：按依赖输入分四类——存在性（NOT_FOUNT_*，与查询加载交织）、状态谓词（激活 ∧ 非终态 ∧ 非结束节点）、权限（NULL_ROLE_NODE 等）、操作参数（协作守卫 IS_ALREADY_* 等）。**仅状态谓词是转移表素材**；原计划附录把协作守卫列为迁移源属类别错误，v1 剔除。
- **实际矩阵近乎退化**：办理族五行（EXECUTE/TERMINATE/UPDATE_HANDLERS/LOAD/REVOKE）共享同一行；唯一离群行是 DELETE（仅要求激活——终态/已结束实例可删，清理已完成流程是合法场景）。表的价值不在收敛重复，而在把不对称与「load 读路径继承 execute 全套前置」从隐式行为变为显式契约。
- **skipType 不是 guard 维度**（PASS/REJECT 状态前置完全相同，skipType 只进裁决半边）；**不设 JUMP 枚举项**（跳转复用 EXECUTE 入口且状态前置无差异，路径守卫属图形状校验）。

## 7. v1 实施记录（guard 半边，2026-09-18，经计划批准）

- **落地内容**：新建包内 enum `FlowOp`（EXECUTE/TERMINATE/UPDATE_HANDLERS/LOAD/REVOKE/DELETE，含范围裁定注释）；`FlowStatusMachine` 新增 `StateGuard`（ACTIVITY/NOT_TERMINAL/NOT_END，声明序即断言序）、`GUARDS` 表（`EnumMap<FlowOp, EnumSet<StateGuard>>`，六行显式 put，DELETE 为离群行）与 `checkGuards(op, definition, instance)`/`apply(guard, ...)`（箭头 switch，三连断言表达式逐字保留）；`FlowExecution.loadTask` 增加 `FlowOp` 参数、`loadInstance` 固化 REVOKE，两处三连断言改表调用；`TaskServiceImpl` 的 `getAndCheck` 两重载透传 FlowOp（execute/terminate/updateHandlers/load 四调用点）、`deleteByInsIds` 改调 `checkGuards(DELETE, ...)`，死助手 `judgeActivityStatus` 删除。
- **行为不变证据**：接线完成后既有 126 个特征测试零修改全绿（异常消息/顺序/查询数全部锁定）；矩阵测试新增后 131/131，仓库根 `mvn clean install -DskipTests` 21 模块 BUILD SUCCESS。
- **测试资产**：新增 `FlowStatusMachineCharacteristicTest` 四用例——办理族矩阵逐格（挂起×2 / 5 终态 / 结束节点 / 正向格）、DELETE 离群行（终态/end/终态+end 放行）、表覆盖完整性（缺行即 NPE）、断言序（挂起+终态 → NOT_ACTIVITY 优先）；`InstanceCleanupCharacteristicTest` 新增端到端用例锁定「终态实例不被删除拦截」（办理至 end 后 deleteByInsIds 放行，无待办行可删按行数语义返回 false）。
- **裁决半边未动**：`taskStatus`/`skipStatus`/`defaultStatus`/`customStatus` 一行未改，维持 2.0 候选；`FlowOp` 保持包内私有。存在性校验留在 loader 内联（与查询加载交织，非状态谓词）。
- 实施计划存档：[2026-09-18-flow-status-machine-guard-table.md](../plans/2026-09-18-flow-status-machine-guard-table.md)。未 commit。
