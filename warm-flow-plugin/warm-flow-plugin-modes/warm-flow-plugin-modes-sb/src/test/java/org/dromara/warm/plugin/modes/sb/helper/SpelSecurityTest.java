/*
 *    Copyright 2026, may (1742057357@qq.com).
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.dromara.warm.plugin.modes.sb.helper;

import org.dromara.warm.flow.core.service.task.AutoNodeContext;
import org.dromara.warm.flow.core.service.task.ServiceTaskContext;
import org.dromara.warm.flow.core.service.task.ServiceTaskHandler;
import org.dromara.warm.flow.core.spi.ListenerVariable;
import org.dromara.warm.plugin.modes.sb.annotation.FlowExpressionBean;
import org.dromara.warm.plugin.modes.sb.config.WarmFlowProperties;
import org.dromara.warm.plugin.modes.sb.expression.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用纯内存替身验证表达式拒绝时不会操作外部基础设施。
 *
 * @author may
 */
class SpelSecurityTest {
    private static final StaticApplicationContext APP = new StaticApplicationContext();
    private static final Probe PROBE = new Probe();
    private static final RedisStub REDIS = new RedisStub();
    private static final WarmFlowProperties PROPERTIES = new WarmFlowProperties();
    private static final Business BUSINESS = new Business();
    private static final UnsafeResult UNSAFE_RESULT = new UnsafeResult();
    private static final ServiceTaskHandler DELEGATE = context -> PROBE.calls++;

    @BeforeAll
    static void init() {
        APP.getBeanFactory().registerSingleton("probe", PROBE);
        APP.getBeanFactory().registerSingleton("redisTemplate", REDIS);
        APP.getBeanFactory().registerSingleton("springUtils", PROBE);
        APP.getBeanFactory().registerSingleton("warmFlowProperties", PROPERTIES);
        APP.getBeanFactory().registerSingleton("annotatedService", new AnnotatedService());
        APP.getBeanFactory().registerSingleton("annotatedProxy",
            new ProxyFactory(new ProxyAnnotated()).getProxy());
        APP.getBeanFactory().registerSingleton("annotatedWithExec", new AnnotatedWithExec());
        APP.getBeanFactory().registerSingleton("business", BUSINESS);
        APP.getBeanFactory().registerSingleton("delegate", DELEGATE);
        APP.getBeanFactory().registerAlias("probe", "probeAlias");
        APP.getBeanFactory().registerAlias("probe", "businessAlias");
        APP.registerBeanDefinition("lazyBean", new RootBeanDefinition(LazyBean.class));
        APP.registerBeanDefinition("lazyFactory", new RootBeanDefinition(LazyFactory.class));
        APP.getBeanFactory().registerSingleton("factory", new ProbeFactory());
        APP.getBeanFactory().registerSingleton("staticProbe", new StaticProbe());
        ProxyFactory proxyFactory = new ProxyFactory(BUSINESS);
        APP.getBeanFactory().registerSingleton("jdkProxy", proxyFactory.getProxy());
        proxyFactory.setProxyTargetClass(true);
        APP.getBeanFactory().registerSingleton("classProxy", proxyFactory.getProxy());
        new SpelHelper().setApplicationContext(APP);
    }

    @AfterAll
    static void close() {
        APP.close();
    }

    @BeforeEach
    void reset() {
        PROBE.calls = 0;
        REDIS.calls = 0;
        UNSAFE_RESULT.calls = 0;
        PROPERTIES.setSpelAllowedBeanMethods(new LinkedHashMap<>());
    }

    @Test
    void rejectsReportedInjectionWithoutRedisSideEffect() {
        assertThrows(RuntimeException.class, () -> eval(
            "#{@springUtils.getBean('KGJG' + @redisTemplate.connectionFactory.connection.replicaOf('127.0.0.1', 1))}"));
        assertEquals(0, REDIS.calls);
        assertEquals(0, PROBE.calls);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "#{@probe.touch()}",
        "#{@probe}",
        "#{@probe.staticLabel()}",
        "#{#probe.touch()}",
        "#{#probe.class}",
        "#{#probe.getClass()}",
        "#{T(java.lang.String).valueOf(1)}",
        "#{new java.lang.String('x')}",
        "#{#values.put('x', 2)}",
        "#{#values['x'] = 2}",
        "#{#number = 2}",
        "#{#number++}",
        "#{#probe.calls = 2}"
    })
    void rejectsUntrustedCapabilities(String expression) {
        Map<String, Object> values = new HashMap<>(Map.of("x", 1));
        Map<String, Object> variables = new HashMap<>(Map.of("probe", PROBE, "values", values, "number", 1));
        assertThrows(RuntimeException.class, () -> SpelHelper.parseExpression(expression, variables));
        assertEquals(0, PROBE.calls);
        assertEquals(Map.of("x", 1), values);
        assertEquals(1, variables.get("number"));
    }

    @Test
    void keepsVariableArithmeticAndTemplateValues() {
        assertEquals(7, SpelHelper.parseExpression("#{#day + 4}", Map.of("day", 3)));
        assertEquals(true, SpelHelper.parseExpression("#{#day > 2 && #day < 5}", Map.of("day", 3)));
        assertEquals("days=3", SpelHelper.parseExpression("days=#{#day}", Map.of("day", 3)));
    }

    @Test
    void permitsOnlyConfiguredBusinessMethod() {
        allow("businessAlias", "touch");
        assertEquals("ok", eval("#{@businessAlias.touch()}"));
        assertEquals(1, PROBE.calls);
        assertThrows(RuntimeException.class, () -> eval("#{@probe.touch()}"));
        assertEquals(1, PROBE.calls);
        assertThrows(RuntimeException.class, () -> eval("#{@businessAlias.getBean('other')}"));
        assertEquals(1, PROBE.calls);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "#{@probe.touch()}#{@redisTemplate}",
        "#{@probe.touch() + @redisTemplate}",
        "#{@probe.touch()}#{#number = 2}",
        "#{true ? @probe.touch() : @redisTemplate}",
        "#{@probe.touch()}#{#probe.class}",
        "#{@probe.touch()}#{#probe.touch()}",
        "#{@probe.getBean(@probe.touch())}",
        "#{@probe.class}",
        "#{@probe['class']}",
        "#{@probe.touch().getClass()}",
        "#{@probeAlias.touch()}",
        "#{&probe}",
        "#{@lazyBean.touch()}",
        "#{@factory.touch()}"
    })
    void rejectsWholeExpressionBeforeAuthorizedSideEffects(String expression) {
        allow("probe", "touch");
        assertThrows(RuntimeException.class,
            () -> SpelHelper.parseExpression(expression, Map.of("probe", PROBE, "number", 1)));
        assertEquals(0, PROBE.calls);
        assertEquals(0, REDIS.calls);
    }

    @Test
    void rejectsConfiguredStaticCallBeforeItsArgumentsExecute() {
        allow("probe", "touch");
        allow("staticProbe", "staticEcho");
        assertThrows(RuntimeException.class, () -> eval("#{@staticProbe.staticEcho(@probe.touch())}"));
        assertEquals(0, PROBE.calls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "getClass", "exec", "invoke"})
    void rejectsInvalidMethodConfiguration(String method) {
        allow("probe", method);
        assertThrows(RuntimeException.class, () -> eval("#{@probe}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "probe*", "&probe", " "})
    void rejectsInvalidBeanConfiguration(String bean) {
        allow(bean, "touch");
        assertThrows(RuntimeException.class, () -> eval("#{@probe}"));
        assertEquals(0, PROBE.calls);
    }

    @Test
    void nullPolicyDoesNotEnableBeans() {
        PROPERTIES.setSpelAllowedBeanMethods(null);
        assertThrows(RuntimeException.class, () -> eval("#{@probe}"));
    }

    @Test
    void nullMethodSetIsNotDelegateAuthorization() {
        PROPERTIES.getSpelAllowedBeanMethods().put("probe", null);
        assertThrows(RuntimeException.class, () -> eval("#{@probe}"));
    }

    @Test
    void missingPropertiesDefaultToDenied() {
        SpelSecurityPolicy policy = new SpelSecurityPolicy(APP.getBeanFactory(), Map.of());
        assertThrows(RuntimeException.class,
            () -> policy.beanResolver().resolve(new StandardEvaluationContext(), "probe"));
    }

    @Test
    void publicBeanResolverRemainsRestricted() {
        assertThrows(RuntimeException.class,
            () -> SpelHelper.beanResolver().resolve(new StandardEvaluationContext(), "redisTemplate"));
    }

    @Test
    void preservesExplicitServiceTaskDelegate() {
        allow("delegate");
        Object result = new ServiceTaskStrategySpel().eval("#{@delegate}", Map.of());
        assertSame(DELEGATE, result);
        assertThrows(RuntimeException.class, () -> eval("#{@delegate.execute(null)}"));
    }

    @Test
    void passesListenerContextOnlyToAuthorizedMethod() {
        allow("business", "notify");
        ListenerVariable context = new ListenerVariable();
        Map<String, Object> variables = Map.of("listenerVariable", context);
        assertTrue(new ListenerStrategySpel().eval("#{@business.notify(#listenerVariable)}", variables));
        assertEquals("notified", context.getEventType());
        assertThrows(RuntimeException.class,
            () -> SpelHelper.parseExpression("#{#listenerVariable.eventType}", variables));
        assertThrows(RuntimeException.class,
            () -> SpelHelper.parseExpression("#{#listenerVariable.setEventType('bad')}", variables));
    }

    @Test
    void passesServiceTaskContextToAuthorizedMethodAndKeepsWritesInBusinessCode() {
        allow("business", "createOrder");
        Map<String, Object> values = new HashMap<>();
        ServiceTaskContext context = new ServiceTaskContext(new AutoNodeContext(null, null, null, values, null));
        Object result = new ServiceTaskStrategySpel().eval("#{@business.createOrder(#execution)}",
            Map.of("execution", context));
        assertEquals("order-1", result);
        assertEquals("order-1", values.get("orderNo"));
        assertThrows(RuntimeException.class, () -> SpelHelper.parseExpression(
            "#{#execution.setVariable('orderNo', 'bad')}", Map.of("execution", context)));
        assertEquals("order-1", values.get("orderNo"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"business", "jdkProxy", "classProxy"})
    void keepsAuthorizedHandlerMethodsIncludingSpringProxies(String name) {
        allow(name, "handlers");
        assertEquals(List.of("u-1", "u-2"), new HandlerStrategySpel()
            .eval("#{@" + name + ".handlers(#handler)}", Map.of("handler", "u-1")));
    }

    @Test
    void keepsDefaultConditionsAndVoteVariables() {
        assertTrue(new ConditionStrategyDefault().eval("${day > 2 && day < 5}", Map.of("day", 3)));
        assertTrue(new VoteSignStrategyDefault().eval("${passNum >= 2}", Map.of("passNum", 2)));
        assertTrue(new VoteSignStrategySpel().eval("#{#passNum >= 2 && #todoList.size() == 0}",
            Map.of("passNum", 2, "todoList", List.of())));
    }

    @Test
    void keepsReadOnlyDataAccessAndPureMethods() {
        Map<String, Object> variables = Map.of("person", Map.of("name", " Alice "),
            "ids", List.of("u1", "u2"), "array", new int[]{1, 2});
        assertEquals("Alice", SpelHelper.parseExpression("#{#person.name.trim()}", variables));
        assertEquals("u2", SpelHelper.parseExpression("#{#ids[1]}", variables));
        assertEquals(2, SpelHelper.parseExpression("#{#ids.size()}", variables));
        assertEquals(2, SpelHelper.parseExpression("#{#array.length}", variables));
        assertEquals(true, SpelHelper.parseExpression("#{#person.containsKey('name')}", variables));
        assertEquals(List.of(1, 2), eval("#{{1, 2}}"));
        assertEquals(List.of("u1", "u2"), SpelHelper.parseExpression(
            "#{#people.![#this.name]}", Map.of("people", List.of(Map.of("name", "u1"), Map.of("name", "u2")))));
    }

    @Test
    void rejectsObjectsHiddenInCollectionsAndCyclicDataBeforeSideEffects() {
        allow("probe", "touch");
        Map<String, Object> cyclic = new HashMap<>();
        cyclic.put("self", cyclic);
        for (Object data : List.of(Map.of("hidden", REDIS), List.of(REDIS), cyclic, new HostMap())) {
            assertThrows(RuntimeException.class, () -> SpelHelper.parseExpression(
                "#{@probe.touch()}#{#data.size()}", Map.of("data", data)));
            assertEquals(0, PROBE.calls);
            assertEquals(0, REDIS.calls);
        }
    }

    @Test
    void bindsExactBeanAndMethodConfiguration() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
            "warm-flow.spel-allowed-bean-methods.business[0]", "handlers",
            "warm-flow.spel-allowed-bean-methods.business[1]", "notify"));
        WarmFlowProperties bound = new Binder(source)
            .bind("warm-flow", Bindable.of(WarmFlowProperties.class)).get();
        assertEquals(Set.of("handlers", "notify"), bound.getSpelAllowedBeanMethods().get("business"));
    }

    @Test
    void bindsExplicitEmptyDelegateMethodSet() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
            "warm-flow.spel-allowed-bean-methods.delegate", ""));
        WarmFlowProperties bound = new Binder(source)
            .bind("warm-flow", Bindable.of(WarmFlowProperties.class)).get();
        assertTrue(bound.getSpelAllowedBeanMethods().containsKey("delegate"));
        assertEquals(Set.of(), bound.getSpelAllowedBeanMethods().get("delegate"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "#{@business.result() == 'payload'}",
        "#{@business.result() + ''}",
        "prefix=#{@business.result()}",
        "#{@business.result()}"
    })
    void rejectsImplicitMethodsOnComplexBeanResults(String expression) {
        allow("business", "result");
        assertThrows(RuntimeException.class, () -> eval(expression));
        assertEquals(0, UNSAFE_RESULT.calls);
    }

    @Test
    void validatesAllSyntaxBeforeFactoryBeanMetadataLookup() {
        allow("lazyFactory", "touch");
        assertThrows(RuntimeException.class, () -> eval("#{@lazyFactory.touch()}#{T(java.lang.String)}"));
        assertEquals(0, REDIS.calls, "FactoryBean constructor must not run");
        assertEquals(0, PROBE.calls, "FactoryBean metadata getter must not run");
    }

    @Test
    void keepsPureMapEntrySelectionAndProjection() {
        for (Map<String, Integer> map : List.of(Map.of("x", 1), new HashMap<>(Map.of("x", 1)))) {
            Map<String, Object> variables = Map.of("map", map);
            assertEquals(List.of(1), SpelHelper.parseExpression("#{#map.![#this.value]}", variables));
            assertEquals(map, SpelHelper.parseExpression("#{#map.?[value > 0]}", variables));
            assertEquals(List.of("x"), SpelHelper.parseExpression("#{#map.![#this.key]}", variables));
        }
    }

    @Test
    void delegateCannotBeUsedInNonServiceExpressionsOrStringTemplates() {
        allow("delegate");
        assertThrows(RuntimeException.class, () -> eval("#{@delegate}"));
        assertThrows(RuntimeException.class, () -> new ServiceTaskStrategySpel()
            .eval("delegate=#{@delegate}", Map.of()));
    }

    @Test
    void opaqueArgumentsCannotTriggerImplicitStringConversion() {
        allow("business", "handlers");
        assertThrows(RuntimeException.class, () -> SpelHelper.parseExpression(
            "#{@business.handlers(#payload)}", Map.of("payload", UNSAFE_RESULT)));
        assertEquals(0, UNSAFE_RESULT.calls);
    }

    @Test
    void allowsHandlerMethodResultOnlyAsDirectServiceTaskDelegate() {
        allow("business", "delegate");
        assertSame(DELEGATE, new ServiceTaskStrategySpel().eval("#{@business.delegate()}", Map.of()));
        assertThrows(RuntimeException.class, () -> eval("#{@business.delegate()}"));
        assertThrows(RuntimeException.class, () -> new ServiceTaskStrategySpel()
            .eval("value=#{@business.delegate()}", Map.of()));
    }

    @Test
    void keepsHashMapTreeEntryProjectionReadOnly() {
        List<String> keys = List.of("");
        for (int i = 0; i < 4; i++) {
            List<String> next = new java.util.ArrayList<>();
            for (String key : keys) {
                next.add(key + "Aa");
                next.add(key + "BB");
            }
            keys = next;
        }
        Map<String, Integer> map = new HashMap<>(64);
        keys.forEach(key -> map.put(key, 1));
        assertEquals(java.util.Collections.nCopies(16, 1),
            SpelHelper.parseExpression("#{#map.![#this.value]}", Map.of("map", map)));
    }

    @Test
    void annotationAuthorizesClassAndMethodScope() {
        ExpressionBeanScanner.reset();
        new ExpressionBeanScanner().setApplicationContext(APP);
        ExpressionBeanScanner.collect();
        // 类级注解：全部 public 方法可用；未注解 Bean 仍拒绝。
        assertEquals("ok", eval("#{@annotatedService.touch()}"));
        assertThrows(RuntimeException.class, () -> eval("#{@business.touch()}"));
        // 方法级注解：仅授权标注的方法。
        assertEquals(List.of("u-1", "u-2"), new HandlerStrategySpel()
            .eval("#{@business.handlers(#handler)}", Map.of("handler", "u-1")));
        assertThrows(RuntimeException.class, () -> eval("#{@business.notify(new Object())}"));
        // 危险方法即使在注解类上仍被沙箱拦截。
        assertThrows(RuntimeException.class, () -> eval("#{@annotatedService.getClass()}"));
        // 注解与 YAML 配置合并取并集。
        PROPERTIES.getSpelAllowedBeanMethods().put("business", Set.of("createOrder"));
        Object result = new ServiceTaskStrategySpel().eval("#{@business.createOrder(#execution)}",
            Map.of("execution", new ServiceTaskContext(new AutoNodeContext(null, null, null, new HashMap<>(), null))));
        assertEquals("order-1", result);
        ExpressionBeanScanner.reset();
    }

    @Test
    void annotationSnapshotIsDefensiveCopyAndEmptyWithoutScan() {
        ExpressionBeanScanner.reset();
        Map<String, Set<String>> snapshot = ExpressionBeanScanner.getAnnotatedAuthorizations();
        assertTrue(snapshot.isEmpty());
        // 修改返回副本不能影响内部授权快照。
        snapshot.put("x", Set.of("y"));
        assertTrue(ExpressionBeanScanner.getAnnotatedAuthorizations().isEmpty());
    }

    @Test
    void annotationOnJdkProxiedBeanStillAuthorizes() {
        ExpressionBeanScanner.reset();
        new ExpressionBeanScanner().setApplicationContext(APP);
        ExpressionBeanScanner.collect();
        assertEquals("ok", eval("#{@annotatedProxy.touch()}"));
        ExpressionBeanScanner.reset();
    }

    @Test
    void classAnnotationSkipsForbiddenMethodNames() {
        ExpressionBeanScanner.reset();
        new ExpressionBeanScanner().setApplicationContext(APP);
        ExpressionBeanScanner.collect();
        // 类上有名为 exec 的公开方法时，不应拖垮整个授权策略，也不能授权 exec 本身。
        assertEquals("ok", eval("#{@annotatedWithExec.touch()}"));
        assertThrows(RuntimeException.class, () -> eval("#{@annotatedWithExec.exec()}"));
        ExpressionBeanScanner.reset();
    }

    @Test
    void keepsUnmodifiableCollectionsAsReadOnlyData() {
        Map<String, Object> variables = Map.of("words",
            java.util.Collections.unmodifiableList(List.of("a", "b")),
            "dict", java.util.Collections.unmodifiableMap(Map.of("x", 1)));
        assertEquals(2, SpelHelper.parseExpression("#{#words.size()}", variables));
        assertEquals(1, SpelHelper.parseExpression("#{#dict.x}", variables));
    }

    @Test
    void indexesMapData() {
        Map<String, Object> variables = Map.of("map", Map.of("x", 1));
        assertEquals(1, SpelHelper.parseExpression("#{#map['x']}", variables));
    }

    @Test
    void chainsReadOnlyOperationsOnAuthorizedDataResults() {
        allow("business", "handlers", "mapResult", "touch");
        assertEquals(List.of("u-1", "u-2"), eval("#{@business.handlers('u-1')}"));
        assertEquals(2, eval("#{@business.handlers('u-1').size()}"));
        assertEquals(1, eval("#{@business.mapResult()['x']}"));
        assertEquals(List.of("u-1"), eval("#{@business.handlers('u-1').?[#this == 'u-1']}"));
        // 授权方法返回纯数据后允许只读链式操作（模板拼接同理）。
        assertEquals("ok!", eval("#{@business.touch() + '!'}"));
        assertEquals(3, eval("#{(@business.touch() + '!').length()}"));
        // 非纯数据结果仍被拒绝，链式访问不能成为跳板。
        allow("business", "result");
        assertThrows(RuntimeException.class, () -> eval("#{@business.result().toString()}"));
        assertEquals(0, UNSAFE_RESULT.calls);
    }

    @Test
    void supportsNumericPower() {
        assertEquals(8, eval("#{2 ^ 3}"));
        assertEquals(9, SpelHelper.parseExpression("#{#day ^ 2}", Map.of("day", 3)));
        // 指数必须是有限字面量，防止 BigDecimal/BigInteger 幂耗尽内存。
        assertThrows(RuntimeException.class, () -> eval("#{2 ^ 1000}"));
        assertThrows(RuntimeException.class, () -> eval("#{2 ^ -1000}"));
        assertThrows(RuntimeException.class,
            () -> SpelHelper.parseExpression("#{#day ^ #exp}", Map.of("day", 2, "exp", 3)));
    }

    @Test
    void annotationSnapshotsAreIsolatedPerContext() {
        ExpressionBeanScanner.reset();
        StaticApplicationContext first = new StaticApplicationContext();
        first.getBeanFactory().registerSingleton("svc", new Probe());
        StaticApplicationContext second = new StaticApplicationContext();
        second.getBeanFactory().registerSingleton("svc", new AnnotatedService());
        new ExpressionBeanScanner().setApplicationContext(first);
        ExpressionBeanScanner.collect();
        new ExpressionBeanScanner().setApplicationContext(second);
        ExpressionBeanScanner.collect();
        // 同名 Bean：仅扫描到注解的上下文获得授权，另一个上下文保持默认拒绝。
        assertTrue(ExpressionBeanScanner.getAnnotatedAuthorizations(first).isEmpty());
        assertEquals(Set.of("touch"), ExpressionBeanScanner.getAnnotatedAuthorizations(second).get("svc"));
        first.close();
        second.close();
        ExpressionBeanScanner.reset();
    }

    private static void allow(String bean, String... methods) {
        PROPERTIES.getSpelAllowedBeanMethods().put(bean, Set.of(methods));
    }

    private static Object eval(String expression) {
        return SpelHelper.parseExpression(expression, Map.of());
    }

    @FlowExpressionBean
    public static class AnnotatedService {
        public String touch() {
            return "ok";
        }
    }

    public interface TouchApi {
        String touch();
    }

    @FlowExpressionBean
    public static class ProxyAnnotated implements TouchApi {
        @Override
        public String touch() {
            return "ok";
        }
    }

    @FlowExpressionBean
    public static class AnnotatedWithExec {
        public String touch() {
            return "ok";
        }

        public String exec() {
            return "must-not-run";
        }
    }

    public static class Probe {
        int calls;

        public String touch() {
            calls++;
            return "ok";
        }

        public Object getBean(String name) {
            calls++;
            return name;
        }

        public static String staticLabel() {
            return "static";
        }

        public static String staticEcho(String value) {
            return value;
        }
    }

    public interface HandlerProvider {
        List<String> handlers(String handler);
    }

    public static class Business implements HandlerProvider {
        @FlowExpressionBean
        public List<String> handlers(String handler) {
            return List.of(handler, "u-2");
        }

        public void notify(ListenerVariable context) {
            context.setEventType("notified");
        }

        public ServiceTaskHandler delegate() {
            return DELEGATE;
        }

        public Object result() {
            return UNSAFE_RESULT;
        }

        public Map<String, Object> mapResult() {
            return Map.of("x", 1);
        }

        public String touch() {
            return "ok";
        }

        public String createOrder(ServiceTaskContext context) {
            context.setVariable("orderNo", "order-1");
            return "order-1";
        }
    }

    public static class LazyBean {
        public LazyBean() {
            PROBE.calls++;
        }

        public String touch() {
            return PROBE.touch();
        }
    }

    public static class UnsafeResult {
        int calls;

        @Override
        public boolean equals(Object other) {
            calls++;
            return false;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            calls++;
            return "unsafe";
        }
    }

    public static class LazyFactory implements FactoryBean<Object> {
        public LazyFactory() {
            REDIS.calls++;
        }

        @Override
        public Object getObject() {
            return PROBE;
        }

        @Override
        public Class<?> getObjectType() {
            PROBE.calls++;
            return Probe.class;
        }
    }

    public static class StaticProbe {
        public static String staticEcho(String value) {
            return value;
        }
    }

    public static class ProbeFactory implements FactoryBean<Probe> {
        @Override
        public Probe getObject() {
            PROBE.calls++;
            return PROBE;
        }

        @Override
        public Class<?> getObjectType() {
            return Probe.class;
        }
    }

    public static class HostMap extends HashMap<String, Object> {
        @Override
        public int size() {
            PROBE.calls++;
            return 0;
        }
    }

    public static class RedisStub {
        int calls;

        public RedisStub getConnectionFactory() {
            return this;
        }

        public RedisStub getConnection() {
            return this;
        }

        public String replicaOf(String host, int port) {
            calls++;
            return "changed";
        }
    }
}
