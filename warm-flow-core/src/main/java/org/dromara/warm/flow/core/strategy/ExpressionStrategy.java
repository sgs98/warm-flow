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

import java.util.Map;

/**
 * 表达式策略接口。
 * <p>
 * 条件、办理人、监听器、票签等可扩展表达式都通过该接口接入。core 只定义统一的
 * 类型匹配、表达式截取和执行契约，具体表达式实现可由内置策略或 plugin-modes 等扩展模块注册。
 *
 * @author warm
 */
public interface ExpressionStrategy<T> {

    /**
     * 获取策略类型前缀。
     * <p>
     * {@code ExpressionUtil} 会根据表达式是否以该前缀开头选择策略，后注册的同类型策略优先匹配。
     *
     * @return 策略类型前缀
     */
    String getType();

    /**
     * 返回策略类型和真实表达式之间的分隔符。
     * <p>
     * 返回空字符串表示执行策略时保留完整表达式；返回非空时会先移除
     * {@code getType() + interceptStr()}，再把剩余内容交给 {@link #eval(String, Map)}。
     *
     * @return 表达式截取分隔符
     */
    default String interceptStr() {
        return "";
    }

    /**
     * 执行表达式并返回策略结果。
     *
     * @param expression 表达式
     * @param variable   流程变量
     * @return 执行结果
     */
    T eval(String expression, Map<String, Object> variable);


    /**
     * 注册表达式策略。
     * <p>
     * 各子接口通常把策略保存到自己的静态策略列表中，供 {@code ExpressionUtil} 统一查找。
     *
     * @param expressionStrategy 表达式策略
     */
    void setExpression(ExpressionStrategy<T> expressionStrategy);

}
