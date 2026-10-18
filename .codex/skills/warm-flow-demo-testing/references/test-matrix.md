# Warm-Flow Demo 测试矩阵

## 常用接口

启动流程：

```http
POST /api/instances
X-User-Name: admin
Content-Type: application/json

{"flowCode":"<published-flow>","businessId":"WF-D-<short-unique>","variables":{"day":"4"}}
```

查询实例、历史和任务：

```text
GET /api/instances/<instanceId>
GET /api/instances/<instanceId>/history
GET /api/tasks/todo?pageNum=1&pageSize=100
POST /api/instances/<instanceId>/revoke
POST /api/instances/<instanceId>/termination
DELETE /api/instances/<instanceId>                 # 物理删除单个实例（演示清数，谨慎使用）
DELETE /api/instances?ids=<id1>&ids=<id2>          # 批量物理删除实例（演示清数，返回删除数量；回归测试默认不用）
```

任务操作请求统一带 `user` 和 `taskId`。按 Demo Controller 当前实际映射调用：

```text
POST /api/tasks/pass
POST /api/tasks/reject
POST /api/tasks/transfer
POST /api/tasks/depute
POST /api/tasks/add-signature
POST /api/tasks/reduction-signature
```

候选人接口按用途分开调用：

```text
GET /api/tasks/instances/<instanceId>/add-signature-handlers
GET /api/tasks/instances/<instanceId>/reduction-signature-handlers
```

事件接口（Demo `EventController`）：

```text
GET  /api/instances/<instanceId>/events            # 查询等待中的事件
POST /api/instances/<instanceId>/messages          # 触发 MESSAGE_CATCH / MESSAGE_BOUNDARY，body {"eventName":"X","payload":{...}}
POST /api/instances/<instanceId>/errors            # 触发 ERROR_CATCH，body {"errorCode":"P001","message":"..."}
POST /api/tasks/<taskId>/errors                    # 触发附着该任务的 ERROR_BOUNDARY，body {"errorCode":"E1"}
```

消息启动与等待记录全局查询：

```text
POST /api/instances/message                        # 消息启动 MESSAGE_START，body {"flowCode":"X","businessId":"...","eventName":"order.paid","variables":{}}；X-User-Name 可选（缺省 admin），用于写实例创建人
GET  /api/event-waits?pageNum=1&pageSize=10        # 等待记录全局只读查询，默认只查定时类、按 next_trigger_at 升序
GET  /api/event-waits?timerOnly=false&instanceId=<id>   # 含消息/错误类；同组条件还支持 definitionId / businessId / flowCode / status / eventType
GET  /api/event-waits/summary                      # 同一套过滤条件下的各状态计数
```

流程定义接口（Demo `DefinitionController`）：

```text
GET    /api/definitions?pageNum=1&pageSize=50&keyword=&isPublish=   # 列表：id / flowCode / flowName / version / isPublish
POST   /api/definitions/import  -F "file=@<flowCode>.json"          # 导入为新的未发布版本，返回新定义 id
PUT    /api/definitions/{id}/publish                                # 发布；同编码的旧版本转未发布（已被实例用过则转已失效）
GET    /api/definitions/{id}/export                                 # 导出，文件名 flowCode_version.json
DELETE /api/definitions/{id}                                        # 删除（级联节点/连线/定义级等待；有实例的定义被拒绝删除）
```

接口字段以当前 `TaskActionRequest` 为准：`user`、`taskId`、`message`、`nodeCode`、`nextHandlers`、`nextHandlerMap`、`addHandlers`、`reductionHandlers`、`variables`、`copyUsers`。

## 场景矩阵

| 场景 | 输入重点 | 必查结果 |
| --- | --- | --- |
| 草稿 | 无审批说明，带/不带 variables；业务号 <= 40 字符 | 实例 `draft`，启动历史任务为 `pass`，变量回显，可继续办理 |
| 排他 | 条件命中与兜底各一例 | 只生成一个出口 |
| 并行 | 完成前后两个分支 | 全部出口生成，最后一个完成后才汇聚 |
| 包容 | 0 个、1 个、多个条件命中 | 多条件同时生效，无条件正确兜底 |
| 通过 | 当前活动任务 | 当前任务归历史，目标任务正确 |
| 驳回 | 真实 REJECT 节点 | 目标节点和历史状态正确 |
| 转办 | `nextHandlers` 一个新用户 | 原用户移除，目标用户存在 |
| 委托 | `nextHandlers` 一个新用户 | 受托办理和委托历史正确 |
| 加签 | 新用户，排除已有用户 | 新增权限，不重复 |
| 减签 | 当前已有用户；逐个减签和一次选择全部用户 | 只移除所选用户，不能清空；全选时拒绝 |
| 撤回 | 发起人、活动实例 | 实例状态与待办符合撤回规则 |
| 终止 | 当前办理人 | 进入终态，剩余待办正确归档 |
| 抄送 | `copyUsers` | 抄送可查，不污染审批办理人 |
| 定时捕获 | 人工A → `TIMER_CATCH`(AFTER PT3S) → 人工B | 办理人工A 后生成 WAITING 等待行、无活动待办；到期后等待行不再活动并生成人工B |
| 定时启动 | `开始 → TIMER_START(AFTER PT3S)`，发布后不手动发起 | 发布即建定义级等待行；手动发起被拒；到期自动建实例（`business_id=T<等待行id>_<毫秒>`）与首个待办；**实例状态=审批中（`waiting`，非 draft）**；节点默认变量并入实例变量 |
| 定时边界 | 人工A 挂 `TIMER_BOUNDARY`(中断型, AFTER PT3S) → 人工C，不办理人工A | 生成边界等待行（`attached_task_id`=人工A任务）；到期中断人工A（历史 `flow_status=10`）并生成人工C |
| 消息抛出 | 人工A → `MESSAGE_THROW` → 结束 | 未注册发布器时告警跳过但继续推进，不阻塞；`MESSAGE_END` 到达写一条 `task_id=0` 留痕历史（demo 业务码 `pass`、标签「通过」，message「流程由该结束事件节点结束」） |
| 消息捕获 | 人工A → `MESSAGE_CATCH`(eventName=X) → 人工B | 生成 WAITING/MESSAGE_CATCH、无活动待办；`POST .../messages` 触发；错误消息名被拒；payload 并入实例变量并落库 `flow_event_wait.payload` |
| 消息边界 | 人工A 挂 `MESSAGE_BOUNDARY`(中断型, `attachedToNodeCode`=人工A, eventName=X) → 人工C | 生成边界等待（`attached_task_id`）；`POST .../messages {eventName:X}` 命中后中断人工A（历史 `flow_status=10`）并生成人工C；历史 `approver` = 触发时的 `X-User-Name` |
| 错误捕获 | 人工A → `ERROR_CATCH`(eventName=P001) → 人工B | 生成 WAITING/ERROR_CATCH（空名=通配）；错误码不匹配报「未被捕获」且流程不变；命中后生成人工B |
| 错误边界 | 人工A 挂 `ERROR_BOUNDARY`(中断型, `attachedToNodeCode`=人工A, eventName=E1) → 人工C | 生成边界等待（`attached_task_id`）；`POST /tasks/{taskId}/errors` 命中后中断人工A（历史 `flow_status=10`）并生成人工C |
| 监听器判定抛错 | 人工A 挂 `ERROR_BOUNDARY`(E1) 且节点挂 `create` 监听器；监听器读流程变量（demo 用 `payAmount` 超限 1000）命中时调 `TaskService.throwError` | 金额未超限：人工A 正常产生待办；超限：人工A 不产生待办即被中断（历史 `flow_status=10`）并直接生成人工C，无需手动按钮 |
| 事件发布校验 | 含启动事件与固定 START 的图、边界事件缺 `attached_to_node_code` | 发布时报结构/能力错误；草稿保存不校验运行时能力 |
| 消息启动 | 已发布定义含 `MESSAGE_START`(eventName=X)，不手动发起 | 手动发起被拒；`POST /api/instances/message` 按消息建实例；消息名不符被拒；后继待办生成 |
| 终止结束 | `demo_event_daily_check`，并行分支上「风控否决判定」带 `veto=1` | 实例状态=**终止**（demo `termination`，内置码 4；不是已完成）；另一并行分支活动任务按失效归档（历史 `flow_status=10`、message「流程结束时待办未办理，自动失效」）；存在 `task_id=0` 留痕历史（demo 业务码 `pass`、标签「通过」，message「流程由该终止结束事件终止」）；`veto=0` 时两分支正常汇聚、实例已完成 |

## 网关综合复测路径（`demo_gateway_classic` / `demo_gateway_mimic`）

两个定义节点与连线完全一致（只差设计器模式），是排他 / 并行 / 包容三类网关的复测主用定义。导入方式与整理见下方「定义整理」。

节点与办理人（`permissionFlag` 已归一化为用户名；`nodeRatio` 0=或签、100=会签、其他=票签比例）：

```text
开始 → 申请人（无 permissionFlag：任务无指定办理人，任意带 X-User-Name 的用户都能办理）
 → 排他网关：gt@@day|3 → 大于三天(approver1) ／ 无条件兜底 → 小于三天(admin)
 → 第四个（admin + approver1，nodeRatio=100 → 会签，两人都通过）
 → 排他网关（单出口，仅汇合两条分支）
 → 并行网关 → 并行一(admin, 或签) ／ 并行一同级(admin, 或签) ／ 并行二(admin + approver1 + approver2, nodeRatio=60 → 票签，3 人达 60% 即 2 票通过)
 → 并行汇聚网关
 → 包容网关：gt@@day|3 → 包容day大于三(approver1) ｜ lt@@day|3 → 包容小于三(admin) ｜ 无条件 → 包容3(admin)、包容4(approver2)
 → 包容汇聚网关 → 最后一个(admin) → 结束
```

发起（`day` 是两条条件出口引用的变量，缺失时通过首节点会报「跳转条件不能为空」）：

```bash
BASE=http://localhost:8080
curl -sS -X POST "$BASE/api/instances" -H 'Content-Type: application/json' -H 'X-User-Name: approver1' \
  -d '{"flowCode":"demo_gateway_classic","businessId":"WF-GW-<unix秒>","variables":{"day":4}}'
```

预期活动任务集合（到达对应网关后、办理前；以 `flow_task` 为准，不要用流程图着色判断）：

| 变量 | 排他网关 | 包容网关 |
| --- | --- | --- |
| `day=4` | 只生成「大于三天」 | 「包容day大于三」+「包容3」+「包容4」（**无条件出口始终生效**） |
| `day=2` | 只生成兜底「小于三天」 | 「包容小于三」+「包容3」+「包容4」 |
| `day=3` | 只生成兜底「小于三天」 | 只剩「包容3」+「包容4」（两条条件都不命中） |

要点：

- 走到包容网关前必须先走完前缀链路：申请人 → 排他分支 → 第四个（会签，两人的待办都要办）→ 单出口排他 → 并行三分支（含票签节点「并行二」）→ 并行汇聚。每一步办完都重新查活动任务。
- 两条条件出口 `gt` / `lt` 互斥，本定义不能证明「多个条件出口同时命中」，详见 `regression-cases.md`。
- 同一场景可再用 `demo_gateway_mimic` 跑一遍，确认仿钉钉模式导出的定义行为一致。

## 定义整理（导入 / 发布 / 全删全导）

定义改了内容、出现同编码多版本（导出文件名会带 `_版本号` 后缀）或误删后，用脚本恢复：

```bash
# 导出备份当前定义 -> 逐个删除 -> 按 resources/json 目录逐个导入 -> 逐个发布
CONFIRM=YES bash scripts/reimport-definitions.sh
# 覆盖默认值：BASE=http://localhost:8081 JSONDIR=/path/to/json DUMPDIR=/tmp/bak
```

脚本会拒绝在存在流程实例时执行（避免删掉仍被引用的定义），并把删除前的定义导出到 `DUMPDIR` 作为回滚料。存在实例时先按业务口径清理实例（`DELETE /api/instances/{id}` 或批量删除，见本文档开头）或换一个库复测，不要直接清表、也不要在有实例时强行绕过引擎删除定义。手工等价步骤：

```bash
curl -sS "$BASE/api/definitions?pageNum=1&pageSize=200"          # 取 id / flowCode / version
curl -sS -o "$BAK/<flowCode>_v<version>_<id>.json" "$BASE/api/definitions/<id>/export"
curl -sS -X DELETE "$BASE/api/definitions/<id>"                  # 逐个删除
curl -sS -X POST "$BASE/api/definitions/import" -F "file=@<flowCode>.json"
curl -sS -X PUT "$BASE/api/definitions/<返回的id>/publish"       # 不发布则不能发起 / 定时启动 / 消息启动
```

## 事件与内置定时器准备

Demo 依赖本地 Maven 仓库里的 `warm-flow` 坐标；先安装当前反应堆，再启动 Demo：

```bash
mvn clean install -DskipTests                     # 仓库根，让 Demo 用上待测代码
# 已存在的库执行增量升级（勿重建库/删表）：flow_node 4 个事件列 + flow_event_wait 表
docker exec -i mysql mysql -uroot warm-flow < sql/mysql/v1-upgrade/warm-flow_<version>.sql
# 启动时开启内置定时器（默认关闭），缩短扫描间隔便于观察
cd warm-flow-demo/warm-flow-demo
mvn spring-boot:run -Dspring-boot.run.arguments="--warm-flow.timer-enabled=true --warm-flow.timer-scan-interval=1000 --warm-flow.timer-lease-seconds=30"
# 启动日志应出现：Warm-Flow 内置定时器已启动，扫描间隔 1000ms
```

事件节点统一用 `nodeType=1`（`BETWEEN`），事件字段直接放在节点上；导入接口 `POST /api/definitions/import`（multipart `file`）。一个中间定时捕获节点示例：

```json
{
  "nodeType": 1,
  "nodeCode": "tc",
  "nodeName": "等待3秒",
  "nodeRatio": "0",
  "coordinate": "288,198|288,198",
  "formCustom": "N",
  "ext": "[]",
  "eventType": "TIMER_CATCH",
  "nodeConfig": "{\"event\":{\"timerType\":\"AFTER\",\"timerValue\":\"PT3S\"}}",
  "skipList": [
    {"nowNodeCode": "tc", "nextNodeCode": "t2", "skipType": "PASS", "coordinate": "338,198;398,198"}
  ]
}
```

要点：

- `TIMER_START` 放在固定 `START` 之后；发布定义即创建定义级等待行（`instance_id` 为空），到期自动建实例。
- `TIMER_BOUNDARY` 必须带 `attachedToNodeCode`（宿主任务节点编码）且 `interrupting=true`（第一阶段只支持中断型），节点自身另有出口指向边界后处理节点。
- 边界中断后的流程图快照（`flow_instance.def_json`）：被中断的宿主节点补记为**已办理**（`status=2`，与终止链一致），不得残留 `status=1`「待办理」；边界事件节点与边界出口后继按正常跳转置状态。
- `nodeConfig` 与 `ext` 是两处：事件配置读 `node_config.event`，自定义扩展属性在 `ext`，不要混用；**节点默认变量是三处的第三处**，写在 `node_config.variables` 顶层（不属于 `event` 段），任何节点都能配，旧写法 `node_config.event.variables` 仍会被自动回落兼容。

## 数据库核对模板

```sql
SELECT id, definition_id, instance_id, node_code, node_name, flow_status, del_flag
FROM flow_task
WHERE instance_id = <instanceId>
ORDER BY id;

SELECT id, instance_id, node_code, node_name, flow_status, task_id, message
FROM flow_his_task
WHERE instance_id = <instanceId>
ORDER BY id;
-- 结束事件留痕：task_id=0 的行（TERMINATE_END / MESSAGE_END 到达时写入），按 node_code 定位是哪个结束节点

SELECT *
FROM flow_user
WHERE associated IN (<taskIds>)
ORDER BY id;

-- 事件等待记录：只把 del_flag='0' 视为活动等待
SELECT id, definition_id, instance_id, node_code, event_type, status,
       next_trigger_at, retry_at, retry_count, repeat_count, attached_task_id, del_flag, last_error
FROM flow_event_wait
WHERE instance_id = <instanceId>          -- 定义级定时启动等待的 instance_id 为空，改用 definition_id 查
ORDER BY id;
```

实际字段以当前数据库 `DESCRIBE` 结果为准，不凭旧 SQL 记忆字段名。

Shell 批量测试时，活动任务 ID 要逐行保存，例如：

```bash
while IFS= read -r task_id; do
  curl -sS -X POST "$base/api/tasks/pass" \
    -H "X-User-Name: $user" \
    -H 'Content-Type: application/json' \
    -d "{\"user\":\"$user\",\"taskId\":$task_id}"
done < <(docker exec mysql mysql -uroot -proot warm-flow -Nse \
  "SELECT id FROM flow_task WHERE instance_id=<instanceId> AND del_flag='0'")
```

`X-User-Name` 与 body 的 `user` 必须一致，缺请求头会因权限为空被判「无权限」。

## 测试报告模板

```text
环境：Demo URL / 数据库 / 流程定义编码和版本
场景：
输入：业务号、变量、操作用户、请求体摘要
实例：
任务流转：
数据库核对：
结果：通过 / 失败
失败现场：
```
