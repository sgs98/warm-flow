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

import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 只读纯数据属性；不反射 Bean/任意对象的 getter，避免属性链取得基础设施能力。
 *
 * @author may
 */
final class SafePropertyAccessor implements PropertyAccessor {
    private static final Set<Class<?>> SCALARS = Set.of(String.class, Boolean.class, Character.class,
        Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class,
        BigDecimal.class, BigInteger.class);
    private static final Set<String> CONTAINERS = Set.of("java.util.HashMap", "java.util.LinkedHashMap",
        "java.util.ArrayList", "java.util.LinkedList", "java.util.HashSet", "java.util.LinkedHashSet",
        "java.util.Arrays$ArrayList", "java.util.Collections$EmptyList", "java.util.Collections$EmptyMap",
        "java.util.Collections$EmptySet", "java.util.Collections$SingletonList",
        "java.util.Collections$SingletonMap", "java.util.Collections$SingletonSet",
        // 常见的只读/同步包装视图：内部迭代与查询仍是 JDK 实现，内容递归校验后才放行。
        "java.util.Collections$UnmodifiableCollection", "java.util.Collections$UnmodifiableList",
        "java.util.Collections$UnmodifiableRandomAccessList", "java.util.Collections$UnmodifiableSet",
        "java.util.Collections$UnmodifiableSortedSet", "java.util.Collections$UnmodifiableNavigableSet",
        "java.util.Collections$UnmodifiableMap", "java.util.Collections$UnmodifiableSortedMap",
        "java.util.Collections$UnmodifiableNavigableMap", "java.util.Collections$SynchronizedCollection",
        "java.util.Collections$SynchronizedList", "java.util.Collections$SynchronizedRandomAccessList",
        "java.util.Collections$SynchronizedSet", "java.util.Collections$SynchronizedMap");

    private static final Set<String> ENTRIES = Set.of("java.util.HashMap$Node", "java.util.HashMap$TreeNode",
        "java.util.LinkedHashMap$Entry", "java.util.KeyValueHolder",
        "java.util.AbstractMap$SimpleEntry", "java.util.AbstractMap$SimpleImmutableEntry");

    static boolean isData(Object value) {
        return isData(value, new IdentityHashMap<>(), 0);
    }

    private static boolean isData(Object value, IdentityHashMap<Object, Boolean> visiting, int depth) {
        if (value == null || SCALARS.contains(value.getClass())) {
            return true;
        }
        if (depth >= 64 || visiting.put(value, Boolean.TRUE) != null) {
            return false;
        }
        try {
            Class<?> type = value.getClass();
            if (type.isArray()) {
                if (type.getComponentType().isPrimitive()) {
                    return true;
                }
                for (Object item : (Object[]) value) {
                    if (!isData(item, visiting, depth + 1)) {
                        return false;
                    }
                }
                return true;
            }
            if (value instanceof Map.Entry<?, ?> entry && type.getModule() == Object.class.getModule()
                && ENTRIES.contains(type.getName())) {
                return isData(entry.getKey(), visiting, depth + 1)
                    && isData(entry.getValue(), visiting, depth + 1);
            }
            // 仅接受确定的 JDK 容器，不能让任意 Map/Collection 实现的 get/size 成为代码入口。
            if (!CONTAINERS.contains(type.getName())
                && !(type.getModule() == Object.class.getModule()
                    && type.getName().startsWith("java.util.ImmutableCollections$"))) {
                return false;
            }
            if (value instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null && !SCALARS.contains(entry.getKey().getClass())) {
                        return false;
                    }
                    if (!isData(entry.getValue(), visiting, depth + 1)) {
                        return false;
                    }
                }
                return true;
            }
            if (value instanceof Collection<?> collection) {
                for (Object item : collection) {
                    if (!isData(item, visiting, depth + 1)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        } finally {
            visiting.remove(value);
        }
    }

    @Override
    public Class<?>[] getSpecificTargetClasses() {
        return null;
    }

    @Override
    public boolean canRead(EvaluationContext context, Object target, String name) throws AccessException {
        if (!isData(target) || target == null) {
            return false;
        }
        if (target instanceof Map<?, ?> map) {
            return map.containsKey(name);
        }
        if (target instanceof Map.Entry<?, ?>) {
            return "key".equals(name) || "value".equals(name);
        }
        return (target.getClass().isArray() || target instanceof String) && "length".equals(name)
            || target instanceof Collection<?> && ("size".equals(name) || "empty".equals(name));
    }

    @Override
    public TypedValue read(EvaluationContext context, Object target, String name) throws AccessException {
        if (!canRead(context, target, name)) {
            throw new AccessException("不允许读取表达式对象属性：" + name);
        }
        if (target instanceof Map<?, ?> map) {
            return new TypedValue(map.get(name));
        }
        if (target instanceof Map.Entry<?, ?> entry) {
            return new TypedValue("key".equals(name) ? entry.getKey() : entry.getValue());
        }
        if (target instanceof String string) {
            return new TypedValue(string.length());
        }
        if (target instanceof Collection<?> collection) {
            return new TypedValue("size".equals(name) ? collection.size() : collection.isEmpty());
        }
        return new TypedValue(Array.getLength(target));
    }

    @Override
    public boolean canWrite(EvaluationContext context, Object target, String name) {
        return false;
    }

    @Override
    public void write(EvaluationContext context, Object target, String name, Object newValue) throws AccessException {
        throw new AccessException("表达式不允许写入属性：" + name);
    }
}
