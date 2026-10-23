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
# 1) Warm-Flow 引擎 8 张核心表（事件能力默认关闭，本 demo 不复制）
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
mvn spring-boot:run            # 或打包 java -jar target/warm-flow-demo-2.1.0.jar
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

- 全局监听器为 `org.dromara.warm.demo.listener.DemoGlobalListener`，按 SPI/Bean 方式注册（`@Component` 交给 Spring 容器，引擎自动发现），无需任何类路径配置；覆盖 `start`、`assignment`、`finish`、`create` 四类全局事件；其中 `assignment` 还负责把 Demo 流程中的用户/部门权限标识展开为实际用户名。
- 任务监听器为 `org.dromara.warm.demo.listener.DemoTaskListener`。在 iframe 流程设计器的节点监听器配置中手动填写该类路径，并按需配置 `start`、`assignment`、`finish`、`create` 事件；同一个监听器配置多个事件时，可通过 `ListenerVariable.getEventType()` 获取本次实际触发的事件。
- 判定型监听器为 `org.dromara.warm.demo.listener.DemoPayFailListener`，挂在「付款失败善后」案例「财务执行付款」节点的 `create` 事件上：读取流程变量 `payAmount`（付款金额），大于限额 `1000` 时调用 `TaskService.throwError` 抛 `PAY_FAILED` 命中错误边界，自动中断付款任务并转到「失败善后处理」；未超限（或未传、非数字）时不处理。它演示「业务在监听器里读变量判定后显式抛错进边界」的写法——引擎**不会**自动把监听器抛出的 Java 异常转成错误事件，必须调用抛出 API。
- 启动流程并办理任务后，可在 Demo 后端日志中查看监听器读取到的流程定义、实例、节点、任务和流程变量上下文。

除 `DemoPayFailListener` 在命中失败时会抛错进边界外，其余监听器示例只输出日志，不修改流程状态、办理人或流程变量；实际接入时可在此基础上实现审计、通知、超时审批等业务逻辑。

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

- 服务任务节点到达即自动执行，不产生待办；历史任务中 `node_type=6`、`flow_status=2`（通过）、`skip_type=NONE`，并保留 `node_config` 配置快照。
- 执行写入的流程变量可在实例详情的流程上下文中看到；发起时可用 `variables.inputDay` 控制互斥网关走向（缺省 5，走「大于三天」分支）。
- 发起方式的请求与查询口径见 `.codex/skills/warm-flow-demo-testing/references/test-matrix.md`。

## 网关综合示例（经典 / 仿钉钉）

`warm-flow-demo/src/main/resources/json/` 下的两个定义**节点与连线结构完全一致**，只差设计器模式（`modelValue`），用于对照验证经典模式与仿钉钉模式导出的同一张复杂流程图：

- `demo_gateway_classic.json`：经典模式（`CLASSICS`），流程名「网关综合-经典模式示例」。
- `demo_gateway_mimic.json`：仿钉钉模式（`MIMIC`），流程名「网关综合-仿钉钉模式示例」。

一条流程覆盖三类网关与「条件出口 + 无条件兜底」（导入方式见「流程定义导出与导入」）：

```text
开始 → 申请人
 → 排他网关：gt@@day|3 → 大于三天(approver1) ／ 无条件兜底 → 小于三天(admin)
 → 第四个                        （admin + approver1，nodeRatio=100）
 → 排他网关（单出口，仅汇合两条分支）
 → 并行网关 → 并行一(admin) ／ 并行一同级(admin) ／ 并行二(admin + approver1 + approver2，nodeRatio=60)
 → 并行汇聚网关
 → 包容网关：gt@@day|3 → 包容day大于三(approver1) ｜ lt@@day|3 → 包容小于三(admin) ｜ 无条件 → 包容3(admin)、包容4(approver2)
 → 包容汇聚网关
 → 最后一个(admin) → 结束
```

等价的发起请求（`day` 是条件出口用的流程变量）：

```bash
curl -X POST http://localhost:8080/api/instances -H "Content-Type: application/json" -H "X-User-Name: approver1" \
  -d '{"flowCode":"demo_gateway_classic","businessId":"gw-demo-1","variables":{"day":4}}'
```

验证要点：

- **排他网关**：`day` 大于 3 走「大于三天」，否则走无条件兜底「小于三天」，两条分支在「第四个」汇合。
- **并行网关**：三个分支同时生成待办，全部办理完才汇聚推进；分支办理人不同，需用 `X-User-Name` 分别办理（口径见 `.codex/skills/warm-flow-demo-testing/references/test-matrix.md`）。
- **包容网关**：条件出口与无条件出口**独立判断**——`day=4` 时同时进入「包容day大于三」「包容3」「包容4」，条件不命中时仍进入「包容3」「包容4」。两条条件出口 `gt` / `lt` 互斥，因此该定义可验证「条件出口 + 无条件出口同时命中」，**不能**证明「多个条件同时命中」，详见 `.codex/skills/warm-flow-demo-testing/references/regression-cases.md`。
- 两个定义的节点编码一致，可用于比对两种设计器模式导出的 `nodeCode`、节点坐标与连线格式能否互相导入。

## 自动审批示例

流程定义配置（`flow_definition.def_config`，JSON 对象）中 `autoPass` 为 `true` 时开启自动审批：

- **开关位置**：设计器「流程定义配置 → 自动审批」，打开「同一办理人自动审批」保存即写入 `flow_definition.def_config`，形如 `{"autoPass":true}`；也可以在设计器外的**流程定义列表**（`流程定义 → 操作 → 编辑`）直接开关，接口为 `PUT /api/definitions/{id}/auto-pass`，只写 `autoPass` 一个键，业务扩展属性 `ext` 不受影响。
- **只读 def_config**：引擎只从 `flow_definition.def_config` 读取 `autoPass`，不回退读业务扩展属性 `ext`。
- **已发布版本**：自动审批是定义级的运行时开关，引擎每次办理时重新读取定义，因此已发布版本也能通过列表「编辑」直接改，改完对后续办理立即生效；已在途的待办不受影响。这与「发布即冻结」不冲突——冻结的是流程结构，`def_config` 是定义配置属性。
- **生效时机**：办理（通过 / 跳转）时判定本次新建的后续待办，不回溯实例上已存在的待办。
- **判定规则**：后继节点是人工节点、且其审批办理人**包含**当前办理人时，引擎以该办理人身份自动通过；会签 / 票签只代投该办理人一票，其余办理人仍需办理，或签与该办理人手工通过等效。
- **链式推进**：自动通过后新建的待办若仍只有该办理人，继续自动通过，直到出现他人待办、流程结束或到达跳数上限。
- **历史记录**：自动通过的历史任务 `flow_status=2`（通过），办理意见为「流程引擎自动审批！」，办理人仍是当前办理人；自动审批不携带本次办理的流程变量、抄送等参数。

验证步骤（Demo 用户：`approver1` / `approver2`）：

1. 在设计器中新建或复制一份流程：开始 → 审批A（办理人 approver1）→ 审批B（办理人 approver1）→ 结束，两条连线均为「通过」。
2. 打开「流程定义配置 → 自动审批」，开启「同一办理人自动审批」，保存后发布该版本（设计器内已发布版本只读，需复制新版本再改；只改自动审批开关时可直接用流程定义列表的「编辑」）。
3. 用 approver1 发起该流程并办理「审批A」：审批B 由引擎自动通过，实例直接到达结束节点；「已办」中能看到办理人为 approver1、状态「**通过**」的审批B记录，待办列表为空。

注意：Demo 设计器的节点办理人写入的是带类型前缀的入库标识（如 `user:approver1`），`DemoPermissionHandler#convertPermissions` 会把它归一化为用户名，保证 `flow_user.processed_by` 与引擎办理人一致；不归一化时按办理人比对的自动审批无法命中。

## 流程实例删除

流程实例列表（`流程实例 -> 列表`）支持物理删除实例：

- 单条删除：行内「删除」按钮，接口 `DELETE /api/instances/{id}`。
- 批量删除：勾选多条后点工具栏「批量删除」，接口 `DELETE /api/instances?ids=1&ids=2`，返回实际删除数量。

两者均按「等待事件 → 流程用户 → 待办 → 历史任务 → 流程实例」的顺序删除相关数据，操作不可恢复，仅用于 Demo 演示清数；正式业务建议使用引擎逻辑删除。引擎的 `InsService.remove(instanceIds)` 同样级联删除等待事件记录，不会留下指向已删除实例的孤儿等待。

Demo 以 `warm-flow.logic-delete=false`（物理删除）运行：引擎自身的删除（删除定义级联节点/跳转、删除实例、事件等待消费等）同样直接物理删除，库中不会残留 `del_flag='1'` 记录。注意该开关关闭后 MyBatis-Plus 的查询也不再过滤 `del_flag`，从 `true` 切到 `false` 前必须先清理存量 `del_flag='1'` 数据，否则旧数据会重新出现在查询结果中。

## 事件案例示例

`warm-flow-demo/src/main/resources/json/` 下的 `demo_event_*.json` 是 6 个事件能力经典案例，每个案例聚焦一类事件组合，可通过设计器「导入」或接口 `POST /api/definitions/import` 导入后发布体验。六个案例均由 `DemoEventDefinitionTest`（warm-flow-core）走真实引擎导入 + 发布门禁校验，保证与引擎校验规则不漂移。

**推荐体验方式**：启动 demo 前端（`warm-flow-demo-web`，`npm run dev`）后进入左侧菜单「事件测试」页，页面提供：

- **案例卡片**：6 个案例各附覆盖事件标签与分步体验指引——普通案例在「流程实例」页正常发起（按钮跳转），消息启动案例必须点「消息启动」按钮（消息启动流程没有普通发起入口）；
- **事件流程实例表**：列出事件案例的实例，操作按钮按实例**等待中的事件**数据驱动出现——有消息捕获/边界等待时出现「触发消息」（弹窗可选消息名、自定义载荷 JSON），任何实例可查看「等待事件」（含下次触发时间）；
- **消息发布记录**：实时展示 `MESSAGE_THROW` / `MESSAGE_END` 经 `DemoEventPublisher` 发布的消息。

不使用前端时，下文各案例附等价的 curl 命令。错误边界案例（案例 4）由节点监听器按流程变量自动判定是否进边界，无需前端手动抛错。

前置：`warm-flow.timer-enabled=true` 开启定时能力（消息 / 错误事件不依赖该开关）。

### 定时任务观测页（`/timers`）

左侧菜单「定时任务」是全局**只读**观测页，直接读引擎 `flow_event_wait`，默认只查定时类事件（`TIMER_START` / `TIMER_CATCH` / `TIMER_BOUNDARY`）、按下次触发时间升序，用于回答「接下来会触发什么、有没有卡住」：

- **顶部计数**：待触发 / 重试中 / 执行中 / 已死 / 已取消，点击即按该状态过滤，计数与列表共用筛选条件；
- **筛选条件**：状态、事件类型（定时 / 消息 / 错误 / 其他分组多选）、流程编码、业务 ID、节点编码、消息名 / 错误码、下次触发时间与创建时间区间，以及「含消息 / 错误类事件」开关（关闭即引擎默认只看定时类）；
- **行操作**：详情抽屉（载荷、抢占租约、重试与触发次数、定时策略）、查看实例；消息类等待可「触发消息」，错误类等待可「抛错」（分别复用 `POST /api/instances/{id}/messages`、`POST /api/instances/{id}/errors`、`POST /api/tasks/{taskId}/errors`）；
- **自动刷新**：不自动 / 5s / 10s / 30s，便于观察定时推进。

页面数据只来自只读查询接口，不提供取消 / 立即触发 / 重新排期（需要引擎侧写操作语义与审计，留待后续版本）。第三方可直接调用等价接口：

```bash
# 分页查询等待记录（默认只查定时类事件，按下次触发时间升序，每页 10 条）
curl "http://localhost:8080/api/event-waits?statuses=WAITING&flowCode=demo_event_refund"

# 含消息 / 错误类事件；消息名 / 错误码走 eventName
curl "http://localhost:8080/api/event-waits?timerOnly=false&eventTypes=MESSAGE_CATCH&businessId=biz-1001"

# 各状态计数（与列表同条件，状态条件由接口逐个覆盖）
curl "http://localhost:8080/api/event-waits/summary?timerOnly=false"
```

可组合条件见引擎 `EventWaitQuery`（`WorkflowService.pageEventWaits`）；示例接口固定按下次触发时间升序、不暴露排序参数，排序白名单校验属引擎侧能力。`businessId` 与 `instanceId(s)`、`flowCode` 与 `definitionId(s)` 互斥同时出现会报错，解析不到主键时按空结果返回。

### 案例 1：退款审批-超时升级（`demo_event_refund.json`）

覆盖：**定时边界 + 消息结束**

流程：提交退款申请（发起人办理，标识 `warmFlowInitiator`）→ 客服初审（approver2，挂 2 分钟定时边界）→ 财务退款（admin）→ 退款结果通知（消息结束 `refund.finish`）→ 结束。初审 2 分钟未办理，任务自动失效并转「客服主管复核」（admin）。

体验要点：发起后不办理初审，等 2 分钟边界自动触发；被中断的初审任务在已办里显示「定时边界事件中断」，状态为失效。

### 案例 2：支付对账-等待外部回调（`demo_event_risk_wait.json`）

覆盖：**消息启动 + 消息捕获**

流程：订单已支付（消息启动 `order.paid`）→ 等待风控审核结果（消息捕获 `risk.result`）→ 对账确认（approver3）→ 结束。

体验要点：该流程不能用「发起」按钮启动，需调用消息启动接口（前端「事件测试」页点卡片上的「消息启动」按钮即可）；实例停在"风控结果等待"后，在「事件测试」页实例表点「触发消息」模拟回调推进。等价 curl：

```bash
# 消息启动（businessId 自定；引擎不按 businessId 去重，重复调用会创建多个实例，幂等需业务侧自行保证）
curl -X POST http://localhost:8080/api/instances/message \
  -H "Content-Type: application/json" -H "X-User-Name: admin" \
  -d '{"flowCode":"demo_event_risk_wait","businessId":"biz-1001","eventName":"order.paid","variables":{"orderId":"1001"}}'

# 风控结果回调（触发后实例推进到对账确认）
curl -X POST http://localhost:8080/api/instances/{instanceId}/messages \
  -H "Content-Type: application/json" -H "X-User-Name: admin" \
  -d '{"eventName":"risk.result","variables":{"riskLevel":"low"}}'

# 查看最近的消息发布记录（MESSAGE_THROW / MESSAGE_END 由 DemoEventPublisher 记录）
curl http://localhost:8080/api/events/published
```

### 案例 3：账号注销-冷静期（`demo_event_cooldown.json`）

覆盖：**定时捕获 + 消息抛出**

流程：提交注销申请（发起人办理，标识 `warmFlowInitiator`）→ 冷静期 2 分钟（定时捕获 `PT2M`）→ 注销终审确认（admin）→ 注销结果推送（消息抛出 `membership.cancelled`）→ 结束。

体验要点：等待期间实例无待办，在「等待中的事件」里能看到下一次触发时间；2 分钟后自动推进到终审。消息抛出需要在宿主注册 `EventPublisher` Bean 才能收到回调（未注册时告警跳过、流程照常继续，demo 已内置 `DemoEventPublisher`，可通过 `GET /api/events/published` 查看发布记录）。

### 案例 4：付款失败善后（`demo_event_pay_fail.json`）

覆盖：**错误边界 + 监听器判定抛错**

流程：发起付款申请（发起人办理，标识 `warmFlowInitiator`）→ 财务执行付款（approver2，挂错误边界 `PAY_FAILED`，`create` 事件监听器 `DemoPayFailListener`）→ 结束；付款任务创建时监听器读变量判定失败即抛错，任务被中断并转「失败善后处理」（admin）。

体验要点：发起流程时在「流程变量」里加入 `payAmount=5000`（大于限额 1000），再按正常流程办理「发起付款申请」；「财务执行付款」节点创建时监听器读取该变量，金额超限即自动抛 `PAY_FAILED` 命中边界，付款任务不产生待办即被置为失效，流程直接进入「失败善后处理」。金额不超过 1000（或未传）时按正常待办办理付款，走「付款 → 结束」。全程由监听器自动判定，无需手动干预。

自动判定之外，引擎仍提供手动抛错 API（Demo 前端不放按钮），可用 curl 演示：

```bash
curl -X POST http://localhost:8080/api/tasks/{taskId}/errors \
  -H "Content-Type: application/json" -H "X-User-Name: approver2" \
  -d '{"errorCode":"PAY_FAILED","message":"卡余额不足"}'
```

无匹配错误事件时该接口报「流程错误未被捕获」，实例状态不变。任务级抛错只匹配附着任务上的错误边界，需要实例级路由时调用 `POST /api/instances/{id}/errors`。

### 案例 5：每日自动巡检（`demo_event_daily_check.json`）

覆盖：**定时启动 + 并行分支 + 终止结束**

流程：每天 09:00 自动发起（定时启动，无需人工）→ 执行数据巡检（approver3）→ 并行分派「质检复核」（approver1）与「风控否决判定」（approver2）→ 风控判变量 `veto=1` 时走「严重异常终止」（终止结束，**同时中断质检分支与所有等待**）；`veto=0` 则两分支正常汇聚结束。

体验要点：办理风控否决判定时携带变量：

```bash
# veto=1：终止结束触发，质检复核未办待办按「失效」归档（历史里可见），实例状态置「终止」
curl -X POST http://localhost:8080/api/tasks -H "Content-Type: application/json" -H "X-User-Name: approver2" \
  -d '{"taskId":<id>,"message":"严重异常","variables":{"veto":"1"}}'
```

定时启动没有调用方，实例初始变量取自流程节点上的**默认变量**（`nodeConfig.variables`）：`demo_timer_start.json` 在定时启动节点上预置了 `veto=0` / `channel=daily`，后续条件网关可以直接拿它们求值。办理时传入的同名变量优先于默认值。

定时启动依赖定时器开启；定义挂起 / 取消发布后不再补发挂起期间错过的触发。

### 案例 6：注销结果通知-下游流程（`demo_event_notify.json`）

覆盖：**消息启动（被其它流程抛出的消息驱动）**

流程：账号已注销（消息启动 `membership.cancelled`）→ 发送注销通知（admin）→ 结束。

体验要点：它不是人工发起的流程，而是**被案例 3 抛出的消息启动的下游流程**——走完案例 3 并确认终审后，「事件测试」页会多出一个停在「发送注销通知」的实例（admin 待办），后端日志出现 `[消息订阅示例] 已按消息 membership.cancelled 启动下游流程 demo_event_notify，实例 ...`；也可点卡片上的「消息启动」手工创建实例。链路代码见 `DemoEventPublisher`（引擎 → 宿主）与 `DemoMessageSubscriber`（收到消息后 `startByMessage` 启动本流程）；不想要这条下游链路，删掉 `DemoMessageSubscriber` 即可。等价 curl：

```bash
curl -X POST http://localhost:8080/api/instances/message \
  -H "Content-Type: application/json" -H "X-User-Name: admin" \
  -d '{"flowCode":"demo_event_notify","businessId":"notify-1","eventName":"membership.cancelled","variables":{"reason":"user-request"}}'
```

## 事件能力落地（宿主接入）

宿主侧的完整写法见官方文档《[流程事件与内置定时器](https://sgs98.github.io/warm-flow-doc/master/advanced/event.html)》：`5.4 接入清单`（照做即可）、`13 完整示例`（六个可直接跑通的例子，覆盖除 `MESSAGE_BOUNDARY` / `ERROR_CATCH` 外的全部事件类型，并给出对应关系）、`12.2 运行期排查`（触发后没反应怎么查）。demo 里只保留最小实现：`DemoEventPublisher`（引擎 → 宿主）+ `DemoMessageSubscriber`（收到消息 → `startByMessage` 启动案例 6）。

## 说明与约束

- 本目录不参与正式发布；设计器页面由后端 `warm-flow-plugin-ui-sb-web` 与 `warm-flow-plugin-vue3-ui` 提供，前端通过 `/warm-flow-ui/index.html` iframe 集成。
- 不改动 Warm-Flow 公共 API、实体字段、状态枚举、正式 SQL 脚本与部署配置。
- 本 demo 不执行 `git commit` / `git push`，由维护者自行提交。
- 任务操作口径：通过/退回需流程中存在对应跳转分支；退回（REJECT）要求审批节点配置了 REJECT 跳转边，单级纯通过流程不能退回。转办/委派/加签/减签/撤回/终止已在 demo 数据上真机跑通；终止需由当前节点办理人执行，撤回由发起人执行。
