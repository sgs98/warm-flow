# 流程设计器认证边界核对

## 核对结论

仓库内 UI 插件的 `POST /warm-flow/save-json` Controller 只映射请求并调用 `WarmFlowService.saveJson`，未在该 Controller 或调用服务中实现登录认证/授权。它是否被保护依赖使用方部署提供的 Spring Security/filter/gateway 等边界。

本仓库 Demo 的 `DemoUserInterceptor` 只从 `X-User-Name` 请求头设置流程操作用户，该头不是身份验证；`WebConfig` 仅将该拦截器注册到 `/api/**`，没有覆盖 `/warm-flow/save-json`。因此 Demo 自身的用户拦截逻辑不能证明 save-json 已认证。

这说明可能是接口缺少部署侧保护，而非已有登录校验被绕过；仅凭当前工作区和截图无法确认真实攻击路径或部署配置。

## 影响

表达式白名单修复可限制恶意流程表达式的能力，但不会给保存接口增加登录保护，也不会防止匿名用户提交不含表达式的流程变更。生产宿主应单独给设计器读写/发布/导入接口配置认证与细粒度授权，并核查服务器访问日志、认证过滤器链和外层网关策略。
