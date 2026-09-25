package org.dromara.warm.flow.core.workflow;

import org.dromara.warm.flow.core.workflow.command.AddSignerCommand;
import org.dromara.warm.flow.core.workflow.command.CompleteCommand;
import org.dromara.warm.flow.core.workflow.command.DelegateCommand;
import org.dromara.warm.flow.core.workflow.command.JumpCommand;
import org.dromara.warm.flow.core.workflow.command.RejectCommand;
import org.dromara.warm.flow.core.workflow.command.RemoveSignerCommand;
import org.dromara.warm.flow.core.workflow.command.RevokeCommand;
import org.dromara.warm.flow.core.workflow.command.StartCommand;
import org.dromara.warm.flow.core.workflow.command.TerminateCommand;
import org.dromara.warm.flow.core.workflow.command.TransferCommand;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowContextMapperTest {

    @Test
    void start_mapsOnlyStartOptionsAndOperator() {
        StartCommand command = new StartCommand();
        command.setVariables(Map.of("key", "value"));
        command.setFlowStatus("flow");
        command.setTaskStatus("task");
        command.setNextHandlers(List.of("next"));
        command.setNextHandlerAppend(true);
        command.setOperator(operator());
        command.setExt("ext");

        WorkflowContext context = WorkflowContextMapper.toContext(command);

        assertEquals(Map.of("key", "value"), context.getVariables());
        assertEquals("flow", context.getFlowStatus());
        assertEquals("task", context.getTaskStatus());
        assertEquals(List.of("next"), context.getNextHandlers());
        assertTrue(context.isNextHandlerAppend());
        assertNull(context.getMessage());
        assertOperatorAndExt(context);
    }

    @Test
    void complete_mapsMessageVariablesStatusesAndHandlers() {
        CompleteCommand command = new CompleteCommand();
        command.setMessage("approved");
        command.setVariables(Map.of("key", "value"));
        command.setFlowStatus("flow");
        command.setTaskStatus("task");
        command.setNextHandlers(List.of("next"));
        command.setNextHandlerAppend(true);

        WorkflowContext context = WorkflowContextMapper.toContext(command);

        assertEquals("approved", context.getMessage());
        assertEquals(Map.of("key", "value"), context.getVariables());
        assertEquals("flow", context.getFlowStatus());
        assertEquals("task", context.getTaskStatus());
        assertEquals(List.of("next"), context.getNextHandlers());
        assertTrue(context.isNextHandlerAppend());
    }

    @Test
    void jumpAndRejectMapTheirTargetNode() {
        JumpCommand jump = new JumpCommand();
        jump.setTargetNodeCode("jump-target");
        jump.setMessage("jump");
        RejectCommand reject = new RejectCommand();
        reject.setTargetNodeCode("reject-target");
        reject.setMessage("reject");

        WorkflowContext jumpContext = WorkflowContextMapper.toContext(jump);
        WorkflowContext rejectContext = WorkflowContextMapper.toContext(reject);

        assertEquals("jump-target", jumpContext.getTargetNodeCode());
        assertEquals("jump", jumpContext.getMessage());
        assertEquals("reject-target", rejectContext.getTargetNodeCode());
        assertEquals("reject", rejectContext.getMessage());
    }

    @Test
    void revokeAndTerminateMapTheirSupportedFields() {
        RevokeCommand revoke = new RevokeCommand();
        revoke.setMessage("revoke");
        revoke.setVariables(Map.of("key", "value"));
        revoke.setFlowStatus("flow");
        revoke.setTaskStatus("task");
        TerminateCommand terminate = new TerminateCommand();
        terminate.setMessage("terminate");
        terminate.setFlowStatus("flow");
        terminate.setTaskStatus("task");

        WorkflowContext revokeContext = WorkflowContextMapper.toContext(revoke);
        WorkflowContext terminateContext = WorkflowContextMapper.toContext(terminate);

        assertEquals("revoke", revokeContext.getMessage());
        assertEquals(Map.of("key", "value"), revokeContext.getVariables());
        assertEquals("flow", revokeContext.getFlowStatus());
        assertEquals("task", revokeContext.getTaskStatus());
        assertEquals("terminate", terminateContext.getMessage());
        assertEquals("flow", terminateContext.getFlowStatus());
        assertEquals("task", terminateContext.getTaskStatus());
        assertNull(terminateContext.getVariables());
    }

    @Test
    void cooperationCommandsMapMessageAndTaskStatus() {
        TransferCommand transfer = new TransferCommand();
        transfer.setMessage("transfer");
        transfer.setTaskStatus("transfer-status");
        DelegateCommand delegate = new DelegateCommand();
        delegate.setMessage("delegate");
        delegate.setTaskStatus("delegate-status");
        AddSignerCommand addSigner = new AddSignerCommand();
        addSigner.setMessage("add");
        addSigner.setTaskStatus("add-status");
        RemoveSignerCommand removeSigner = new RemoveSignerCommand();
        removeSigner.setMessage("remove");
        removeSigner.setTaskStatus("remove-status");

        assertCooperationContext(WorkflowContextMapper.toContext(transfer), "transfer", "transfer-status");
        assertCooperationContext(WorkflowContextMapper.toContext(delegate), "delegate", "delegate-status");
        assertCooperationContext(WorkflowContextMapper.toContext(addSigner), "add", "add-status");
        assertCooperationContext(WorkflowContextMapper.toContext(removeSigner), "remove", "remove-status");
    }

    private OperatorContext operator() {
        OperatorContext operator = new OperatorContext();
        operator.setHandler("operator");
        operator.setIgnorePermission(true);
        operator.setIgnore(true);
        return operator;
    }

    private void assertOperatorAndExt(WorkflowContext context) {
        assertEquals("operator", context.getHandler());
        assertTrue(context.isIgnorePermission());
        assertTrue(context.isIgnore());
        assertEquals("ext", context.getExt());
    }

    private void assertCooperationContext(WorkflowContext context, String message, String taskStatus) {
        assertEquals(message, context.getMessage());
        assertEquals(taskStatus, context.getTaskStatus());
        assertNull(context.getFlowStatus());
        assertNull(context.getVariables());
        assertFalse(context.isNextHandlerAppend());
    }
}
