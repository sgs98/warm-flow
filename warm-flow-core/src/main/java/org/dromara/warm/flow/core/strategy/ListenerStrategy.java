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

import java.util.ArrayList;
import java.util.List;

/**
 * 监听器表达式策略接口。
 * <p>
 * 监听器路径支持表达式化解析，外部扩展可通过该接口注册监听器表达式策略。
 * 执行结果用于表示监听器表达式是否成功触发。
 *
 * @author warm,battcn
 */
public interface ListenerStrategy extends ExpressionStrategy<Boolean> {

    /**
     * 监听器表达式策略实现集合。
     * <p>
     * {@code ExpressionUtil} 倒序遍历该集合，因此后注册的策略优先级更高。
     */
    List<ExpressionStrategy<Boolean>> EXPRESSION_STRATEGY_LIST = new ArrayList<>();

    /**
     * 注册监听器表达式策略。
     *
     * @param expressionStrategy 监听器表达式策略
     */
    @Override
    default void setExpression(ExpressionStrategy<Boolean> expressionStrategy) {
        EXPRESSION_STRATEGY_LIST.add(expressionStrategy);
    }

}
