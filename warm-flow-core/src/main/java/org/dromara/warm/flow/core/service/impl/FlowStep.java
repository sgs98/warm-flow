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

import java.util.Optional;

/**
 * 流程操作步骤：有序流水线的单一步骤，包内私有，不构成扩展点。
 *
 * <p>步骤不跨操作携带可变状态——操作参数与链内中间态由所属链实例（每次操作新建）承载，
 * 聚合数据经 {@link FlowExecution} 读取。引擎已有 Listener、表达式、网关三套公共 SPI
 * 作为扩展正门，开放内部步骤等于新增永久契约。</p>
 *
 * @author warm
 */
interface FlowStep {

    /**
     * 执行本步骤。
     *
     * @param execution 执行作用域
     * @return 非空即短路——链终止并把实例透传调用方；空表示继续执行后续步骤
     */
    Optional<Instance> execute(FlowExecution execution);
}
