# 前端源码边界与同步检查

## 职责边界

- `warm-flow-ui` 是唯一设计器源码工程，负责经典模式、仿钉钉模式、iframe 参数和 `postMessage` 协议。
- 构建产物由 `warm-flow-plugin-vue3-ui` 打包为 jar 内静态资源，业务系统通过 `/warm-flow-ui/index.html` 集成。
- 流程 JSON 字段、状态或接口变更必须保持向后兼容，并同步验证设计器、流程图和 Demo。

## 共享语义检查

| 语义 | 位置 |
| --- | --- | --- |
| 流程 JSON 转换 | `warm-flow-ui/src/components/design/common/js/tool.js` |
| 节点 / 边模型 | `warm-flow-ui/src/components/design/classics/js`、`mimic/js` |
| 数据接口 | `warm-flow-ui/src/api` |
| 主题变量 | `warm-flow-ui/src/config/themeConfig.js` |

```bash
cd warm-flow-ui && npm run build:prod && npm run sync:plugin
mvn -pl warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui -am -DskipTests clean package
```

修改流程语义后，必须回归经典模式、仿钉钉模式、保存回写和流程图查看。
