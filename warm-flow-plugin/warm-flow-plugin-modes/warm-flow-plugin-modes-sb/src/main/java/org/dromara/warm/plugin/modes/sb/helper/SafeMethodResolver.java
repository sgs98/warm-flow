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

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.MethodExecutor;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.spel.support.DataBindingMethodResolver;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 只允许已授权 Bean 的实例方法及有限的纯数据方法；必须替换默认解析器而不是追加。
 *
 * @author warm
 * @since 2026/3/31
 */
public class SafeMethodResolver implements MethodResolver {
    private static final Set<String> DANGEROUS_METHODS = Set.of("getClass", "getRuntime", "exec",
        "forName", "loadClass", "getClassLoader", "setAccessible", "newInstance", "invoke",
        "getField", "getDeclaredField", "getMethod", "getDeclaredMethod");
    private static final Set<String> STRING_METHODS = Set.of("length", "isEmpty", "isBlank", "contains",
        "startsWith", "endsWith", "equals", "equalsIgnoreCase", "compareTo", "compareToIgnoreCase",
        "substring", "indexOf", "lastIndexOf", "toLowerCase", "toUpperCase", "trim", "strip", "charAt");
    private static final Set<String> COLLECTION_METHODS = Set.of("size", "isEmpty", "contains");
    private static final Set<String> MAP_METHODS = Set.of("size", "isEmpty", "containsKey", "containsValue", "get");

    private final SpelSecurityPolicy policy;
    private final MethodResolver delegate = DataBindingMethodResolver.forInstanceMethodInvocation();

    /**
     * 兼容原构造器，默认只开放纯数据方法，不授予任何 Bean 能力。
     */
    public SafeMethodResolver() {
        this(null);
    }

    SafeMethodResolver(SpelSecurityPolicy policy) {
        this.policy = policy;
    }

    static boolean isForbidden(String name) {
        return DANGEROUS_METHODS.contains(name);
    }

    static boolean isDataMethod(String name) {
        return STRING_METHODS.contains(name) || COLLECTION_METHODS.contains(name)
            || MAP_METHODS.contains(name);
    }

    @Nullable
    @Override
    public MethodExecutor resolve(@NonNull EvaluationContext context, @NonNull Object targetObject,
        @NonNull String name, @NonNull List<TypeDescriptor> argumentTypes) throws AccessException {
        if (isForbidden(name) || targetObject instanceof Class<?> || targetObject instanceof ClassLoader) {
            throw new AccessException("不允许调用方法：" + name);
        }
        boolean beanAllowed = policy != null && policy.permits(targetObject, name);
        boolean dataAllowed = SafePropertyAccessor.isData(targetObject)
            && (targetObject instanceof String && STRING_METHODS.contains(name)
                || targetObject instanceof Collection<?> && COLLECTION_METHODS.contains(name)
                || targetObject instanceof Map<?, ?> && MAP_METHODS.contains(name));
        if (!beanAllowed && !dataAllowed) {
            throw new AccessException("方法未授权：" + name);
        }
        MethodExecutor executor = delegate.resolve(context, targetObject, name, argumentTypes);
        if (executor == null) {
            // 不得向默认反射解析器回退，静态/Object 方法不能成为间接能力入口。
            throw new AccessException("不允许的方法签名或静态调用：" + name);
        }
        return (evaluationContext, target, arguments) -> {
            org.springframework.expression.TypedValue result = executor.execute(evaluationContext, target, arguments);
            // SpEL 运算符和模板转换会隐式调用 equals/toString，不能让复杂返回值绕过方法解析器。
            if (beanAllowed && !policy.acceptsResult(result.getValue())) {
                throw new AccessException("授权方法只能返回纯数据或服务任务的显式委托处理器");
            }
            return result;
        };
    }
}
