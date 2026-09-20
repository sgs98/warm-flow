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
package org.dromara.warm.flow.core.handler;

import org.dromara.warm.flow.core.strategy.HandlerStrategy;

import java.util.Map;

/**
 * 默认办理人表达式策略。
 * <p>
 * 支持形如 {@code ${flag}} 的变量表达式，解析后从流程变量 Map 中取出 {@code flag} 对应的值，
 * 再由 {@link HandlerStrategy} 统一转换为办理人标识列表。
 *
 * @author warm
 */
public class DefaultHandlerStrategy implements HandlerStrategy {

    @Override
    public String getType() {
        return "$";
    }

    /**
     * 从流程变量中取出表达式指向的值。
     *
     * @param expression 办理人表达式
     * @param variable   流程变量
     * @return 流程变量中的原始值
     */
    @Override
    public Object preEval(String expression, Map<String, Object> variable) {
        String result = expression.replace("${", "").replace("}", "");
        return variable.get(result);
    }

}
