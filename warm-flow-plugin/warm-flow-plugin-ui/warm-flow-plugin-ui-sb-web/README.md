# 设计器接口安全接入

`warm-flow-plugin-ui-sb-web` 暴露流程设计器所需的 `/warm-flow/**` 接口和 `/warm-flow-ui/**` 静态资源。
**引擎不内置账号体系，也不会校验登录**：这些接口是否受保护完全取决于宿主应用的网关、过滤器或
Spring Security 配置。未接入认证时，任何能访问服务端口的人都可以读取、保存流程定义并办理任务。

**本版本起设计器数据接口默认拒绝**：宿主必须实现 `org.dromara.warm.flow.ui.service.DesignerAuthHandler`
并注册为 Spring Bean，否则 `/warm-flow/**` 全部返回 401。静态资源 `/warm-flow-ui/**` 放行。
旧版的 `warm-flow.ui.token-name` URL/token 透传机制与 `/warm-flow-ui/config` 接口已移除（破坏性变更），
认证统一走 Cookie 或网关注入头，由 `DesignerAuthHandler.isAuthorized()` 做登录级裁决——
SPI 只有一个方法，读/写/办理统一「已登录即可」，更细的权限由宿主网关或业务接口注解把关。

## 暴露的接口与敏感级别

| 接口 | 方法 | 敏感级别 | 建议 |
| --- | --- | --- | --- |
| `/warm-flow/save-json` | POST | 高（写入流程定义） | 仅流程管理员，校验 CSRF |
| `/warm-flow/form-content/{id}` | GET | 中（读取表单配置） | 登录用户 |
| `/warm-flow/form-content` | POST | 中（写入表单配置） | 流程管理员 |
| `/warm-flow/execute/load/{taskId}` | GET | 中（读取任务上下文） | 登录用户且为任务相关人 |
| `/warm-flow/execute/hisLoad/{taskId}` | GET | 中（读取历史任务） | 登录用户且为任务相关人 |
| `/warm-flow/execute/handle` | POST | **高（直接办理任务）** | 必须与引擎任务权限一致，校验 CSRF |
| `/warm-flow/query-def`、`/query-flow-chart/{id}` | GET | 中（读取流程定义） | 登录用户或流程管理员 |
| `/warm-flow/handler-type`、`handler-result`、`handler-feedback`、`handler-dict` | GET | 中（枚举用户/角色/部门） | 登录用户 |
| `/warm-flow/published-form`、`node-ext`、`listener-list` | GET | 低（设计器元数据） | 登录用户 |
| `/warm-flow-ui/**` | GET | 低（静态页面） | 可匿名，但页面内的数据请求仍走上面的接口 |

宿主自行扩展的设计器相关接口（发布、删除、导入、复制等）同样必须鉴权。

## 最小接入方式

### 1. Spring Security（推荐，宿主已有安全框架时）

```java
http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/warm-flow-ui/**").permitAll()
        .requestMatchers(HttpMethod.POST, "/warm-flow/save-json", "/warm-flow/form-content").hasRole("FLOW_ADMIN")
        .requestMatchers("/warm-flow/**").authenticated()
        .anyRequest().authenticated())
    .csrf(csrf -> csrf.ignoringRequestMatchers("/warm-flow-ui/**"));
```

`/warm-flow/execute/handle` 还需要在业务层校验「当前登录人 == 任务办理人」，不能只依赖登录状态。

### 2. 轻量拦截器（不引入 Spring Security 时）

```java
registry.addInterceptor(new AuthInterceptor())
    .addPathPatterns("/api/**", "/warm-flow/**")
    .excludePathPatterns("/api/auth/login", "/warm-flow-ui/**");
```

拦截器内校验会话或签名 Cookie，失败返回 401；成功后把当前用户写入宿主上下文，供
`PermissionHandler`、监听器和业务服务使用。**不要从 `X-User-Name` 之类的请求头直接认定身份**，
该头可以被任意伪造。

### 3. 网关 / 反向代理

在网关层统一要求 `Authorization` 或会话 Cookie，并把用户身份透传给后端。设计器接口与业务接口
使用同一套认证；仅靠"设计器只在后台菜单里"不等于接口受保护。

## 内嵌设计器与 Cookie

- 设计器通过 iframe 加载同源的 `/warm-flow-ui/index.html?id=...`，同源 Cookie 自动携带，前端零配置。
- 登录后签发 `HttpOnly + SameSite` Cookie，凭证不暴露给 JS；`tokenName` URL 透传机制与 `/warm-flow-ui/config` 接口已删除。
- 前后端分域 / 微服务部署：Cookie 的 Domain/Path 需覆盖流程服务，或由网关认证后注入内部请求头，
  `DesignerAuthHandler` 实现里读取该头裁决——SPI 接口不变，只是取值来源不同。
- 已登录用户在 iframe 中执行的保存、办理请求同样要按当前用户做权限判断，不能因为"来自设计器"而放行。
- `DemoDesignerAuthHandler`（warm-flow-demo）是完整参考实现。

## 与表达式沙箱的关系

这是两层独立防护，需要同时打开：

- **设计器接口认证**：控制"谁可以提交、修改流程定义"。
- **表达式沙箱**（见 `warm-flow-plugin-modes-sb/README.md`）：控制"已提交的表达式能调用什么"。

只做其中一层都不够：接口裸奔时攻击者可以篡改审批流转；表达式无沙箱时，任何能打开设计器的人都能
借表达式触达宿主 Bean。

## 上线检查清单

- [ ] `/warm-flow/**` 全部要求认证，匿名请求返回 401/403
- [ ] 保存、表单写入、发布等写操作仅流程管理员可用
- [ ] `/warm-flow/execute/handle` 校验当前用户确实有权办理该任务
- [ ] 写接口启用 CSRF 防护或等效的同源/令牌校验
- [ ] 身份来自服务端会话或签名令牌，而不是可伪造的请求头
- [ ] 设计器与业务接口使用同一套认证，不单独暴露到公网
- [ ] 已按 `warm-flow-plugin-modes-sb/README.md` 配置表达式 Bean 白名单
