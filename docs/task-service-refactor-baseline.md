# TaskServiceImpl 重构基线

记录日期：2026-09-05

## 当前环境

- JDK：17.0.9（GraalVM Community）
- Maven：3.9.16
- 源码编译基线：`maven.compiler.release = 17`，由父 `pom.xml` 管理
- 外部测试仓库：已拉取到 `/tmp/warm-flow-test`，核心测试模块可编译；完整状态机测试仍需数据库/容器运行环境

## 公共入口分组

`TaskServiceImpl` 当前包含以下公共入口，均属于既有 `TaskService` 契约，不允许改签名：

- 通过 / 退回：`pass`、`passAtWill`、`reject`、`rejectAtWill`
- 跳转 / 回退：`skip`、`skipByInsId`、`rejectLast`、`taskBack`
- 实例操作：`revoke`、`termination`、`pending`
- 任务协作：`transfer`、`depute`、`addSignature`、`reductionSignature`、`updateHandler`
- 任务查询 / 构造：`addTask`、`getByInsId`、`getByInsIdAndNodeCodes`、`setInsFinishInfo`、`mergeVariable`
- 展示加载：`load`、`hisLoad`

## 内部高风险区域

- `getAndCheck`：任务、实例、定义、当前节点和流程活动状态校验；异常类型和顺序必须保持。
- `handleDepute`：委托历史写入、用户删除和委托人待办恢复。
- `cooperate`：或签、会签、票签、转办/委派办理人权限与剩余办理人计算。
- `isGenerateNewTask`：后继节点生成和网关汇聚。
- `handUndoneTask` / `updateFlowInfo`：未完成任务处理和实例状态更新。
- `taskBack`：历史任务查询和回退参数构造。

## 已执行验证

```bash
mvn -pl warm-flow-core -am -DskipTests compile
mvn -q -DskipTests -pl \
warm-flow-orm/warm-flow-mybatis/warm-flow-mybatis-core,\
warm-flow-orm/warm-flow-mybatis-plus/warm-flow-mybatis-plus-core,\
warm-flow-orm/warm-flow-easy-query/warm-flow-easy-query-core -am compile
```

结果：通过。

## 重构前置条件

当前外部仓库只有手工调用式核心测试，没有标准 `src/test` 自动入口。因此在完整状态机运行环境可用前，只继续做只读审计和编译验证；不得提取会改变事务调用顺序、状态更新顺序或 DAO 写入时机的内部代码。
