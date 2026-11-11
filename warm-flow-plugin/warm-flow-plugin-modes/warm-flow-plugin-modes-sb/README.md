# SpEL 表达式安全配置

Spring 模式的 SpEL 默认不允许引用任何 Spring Bean。推荐直接在业务 Bean 上声明授权，无需改配置：

```java
@FlowExpressionBean          // 类级：该 Bean 全部 public 实例方法可被流程表达式调用
@Service("user")
public class UserServiceImpl { ... }

@FlowExpressionBean          // 方法级：仅该方法可被调用
public String createOrder(ServiceTaskContext context) { ... }

@FlowExpressionBean(delegate = true)   // 类级：仅作为服务任务委托 #{@beanName}
public class DemoDelegateServiceTask implements ServiceTaskHandler { ... }
```

注解由引擎启动时自动扫描并合并进白名单，通常无需 YAML。第三方 jar 中无法改源码的 Bean 仍可用精确配置补充：

```yaml
warm-flow:
  spel-allowed-bean-methods:
    demoTaskListener:
      - notify
    demoDelegateServiceTask: []
```

注解与 `spel-allowed-bean-methods` 合并生效（同一 Bean 的方法取并集）。`spel-allowed-bean-methods` 的 key 只能是精确 Spring Bean 名，value 是获授权的方法名列表。空列表仅允许服务任务的 `#{@beanName}` delegate 表达式取得实现 `ServiceTaskHandler` 的 Bean；它不允许调用 Bean 方法，也不能用于普通表达式或字符串模板。没有默认授权，未登记 Bean 或未登记方法会在表达式执行前失败。一个方法名授权其全部可匹配重载；宿主必须审查所有重载及其参数、返回值、下游副作用。不得授权 Redis、数据库、ApplicationContext、容器、通用代理或其他基础设施 Bean。

授权 Bean 方法的返回值必须是纯数据（基础标量或仅含纯数据的受支持 JDK 容器/数组）；复杂对象不能参与隐式 `equals/toString` 转换。单一服务任务表达式额外允许直接返回 `ServiceTaskHandler`，但不能把该处理器嵌入运算或模板。方法参数不能嵌套其他 Bean 调用，可传纯数据或原始的 `#execution`、`#listenerVariable` 上下文。

表达式仍可使用纯数据流程变量、比较、算术（含幂运算，指数须为绝对值不超过 64 的整数字面量）、受限只读 Map/List/数组访问、选择/投影、模板插值和纯字符串/集合查询方法；已授权方法返回纯数据后可继续做只读操作（如 `.size()`）。构造器、类型引用（含 `instanceof`）、反射、任意变量对象方法、对象属性链、写入和集合变更表达式均拒绝。`#execution`、`#listenerVariable` 等复杂上下文只可作为显式授权业务入口的参数；表达式本身不能调用上下文上的方法。

升级影响：旧版任意 `@bean.method(...)` 与 `#{@delegate}` 需改为给业务 Bean 添加 `@FlowExpressionBean`，或分别加入方法白名单 / 显式配置空集合。上线前审核每个授权入口及其重载；只授权窄小、可审计的业务方法。此白名单控制 SpEL 的入口，不替代设计器保存/发布接口的认证、流程级授权或网络边界。
