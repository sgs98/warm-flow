# AGENTS.md — warm-flow-core 模块规则

> 本文件只写 `warm-flow-core` 的差异化规则。通用工程与编码规范以仓库根 [`../AGENTS.md`](../AGENTS.md) 为准；规则优先级见根
`AGENTS.md`「规则优先级」。
>
> 最高约束：core 是整个引擎的地基，被所有 orm / plugin 模块与下游使用方依赖。**任何公共行为改动按 L2 高风险处理**。

## 模块职责

框架无关、ORM 无关、JSON 库无关的流程引擎核心，包根 `org.dromara.warm.flow.core`：

- `FlowEngine`：静态门面，持有各 `XxxService`、实体 `Supplier`、handler / listener / `jsonConvert`。
- `config`：`WarmFlow` 引擎配置与 `init()` 装配。
- `invoker/FrameInvoker`：框架桥接（`setBeanFunction` / `setCfgFunction`），core 不依赖容器的关键。
- `entity`：`Definition`/`Node`/`Skip`/`Instance`/`Task`/`HisTask`/`User`/`Form` 等接口。
- `service` + `service.impl`：`DefService`/`NodeService`/`SkipService`/`InsService`/`TaskService`/`HisTaskService`/
  `UserService`/`FormService`/`ChartService`。
- `workflow`：v2.0.0 起对外的统一流程操作门面——`WorkflowService`（`start`/`complete`/`reject`/`jump`/`revoke`/`terminate`/`transfer`/`delegate`/`addSigner`/`removeSigner`，经 `FlowEngine.workflow()` 获取）、`command/`（`WorkflowCommand` 基类 + 各 `XxxCommand`）、`context/`（`WorkflowContext`/`OperatorContext`，字段为 `flowStatus`/`taskStatus`）、`result/`（`WorkflowResult`）、`WorkflowContextMapper`。**这是当前主推的对外写操作契约**，属 L2。
- `orm`：抽象 `dao/WarmDao`、`agent/WarmQuery`、`service/WarmServiceImpl`（ORM 接缝，不含具体实现）。
- `handler`：`DataFillHandler`/`TenantHandler`/`PermissionHandler`。
- `listener`：`Listener`/`GlobalListener`/`ListenerVariable`（`Listener` 含 create/start/assignment/finish/formLoad 五种事件常量）。
- `strategy`：策略接口——`ConditionStrategy`（条件）、`HandlerStrategy`（办理人）、`ListenerStrategy`（监听器）、`VoteSignStrategy`
  （票签）、`GatewayStrategy`（网关，实现见 `strategy/gateway/{Serial,Parallel,Inclusive}GatewayStrategy`）、`ExpressionStrategy`（表达式基类）；具体 SpEL 实现在 `plugin-modes`。
- `strategy/condition`：条件比较运算的具体实现（`AbstractConditionStrategy` + `ConditionStrategyEq`/`Ne`/`Gt`/`Ge`/`Lt`/`Le`/`Like`/`NotLike`）。
- `enums`/`constant`/`dto`/`exception`/`transaction`：状态与类型枚举（`NodeType`/`SkipType`/`CooperateType`/`FlowStatus` 等）、常量、传输对象、异常、事务抽象。
- `utils/IdUtils`：core 默认 ID 生成与 ORM 原生生成器接入。
- `json`：`JsonConvert` SPI 接口（实现在 plugin-json）。
- `utils`：引擎自带工具（`StringUtils`/`ObjectUtil`/`CollUtil`/`MapUtil`/`AssertUtil` 等）。

## 改动前必读

- 根 [`../AGENTS.md`](../AGENTS.md)「架构与扩展机制」「兼容性红线」。
- 改服务 / 状态机前，先读对应 `service.impl` + 相关 `strategy` / `handler` / `listener` + 状态枚举（`FlowStatus`/
  `NodeType`/`SkipType`/`CooperateType` 等）。
- `../.qoder/repowiki/zh/content/核心引擎架构/` 有服务层、实体模型、数据流的详细文档（本地参考）。

## 高风险点（一律 L2）

- **零框架依赖红线**：core **禁止**出现 `org.springframework.*`、`com.baomidou.*` 等具体框架 / ORM
  import（当前已是零依赖，必须保持）。需要容器能力时走 `FrameInvoker`，需要可替换实现走 SPI。
- **门面与契约**：`FlowEngine` 方法、`WarmDao` 抽象、实体接口、`WarmFlow` 配置项、枚举常量（code / 顺序 /
  名称）都是对外契约，改动评估下游破坏，优先「加法」，废弃用 `@Deprecated` 留过渡期。
- **状态机语义**：通过 / 退回 / 跳转 / 转办 / 加减签 / 终止 / 撤回 / 票签 / 网关有副作用，先确认现有流转再改，不要凭文件名猜。
- **实体字段**：新增 / 改字段要同步各 ORM 实体实现、JSON 序列化与 `sql/` 四套表结构。

## 聚焦验证

```bash
mvn -pl warm-flow-core -am -DskipTests compile   # 聚焦编译
mvn -pl warm-flow-core test                       # 跑 core 自带单元 / 特性测试（改逻辑时优先）
```

core 改动后至少再编译一个下游模块（如某 orm-core 或 plugin），确认接缝未破。
