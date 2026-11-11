# SpEL 白名单修复验证

## 最终结果

- 原实现红灯：15 项测试，12 项失败，0 errors；包含原注入形态的纯内存替身未被拦截。
- 修复后 Spring Boot 3.5.16 / Spring 6.2.19：表达式模块 65 项全部通过。上游 core 498 项、JSON v1 15 项也全部通过，默认反应堆总计 578 项。
- Spring Boot 4.0.8 / Spring 7.0.9 / JUnit 6.0.3：用本机缓存的精确依赖与 `javac --release 17` 编译全部 helper、表达式策略、WarmFlowProperties 和同一测试类，再通过 JUnit Launcher 执行，65 项全部通过。
- 独立只读审查确认的三处问题已由覆盖测试 RED→GREEN 修复：隐式 equals/toString 调用、FactoryBean 提前初始化/类型元数据副作用、Map.Entry 只读投影（含 HashMap TreeNode）。没有遗漏的已确认审查发现。
- `git diff --check` 通过。

## Maven 验证命令

聚焦红灯及绿灯：

```bash
mvn -o -pl warm-flow-plugin/warm-flow-plugin-modes/warm-flow-plugin-modes-sb -am -Dtest=SpelSecurityTest -Dsurefire.failIfNoSpecifiedTests=false test
```

默认反应堆最终回归：

```bash
mvn -o -pl warm-flow-plugin/warm-flow-plugin-modes/warm-flow-plugin-modes-sb -am test
```

## 验证限制

Boot 4 完整 Maven 上游反应堆尝试的命令：

```bash
mvn -o -pl warm-flow-plugin/warm-flow-plugin-modes/warm-flow-plugin-modes-sb -am -Dspringboot3.version=4.0.8 -Dtest=SpelSecurityTest -Dsurefire.failIfNoSpecifiedTests=false test
```

该命令在 JSON v1 依赖解析阶段失败：`jackson-core:2.21.5` 的 central 仓库离线缓存记录不满足，未进入表达式模块测试。没有改依赖版本或缓存元数据。隔离 Boot 4 测试验证了本次修改的全部 Java 源文件，但不声称完整 Boot 4 上游反应堆通过；完整 Maven 验证需在依赖缓存满足的环境重跑该命令。

未操作真实 Redis，未核验截图所述生产请求；未修改设计器认证边界、版本或 git 提交。未启动浏览器：此改动是 Java 表达式运行时和测试，不是 UI。

## 兼容性与部署注意

- 旧 Bean 表达式必须显式加入精确 Bean/方法白名单。方法名授权其全部公开可匹配重载，宿主必须审查每个重载及业务副作用；不允许授权容器、万能代理或基础设施。
- Bean 方法返回值须为受支持纯数据；只有单一服务任务表达式可直接返回 `ServiceTaskHandler`。复杂上下文保留原对象参数，但表达式不能调用其方法或 getter。
- 表达式保护不替代设计器接口认证。代码核对发现 Demo 用户拦截器不是身份验证且未覆盖 `/warm-flow/save-json`，生产是否已有外层认证仍未核验。
