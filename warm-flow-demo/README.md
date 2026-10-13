# warm-flow-demo

Warm-Flow 全栈集成演示工程（非正式发布，不参与 Warm-Flow Maven 反应堆，不改动引擎公共契约）。

- 后端：`warm-flow-demo/`（Spring Boot 3.5 + MyBatis-Plus 3.5.17 + MySQL 8，源码编译基线 Java 17）
- 前端：`warm-flow-demo-web/`（Vue 3 + Vite 5 + TypeScript + Element Plus，通过 iframe 使用后端内置设计器）

## 目录结构

```text
warm-flow-demo/
├── warm-flow-demo/          # 后端独立 Maven 工程
│   └── src/main/resources/sql/   # schema.sql / data.sql
└── warm-flow-demo-web/      # 前端独立 Vite 工程
```

## 数据库准备

需要本机存在 MySQL 8 与数据库 `warm-flow`（可通过环境变量 `WARM_DEMO_DB_URL` / `WARM_DEMO_DB_USERNAME` / `WARM_DEMO_DB_PASSWORD` 覆盖）。

按顺序执行 SQL：

```bash
# 1) Warm-Flow 引擎 7 张核心表（官方规范脚本，本 demo 不复制）
mysql -uroot -p warm-flow < <repo>/sql/mysql/warm-flow-all.sql

# 2) demo 业务表
mysql -uroot -p warm-flow < warm-flow-demo/src/main/resources/sql/schema.sql

# 3) demo 初始数据
mysql -uroot -p warm-flow < warm-flow-demo/src/main/resources/sql/data.sql
```

## 启动

后端：

```bash
cd warm-flow-demo
mvn spring-boot:run            # 或打包 java -jar target/warm-flow-demo-2.0.0.jar
```

前端：

```bash
cd warm-flow-demo-web
pnpm install
pnpm dev                      # http://localhost:5173
```

## 流程定义导出与导入

流程定义列表页（`流程定义 -> 列表`）支持把单个流程定义导出为 json 文件，再把该文件导入回来：

- 导出：行内「导出」按钮，下载文件名形如 `leave_1.json`（`流程编码_版本号.json`），内容是引擎标准导出格式（`DefJson`：流程定义 + 节点 + 连线，含坐标与办理人），不带定义主键与发布状态，可跨环境使用。
- 导入：工具栏「导入流程」按钮选择 json 文件，由引擎 `importIs` 解析并校验结构（开始节点唯一、节点编码不重复、连线目标存在等）后，**始终作为新的流程定义版本落库**，导入后为未发布状态，不会覆盖本地已有定义；同编码已存在时版本号自动递增。

对应接口（也可用 curl 直接验证）：

```bash
# 导出为文件
curl -OJ "http://localhost:8080/api/definitions/{id}/export"

# 导入文件，返回新流程定义主键
curl -F "file=@leave_1.json" "http://localhost:8080/api/definitions/import"
```

注意：

- 监听器类路径、自定义表单 `formPath` 等按名称原样带过去，导入环境需存在同名实现，否则运行时才报错。
- 前端无论走 vite 代理还是直连 8080，POST/PUT/DELETE 都会带 `Origin`，后端 `WebConfig` 已对 `/api/**` 放开本地联调跨域（`allowedOriginPatterns("*")`）；来源不在白名单时 Spring 会直接返回 403，正式集成请自行收紧。改完 CORS 需要重启后端才生效。
- 引擎 `importIs` 按 JVM 默认字符集读取文件（JDK 18+ 默认 UTF-8）；若在 JDK 17 且默认字符集非 UTF-8 的环境（如部分 Windows）导入含中文的文件，可用 `-Dfile.encoding=UTF-8` 启动。

## 监听器示例

Demo 内置了全局监听器和任务监听器示例，便于观察 Warm-Flow 的监听器触发时机：

- 全局监听器通过 `warm-flow.global-listener-path` 配置为
  `org.dromara.warm.demo.listener.DemoGlobalListener`，覆盖 `start`、`assignment`、`finish`、`create` 四类全局事件；其中 `assignment` 还负责把 Demo 流程中的用户/部门权限标识展开为实际用户名。
- 任务监听器为 `org.dromara.warm.demo.listener.DemoTaskListener`。在 iframe 流程设计器的节点监听器配置中手动填写该类路径，并按需配置 `start`、`assignment`、`finish`、`create` 事件；同一个监听器配置多个事件时，可通过 `ListenerVariable.getEventType()` 获取本次实际触发的事件。
- 启动流程并办理任务后，可在 Demo 后端日志中查看监听器读取到的流程定义、实例、节点、任务和流程变量上下文。

监听器示例只输出日志，不修改流程状态、办理人或流程变量；实际接入时可在此基础上实现审计、通知等业务逻辑。

## 服务任务示例

Demo 内置三种服务任务执行方式的示例处理器（`org.dromara.warm.demo.servicetask`），并在 `warm-flow-demo/src/main/resources/json/` 下提供可直接**导入**的流程定义：

| 执行方式 | 节点配置（`nodeConfig`） | 示例处理器 | 执行结果 |
| --- | --- | --- | --- |
| Java 类 | `{"serviceTask":{"type":"class","handler":"org.dromara.warm.demo.servicetask.DemoClassServiceTask"}}` | `DemoClassServiceTask` | 写入变量 `classTaskNode`、`classTaskResult` |
| Bean 委托表达式 | `{"serviceTask":{"type":"delegateExpression","expression":"#{@demoDelegateServiceTask}"}}` | `DemoDelegateServiceTask`（Spring Bean，表达式结果必须是 `ServiceTaskHandler`） | 写入变量 `delegateTaskNode`、`delegateTaskResult` |
| 方法表达式 | `{"serviceTask":{"type":"expression","expression":"#{@demoOrderService.createOrder(#execution)}","resultVariable":"orderNo"}}` | `DemoOrderService#createOrder`（返回值由 `resultVariable` 写回流程变量） | 写入变量 `orderNo`、`expressionTaskNode`、`expressionTaskResult` |

示例流程定义 `demo_service_task.json`（导入方式见「流程定义导出与导入」），一条流程覆盖三种执行方式与网关、并行组合：

```text
开始
 → Java 类           （class）
 → Bean 委托表达式    （delegateExpression）
 → 方法表达式         （expression，写 orderNo）
 → 计算天数           （expression，写 day）
 → 互斥网关：day > 3 / 否则  → 人工审批（approver1 / approver2）
 → 并行网关：Java 类服务任务 ‖ 人工审批（approver2）
 → 汇聚网关 → 人工审批（approver1） → 结束
```

服务任务写回的变量参与网关条件（`day`）；并行分支证明服务任务可与人工分支并行推进、并在汇聚前自动完成。

注意版本可编辑性：warm-flow 中**已发布**与**已失效**的流程定义在设计器里是只读的（引擎契约：发布即冻结），发起流程也要求已发布版本。要修改示例先复制新版本（Demo 前端列表页为「复制并设计」，也可直接调用接口）：

```bash
curl -X POST "http://localhost:8080/api/definitions/{已发布定义主键}/copy"   # 返回新版本主键，未发布、可编辑
```

编辑完成后再发布；发布后同一流程编码的旧版本会转为失效，保证一个流程编码只有一个当前发布版本。

在设计器属性面板的「服务任务」节点中，按上表选择执行方式并填写处理类 / 执行表达式 / 结果变量即可，面板占位提示与 Demo 的类名、Bean 名一致；表达式内可用 `#execution` 访问 `ServiceTaskContext`（当前定义、实例、节点与流程变量）。

验证要点：

- 服务任务节点到达即自动执行，不产生待办；历史任务中 `node_type=6`、`flow_status=3`（自动完成）、`skip_type=NONE`，并保留 `node_config` 配置快照。
- 执行写入的流程变量可在实例详情的流程上下文中看到；发起时可用 `variables.inputDay` 控制互斥网关走向（缺省 5，走「大于三天」分支）。
- 发起方式的请求与查询口径见 `.codex/skills/warm-flow-demo-testing/references/test-matrix.md`。

## 说明与约束

- 本目录不参与正式发布；设计器页面由后端 `warm-flow-plugin-ui-sb-web` 与 `warm-flow-plugin-vue3-ui` 提供，前端通过 `/warm-flow-ui/index.html` iframe 集成。
- 不改动 Warm-Flow 公共 API、实体字段、状态枚举、正式 SQL 脚本与部署配置。
- 本 demo 不执行 `git commit` / `git push`，由维护者自行提交。
- 任务操作口径：通过/退回需流程中存在对应跳转分支；退回（REJECT）要求审批节点配置了 REJECT 跳转边，单级纯通过流程不能退回。转办/委派/加签/减签/撤回/终止已在 demo 数据上真机跑通；终止需由当前节点办理人执行，撤回由发起人执行。
