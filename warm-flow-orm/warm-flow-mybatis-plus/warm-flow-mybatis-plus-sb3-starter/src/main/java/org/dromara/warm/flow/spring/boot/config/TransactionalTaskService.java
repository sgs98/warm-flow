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
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.service.impl.TaskServiceImpl;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/** 人工任务写入先锁实例再由标准办理链重新读取决策数据。 @author may */
class TransactionalTaskService extends TaskServiceImpl {
    private final InstanceTransactionSupport transaction;

    TransactionalTaskService(TransactionTemplate template) { transaction = new InstanceTransactionSupport(template); }

    @Override
    public Instance execute(Long taskId, WorkflowContext context, String skipType) {
        return transaction.execute(instanceIdOf(taskId), () -> super.execute(taskId, context, skipType));
    }

    @Override
    public Instance revoke(Long instanceId, WorkflowContext context) {
        return transaction.execute(instanceId, () -> super.revoke(instanceId, context));
    }

    @Override
    public Instance terminateByInstanceId(Long instanceId, WorkflowContext context) {
        return transaction.execute(instanceId, () -> super.terminateByInstanceId(instanceId, context));
    }

    @Override
    public Instance terminateByTaskId(Long taskId, WorkflowContext context) {
        return transaction.execute(instanceIdOf(taskId), () -> super.terminateByTaskId(taskId, context));
    }

    @Override
    public Instance updateHandlers(Long taskId, WorkflowContext context, List<String> addHandlers,
        List<String> removeHandlers, String cooperateType) {
        return transaction.execute(instanceIdOf(taskId),
            () -> super.updateHandlers(taskId, context, addHandlers, removeHandlers, cooperateType));
    }

    private Long instanceIdOf(Long taskId) {
        Task task = taskId == null ? null : super.getById(taskId);
        return task == null ? null : task.getInstanceId();
    }
}
