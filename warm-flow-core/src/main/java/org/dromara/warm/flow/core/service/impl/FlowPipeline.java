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
package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.entity.Instance;

import java.util.List;
import java.util.Optional;

/**
 * 流程操作编排器：按操作装配的不可变步骤列表，逐个执行至短路或链尾。
 *
 * <p>包内私有。步骤顺序即状态机契约，短路语义显式化为 {@code Optional} 非空即终止。
 * run 不捕获异常——异常类名与消息必须原样透传给调用方。</p>
 *
 * @author warm
 */
final class FlowPipeline {

    /**
     * 有序步骤列表，装配后不可变（元素为方法引用，非空且不替换）。
     */
    private final List<FlowStep> steps;

    /**
     * @param steps 有序步骤列表
     */
    private FlowPipeline(List<FlowStep> steps) {
        this.steps = steps;
    }

    /**
     * 装配流水线。
     *
     * @param steps 有序步骤
     * @return 流水线
     */
    static FlowPipeline of(FlowStep... steps) {
        return new FlowPipeline(List.of(steps));
    }

    /**
     * 逐个执行步骤：首个返回非空 {@code Optional} 的步骤即短路，其实例透传调用方；
     * 全部走完返回执行作用域的实例（与各操作链尾返回语义一致）。
     *
     * @param execution 执行作用域
     * @return 流程实例
     */
    Instance run(FlowExecution execution) {
        for (FlowStep step : steps) {
            Optional<Instance> shortCircuit = step.execute(execution);
            if (shortCircuit.isPresent()) {
                return shortCircuit.get();
            }
        }
        return execution.instance;
    }
}
