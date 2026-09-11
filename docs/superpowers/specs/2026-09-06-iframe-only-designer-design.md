# Warm-Flow 设计器统一为 iframe 交付设计

## 1. 目标

Warm-Flow 对外只提供 iframe 设计器集成方式，不再发布或承诺 npm 组件包能力。后续只维护 `warm-flow-ui` 这一套 iframe 设计器源码，移除 `warm-flow-vue-designer`，避免两套工程重复开发、重复修复和功能漂移。

## 2. 最终架构

```text
唯一设计器源码
  └─ 构建独立 SPA 静态资源
       └─ warm-flow-plugin-vue3-ui 打入 jar
            └─ /warm-flow-ui/index.html
                 └─ 业务系统通过 iframe 使用
```

保留现有 `/warm-flow-ui/**` 静态资源路径、URL 参数、后端接口与 iframe 通信协议，避免现有 Warm-Flow 使用方升级后失效。

## 3. 源码归并原则

以 `warm-flow-ui` 作为唯一设计器源码和 iframe 构建基线。它本身就是当前 jar 内置设计器的 SPA 源工程，构建产物直接服务 `warm-flow-plugin-vue3-ui`。

迁移完成后：

- 删除 `warm-flow-vue-designer` 工程及其 workspace、npm 包和专用文档引用。
- `warm-flow-ui` 负责完整设计器功能、iframe 参数、Token、后端地址、主题和 `postMessage`。
- `warm-flow-ui` 的 `dist/` 产物继续同步到 `warm-flow-plugin-vue3-ui` 的静态资源目录。
- 不保留第二套组件库源码或 npm 兼容层。

## 4. 对外能力

### 4.1 保留

- 经典和仿钉钉两种设计模式。
- 流程定义新增、编辑、查看和保存。
- 流程图查看。
- 办理人选择、监听器、节点扩展和按钮权限。
- 现有 `/warm-flow-ui/index.html` 访问路径。
- 现有查询参数，包括流程定义 ID、页面类型和认证信息。
- iframe 关闭、保存完成等宿主通信行为。
- `warm-flow-plugin-vue3-ui` 的 jar 内静态资源交付。

### 4.2 下线

- `@dromara/warm-flow-designer` npm 包及其源码工程。
- 面向第三方宿主的组件 Props、Events、Hooks、DataProvider 和 UI Adapter 兼容承诺。
- npm 消费 Demo 与 npm 发布脚本、发布文档。
- `warm-flow-demo` 对 workspace npm 包的依赖。
- npm 组件 Props、Events、Hooks、DataProvider 和 UI Adapter 接口。

## 5. Demo 调整

`warm-flow-demo` 改为验证真实 iframe 集成：

- 流程定义列表中的“设计”打开 iframe 页面。
- iframe URL 指向后端提供的 `/warm-flow-ui/index.html`。
- 定义 ID、Token、主题等通过既有 URL 参数传递。
- 保存或关闭通过 `postMessage` 通知宿主页面刷新或返回列表。
- Demo 不再依赖 `@dromara/warm-flow-designer`，不再调用 `setDataProvider`、`setUiAdapter` 或直接渲染 `FlowDesigner`。

节点按钮权限必须继续由 iframe 设计器调用后端 `nodeExt` 接口获得，保存格式保持：

```json
[
  {
    "code": "ButtonPermissionEnum",
    "value": "copy,back,termination,file"
  }
]
```

## 6. 构建与资源同步

统一工程提供一个明确的 iframe 生产构建命令，产出完整 SPA 静态资源。构建产物同步到：

```text
warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui/
  src/main/resources/warm-flow-ui/
```

同步过程必须满足：

- 先构建到独立 `dist/`，再更新插件资源。
- 不在构建失败时覆盖插件中当前可用资源。
- 清理旧哈希资源时仅限定上述静态资源目录。
- `index.html` 中引用的 JS/CSS 必须全部存在。
- 不改变 Maven 模块、artifactId 或资源访问路径。

## 7. 迁移阶段

### 阶段一：warm-flow-ui 作为唯一源码

- 在 `warm-flow-ui` 补齐或修复 iframe 参数和通信能力。
- 以 `warm-flow-ui` 生成 iframe 静态资源并装入 `warm-flow-plugin-vue3-ui`。
- 对照 `warm-flow-vue-designer` 中已完成的功能逐项迁移必要能力到 `warm-flow-ui`。

### 阶段二：Demo 切换

- Demo 前端从 npm 组件改为 iframe。
- 删除 Demo 的 workspace npm 依赖和 provider 注入。
- 完成设计、保存、按钮权限和关闭回调联调。

### 阶段三：移除 npm 工程

- 从 pnpm workspace 移除 `warm-flow-vue-designer` 及其专用 demo。
- 删除 `warm-flow-vue-designer` 工程，不迁移无关的 npm 专用代码。
- 更新根规则、模块规则、README 和计划文档中的定位。

### 阶段四：资源和功能收尾

- 对照经典模式、仿钉钉模式、表单、流程图、节点权限和接口清单。
- 确认 `warm-flow-ui` 构建完全覆盖插件中的 iframe 资源。
- 删除无引用的 npm 文档、锁文件、脚本和 workspace 配置。

## 8. 错误处理与兼容

- iframe 初始化参数缺失时展示明确错误，不渲染空白画布。
- 后端接口失败时沿用设计器统一消息提示，不返回模拟成功数据。
- `postMessage` 消息必须包含稳定的 `method` 和必要数据；宿主只处理 Warm-Flow 定义的消息。
- 第一阶段保持现有消息格式，若需要增强只能增加字段，不删除旧字段。
- 现有用户仍可通过原 URL 使用设计器，不要求其安装 npm 包或调整前端技术栈。

## 9. 验证标准

### 前端

- TypeScript 类型检查通过。
- iframe SPA 生产构建通过。
- 经典和仿钉钉设计器都能打开。
- 新建、查询、保存、重新打开流程定义正常。
- 节点“权限”页签显示，默认值和保存值正确。
- 浏览器控制台无真实错误。

### 后端与 jar

- `warm-flow-plugin-vue3-ui` 编译和打包通过。
- jar 中包含 `/warm-flow-ui/index.html` 及其引用资源。
- Spring Boot 2、3、4 的 UI Web 适配路径不受影响。
- Demo 后端编译通过，JDK 17 和 JDK 21 均可运行。

### 端到端

- Demo 通过 iframe 打开设计器。
- 保存后定义列表刷新。
- 发布并发起流程后，待办按钮按照节点权限显示。
- 调用未授权动作时后端拒绝，不能仅依赖前端隐藏按钮。

## 10. 非目标

- 不修改 Warm-Flow 核心流程 API、实体字段或数据库结构。
- 不升级 Vue、Element Plus、LogicFlow 或 Spring Boot。
- 不改变正式资源 URL、鉴权协议或后端统一返回结构。
- 不在本次迁移中引入新的 UI 框架。
- 不同时重构与 iframe 迁移无关的设计器业务代码。

## 11. 风险控制

- npm 下线属于对外能力收缩，执行前必须确认尚未正式发布，或明确提供版本迁移说明。
- 删除 `warm-flow-vue-designer` 前必须完成设计器功能清单和 iframe 资源回归。
- 静态资源复制必须可验证，避免 `index.html` 与哈希文件不一致。
- 按钮权限、Long ID 精度和 iframe Token 传递需要纳入回归清单。
- 全过程不执行 `git commit` 或 `git push`，由维护者自行提交。
