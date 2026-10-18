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
package org.dromara.warm.flow.core.strategy;

import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 办理人表达式策略接口。
 * <p>
 * 节点配置的办理人表达式最终会归一化为办理人标识列表。该接口允许不同实现先解析表达式，
 * 再通过统一的后处理把单值、数组或集合转换为 {@code List<String>}。
 *
 * @author warm,battcn
 */
public interface HandlerStrategy extends ExpressionStrategy<List<String>> {

    /**
     * 办理人表达式策略实现集合。
     * <p>
     * {@code ExpressionUtil} 倒序遍历该集合，因此后注册的策略优先级更高。
     */
    List<ExpressionStrategy<List<String>>> EXPRESSION_STRATEGY_LIST = new ArrayList<>();

    /**
     * 注册办理人表达式策略。
     *
     * @param expressionStrategy 办理人表达式策略
     */
    @Override
    default void setExpression(ExpressionStrategy<List<String>> expressionStrategy) {
        EXPRESSION_STRATEGY_LIST.add(expressionStrategy);
    }

    /**
     * 执行办理人表达式原始解析。
     *
     * @param expression 办理人表达式
     * @param variable   流程变量
     * @return 原始解析结果，可为单值、数组或集合
     */
    Object preEval(String expression, Map<String, Object> variable);

    /**
     * 执行办理人表达式并转换为办理人标识列表。
     *
     * @param expression 办理人表达式
     * @param variable   流程变量
     * @return 办理人标识列表
     */
    @Override
    default List<String> eval(String expression, Map<String, Object> variable) {
        return afterEval(preEval(expression, variable));
    }

    /**
     * 将表达式原始返回值归一化为字符串列表。
     * <p>
     * {@code null} 保持为空；集合和数组逐项转字符串；其它对象作为单个办理人标识返回。
     *
     * @param o 原始返回值
     * @return 办理人标识列表
     */
    default List<String> afterEval(Object o) {
        if (ObjectUtil.isNull(o)) {
            return null;
        }
        if (o instanceof List<?> list) {
            return StreamUtils.toList(list, Object::toString);
        }
        if (o instanceof Object[] objects) {
            return Arrays.stream(objects).map(Object::toString).collect(Collectors.toList());
        }
        return List.of(o.toString());
    }
}
