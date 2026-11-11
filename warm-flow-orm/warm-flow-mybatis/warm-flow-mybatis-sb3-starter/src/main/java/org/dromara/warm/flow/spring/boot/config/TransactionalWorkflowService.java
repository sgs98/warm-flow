package org.dromara.warm.flow.spring.boot.config;

import org.dromara.warm.flow.core.workflow.WorkflowServiceImpl;
import org.dromara.warm.flow.core.workflow.command.ResetCommand;
import org.dromara.warm.flow.core.workflow.command.RestartCommand;
import org.dromara.warm.flow.core.workflow.result.WorkflowResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 重启及废弃 reset 入口也须先锁后读，不能绕过任务服务的实例写协议。 @author may
 */
class TransactionalWorkflowService extends WorkflowServiceImpl {
    private final InstanceTransactionSupport transaction;

    TransactionalWorkflowService(TransactionTemplate template) {
        transaction = new InstanceTransactionSupport(template);
    }

    @Override
    public WorkflowResult restart(RestartCommand command) {
        return transaction.execute(command == null ? null : command.getInstanceId(), () -> super.restart(command));
    }

    @Override
    @Deprecated
    public WorkflowResult reset(ResetCommand command) {
        return transaction.execute(command == null ? null : command.getInstanceId(), () -> super.reset(command));
    }
}
