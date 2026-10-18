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

import org.dromara.warm.flow.core.constant.FlowCons;

import java.util.ArrayList;
import java.util.List;

/**
 * 票签表达式策略接口。
 * <p>
 * 票签规则需要根据当前投票上下文判断是否满足通过或驳回条件。默认规则与 SpEL 等扩展规则
 * 都通过该接口注册到统一策略列表中。
 *
 * @author warm
 */
public interface VoteSignStrategy extends ExpressionStrategy<Boolean> {

    /**
     * 票签表达式策略实现集合。
     * <p>
     * {@code ExpressionUtil} 倒序遍历该集合，因此后注册的策略优先级更高。
     */
    List<ExpressionStrategy<Boolean>> EXPRESSION_STRATEGY_LIST = new ArrayList<>();

    /**
     * 注册票签表达式策略。
     *
     * @param expressionStrategy 票签表达式策略
     */
    @Override
    default void setExpression(ExpressionStrategy<Boolean> expressionStrategy) {
        EXPRESSION_STRATEGY_LIST.add(expressionStrategy);
    }

    /**
     * 票签表达式默认使用 {@code @@} 分隔策略类型和表达式主体。
     *
     * @return 表达式分隔符
     */
    @Override
    default String interceptStr() {
        return FlowCons.SPLIT_AT;
    }
}
