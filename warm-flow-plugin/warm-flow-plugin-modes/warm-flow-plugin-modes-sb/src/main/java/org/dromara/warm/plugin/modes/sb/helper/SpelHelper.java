/*
 *    Copyright 2024-2025, Warm-Flow (290631660@qq.com).
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
import org.dromara.warm.plugin.modes.sb.config.WarmFlowProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.lang.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 条件表达式 spel
 *
 * @author warm,battcn
 */
@Configuration
public class SpelHelper implements ApplicationContextAware {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private final static ParserContext PARSER_CONTEXT = new TemplateParserContext();

    private static ApplicationContext applicationContext;
    private static WarmFlowProperties properties;

    /**
     * 保留公共入口，但不再暴露无约束的 BeanFactoryResolver。
     */
    public static BeanResolver beanResolver() {
        return securityPolicy().beanResolver();
    }

    private static SpelSecurityPolicy securityPolicy() {
        ListableBeanFactory factory = getBeanFactory();
        return new SpelSecurityPolicy(factory, effectiveAuthorizations());
    }

    /**
     * 配置白名单与注解声明合并：同一 Bean 的方法取并集，两者皆空则默认拒绝。
     */
    private static Map<String, Set<String>> effectiveAuthorizations() {
        Map<String, Set<String>> merged = new HashMap<>();
        WarmFlowProperties config = properties;
        if (config != null && config.getSpelAllowedBeanMethods() != null) {
            merged.putAll(config.getSpelAllowedBeanMethods());
        }
        ExpressionBeanScanner.getAnnotatedAuthorizations(applicationContext).forEach((name, methods) ->
            merged.merge(name, methods, (configured, annotated) -> {
                Set<String> union = new HashSet<>(configured);
                union.addAll(annotated);
                return union;
            }));
        return merged;
    }

    /**
     * @param expression expression
     * @return Object
     */
    public static Object parseExpression(String expression, Map<String, Object> variable) {
        return parseExpression(expression, variable, false);
    }

    /**
     * 服务任务专用入口：额外允许单一表达式返回显式授权的 ServiceTaskHandler。
     */
    public static Object parseServiceTaskExpression(String expression, Map<String, Object> variable) {
        return parseExpression(expression, variable, true);
    }

    private static Object parseExpression(String expression, Map<String, Object> variable, boolean allowDelegate) {
        Map<String, Object> variables = variable == null ? Map.of() : variable;
        SpelSecurityPolicy policy = securityPolicy();
        Expression parsed = PARSER.parseExpression(expression, PARSER_CONTEXT);
        // 预检所有模板片段、分支及方法参数，拒绝前不能先执行其中的业务方法。
        policy.validate(parsed, variables, allowDelegate);

        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setBeanResolver(policy.beanResolver());
        context.setVariables(variables);
        context.setTypeLocator(new SafeTypeLocator());
        context.setConstructorResolvers(List.of());
        context.setPropertyAccessors(List.of(new SafePropertyAccessor()));
        context.setMethodResolvers(List.of(new SafeMethodResolver(policy)));
        return parsed.getValue(context, Object.class);
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        if (SpelHelper.applicationContext == null) {
            SpelHelper.applicationContext = applicationContext;
            // 在容器初始化阶段解析配置，不在不可信表达式求值时进行可能初始化 FactoryBean 的按类型扫描。
            String[] names = applicationContext.getBeanNamesForType(WarmFlowProperties.class, true, false);
            if (names.length > 1) {
                throw new FlowException("WarmFlowProperties 配置 Bean 不唯一");
            }
            properties = names.length == 0 ? null : applicationContext.getBean(names[0], WarmFlowProperties.class);
        }
    }

    public static ListableBeanFactory getBeanFactory() {
        if (null == applicationContext) {
            throw new FlowException("No ConfigurableListableBeanFactory or ApplicationContext injected, maybe not in the Spring environment?");
        } else {
            return applicationContext;
        }
    }

    public static String replace(String expression, Map<String, Object> variable) {
        expression = expression.replace("$", "#");
        for (Map.Entry<String, Object> entry : variable.entrySet()) {
            if (expression.contains(entry.getKey())) {
                expression = expression.replace(entry.getKey(), "#" + entry.getKey());
            }
        }
        return expression;
    }

}
