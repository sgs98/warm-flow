package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.ChartStatus;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.spi.PermissionHandler;
import org.dromara.warm.flow.core.spi.StatusMapper;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.test.listener.RecordingListener;
import org.dromara.warm.flow.core.test.listener.MutatingGlobalListener;
import org.dromara.warm.flow.core.workflow.command.ResetCommand;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;
import org.dromara.warm.flow.core.workflow.result.WorkflowResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 结束流程实例重置特征测试。
 *
 * @author may
 */
class ResetCharacteristicTest {

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
    }

    @AfterEach
    void tearDown() {
        MutatingGlobalListener.onStart = null;
        MutatingGlobalListener.onAssignment = null;
        harness.close();
    }

    @Test
    void reset_allSupportedTerminalStatuses_createAssignedPendingTaskAndKeepHistory() {
        List<String> statuses = List.of(FlowStatus.FINISHED.getKey(), FlowStatus.TERMINATE.getKey()
            , FlowStatus.INVALID.getKey());
        for (int i = 0; i < statuses.size(); i++) {
            Instance instance = finished("reset-ok-" + i, "biz-reset-ok-" + i);
            instance.setFlowStatus(statuses.get(i));
            FlowEngine.insService().updateById(instance);
            List<String> historyBefore = historySnapshot(instance.getId());
            EventWait wait = FlowEngine.newEventWait()
                .setDefinitionId(instance.getDefinitionId())
                .setInstanceId(instance.getId())
                .setNodeCode("stale-event")
                .setEventType(EventType.MESSAGE_CATCH.getValue())
                .setStatus(EventCons.STATUS_WAITING)
                .setVersion(0);
            FlowEngine.dataFillHandler().idFill(wait);
            FlowEngine.eventWaitService().save(wait);

            ResetCommand command = command(instance.getId(), "apply", List.of("lisi", "wangwu", "lisi"));
            WorkflowResult result = FlowEngine.workflow().reset(command);

            Instance reset = FlowEngine.insService().getById(instance.getId());
            assertEquals("reset", result.getOperation());
            assertEquals(FlowStatus.WAITING.getKey(), reset.getFlowStatus());
            assertEquals("apply", reset.getNodeCode());
            assertEquals(TestFlows.HANDLER, reset.getUpdateBy());
            Task task = TestFlows.currentTask(instance.getId());
            assertEquals("apply", task.getNodeCode());
            assertEquals(TaskStatus.PENDING.getKey(), task.getFlowStatus());
            assertEquals(List.of("lisi", "wangwu"), FlowEngine.userService()
                .getPermission(task.getId(), UserType.APPROVAL.getKey()));
            assertEquals(historyBefore, historySnapshot(instance.getId()), "旧审批历史不得变化");
            assertEquals(EventCons.STATUS_CANCELLED
                , FlowEngine.eventWaitService().getById(wait.getId()).getStatus());

            DefJson chart = FlowEngine.jsonConvert.strToBean(reset.getDefJson(), DefJson.class);
            assertEquals(ChartStatus.TO_DO.getKey(), chartNode(chart, "apply").getStatus());
            assertEquals(ChartStatus.NOT_DONE.getKey(), chartNode(chart, "end").getStatus());
        }
    }

    @Test
    void reset_recreatesBoundaryWaitAndFiresArrivalListeners() {
        Definition definition = TestFlows.serialFlow("reset-boundary");
        FlowEngine.nodeService().save(FlowEngine.newNode()
            .setDefinitionId(definition.getId())
            .setNodeCode("message-boundary")
            .setNodeName("message-boundary")
            .setNodeType(NodeType.BETWEEN.getKey())
            .setEventType(EventType.MESSAGE_BOUNDARY.getValue())
            .setEventName("cancel")
            .setAttachedToNodeCode("apply"));
        Instance instance = FlowEngine.insService().start("biz-reset-boundary", "reset-boundary"
            , TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        harness.reset();

        FlowEngine.workflow().reset(command(instance.getId(), "apply", List.of("lisi")));

        Task resetTask = TestFlows.currentTask(instance.getId());
        List<EventWait> waits = FlowEngine.eventWaitService().getWaitingByInstanceId(instance.getId());
        assertEquals(1, waits.size());
        assertEquals("message-boundary", waits.get(0).getNodeCode());
        assertEquals(resetTask.getId(), waits.get(0).getAttachedTaskId());
        assertEquals(1, RecordingListener.EVENTS.stream()
            .filter(event -> event.startsWith("assignment{node=apply")).count());
        assertEquals(1, RecordingListener.EVENTS.stream()
            .filter(event -> event.startsWith("create{node=apply")).count());
    }

    @Test
    void reset_appliesTargetDefaultsAndPersistsAssignmentListenerVariables() {
        TestFlows.serialFlowWithApplyConfig("reset-vars", "{\"variables\":{\"targetDefault\":\"from-node\"}}");
        Instance instance = finished("reset-vars", "biz-reset-vars");
        harness.register(org.dromara.warm.flow.core.spi.GlobalListener.class, MutatingGlobalListener.instance());
        FlowEngine.initGlobalListener();
        MutatingGlobalListener.onAssignment = listener -> listener.getVariable().put("assignmentListener", "updated");
        FlowEngine.workflow().reset(command(instance.getId(), "apply", List.of("lisi")));

        Instance reset = FlowEngine.insService().getById(instance.getId());
        assertEquals("from-node", reset.getVariableMap().get("targetDefault"));
        assertEquals("updated", reset.getVariableMap().get("assignmentListener"));
    }

    @Test
    void reset_usesOrdinaryHumanTaskWithAnyCooperationMode() {
        for (String ratio : List.of("0", "100")) {
            Instance instance = finished("reset-cooperate-" + ratio, "biz-reset-cooperate-" + ratio);
            Node target = FlowEngine.nodeService().getByDefIdAndNodeCode(instance.getDefinitionId(), "apply");
            target.setNodeRatio(ratio);
            FlowEngine.nodeService().updateById(target);

            FlowEngine.workflow().reset(command(instance.getId(), "apply", List.of("lisi", "wangwu")));

            Task task = TestFlows.currentTask(instance.getId());
            assertEquals("apply", task.getNodeCode());
            assertEquals(List.of("lisi", "wangwu"), FlowEngine.userService()
                .getPermission(task.getId(), UserType.APPROVAL.getKey()));
        }
    }

    @Test
    void reset_rejectsServiceTask() {
        Instance instance = finished("reset-service", "biz-reset-service");
        Node service = FlowEngine.newNode()
            .setDefinitionId(instance.getDefinitionId())
            .setNodeCode("service-target")
            .setNodeName("service-target")
            .setNodeType(NodeType.SERVICE.getKey());
        FlowEngine.nodeService().save(service);

        assertThrows(FlowException.class, () -> FlowEngine.workflow().reset(
            command(instance.getId(), service.getNodeCode(), List.of("lisi"))));
        assertEquals(List.of(), TestFlows.pendingTasks(instance.getId()));
    }

    @Test
    void reset_runsConfiguredAutoCopy() {
        TestFlows.serialFlowWithApplyConfig("reset-copy"
            , "{\"copy\":{\"handlers\":[\"copy-user\"]}}");
        Instance instance = FlowEngine.insService().start("biz-reset-copy", "reset-copy"
            , TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        long copiesBefore = FlowEngine.hisTaskService().getByInsId(instance.getId()).stream()
            .filter(task -> TaskStatus.COPY.getKey().equals(task.getFlowStatus())).count();

        FlowEngine.workflow().reset(command(instance.getId(), "apply", List.of("lisi")));

        Task resetTask = TestFlows.currentTask(instance.getId());
        List<HisTask> copies = FlowEngine.hisTaskService().getByInsId(instance.getId()).stream()
            .filter(task -> TaskStatus.COPY.getKey().equals(task.getFlowStatus()))
            .collect(Collectors.toList());
        assertEquals(copiesBefore + 1, copies.size());
        assertEquals(resetTask.getId(), copies.get(copies.size() - 1).getTaskId());
    }

    @Test
    void reset_clearsOtherStaleTodoMarkers() {
        Definition definition = TestFlows.serialFlow("reset-chart");
        FlowEngine.nodeService().save(FlowEngine.newNode()
            .setDefinitionId(definition.getId())
            .setNodeCode("orphan")
            .setNodeName("orphan")
            .setNodeType(NodeType.BETWEEN.getKey()));
        Instance instance = FlowEngine.insService().start("biz-reset-chart", "reset-chart"
            , TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        instance = FlowEngine.insService().getById(instance.getId());
        DefJson before = FlowEngine.jsonConvert.strToBean(instance.getDefJson(), DefJson.class);
        chartNode(before, "orphan").setStatus(ChartStatus.TO_DO.getKey());
        instance.setDefJson(FlowEngine.jsonConvert.objToStr(before));
        FlowEngine.insService().updateById(instance);

        FlowEngine.workflow().reset(command(instance.getId(), "apply", List.of("lisi")));

        DefJson after = FlowEngine.jsonConvert.strToBean(
            FlowEngine.insService().getById(instance.getId()).getDefJson(), DefJson.class);
        assertEquals(ChartStatus.TO_DO.getKey(), chartNode(after, "apply").getStatus());
        assertEquals(ChartStatus.NOT_DONE.getKey(), chartNode(after, "orphan").getStatus());
    }

    @Test
    void reset_invalidStatusWithExistingTask_rejectsWithoutWrites() {
        Instance instance = TestFlows.start("reset-invalid-with-task", "biz-reset-invalid-with-task");
        Task oldTask = TestFlows.currentTask(instance.getId());
        instance.setFlowStatus(FlowStatus.INVALID.getKey());
        FlowEngine.insService().updateById(instance);
        List<String> historyBefore = historySnapshot(instance.getId());
        List<String> usersBefore = FlowEngine.userService().getPermission(oldTask.getId(), UserType.APPROVAL.getKey());

        assertThrows(FlowException.class, () -> FlowEngine.workflow().reset(
            command(instance.getId(), "apply", List.of("lisi"))));

        assertEquals(historyBefore, historySnapshot(instance.getId()));
        assertEquals(oldTask.getId(), TestFlows.currentTask(instance.getId()).getId());
        assertEquals(usersBefore, FlowEngine.userService()
            .getPermission(oldTask.getId(), UserType.APPROVAL.getKey()));
        assertEquals(FlowStatus.INVALID.getKey(), FlowEngine.insService().getById(instance.getId()).getFlowStatus());
    }

    @Test
    void reset_rejectsNonTerminalAndExistingTaskWithoutWrites() {
        Instance instance = TestFlows.start("reset-running", "biz-reset-running");
        instance.setFlowStatus(FlowStatus.FINISHED.getKey());
        FlowEngine.insService().updateById(instance);
        int taskCount = harness.taskDao.all().size();

        assertThrows(FlowException.class, () -> FlowEngine.workflow().reset(
            command(instance.getId(), "apply", List.of("lisi"))));

        assertEquals(taskCount, harness.taskDao.all().size());
        assertEquals(FlowStatus.FINISHED.getKey(), FlowEngine.insService().getById(instance.getId()).getFlowStatus());
    }

    @Test
    void reset_rejectsInactiveDefinitionOrInstance() {
        Instance definitionInactive = finished("reset-def-off", "biz-reset-def-off");
        Definition definition = FlowEngine.defService().getById(definitionInactive.getDefinitionId());
        definition.setActivityStatus(ActivityStatus.SUSPENDED.getKey());
        FlowEngine.defService().updateById(definition);
        assertThrows(FlowException.class, () -> FlowEngine.workflow().reset(
            command(definitionInactive.getId(), "apply", List.of("lisi"))));

        Instance instanceInactive = finished("reset-ins-off", "biz-reset-ins-off");
        instanceInactive.setActivityStatus(ActivityStatus.SUSPENDED.getKey());
        FlowEngine.insService().updateById(instanceInactive);
        assertThrows(FlowException.class, () -> FlowEngine.workflow().reset(
            command(instanceInactive.getId(), "apply", List.of("lisi"))));
    }

    @Test
    void reset_rejectsTargetMissingFromInstanceSnapshot() {
        Instance instance = finished("reset-snapshot-missing", "biz-reset-snapshot-missing");
        Node lateNode = FlowEngine.newNode()
            .setDefinitionId(instance.getDefinitionId())
            .setNodeCode("late-node")
            .setNodeName("late-node")
            .setNodeType(NodeType.BETWEEN.getKey());
        FlowEngine.nodeService().save(lateNode);

        assertThrows(FlowException.class, () -> FlowEngine.workflow().reset(
            command(instance.getId(), lateNode.getNodeCode(), List.of("lisi"))));
        assertEquals(List.of(), TestFlows.pendingTasks(instance.getId()));
    }

    @Test
    void reset_rejectsWrongDefinitionNonApprovalAndEventNodes() {
        Instance instance = finished("reset-target", "biz-reset-target");
        Definition other = TestFlows.serialFlow("reset-other");
        Node crossDefinition = FlowEngine.newNode()
            .setDefinitionId(other.getId())
            .setNodeCode("other-only")
            .setNodeName("other-only")
            .setNodeType(NodeType.BETWEEN.getKey());
        FlowEngine.nodeService().save(crossDefinition);
        assertThrows(FlowException.class, () -> FlowEngine.workflow().reset(
            command(instance.getId(), crossDefinition.getNodeCode(), List.of("lisi"))));
        assertThrows(FlowException.class, () -> FlowEngine.workflow().reset(
            command(instance.getId(), "end", List.of("lisi"))));

        Node eventNode = FlowEngine.newNode()
            .setDefinitionId(instance.getDefinitionId())
            .setNodeCode("message")
            .setNodeName("message")
            .setNodeType(NodeType.BETWEEN.getKey())
            .setEventType(EventType.MESSAGE_CATCH.getValue());
        FlowEngine.nodeService().save(eventNode);
        assertThrows(FlowException.class, () -> FlowEngine.workflow().reset(
            command(instance.getId(), "message", List.of("lisi"))));
        assertEquals(List.of(), TestFlows.pendingTasks(instance.getId()));
    }

    @Test
    void reset_normalizesExplicitHandlersThroughPermissionHandler() {
        harness.register(PermissionHandler.class, new PermissionHandler() {
            @Override
            public List<String> permissions() {
                return List.of();
            }

            @Override
            public String getHandler() {
                return TestFlows.HANDLER;
            }

            @Override
            public List<String> convertPermissions(List<String> permissions) {
                return permissions.stream().map(permission -> permission.startsWith("user:")
                    ? permission.substring("user:".length()) : permission).collect(Collectors.toList());
            }
        });
        FlowEngine.initPermissionHandler(null);
        Instance instance = finished("reset-normalize", "biz-reset-normalize");

        FlowEngine.workflow().reset(command(instance.getId(), "apply", List.of("user:lisi")));

        assertEquals(List.of("lisi"), FlowEngine.userService().getPermission(
            TestFlows.currentTask(instance.getId()).getId(), UserType.APPROVAL.getKey()));
    }

    @Test
    void reset_usesCallerFlowStatusWhenProvided() {
        Instance instance = finished("reset-status", "biz-reset-status");
        ResetCommand command = command(instance.getId(), "apply", List.of("lisi"));
        command.setFlowStatus("reopened_review");

        FlowEngine.workflow().reset(command);

        assertEquals("reopened_review", FlowEngine.insService().getById(instance.getId()).getFlowStatus());
    }

    @Test
    void reset_usesMappedInstanceAndTaskStatuses() {
        FlowEngine.setStatusMapper(new StatusMapper() {
            @Override
            public String instanceStatus(FlowStatus semantic) {
                return "ins_" + semantic.getKey();
            }

            @Override
            public String taskStatus(TaskStatus semantic) {
                return "task_" + semantic.getKey();
            }
        });
        Instance instance = finished("reset-mapped", "biz-reset-mapped");

        FlowEngine.workflow().reset(command(instance.getId(), "apply", List.of("lisi")));

        assertEquals("ins_" + FlowStatus.WAITING.getKey()
            , FlowEngine.insService().getById(instance.getId()).getFlowStatus());
        assertEquals("task_" + TaskStatus.PENDING.getKey()
            , TestFlows.currentTask(instance.getId()).getFlowStatus());
    }

    @Test
    void reset_rejectsRunningAndArbitraryTerminalStatuses() {
        Instance running = finished("reset-running-no-task", "biz-reset-running-no-task");
        running.setFlowStatus(FlowStatus.WAITING.getKey());
        FlowEngine.insService().updateById(running);
        assertThrows(FlowException.class, () -> FlowEngine.workflow().reset(
            command(running.getId(), "apply", List.of("lisi"))));

        Instance custom = finished("reset-custom", "biz-reset-custom");
        custom.setFlowStatus("custom_terminal");
        FlowEngine.insService().updateById(custom);
        assertThrows(FlowException.class, () -> FlowEngine.workflow().reset(
            command(custom.getId(), "apply", List.of("lisi"))));

        assertEquals(List.of(), TestFlows.pendingTasks(running.getId()));
        assertEquals(List.of(), TestFlows.pendingTasks(custom.getId()));
    }

    private Instance finished(String flowCode, String businessId) {
        Instance instance = TestFlows.start(flowCode, businessId);
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        return FlowEngine.insService().getById(instance.getId());
    }

    private ResetCommand command(Long instanceId, String targetNodeCode, List<String> handlers) {
        ResetCommand command = new ResetCommand();
        command.setInstanceId(instanceId);
        command.setTargetNodeCode(targetNodeCode);
        command.setHandlers(handlers);
        OperatorContext operator = new OperatorContext(TestFlows.HANDLER);
        operator.setIgnorePermission(true);
        command.setOperator(operator);
        return command;
    }

    private List<String> historySnapshot(Long instanceId) {
        return FlowEngine.hisTaskService().getByInsId(instanceId).stream()
            .map(this::historyValue)
            .collect(Collectors.toList());
    }

    private String historyValue(HisTask task) {
        return task.getId() + ":" + task.getTaskId() + ":" + task.getNodeCode() + ":"
            + task.getFlowStatus() + ":" + task.getApprover() + ":" + task.getMessage();
    }

    private NodeJson chartNode(DefJson chart, String nodeCode) {
        return chart.getNodeList().stream().filter(node -> nodeCode.equals(node.getNodeCode())).findFirst().orElseThrow();
    }
}
