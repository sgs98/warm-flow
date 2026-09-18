# FlowExecution 阶段 A 实施计划（行为不变改造）

- 日期：2026-09-18
- 状态：**已执行并验证通过**（123/123 特征测试全绿；下游 mybatis-core 编译与 21 模块 `mvn clean install -DskipTests` 均通过；落地偏差见文末执行结果）
- 上游设计：[2026-09-18-flow-execution-design.md](../specs/2026-09-18-flow-execution-design.md)（§13.5 为本轮实施记录）
- 范围裁定：经用户确认**只做阶段 A**（执行作用域 + R1 终止查询去重）；B4 FlowStatusMachine 转移表化本轮仅设计附录，2.0 版本决策另起。

## Context

特征测试安全网已就位（**122/122 全绿**，core 行覆盖 86.6%、分支 66.2%，跑在 HEAD 原始代码上）。用户授权进入实施。背景事实：

- 阶段 A 曾实施过一轮后应用户要求回滚（超出当时授权）；回滚备份补丁已被用户删除——**本轮从零重写，不恢复旧实现**。
- 设计文档 §13 保留了第一轮的实施/偏差记录，是本轮最重要的经验输入：`snapshotTasks`/`tasksRemoved`/`refreshTasks`/`invalidateAfterCallback` 当时因无消费者未实现（R4 前提被特征测试证伪），**本轮直接不建这套死脚手架**，类比第一轮更瘦。

## 硬性不变量（每步完成前后都必须成立，由 122 个特征测试把关）

- 公共签名零变化；唯一 API 增量：`NodeService.previousNodeList(String, FlowCombine)` 重载（W5）。
- 异常消息与**触发顺序**不变（校验序由 `TaskExecuteCharacteristicTest`/`RevokeCharacteristicTest` 等锁定）。
- 监听器触发次数/时机不变：execute 在 start 监听器**前**设置 `task.userList`，terminate 在 start 后、finish 前设置。
- 持久化顺序不变：his 插入 → 删待办 → 删办理人 → `setInsFinishInfo` → 插待办 → 更新实例 → 插办理人。
- 引擎在合并点**原地写回** `context.getVariables()` 的 Map（设计 §13.3 第 9 条契约，不可"顺手"改成替换新 Map）。
- `load(taskId)` 保持表单读取路径无副作用查询（`FlowLoadCharacteristicTest` 锁定；load 调用点构建的 ListenerVariable 不带 context——rawListener 语义）。
- core 红线：不 import `org.springframework.*`/`com.baomidou.*`；新文件带 Apache 2.0 header + `@author warm` + `org.dromara.warm` 包名；JDK 17 语法（无 Java 18+，无 preview 特性）。

## 实施步骤（每步独立可编译、测试保持绿）

### W1 新建 FlowExecution（唯一新文件）
`warm-flow-core/src/main/java/org/dromara/warm/flow/core/service/impl/FlowExecution.java`，包内 final 类，零公共面：

- 字段（仅身份段+一次加载段）：`task`/`instance`/`definition`/`nowNode`/`intent`(WorkflowContext)/`combine`(FlowCombine)。不建 taskUsers/snapshot/path 工作段（无消费者）。
- `static FlowExecution loadTask(Task task, WorkflowContext intent)`——逐条镜像 `FlowTaskContextLoader.load` 的 7 步校验序：
  task null→`NOT_FOUNT_TASK`；instance getById→`NOT_FOUNT_INSTANCE`；definition getById→`NOT_FOUNT_DEF`；双重激活→`NOT_ACTIVITY`；终态→`FLOW_FINISH`；结束节点→`FLOW_FINISH`；nowNode getByDefIdAndNodeCode→`LOST_CUR_NODE`。
- `static FlowExecution loadInstance(Long instanceId, WorkflowContext intent)`——逐条镜像 `revokeInternal` 实例级校验序：默认状态仅空时补写 CANCEL → instance 查询/判空 → 变量合并（`MapUtil.mergeAll` + `context.setVariables` 回写）→ definition 查询/判空 → `judgeActivityStatus` → 终态 → 结束节点。**不**自动加载待办列表——revoke 的两次任务查询仍由原编排点分别发起（R3 不动）。
- `FlowCombine loadCombine()` / `loadCombineNoDef()`——一次操作内只加载一次，操作内复用同一引用（现状语义，设计 §5.1 唯一口径）；**不因监听器回调失效也不重载**。
- `Map<String,Object> mergeVariables()`——在既有 4 个合并点显式调用，保持 `context.setVariables(merged)` 写回。
- `ListenerVariable rawListener(...)` / `contextListener(...)`（含 7 参重载）——**每次调用必须新建实例**（`endCreateListener` 以游标方式原地修改传入对象）；列表引用不复制、原地修改时机不变。

### W2 迁移调用点，删除 R 与 FlowTaskContextLoader
- `getAndCheck(Long)` / `getAndCheck(Task)` 两重载改返回 `FlowExecution`（内部改走 `FlowExecution.loadTask`）。
- 调用点迁移：execute、terminate、updateHandlers、load；revoke 改走 `loadInstance`，**不**伪造唯一 task。
- 删除 `static class R` 与 `FlowTaskContextLoader.java`；`updateHandlersInternal` 等内部方法签名 `R r` → `FlowExecution execution`。
- 全程 `mvn -pl warm-flow-core test`：**122/122 必须保持绿**。

### W3 变量合并与监听器工厂接线
- 4 个 `MapUtil.mergeAll` 散点收敛为 `execution.mergeVariables()`（revoke 的在 loadInstance 内）。
- core 内 `new ListenerVariable(...)` 构造改走 `rawListener`/`contextListener` 工厂；updateHandlers 的 finish 监听器保持**不带 context** 的既有语义；load 的无 context 构造保留内联（无 intent 可传）。
- 契约对照：`TaskExecuteCharacteristicTest`（监听器序列与 userList 时序）、`TerminateCharacteristicTest`（start 前 taskUsers=false）必须原样绿。

### W4 R1：terminate 查询去重（唯一行为观测变化）
- `terminateByTaskId`：`getById(taskId)` 后改传已加载对象 `FlowExecution.loadTask(task, context)`，去掉内部第二次主键查询。
- 任务主键 selectById 基线 2→1。

### W5 previousNodeList Combine 重载（API 增量，R2 不接线）
- `NodeService` + `NodeServiceImpl` 新增 `previousNodeList(String nodeCode, FlowCombine combine)`，与既有 `suffixNodeList(String, FlowCombine)` 对称，实现复用 combine 内的节点+连线列表。
- `FlowPathResolver` 维持重查现状，**不改任何调用点**（R2 等价性门控未过，永不默认启用）。

### W6 R3/R5：无改动
revoke 双查询、按类型用户查询维持原样（有特征测试锁基线，防"顺手优化"）。

### W7 测试对账（两个触点，其余测试一行不动）
1. `TerminateCharacteristicTest.happyPath_locksDoubleTaskQueryBaseline`：断言 2→1，注释改为「R1 后基线：门面查询后直接复用任务对象」。
2. `QueryServiceCharacteristicTest` 恢复 `previousNodeList_withCombine_matchesDefinitionReloadPath` 测试（W5 重载 vs 定义图重查路径结果一致，均含 a1/b1）及相关 import。
3. 全量：**123/123 绿**。

## 验证方式
1. 每步：`mvn -pl warm-flow-core test`（阶段 A 期间 122 绿；W7 后 123 绿）。
2. 终验：`mvn -pl warm-flow-orm/warm-flow-mybatis/warm-flow-mybatis-core -am -DskipTests compile`（core 改动至少编译一个下游 orm 模块）。
3. 全仓：`mvn clean install -DskipTests`（21 模块 BUILD SUCCESS）。
4. 行为抽检锚点（均已有测试）：terminate 监听器时序、revoke 发起人门、execute 持久化顺序、load 无副作用查询、会签/票签路径。

## 涉及文件清单
| 动作 | 文件 |
|---|---|
| 新建 | `warm-flow-core/src/main/java/org/dromara/warm/flow/core/service/impl/FlowExecution.java` |
| 删除 | `warm-flow-core/src/main/java/org/dromara/warm/flow/core/service/impl/FlowTaskContextLoader.java` |
| 修改 | `TaskServiceImpl.java`（R 删除、getAndCheck→FlowExecution、4 合并点、监听器工厂、R1） |
| 修改 | `NodeService.java` / `NodeServiceImpl.java`（仅 W5 重载） |
| 修改 | `TerminateCharacteristicTest.java` / `QueryServiceCharacteristicTest.java`（W7 两触点） |
| 修改 | 设计文档状态行与 §13.5；本计划落档 |

## B4 设计附录（本轮不写码，2.0 决策后另启）
- `FlowOp` 包内 enum：EXECUTE / REVOKE / TERMINATE / UPDATE_HANDLERS / COOPERATE（加减签）/ JUMP；REJECT 经由 skipType 派生维度。
- guard 迁移源（三个）：`FlowExecution.loadTask` 的 7 步任务级序（阶段 A 后所在位置）、revoke 实例级序、协作守卫（updateHandlersInternal 起）。
- 表结构：`FlowOp × (任务级|实例级) × 状态谓词(激活/终态/结束节点)` → `ALLOW | DENY(ExceptionCons 常量)`，Map/switch 表驱动（JDK17 无模式匹配 switch）。
- 状态裁决半边（taskStatus/skipStatus/defaultStatus/customStatus）首版**保持原样**，表只吸收 guard；customStatus 透传通道不闭集化。
- 验收：既有异常消息测试全绿 + 新增矩阵枚举测试逐格断言。

## 执行结果（2026-09-18）

全部步骤按计划完成，偏差两条（均已记入设计文档 §13.5）：

1. **计划 vs 实际调用点**：计划 Context 段提到「deleteByInsIds 路径」，实际 `deleteByInsIds` 自有内联校验，不在 `getAndCheck` 迁移面内；实际迁移调用点为 execute / terminate / updateHandlers / load 四处 + revoke（loadInstance）。
2. **loadTask 签名**：计划骨架沿用设计 §3.2 的单参 `loadTask(Task)`，落地为 `loadTask(Task, WorkflowContext)`——intent 归属执行作用域，读取路径（load）传 null。

验证证据：
- `mvn -pl warm-flow-core test`：W2+W3 后 122/122，W4 后 122/122，W7 后 **123/123**；
- `mvn -pl warm-flow-orm/warm-flow-mybatis/warm-flow-mybatis-core -am -DskipTests compile`：BUILD SUCCESS；
- 仓库根 `mvn clean install -DskipTests`：21 模块 **BUILD SUCCESS**（1.8.9，27.3s）。

R1 为唯一行为观测变化（terminate 任务主键查询 2→1，测试基线随翻转）；R2/R3/R5 未接线，查询时序不变。未 commit，等待用户指示。

## 后续接入（同日，用户指示「继续接入」）

R2/R3/R5 已于同日接入（超出本计划范围，另行记录）：汇聚判定复用操作内定义图、撤回清理复用监听器前快照、协作守卫/会签计数/权限门改从办理人全集派生。语义统一为「操作内快照固定复用」，由 `ExecutionSnapshotCharacteristicTest` 锁定；测试 126/126，21 模块 install 通过。详见设计文档 §13.6。
