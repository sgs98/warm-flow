package org.dromara.warm.plugin.modes.sb.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 Spring Bean 或其方法可以被流程表达式（办理人、条件、票签、监听器、服务任务）调用。
 * <p>
 * 类级注解：授权该 Bean 的全部 public 实例方法；方法级注解：仅授权标注的方法。
 * 注解与 {@code warm-flow.spel-allowed-bean-methods} 配置合并生效，未声明的 Bean 默认拒绝。
 * 该注解属于对外契约，发布后不可随意改名或移动包。
 * <p>
 * 授权仅开放表达式入口，{@code getClass}/{@code exec}/{@code invoke} 等危险方法、
 * 反射、写操作与基础设施调用仍被表达式沙箱拦截；宿主仍需审查入口的业务副作用。
 *
 * @author may
 * @since 2.1.1
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface FlowExpressionBean {
}
