package org.dromara.warm.flow.core.workflow;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.handler.PermissionHandler;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.workflow.command.*;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

/**
 * 将场景化流程命令转换为引擎执行上下文。
 *
 * @author may
 */
final class WorkflowContextMapper {

    private WorkflowContextMapper() {
    }

    static WorkflowContext toContext(StartCommand command) {
        WorkflowContext context = baseContext(command);
        context.setVariables(command.getVariables());
        context.setFlowStatus(command.getFlowStatus());
        context.setTaskStatus(command.getTaskStatus());
        context.setNextHandlers(command.getNextHandlers());
        context.setNextHandlerAppend(command.isNextHandlerAppend());
        return context;
    }

    static WorkflowContext toContext(CompleteCommand command) {
        WorkflowContext context = baseContext(command);
        context.setMessage(command.getMessage());
        context.setVariables(command.getVariables());
        context.setFlowStatus(command.getFlowStatus());
        context.setTaskStatus(command.getTaskStatus());
        context.setNextHandlers(command.getNextHandlers());
        context.setNextHandlerAppend(command.isNextHandlerAppend());
        return context;
    }

    static WorkflowContext toContext(JumpCommand command) {
        WorkflowContext context = baseContext(command);
        context.setTargetNodeCode(command.getTargetNodeCode());
        context.setMessage(command.getMessage());
        context.setVariables(command.getVariables());
        context.setFlowStatus(command.getFlowStatus());
        context.setTaskStatus(command.getTaskStatus());
        context.setNextHandlers(command.getNextHandlers());
        context.setNextHandlerAppend(command.isNextHandlerAppend());
        return context;
    }

    static WorkflowContext toContext(RejectCommand command) {
        WorkflowContext context = baseContext(command);
        context.setTargetNodeCode(command.getTargetNodeCode());
        context.setMessage(command.getMessage());
        context.setVariables(command.getVariables());
        context.setFlowStatus(command.getFlowStatus());
        context.setTaskStatus(command.getTaskStatus());
        context.setNextHandlers(command.getNextHandlers());
        context.setNextHandlerAppend(command.isNextHandlerAppend());
        return context;
    }

    static WorkflowContext toContext(RevokeCommand command) {
        WorkflowContext context = baseContext(command);
        context.setMessage(command.getMessage());
        context.setVariables(command.getVariables());
        context.setFlowStatus(command.getFlowStatus());
        context.setTaskStatus(command.getTaskStatus());
        return context;
    }

    static WorkflowContext toContext(TerminateCommand command) {
        WorkflowContext context = baseContext(command);
        context.setMessage(command.getMessage());
        context.setFlowStatus(command.getFlowStatus());
        context.setTaskStatus(command.getTaskStatus());
        return context;
    }

    static WorkflowContext toContext(TransferCommand command) {
        WorkflowContext context = baseContext(command);
        context.setMessage(command.getMessage());
        context.setTaskStatus(command.getTaskStatus());
        return context;
    }

    static WorkflowContext toContext(DelegateCommand command) {
        WorkflowContext context = baseContext(command);
        context.setMessage(command.getMessage());
        context.setTaskStatus(command.getTaskStatus());
        return context;
    }

    static WorkflowContext toContext(AddSignerCommand command) {
        WorkflowContext context = baseContext(command);
        context.setMessage(command.getMessage());
        context.setTaskStatus(command.getTaskStatus());
        return context;
    }

    static WorkflowContext toContext(RemoveSignerCommand command) {
        WorkflowContext context = baseContext(command);
        context.setMessage(command.getMessage());
        context.setTaskStatus(command.getTaskStatus());
        return context;
    }

    private static WorkflowContext baseContext(WorkflowCommand command) {
        WorkflowContext context = new WorkflowContext();
        context.setExt(command.getExt());
        OperatorContext operator = command.getOperator();
        PermissionHandler permissionHandler = FlowEngine.permissionHandler();
        if (operator != null) {
            if (StringUtils.isNotEmpty(operator.getHandler())) {
                context.setHandler(operator.getHandler());
            }
            context.setIgnorePermission(operator.isIgnorePermission());
            context.setIgnore(operator.isIgnore());
        }
        if (permissionHandler != null) {
            if (StringUtils.isEmpty(context.getHandler())) {
                context.setHandler(permissionHandler.getHandler());
            }
            context.setPermissions(permissionHandler.permissions());
        }
        return context;
    }
}
