package org.dromara.warm.plugin.modes.sb.helper;

import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.plugin.modes.sb.annotation.FlowExpressionBean;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 收集 {@link FlowExpressionBean} 声明，合并进表达式 Bean 白名单。
 * <p>
 * 单例就绪后按名称遍历 Bean，类级注解授权全部 public 实例方法，方法级注解仅授权标注方法；
 * 授权结果为静态快照，启动后不再变化。配置与注解合并时方法列表取并集。
 *
 * @author may
 * @since 2.1.1
 */
@Configuration
public class ExpressionBeanScanner implements ApplicationContextAware, SmartInitializingSingleton {

    private static ApplicationContext applicationContext;

    /**
     * 按上下文隔离的注解授权快照。同一 JVM 可能承载多个 warm-flow 上下文，
     * 不能把某个上下文的授权应用到另一个上下文（Bean 名可能相同）。
     */
    private static final Map<ApplicationContext, Map<String, Set<String>>> SNAPSHOTS =
        Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        ExpressionBeanScanner.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        collect();
    }

    /**
     * 供测试与宿主手动触发扫描。
     */
    public static void collect() {
        Map<String, Set<String>> collected = new HashMap<>();
        ListableBeanFactory factory = getBeanFactory();
        // ApplicationContext 本身不是 DefaultSingletonBeanRegistry，需取底层 BeanFactory 才能枚举手动注册的单例。
        if (applicationContext instanceof ConfigurableApplicationContext context) {
            factory = context.getBeanFactory();
        }
        // 定义列表 + 已注册单例列表取并集，覆盖手动 registerSingleton 的 Bean。
        Set<String> names = new HashSet<>(List.of(factory.getBeanDefinitionNames()));
        if (factory instanceof DefaultSingletonBeanRegistry registry) {
            names.addAll(List.of(registry.getSingletonNames()));
        }
        for (String name : names) {
            Class<?> type;
            Object singleton = null;
            try {
                // 不初始化单例：仅读类型元数据，避免为收集授权而提前实例化业务 Bean。
                type = factory.getType(name, false);
                if (factory instanceof DefaultSingletonBeanRegistry registry) {
                    singleton = registry.getSingleton(name);
                }
            } catch (Exception e) {
                continue;
            }
            if (type == null) {
                continue;
            }
            // JDK 动态代理的 ClassUtils#getUserClass 只解 CGLIB，需从已创建的单例取真实目标类。
            Class<?> userClass = singleton != null ? AopUtils.getTargetClass(singleton)
                : ClassUtils.getUserClass(type);
            FlowExpressionBean classAnnotation = AnnotatedElementUtils.findMergedAnnotation(userClass, FlowExpressionBean.class);
            if (classAnnotation != null) {
                if (classAnnotation.delegate()) {
                    // 只登记委托：空方法集合，等价于 YAML 中的 `bean: []`。
                    collected.putIfAbsent(name, new HashSet<>());
                } else {
                    collectPublicMethods(userClass, collected.computeIfAbsent(name, key -> new HashSet<>()));
                }
                continue;
            }
            // AOP 代理上方法级注解可能被代理类遮蔽，需按原始类的方法查找。
            for (Method method : userClass.getMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                    || SafeMethodResolver.isForbidden(method.getName())
                    || !AnnotatedElementUtils.hasAnnotation(method, FlowExpressionBean.class)) {
                    continue;
                }
                collected.computeIfAbsent(name, key -> new HashSet<>()).add(method.getName());
            }
        }
        SNAPSHOTS.put(applicationContext, collected);
    }

    private static void collectPublicMethods(Class<?> type, Set<String> methods) {
        for (Method method : type.getMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())
                && !method.isBridge() && !method.isSynthetic()
                && method.getDeclaringClass() != Object.class
                // 类级注解会收集 Bean 的全部公开方法；危险方法名直接跳过，
                // 避免一个名为 exec/invoke 的公开方法让整份授权策略构造失败。
                && !SafeMethodResolver.isForbidden(method.getName())) {
                methods.add(method.getName());
            }
        }
    }

    /**
     * 返回指定上下文的注解授权快照；未扫描过该上下文时返回空，保持默认拒绝。
     */
    public static Map<String, Set<String>> getAnnotatedAuthorizations(ApplicationContext context) {
        Map<String, Set<String>> copy = new HashMap<>();
        Map<String, Set<String>> snapshot = context == null ? null : SNAPSHOTS.get(context);
        if (snapshot != null) {
            snapshot.forEach((name, methods) -> copy.put(name, Set.copyOf(methods)));
        }
        return copy;
    }

    /**
     * 返回当前扫描上下文的授权快照，供测试与无上下文调用方使用。
     */
    public static Map<String, Set<String>> getAnnotatedAuthorizations() {
        return getAnnotatedAuthorizations(applicationContext);
    }

    private static ListableBeanFactory getBeanFactory() {
        if (null == applicationContext) {
            throw new FlowException("No ApplicationContext injected, maybe not in the Spring environment?");
        }
        return applicationContext;
    }

    /**
     * 仅供测试注入上下文后清理快照。
     */
    static void reset() {
        SNAPSHOTS.clear();
        applicationContext = null;
    }
}
