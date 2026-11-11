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
package org.dromara.warm.flow.spring.boot.config;

import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.service.impl.InsServiceImpl;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/** 启动及可能整行覆盖实例快照的内置操作使用相同锁协议。 @author may */
class TransactionalInsService extends InsServiceImpl {
    private final InstanceTransactionSupport transaction;

    TransactionalInsService(TransactionTemplate template) { transaction = new InstanceTransactionSupport(template); }

    @Override
    public Instance start(String businessId, String flowCode, WorkflowContext context) {
        transaction.requireStart(flowCode, null);
        return transaction.execute(null, () -> super.start(businessId, flowCode, context));
    }

    @Override
    public Instance startByMessage(String businessId, String flowCode, String eventName,
        Map<String, Object> payload, WorkflowContext context) {
        transaction.requireStart(flowCode, null);
        return transaction.execute(null, () -> super.startByMessage(businessId, flowCode, eventName, payload, context));
    }

    @Override
    public Instance startByTimer(String businessId, Long definitionId, String nodeCode,
        Map<String, Object> payload, WorkflowContext context) {
        transaction.requireStart(null, definitionId);
        return transaction.execute(null, () -> super.startByTimer(businessId, definitionId, nodeCode, payload, context));
    }

    @Override
    public boolean active(Long id) { return transaction.execute(id, () -> super.active(id)); }

    @Override
    public boolean unActive(Long id) { return transaction.execute(id, () -> super.unActive(id)); }

    @Override
    public void removeVariables(Long instanceId, String... keys) {
        transaction.execute(instanceId, () -> { super.removeVariables(instanceId, keys); return null; });
    }
}
