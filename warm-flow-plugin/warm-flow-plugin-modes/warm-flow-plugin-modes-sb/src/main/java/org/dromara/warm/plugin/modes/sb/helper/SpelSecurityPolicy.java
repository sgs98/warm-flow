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

import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.service.task.ServiceTaskHandler;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.common.CompositeStringExpression;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.*;
import org.springframework.expression.spel.standard.SpelExpression;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 每次求值的授权快照及 AST 预检。先检查整个模板，再解析 Bean 或执行任何业务方法。
 *
 * @author may
 */
final class SpelSecurityPolicy {
    private enum Kind { NONE, DATA, OPAQUE, BEAN, RESULT }

    private static final Set<Class<?>> LITERALS = Set.of(BooleanLiteral.class, IntLiteral.class,
        LongLiteral.class, RealLiteral.class, StringLiteral.class, NullLiteral.class);
    private static final Set<Class<?>> DATA_OPERATORS = Set.of(OpPlus.class, OpMinus.class,
        OpMultiply.class, OpDivide.class, OpModulus.class, OperatorPower.class, OpEQ.class, OpNE.class,
        OpGT.class, OpGE.class, OpLT.class, OpLE.class, OpAnd.class, OpOr.class, OperatorNot.class,
        OperatorBetween.class, Ternary.class, Elvis.class, InlineList.class, InlineMap.class);
    private static final Set<String> FORBIDDEN_PROPERTIES = Set.of("class", "classLoader",
        "declaringClass", "protectionDomain");

    /**
     * 幂运算允许的最大指数；更大指数（或变量指数）在预检阶段拒绝。
     */
    private static final int MAX_POWER = 64;

    private final Map<String, Set<String>> allowed;
    private final Map<Object, Set<String>> resolved = new IdentityHashMap<>();
    private final ListableBeanFactory beanFactory;
    private final List<BeanMethod> methodsToCheck = new ArrayList<>();
    private boolean handlerResultAllowed;

    private record BeanMethod(String beanName, MethodReference method) { }

    SpelSecurityPolicy(ListableBeanFactory beanFactory, Map<String, Set<String>> allowedBeanMethods) {
        this.beanFactory = beanFactory;
        this.allowed = new HashMap<>();
        if (allowedBeanMethods != null) {
            allowedBeanMethods.forEach((name, methods) -> {
                if (name == null || name.isBlank() || name.contains("*") || name.startsWith("&")) {
                    throw denied("Bean 白名单必须使用精确名称，禁止通配符及 FactoryBean 解引用");
                }
                if (methods == null) {
                    throw denied("Bean 方法集合不能为 null，委托 Bean 必须显式配置空集合");
                }
                Set<String> copy = new HashSet<>();
                for (String method : methods) {
                    if (method == null || !method.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")
                        || SafeMethodResolver.isForbidden(method)) {
                        throw denied("Bean 方法白名单存在不允许的方法");
                    }
                    copy.add(method);
                }
                this.allowed.put(name, copy);
            });
        }
    }

    BeanResolver beanResolver() {
        return (context, name) -> {
            checkBean(name);
            Object bean = beanFactory.getBean(name);
            if (allowed.get(name).isEmpty() && !(bean instanceof ServiceTaskHandler)) {
                throw denied("空方法白名单只允许 ServiceTaskHandler 委托 Bean");
            }
            resolved.computeIfAbsent(bean, key -> new HashSet<>()).addAll(allowed.get(name));
            return bean;
        };
    }

    boolean permits(Object bean, String method) {
        Set<String> methods = resolved.get(bean);
        return methods != null && methods.contains(method);
    }

    boolean acceptsResult(Object value) {
        return SafePropertyAccessor.isData(value) || handlerResultAllowed && value instanceof ServiceTaskHandler;
    }

    void validate(Expression expression, Map<String, Object> variables, boolean allowDelegate) {
        validateStructure(expression, variables, allowDelegate);
        // 只有整个模板的语法和名称授权全部通过，才能查询可能执行 FactoryBean 代码的类型元数据。
        for (BeanMethod call : methodsToCheck) {
            validateBeanMethod(call.beanName(), call.method());
        }
        handlerResultAllowed = allowDelegate && expression instanceof SpelExpression spel
            && spel.getAST() instanceof CompoundExpression compound && compound.getChildCount() == 2
            && compound.getChild(0) instanceof BeanReference && compound.getChild(1) instanceof MethodReference;
    }

    private void validateStructure(Expression expression, Map<String, Object> variables, boolean allowDelegate) {
        if (expression instanceof CompositeStringExpression composite) {
            for (Expression part : composite.getExpressions()) {
                validateStructure(part, variables, false);
            }
        } else if (expression instanceof SpelExpression spel) {
            Kind result = validateNode(spel.getAST(), variables, Kind.NONE);
            if (result == Kind.OPAQUE) {
                throw denied("复杂变量只能作为已授权 Bean 方法的参数");
            }
            if (result == Kind.BEAN) {
                SpelNode root = spel.getAST();
                if (!allowDelegate || !(root instanceof BeanReference bean)
                    || !allowed.get(bean.getName()).isEmpty()) {
                    throw denied("Bean 只能以空方法白名单显式配置为服务任务委托返回");
                }
            }
        } else if (!(expression instanceof LiteralExpression)) {
            throw denied("不支持的表达式类型");
        }
    }

    private Kind validateNode(SpelNode node, Map<String, Object> variables, Kind receiver) {
        if (LITERALS.contains(node.getClass())) {
            return Kind.DATA;
        }
        if (node instanceof BeanReference bean) {
            checkBean(bean.getName());
            return Kind.BEAN;
        }
        if (node instanceof VariableReference) {
            String name = node.toStringAST().substring(1);
            if ("this".equals(name)) {
                return receiver == Kind.DATA ? Kind.DATA : Kind.OPAQUE;
            }
            if ("root".equals(name)) {
                return Kind.OPAQUE;
            }
            return SafePropertyAccessor.isData(variables.get(name)) ? Kind.DATA : Kind.OPAQUE;
        }
        if (node instanceof CompoundExpression) {
            Kind current = receiver;
            for (int i = 0; i < node.getChildCount(); i++) {
                SpelNode child = node.getChild(i);
                if (i == 1 && node.getChild(0) instanceof BeanReference bean
                    && child instanceof MethodReference method) {
                    if (!allowed.get(bean.getName()).contains(method.getName())) {
                        throw denied("Bean 方法未授权：" + bean.getName() + "." + method.getName());
                    }
                    for (int j = 0; j < method.getChildCount(); j++) {
                        if (containsBean(method.getChild(j))) {
                            throw denied("Bean 方法参数不能嵌套其他 Bean 调用或引用");
                        }
                    }
                    methodsToCheck.add(new BeanMethod(bean.getName(), method));
                    // 原始引擎上下文可传给已审查入口，但不允许在表达式中调用其方法或 getter。
                    for (int j = 0; j < method.getChildCount(); j++) {
                        validateNode(method.getChild(j), variables, Kind.NONE);
                    }
                    current = Kind.RESULT;
                } else {
                    current = validateNode(child, variables, current);
                }
            }
            return current;
        }
        if (node instanceof PropertyOrFieldReference property) {
            requireData(receiver);
            if (FORBIDDEN_PROPERTIES.contains(property.getName())) {
                throw denied("不允许访问类型或反射属性");
            }
            return Kind.DATA;
        }
        if (node instanceof MethodReference method) {
            requireData(receiver);
            if (!SafeMethodResolver.isDataMethod(method.getName())) {
                throw denied("不允许调用数据方法：" + method.getName());
            }
            validateDataChildren(node, variables, Kind.NONE);
            return Kind.DATA;
        }
        if (node instanceof Indexer) {
            requireData(receiver);
            validateDataChildren(node, variables, Kind.NONE);
            return Kind.DATA;
        }
        if (node instanceof Selection || node instanceof Projection) {
            requireData(receiver);
            validateDataChildren(node, variables, Kind.DATA);
            return Kind.DATA;
        }
        if (DATA_OPERATORS.contains(node.getClass())) {
            if (node instanceof OperatorPower) {
                // BigDecimal/BigInteger 幂按指数分配结果，必须先限制字面量指数，避免内存耗尽。
                SpelNode exponent = node.getChild(1);
                if (!(exponent instanceof IntLiteral literal)
                    || !(literal.getLiteralValue().getValue() instanceof Integer value)
                    || Math.abs(value) > MAX_POWER) {
                    throw denied("幂运算指数必须是绝对值不超过 " + MAX_POWER + " 的整数字面量");
                }
            }
            Kind result = Kind.DATA;
            for (int i = 0; i < node.getChildCount(); i++) {
                Kind kind = validateNode(node.getChild(i), variables, receiver);
                if (kind != Kind.DATA && kind != Kind.RESULT) {
                    throw denied("运算、集合及分支只允许纯数据或已授权方法的结果");
                }
                if (kind == Kind.RESULT) {
                    result = Kind.RESULT;
                }
            }
            return result;
        }
        // 构造、类型、函数、赋值、自增、正则及未知 AST 一律默认拒绝。
        throw denied("不允许的表达式语法：" + node.getClass().getSimpleName());
    }

    private void validateDataChildren(SpelNode node, Map<String, Object> variables, Kind receiver) {
        for (int i = 0; i < node.getChildCount(); i++) {
            requireData(validateNode(node.getChild(i), variables, receiver));
        }
    }

    private void validateBeanMethod(String beanName, MethodReference expressionMethod) {
        boolean hasSafeOverload = false;
        if (expressionMethod.getName().equals("equals") || expressionMethod.getName().equals("hashCode")
            || expressionMethod.getName().equals("toString")) {
            throw denied("不能授权 Object 基础方法");
        }
        Class<?> type = beanFactory.getType(beanName, false);
        if (type == null) {
            throw denied("无法确认白名单 Bean 类型：" + beanName);
        }
        for (Method method : type.getMethods()) {
            if (method.getName().equals(expressionMethod.getName())
                && Modifier.isPublic(method.getModifiers())
                && !Modifier.isStatic(method.getModifiers())
                && !method.isBridge() && !method.isSynthetic()) {
                hasSafeOverload = true;
            }
        }
        if (!hasSafeOverload) {
            throw denied("授权方法必须是 Bean 上明确存在的 public 实例方法：" + beanName + "."
                + expressionMethod.getName());
        }
    }

    private static boolean containsBean(SpelNode node) {
        if (node instanceof BeanReference) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsBean(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private void checkBean(String name) {
        if (name == null || name.startsWith("&") || !allowed.containsKey(name)) {
            throw denied("Bean 未授权：" + name);
        }
    }

    /**
     * 已授权方法的结果在解析器处已强制为纯数据（复杂返回值直接中断求值），
     * 因此允许在此基础上继续做只读数据访问；运行期访问器仍会再次校验纯数据。
     */
    private static void requireData(Kind kind) {
        if (kind != Kind.DATA && kind != Kind.RESULT) {
            throw denied("只允许纯数据的只读访问，禁止 Bean/复杂变量/方法返回值的对象链");
        }
    }

    private static FlowException denied(String reason) {
        return new FlowException("SpEL 安全校验失败：" + reason);
    }
}
