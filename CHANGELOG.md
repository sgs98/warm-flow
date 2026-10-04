# 更新日志

本文件记录 warm-flow（sgs98 二次开发版）的重要版本变更。使用文档、在线更新日志与升级指南见 [sgs98.github.io/warm-flow-doc](https://sgs98.github.io/warm-flow-doc/)。

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
