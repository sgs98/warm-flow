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
import org.dromara.warm.flow.orm.dao.InstanceRowLock;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 为人工任务流转和实例生命周期操作提供数据库事务。
 *
 * @author may
 */
class TransactionalTaskService extends TaskServiceImpl {

    private final TransactionTemplate transactionTemplate;

    TransactionalTaskService(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public Instance execute(Long taskId, WorkflowContext context, String skipType) {
        Long instanceId = instanceIdOf(taskId);
        return transactionTemplate.execute(status -> {
            InstanceRowLock.lock(instanceId);
            return super.execute(taskId, context, skipType);
        });
    }

    @Override
    public Instance revoke(Long instanceId, WorkflowContext context) {
        return transactionTemplate.execute(status -> {
            InstanceRowLock.lock(instanceId);
            return super.revoke(instanceId, context);
        });
    }

    @Override
    public Instance terminateByInstanceId(Long instanceId, WorkflowContext context) {
        return transactionTemplate.execute(status -> {
            InstanceRowLock.lock(instanceId);
            return super.terminateByInstanceId(instanceId, context);
        });
    }

    @Override
    public Instance terminateByTaskId(Long taskId, WorkflowContext context) {
        Long instanceId = instanceIdOf(taskId);
        return transactionTemplate.execute(status -> {
            InstanceRowLock.lock(instanceId);
            return super.terminateByTaskId(taskId, context);
        });
    }

    @Override
    public Instance updateHandlers(Long taskId, WorkflowContext context, List<String> addHandlers,
        List<String> removeHandlers, String cooperateType) {
        Long instanceId = instanceIdOf(taskId);
        return transactionTemplate.execute(status -> {
            InstanceRowLock.lock(instanceId);
            return super.updateHandlers(taskId, context, addHandlers, removeHandlers, cooperateType);
        });
    }

    private Long instanceIdOf(Long taskId) {
        Task task = taskId == null ? null : super.getById(taskId);
        return task == null ? null : task.getInstanceId();
    }
}
