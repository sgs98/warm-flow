# SDD ledger — plan: docs/superpowers/plans/2026-11-11-spel-security-plan.md

Pre-flight: 单一耦合任务，无跨任务接口。
Ruling: 在用户现有 dev 工作区原位实施，不建立新 worktree或提交 — 明确保护并排除工作区其他功能变更 — 验证需聚焦本次模块以免混淆。
Ruling: 执行记录保留在 .codex，设计/计划移动到 docs/superpowers 的对应目录；不运行未列出的技能或需要提交的辅助脚本 — 以根 AGENTS 和用户授权为准 — 无 git 提交作为执行记录。
Task 1: RED — mvn -o -pl warm-flow-plugin/warm-flow-plugin-modes/warm-flow-plugin-modes-sb -am -Dtest=SpelSecurityTest -Dsurefire.failIfNoSpecifiedTests=false test：15 tests, 12 failures, 0 errors；原注入内存替身未被拒绝。
Task 1: GREEN — 同命令，末轮 55 tests, 0 failures/errors；null 配置、静态调用参数副作用、只读投影 RED→GREEN。Boot 4.0.8/Spring 7.0.9/JUnit 6.0.3/Java 17 的隔离 javac + JUnit Launcher 55 tests 全部通过。默认上游反应堆 core 498 + JSON v1 15 + 当时表达式 54 tests 全部通过。
Ruling: Boot 4 上游反应堆被 Jackson central 离线缓存记录阻断；不修改版本/缓存元数据，使用已缓存的精确 Boot 4、Spring 7 和 JUnit 6 依赖隔离编译并运行全部改动代码 — 验证本次代码兼容性 — 不能据此声称完整 Boot 4 上游反应堆通过。
Ruling: 方法白名单开放入口上的公开重载集合，可能的方法签名无法从 SpEL AST 唯一确定 — 最小实现先校验名称至少对应一个公开实例方法、运行期由 Spring resolver 绑定实参，并明确将所有重载/副作用责任交给宿主审查；返回值限制纯数据，单一服务表达式才可返回 ServiceTaskHandler — 若宿主未审查其他重载，可能授权了比预期更宽的入口；迁移文档必须显著警告。
Final: fixed 隐式结果 equals/toString 绕过 — rejectsImplicitMethodsOnComplexBeanResults RED→GREEN；方法执行后验证纯数据，服务委托只允许单一服务表达式；补 opaque 参数转换和委托隔离测试。
Final: fixed FactoryBean 预检副作用 — validatesAllSyntaxBeforeFactoryBeanMetadataLookup RED→GREEN；完整结构/授权预检后才查类型（allowFactoryBeanInit=false），配置引用在容器初始化时解析，求值不再按类型扫描配置 Bean。
Final: fixed Map.Entry 只读投影回归 — keepsPureMapEntrySelectionAndProjection 和 keepsHashMapTreeEntryProjectionReadOnly RED→GREEN；仅放行确定的 JDK Entry 实现且递归确认 key/value 为纯数据。
Task 1: complete — 默认反应堆 core 498 + JSON v1 15 + 表达式 65 tests 全部通过；精确 Boot 4.0.8/Spring 7.0.9/JUnit 6.0.3 隔离编译、运行 65 tests 全部通过；git diff --check 通过。未提交，未修改认证边界。
Task 2（注解授权）: 新增 FlowExpressionBean（TYPE/METHOD）+ ExpressionBeanScanner（单例就绪后扫描），与 YAML 合并取并集；默认拒绝不变。RED→GREEN：注解类级/方法级、YAML 并集、危险方法仍拦截、快照隔离。默认反应堆 core 498 + JSON v1 15 + 表达式 67 tests 全部通过；Boot 4 隔离 67 tests 全部通过。用户额外要求：新增类不加 license 头部、@author 使用 may。
Ruling: 扫描器最初只遍历 getBeanDefinitionNames()，手动 registerSingleton 的 Bean 被漏掉；且 ApplicationContext 本身不是 DefaultSingletonBeanRegistry — 改为先取 ConfigurableApplicationContext 的底层 BeanFactory，再合并定义名与单例名 — 不改变生产 refreshed 上下文行为，测试与手动单例注册可被识别。
已提交 8fb4207「feat: 支持退回后再次提交至退回节点」（62 文件），表达式改动与会话文档未混入。
复查（2026-11-11）发现并修复 3 处边界问题：
- JDK 动态代理 Bean 的类级/方法级注解识别不到（ClassUtils.getUserClass 只解 CGLIB）→ 从已创建单例取 AopUtils.getTargetClass；新增 spring-aop 编译依赖。
- 类级注解收集到名为 exec/invoke 的公开方法会让整份授权策略构造失败并拖垮所有表达式 → 扫描时跳过危险方法名；方法级注解同样跳过。
- Collections.unmodifiable*/Synchronized* 常见容器被当成复杂对象 → 加入只读数据容器白名单（内容仍递归校验）。
- ANNOTATED 改为 ConcurrentHashMap；补充 Map 下标读取测试。
复查后验证：默认反应堆 core 498 + JSON 15 + 表达式 71 全部通过；Boot 4.0.8/Spring 7.0.9 隔离编译运行 71 tests 全部通过。
第二轮查缺补漏（表达式矩阵探测）：括号、三元、内联 Map、索引、选择/投影、字符串操作、between 均正常；`T()`/`instanceof`/`getClass`/`entrySet`/`getBytes` 及根属性链按预期拒绝。补两处误伤：幂运算 `^` 加入数据运算符；已授权方法返回纯数据后允许只读链式操作（运行期访问器仍强校验纯数据，复杂返回值仍中断）。验证：默认反应堆 core 498 + JSON 15 + 表达式 72 全部通过；Boot 4.0.8/Spring 7.0.9 隔离 72 tests 全部通过。
第三轮独立审查：未发现沙箱逃逸；确认三处问题并处理 —
- 幂运算内存耗尽：指数限制为绝对值 ≤64 的整数字面量，变量指数拒绝；补测试。
- 多 Spring 上下文注解快照串台：快照改为按 ApplicationContext 隔离（WeakHashMap），授权只作用于对应上下文；补双上下文测试。
- 新类缺少 Apache license 头：用户明确要求新增类不加头部（`类不用加头部说明，作者是may`），保持现状并记录为决定。
- 已知残余（不修改）：授权方法可在选择/投影谓词中按元素调用（属授权语义，宿主责任）；策略每次求值重建（性能优化项）；SafeTypeLocator 存在改动前的重复注释与死代码。
演示环境实测（warm-flow-demo，Spring Boot 3.5 + MySQL + 已安装 2.1.1 本地构件）：
- 正例：`demo_service_task` 三种服务任务（class / `#{@demoDelegateServiceTask}` / `@demoOrderService.createOrder/evalDay`）执行成功，day=5 驱动网关；`#{@demoTaskListener.notify(#listenerVariable)}` 在节点 finish 监听器真实触发。
- 注解路径：DemoOrderService 改用 `@FlowExpressionBean` 且移除 YAML 配置后，流程仍执行成功。
- 负例：permissionFlag 注入 `#{@springUtils.getBean("dataSource").getConnection()}` 发起返回 400「Bean 未授权：springUtils」，无实例落库；监听器注入同形态在办理时被拒，实例保持 waiting、任务 pending，负面状态未被污染。
- Demo 改动仅用于验证：application.yml 白名单与 DemoOrderService 注解，保持在工作区。

