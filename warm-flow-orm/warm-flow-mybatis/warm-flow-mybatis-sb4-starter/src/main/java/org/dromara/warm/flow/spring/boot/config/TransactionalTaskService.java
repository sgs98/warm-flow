package org.dromara.warm.flow.spring.boot.config;

import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.service.impl.TaskServiceImpl;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 人工任务写入先锁实例再由标准办理链重新读取决策数据。 @author may
 */
class TransactionalTaskService extends TaskServiceImpl {
    private final InstanceTransactionSupport transaction;

    TransactionalTaskService(TransactionTemplate template) {
        transaction = new InstanceTransactionSupport(template);
    }

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
