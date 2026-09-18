# B4-v1 FlowStatusMachine guard 转移表实施计划（行为不变）

- 日期：2026-09-18
- 状态：**已执行并验证通过**（既有 126 个特征测试零修改全绿；矩阵测试新增后 131/131；仓库根 `mvn clean install -DskipTests` 21 模块 BUILD SUCCESS）
- 上游设计：[2026-09-18-flow-status-machine-design.md](../specs/2026-09-18-flow-status-machine-design.md)（§6 研究结论 / §7 实施记录）
- 范围裁定：经用户批准「做」，v1 = **只吸收状态谓词 guard 进表驱动结构**——异常消息/顺序零变化、零公共 API 变化（`FlowStatusMachine`/`FlowExecution`/`FlowOp` 均包内私有），与阶段 A 同风险等级，**不构成 2.0 破坏性改造**；裁决半边（`taskStatus`/`skipStatus`/`defaultStatus`/`customStatus`）与 `FlowOp` 公开化维持 2.0 门控。

## Context

B4 研究结论：阶段 A 后状态三连断言已收敛到 `FlowExecution` 两个 loader，真正的 op × 状态谓词矩阵方差只有一行——`deleteByInsIds` 仅要求激活（终态/已结束实例可删）。范围裁定四条（写入实现注释与文档）：

- 存在性校验（NOT_FOUNT_*）留在 loader 内联——与查询加载交织，不属于状态谓词；
- 协作守卫（IS_ALREADY_* 等）不进表——参数/数据校验，非实例状态；
- 权限守卫（NULL_ROLE_NODE/NOT_AUTHORITY）不进表——同上；
- skipType 不是 guard 维度（PASS/REJECT 状态前置完全相同）；不设 JUMP 枚举项（跳转复用 EXECUTE 入口，状态前置无差异，设了就是死行）。

## 硬性不变量（126 个既有测试把关，除新增外零修改）

- 异常消息与触发顺序不变：三连断言表达式逐字保留（`isFalse(def&&ins active, NOT_ACTIVITY)` → `isTrue(isTerminal, FLOW_FINISH)` → `isTrue(isEnd, FLOW_FINISH)`）；
- 查询数零变化（guard 全为内存判断，表不触发 DAO）；
- 裁决半边四个方法一行不动；
- core 红线：无 spring/baomidou import；新文件 Apache 2.0 header + `@author warm`；JDK17（箭头 switch 可用，无 preview）；
- `FlowStatusMachine.java` 现状无 license header、`@author may`——保持原样，不顺手改。

## 实施步骤

### W1 新建 FlowOp.java（唯一新生产文件）
包内 enum：EXECUTE / TERMINATE / UPDATE_HANDLERS / LOAD / REVOKE / DELETE（DELETE 为离群行，javadoc 注明跳转复用 EXECUTE 行、协作守卫属参数校验不进表）。

### W2 FlowStatusMachine 增加表与入口
`StateGuard` 私有枚举（ACTIVITY/NOT_TERMINAL/NOT_END，声明序即断言序——EnumSet 按自然序迭代）；`OPERABLE_GUARDS` 办理族通用行；`GUARDS` = `EnumMap` 六行显式 put + `Collections.unmodifiableMap`；`checkGuards(op, definition, instance)`（缺行 requireNonNull 即 NPE，转为清晰报错）+ `apply`（箭头 switch，default 分支防未来新增 StateGuard 静默漏判）。

### W3 FlowExecution 接线
`loadTask(Task, WorkflowContext, FlowOp)` 加第三参，三连断言 → `checkGuards(op, definition, instance)`；`loadInstance` 签名不动（单一消费者 revoke），三连断言 → `checkGuards(FlowOp.REVOKE, ...)`。顺带清理 FlowExecution 不再使用的 ActivityStatus/NodeType import。

### W4 TaskServiceImpl 接线 + 删死助手
`getAndCheck` 两重载各加 `FlowOp` 参数；execute→EXECUTE、terminate 直调→TERMINATE、updateHandlers→UPDATE_HANDLERS、load→LOAD；`deleteByInsIds` 激活断言 → `checkGuards(FlowOp.DELETE, ...)`（NOT_FOUNT_DEF 存在性断言留原位）；`judgeActivityStatus` 唯一调用点迁移后删除（ActivityStatus 经 `enums.*` 通配 import，无需清理）。

### W5 测试（126 → 131）
新建 `FlowStatusMachineCharacteristicTest` 四用例（矩阵逐格 / DELETE 离群行 / 表覆盖完整性 / 断言序）；`InstanceCleanupCharacteristicTest` 新增端到端用例（不改既有方法）。

### W6 文档与记忆收尾
B4 设计文档状态行 + §5 授权边界更新说明 + §6 研究结论 + §7 实施记录；本计划落档；记忆 B4 行更新。

## 执行结果（2026-09-18）

- 全部步骤按计划完成，偏差一条：
  1. **端到端用例断言修正**：原拟「终态实例 deleteByInsIds 返回 true」，实测办理至 end 后无待办行可删，`SqlHelper.retBool` 按删除行数语义返回 false——断言改为锁定真实契约（守卫放行不抛 + 返回 false + 待办为空），用例更名 `deleteByInsIds_finishedInstance_notBlockedByTerminalState`。
- 验证证据：
  - W3/W4 接线后 `mvn -pl warm-flow-core test`：**126/126 零修改全绿**（行为不变核心证据）；
  - W5 后：**131/131**（`FlowStatusMachineCharacteristicTest` 4 + `InstanceCleanupCharacteristicTest` +1）；
  - 仓库根 `mvn clean install -DskipTests`：21 模块 BUILD SUCCESS。
- 裁决半边未动、`FlowOp` 包内私有；未 commit（基线 311dbac 上的阶段 A + R2/R3/R5 + 本轮改动均待用户指示）。
