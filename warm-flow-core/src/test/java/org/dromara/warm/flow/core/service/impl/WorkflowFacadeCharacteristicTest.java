package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.handler.PermissionHandler;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.workflow.WorkflowService;
import org.dromara.warm.flow.core.workflow.command.AddSignerCommand;
import org.dromara.warm.flow.core.workflow.command.CompleteCommand;
import org.dromara.warm.flow.core.workflow.command.RejectCommand;
import org.dromara.warm.flow.core.workflow.command.StartCommand;
import org.dromara.warm.flow.core.workflow.command.TerminateCommand;
import org.dromara.warm.flow.core.workflow.command.TransferCommand;
import org.dromara.warm.flow.core.workflow.command.RevokeCommand;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;
import org.dromara.warm.flow.core.workflow.result.WorkflowResult;
import org.dromara.warm.flow.core.workflow.result.WorkflowTaskView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程统一门面（WorkflowService）特征测试：Command → WorkflowContext 转换、
 * 操作结果视图（含批量办理人查询的待办视图）、入参守卫、operator 缺省时
 * 回退 PermissionHandler。
 *
 * @author warm
 */
class WorkflowFacadeCharacteristicTest {

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
        harness.register(PermissionHandler.class, new PermissionHandler() {
            @Override
            public List<String> permissions() {
                return List.of(TestFlows.HANDLER);
            }

            @Override
            public String getHandler() {
                return TestFlows.HANDLER;
            }
        });
        FlowEngine.initPermissionHandler(null);
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    private WorkflowService facade() {
        return FlowEngine.workflow();
    }

    /** 门面不绕过权限校验：权限处理器提供当前执行用户的权限集合 */
    private OperatorContext operator(String handler) {
        return new OperatorContext(handler);
    }

    @Test
    void start_returnsResultWithPendingView() {
        TestFlows.serialFlow("wfc1");
        StartCommand cmd = new StartCommand();
        cmd.setBusinessId("biz-wf1");
        cmd.setFlowCode("wfc1");
        cmd.setOperator(operator(TestFlows.HANDLER));

        WorkflowResult result = facade().start(cmd);

        assertTrue(result.isSuccess());
        assertEquals("start", result.getOperation());
        assertNotNull(result.getInstanceId());
        assertEquals("biz-wf1", result.getBusinessId());
        assertEquals(1, result.getCurrentTasks().size());
        WorkflowTaskView view = result.getCurrentTasks().get(0);
        assertEquals("apply", view.getNodeCode());
        assertEquals(List.of(TestFlows.HANDLER), view.getHandlers());
    }

    @Test
    void inheritedCommandOptions_mapToInstanceHistoryAndNextTask() {
        TestFlows.serialFlow("wfc-options");
        StartCommand command = new StartCommand();
        command.setBusinessId("biz-wfc-options");
        command.setFlowCode("wfc-options");
        command.setOperator(operator(TestFlows.HANDLER));
        command.setVariables(new HashMap<>(Map.of("source", "command")));
        command.setExt("command-ext");
        command.setFlowStatus(FlowStatus.PENDING.getKey());
        command.setTaskStatus(FlowStatus.PASS.getKey());
        command.setNextHandlers(List.of("lisi"));

        WorkflowResult result = facade().start(command);

        Instance instance = FlowEngine.insService().getById(result.getInstanceId());
        assertEquals(FlowStatus.PENDING.getKey(), instance.getFlowStatus());
        assertEquals("command", instance.getVariableMap().get("source"));
        List<HisTask> history = FlowEngine.hisTaskService().getByInsId(instance.getId());
        assertEquals(FlowStatus.PASS.getKey(), history.get(0).getFlowStatus());
        assertEquals("command-ext", history.get(0).getExt());
        assertEquals(List.of("lisi"), result.getCurrentTasks().get(0).getHandlers());
    }

    @Test
    void complete_flowsToEndWithEmptyPendingView() {
        Instance instance = TestFlows.start("wfc2", "biz-wf2");
        Long taskId = TestFlows.currentTask(instance.getId()).getId();
        CompleteCommand cmd = new CompleteCommand();
        cmd.setTaskId(taskId);
        cmd.setOperator(operator(TestFlows.HANDLER));

        WorkflowResult result = facade().complete(cmd);

        assertTrue(result.isSuccess());
        assertEquals("complete", result.getOperation());
        assertEquals(taskId, result.getCompletedTaskId());
        assertEquals(FlowStatus.FINISHED.getKey(), result.getFlowStatus());
        assertEquals(List.of(), result.getCurrentTasks(), "到达终点后无待办");
    }

    @Test
    void reject_viaFacade_returnsToPredecessor() {
        Instance instance = TestFlows.startRejectFlow("wfc3", "biz-wf3");
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        Long auditId = TestFlows.currentTask(instance.getId()).getId();
        RejectCommand cmd = new RejectCommand();
        cmd.setTaskId(auditId);
        cmd.setOperator(operator(TestFlows.HANDLER));

        WorkflowResult result = facade().reject(cmd);

        assertEquals("reject", result.getOperation());
        assertEquals(FlowStatus.REJECT.getKey(), result.getFlowStatus());
        assertEquals("apply", result.getCurrentTasks().get(0).getNodeCode());
    }

    @Test
    void transfer_viaFacade_switchesHandlerInView() {
        Instance instance = TestFlows.start("wfc4", "biz-wf4");
        Long taskId = TestFlows.currentTask(instance.getId()).getId();
        TransferCommand cmd = new TransferCommand();
        cmd.setTaskId(taskId);
        cmd.setTargetHandler("lisi");
        cmd.setOperator(operator(TestFlows.HANDLER));

        WorkflowResult result = facade().transfer(cmd);

        assertEquals("transfer", result.getOperation());
        assertEquals(List.of("lisi"), result.getCurrentTasks().get(0).getHandlers());
    }

    @Test
    void terminate_supportsInstanceAndTaskLevel() {
        Instance byInstance = TestFlows.start("wfc5", "biz-wf5");
        TerminateCommand instanceCmd = new TerminateCommand();
        instanceCmd.setInstanceId(byInstance.getId());
        instanceCmd.setOperator(operator(TestFlows.HANDLER));
        WorkflowResult instanceResult = facade().terminate(instanceCmd);
        assertEquals(FlowStatus.TERMINATE.getKey(), instanceResult.getFlowStatus());
        assertEquals(List.of(), instanceResult.getCurrentTasks());

        Instance byTask = TestFlows.start("wfc6", "biz-wf6");
        TerminateCommand taskCmd = new TerminateCommand();
        taskCmd.setTaskId(TestFlows.currentTask(byTask.getId()).getId());
        taskCmd.setOperator(operator(TestFlows.HANDLER));
        WorkflowResult taskResult = facade().terminate(taskCmd);
        assertEquals(FlowStatus.TERMINATE.getKey(), taskResult.getFlowStatus());
    }

    @Test
    void revoke_viaFacade_returnsToStartSuccessor() {
        Instance instance = TestFlows.startRejectFlow("wfc7", "biz-wf7");
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        RevokeCommand cmd = new RevokeCommand();
        cmd.setInstanceId(instance.getId());
        cmd.setOperator(operator(TestFlows.HANDLER));

        WorkflowResult result = facade().revoke(cmd);

        assertEquals("revoke", result.getOperation());
        assertEquals("apply", result.getCurrentTasks().get(0).getNodeCode()
                , "撤回目标为开始节点的下一节点");
    }

    @Test
    void jump_viaFacade_reachesTargetNode() {
        Instance instance = TestFlows.startRejectFlow("wfc8", "biz-wf8");
        Long applyId = TestFlows.currentTask(instance.getId()).getId();
        org.dromara.warm.flow.core.workflow.command.JumpCommand cmd =
                new org.dromara.warm.flow.core.workflow.command.JumpCommand();
        cmd.setTaskId(applyId);
        cmd.setTargetNodeCode("end");
        cmd.setOperator(operator(TestFlows.HANDLER));

        WorkflowResult result = facade().jump(cmd);

        assertEquals("jump", result.getOperation());
        assertEquals(FlowStatus.FINISHED.getKey(), result.getFlowStatus());
        assertEquals(List.of(), result.getCurrentTasks());
    }

    @Test
    void delegate_viaFacade_keepsBothHandlersInView() {
        Instance instance = TestFlows.start("wfc10", "biz-wf10");
        Long taskId = TestFlows.currentTask(instance.getId()).getId();
        org.dromara.warm.flow.core.workflow.command.DelegateCommand cmd =
                new org.dromara.warm.flow.core.workflow.command.DelegateCommand();
        cmd.setTaskId(taskId);
        cmd.setTargetHandler("lisi");
        cmd.setOperator(operator(TestFlows.HANDLER));

        WorkflowResult result = facade().delegate(cmd);

        assertEquals("delegate", result.getOperation());
        // 委派期间办理权完全转移：仅受托人持有待办，委托人办理后才回还（见 DeputeCharacteristicTest）
        assertEquals(List.of("lisi"), result.getCurrentTasks().get(0).getHandlers());
    }

    @Test
    void signerManagement_viaFacade_addsThenRemoves() {
        Instance instance = TestFlows.start("wfc11", "biz-wf11");
        Long taskId = TestFlows.currentTask(instance.getId()).getId();

        org.dromara.warm.flow.core.workflow.command.AddSignerCommand add =
                new org.dromara.warm.flow.core.workflow.command.AddSignerCommand();
        add.setTaskId(taskId);
        add.setTargetHandlers(List.of("wangwu"));
        add.setOperator(operator(TestFlows.HANDLER));
        WorkflowResult added = facade().addSigner(add);
        assertEquals(List.of(TestFlows.HANDLER, "wangwu"), added.getCurrentTasks().get(0).getHandlers());

        org.dromara.warm.flow.core.workflow.command.RemoveSignerCommand remove =
                new org.dromara.warm.flow.core.workflow.command.RemoveSignerCommand();
        remove.setTaskId(taskId);
        remove.setTargetHandlers(List.of("wangwu"));
        remove.setOperator(operator(TestFlows.HANDLER));
        WorkflowResult removed = facade().removeSigner(remove);
        assertEquals(List.of(TestFlows.HANDLER), removed.getCurrentTasks().get(0).getHandlers());
    }

    @Test
    void guards_rejectInvalidCommands() {
        FlowException nullCommand = assertThrows(FlowException.class, () -> facade().start(null));
        assertEquals("启动流程参数不能为空", nullCommand.getMessage());

        CompleteCommand noTaskId = new CompleteCommand();
        FlowException noTask = assertThrows(FlowException.class, () -> facade().complete(noTaskId));
        assertEquals("任务ID不能为空", noTask.getMessage());

        TerminateCommand empty = new TerminateCommand();
        FlowException bothNull = assertThrows(FlowException.class, () -> facade().terminate(empty));
        assertEquals("流程实例ID和任务ID不能同时为空", bothNull.getMessage());

        TransferCommand noTarget = new TransferCommand();
        noTarget.setTaskId(1L);
        FlowException noHandler = assertThrows(FlowException.class, () -> facade().transfer(noTarget));
        assertEquals("转办办理人不能为空", noHandler.getMessage());

        AddSignerCommand noSigners = new AddSignerCommand();
        noSigners.setTaskId(1L);
        FlowException emptySigners = assertThrows(FlowException.class, () -> facade().addSigner(noSigners));
        assertEquals("加签办理人不能为空", emptySigners.getMessage());
    }

    @Test
    void operatorAbsent_fallsBackToPermissionHandler() {
        TestFlows.serialFlow("wfc9");
        harness.register(PermissionHandler.class, new PermissionHandler() {
            @Override
            public List<String> permissions() {
                return List.of(TestFlows.HANDLER);
            }

            @Override
            public String getHandler() {
                return TestFlows.HANDLER;
            }
        });
        FlowEngine.initPermissionHandler(null);
        StartCommand cmd = new StartCommand();
        cmd.setBusinessId("biz-wf9");
        cmd.setFlowCode("wfc9");

        WorkflowResult result = facade().start(cmd);

        // 无 operator 时从 PermissionHandler 取办理人，写入实例创建人
        assertEquals(TestFlows.HANDLER, harness.insDao.raw(result.getInstanceId()).getCreateBy());
    }

    @Test
    void operatorWithoutHandler_fallsBackToPermissionHandler() {
        TestFlows.serialFlow("wfc10");
        StartCommand cmd = new StartCommand();
        cmd.setBusinessId("biz-wf10");
        cmd.setFlowCode("wfc10");
        cmd.setOperator(new OperatorContext());

        WorkflowResult result = facade().start(cmd);

        assertEquals(TestFlows.HANDLER, harness.insDao.raw(result.getInstanceId()).getCreateBy());
        assertEquals(TestFlows.HANDLER, harness.hisTaskDao.all().get(0).getApprover());
    }
}
