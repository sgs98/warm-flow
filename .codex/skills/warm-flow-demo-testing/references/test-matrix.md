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

## 数据库核对模板

```sql
SELECT id, definition_id, instance_id, node_code, node_name, flow_status, del_flag
FROM flow_task
WHERE instance_id = <instanceId>
ORDER BY id;

SELECT id, instance_id, node_code, node_name, flow_status
FROM flow_his_task
WHERE instance_id = <instanceId>
ORDER BY id;

SELECT *
FROM flow_user
WHERE associated IN (<taskIds>)
ORDER BY id;
```

实际字段以当前数据库 `DESCRIBE` 结果为准，不凭旧 SQL 记忆字段名。

Shell 批量测试时，活动任务 ID 要逐行保存，例如：

```bash
while IFS= read -r task_id; do
  curl -sS -X POST "$base/api/tasks/pass" \
    -H 'Content-Type: application/json' \
    -d "{\"user\":\"admin\",\"taskId\":$task_id}"
done < <(docker exec mysql mysql -uroot -proot warm-flow -Nse \
  "SELECT id FROM flow_task WHERE instance_id=<instanceId> AND del_flag='0'")
```

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
