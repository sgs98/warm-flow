package org.dromara.warm.flow.ui.service;

/**
 * 设计器接口鉴权扩展点。
 * <p>
 * {@code /warm-flow/**} 数据接口的强制裁决点：宿主实现本接口并注册为 Bean 后，所有设计器
 * 数据接口先经过这里判断；容器中不存在实现时，数据接口一律拒绝（401），静态资源不受影响。
 * <p>
 * 只做登录级鉴权，不区分读/写/办理：更细的操作权限由宿主自己的网关、拦截器或业务接口
 * 注解把关。本接口不绑定凭证来源：单体应用可读会话/Cookie，微服务可读网关注入的内部
 * 请求头，由实现方决定如何识别当前请求的操作者。
 *
 * @author may
 * @since 2.1.2
 */
@FunctionalInterface
public interface DesignerAuthHandler {

    /**
     * 当前请求是否已认证。身份来自宿主的会话、Cookie 或网关注入头，由实现方决定。
     *
     * @return 已认证返回 {@code true}
     */
    boolean isAuthorized();
}
