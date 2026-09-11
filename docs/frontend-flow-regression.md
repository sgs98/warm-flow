# 前端流程回归基线

用于 iframe 设计器发布前的人工回归。样例覆盖流程 JSON 的关键结构，不改变后端契约。

## 最小流程 JSON

```json
{
  "flowCode": "demo-approval",
  "flowName": "示例审批",
  "modelValue": 0,
  "version": 1,
  "nodeList": [
    {"nodeType": 0, "nodeCode": "start", "nodeName": "开始", "skipList": [{"skipType": "PASS", "nowNodeCode": "start", "nextNodeCode": "approve", "skipName": "提交"}]},
    {"nodeType": 1, "nodeCode": "approve", "nodeName": "审批", "nodeRatio": 1, "skipList": [{"skipType": "PASS", "nowNodeCode": "approve", "nextNodeCode": "end", "skipName": "通过", "skipCondition": "eq@@status|approved"}]},
    {"nodeType": 2, "nodeCode": "end", "nodeName": "结束", "skipList": []}
  ]
}
```

## 回归场景

- 加载新建流程，确认开始、审批、结束节点均出现。
- 打开节点属性，修改办理人、监听器、扩展属性并保存。
- 编辑条件跳转，确认 `skipCondition` 导出后仍存在。
- 添加并行 / 包含网关，验证经典和仿钉钉两种模式均可连线。
- 撤销、重做、只读预览、刷新回显和保存失败提示。
- iframe 工程通过 `npm run build:prod` 构建并同步插件资源，Demo 前端通过 `pnpm typecheck` 与 `pnpm build` 验证。

外部测试仓库不可用时，必须在发布记录中注明未执行状态机和浏览器自动化测试。
