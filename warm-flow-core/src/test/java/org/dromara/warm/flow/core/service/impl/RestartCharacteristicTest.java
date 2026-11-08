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
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.spi.PermissionHandler;
import org.dromara.warm.flow.core.spi.StatusMapper;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.test.listener.RecordingListener;
import org.dromara.warm.flow.core.test.listener.MutatingGlobalListener;
import org.dromara.warm.flow.core.workflow.command.RestartCommand;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;
import org.dromara.warm.flow.core.workflow.result.WorkflowResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结束流程实例重启特征测试。
 *
 * @author may
 */
class RestartCharacteristicTest {

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
    void restart_allSupportedTerminalStatuses_createAssignedPendingTaskAndKeepHistory() {
        List<String> statuses = List.of(FlowStatus.FINISHED.getKey(), FlowStatus.TERMINATE.getKey()
            , FlowStatus.INVALID.getKey());
        for (int i = 0; i < statuses.size(); i++) {
            Instance instance = finished("restart-ok-" + i, "biz-restart-ok-" + i);
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

            RestartCommand command = command(instance.getId(), "apply", List.of("lisi", "wangwu", "lisi"));
            command.setExt("restart-ext");
            WorkflowResult result = FlowEngine.workflow().restart(command);

            Instance restart = FlowEngine.insService().getById(instance.getId());
            assertEquals("restart", result.getOperation());
            assertEquals(FlowStatus.WAITING.getKey(), restart.getFlowStatus());
            assertEquals("apply", restart.getNodeCode());
            assertEquals(TestFlows.HANDLER, restart.getUpdateBy());
            Task task = TestFlows.currentTask(instance.getId());
            assertEquals("apply", task.getNodeCode());
            assertEquals(TaskStatus.PENDING.getKey(), task.getFlowStatus());
            assertEquals(List.of("lisi", "wangwu"), FlowEngine.userService()
                .getPermission(task.getId(), UserType.APPROVAL.getKey()));
            List<String> historyAfter = historySnapshot(instance.getId());
            assertEquals(historyBefore.size() + 1, historyAfter.size());
            assertTrue(historyAfter.containsAll(historyBefore), "旧审批历史不得变化");
            HisTask restartHistory = FlowEngine.hisTaskService().getByInsId(instance.getId()).stream()
                .filter(item -> TaskStatus.RESTART.getKey().equals(item.getFlowStatus()))
                .findFirst().orElseThrow();
            assertEquals(-1L, restartHistory.getTaskId(), "重启审计记录使用哨兵任务ID");
            assertEquals(instance.getNodeCode(), restartHistory.getNodeCode());
            assertEquals(instance.getNodeName(), restartHistory.getNodeName());
            assertEquals(instance.getNodeType(), restartHistory.getNodeType());
            assertEquals("apply", restartHistory.getTargetNodeCode());
            assertEquals("apply", restartHistory.getTargetNodeName());
            assertEquals(TestFlows.HANDLER, restartHistory.getApprover());
            assertEquals(SkipType.NONE.getKey(), restartHistory.getSkipType());
            assertEquals("none", restartHistory.getCooperateType());
            assertEquals("restart-ext", restartHistory.getExt());
            assertNotNull(restartHistory.getCreateTime());
            assertNotNull(restartHistory.getVariable());
            assertEquals(EventCons.STATUS_CANCELLED
                , FlowEngine.eventWaitService().getById(wait.getId()).getStatus());

            DefJson chart = FlowEngine.jsonConvert.strToBean(restart.getDefJson(), DefJson.class);
            assertEquals(ChartStatus.TO_DO.getKey(), chartNode(chart, "apply").getStatus());
            assertEquals(ChartStatus.NOT_DONE.getKey(), chartNode(chart, "end").getStatus());
        }
    }

    @Test
    void restart_recreatesBoundaryWaitAndFiresArrivalListeners() {
        Definition definition = TestFlows.serialFlow("restart-boundary");
        FlowEngine.nodeService().save(FlowEngine.newNode()
            .setDefinitionId(definition.getId())
            .setNodeCode("message-boundary")
            .setNodeName("message-boundary")
            .setNodeType(NodeType.BETWEEN.getKey())
            .setEventType(EventType.MESSAGE_BOUNDARY.getValue())
            .setEventName("cancel")
            .setAttachedToNodeCode("apply"));
        Instance instance = FlowEngine.insService().start("biz-restart-boundary", "restart-boundary"
            , TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        harness.reset();

        FlowEngine.workflow().restart(command(instance.getId(), "apply", List.of("lisi")));

        Task restartTask = TestFlows.currentTask(instance.getId());
        List<EventWait> waits = FlowEngine.eventWaitService().getWaitingByInstanceId(instance.getId());
        assertEquals(1, waits.size());
        assertEquals("message-boundary", waits.get(0).getNodeCode());
        assertEquals(restartTask.getId(), waits.get(0).getAttachedTaskId());
        assertEquals(1, RecordingListener.EVENTS.stream()
            .filter(event -> event.startsWith("assignment{node=apply")).count());
        assertEquals(1, RecordingListener.EVENTS.stream()
            .filter(event -> event.startsWith("create{node=apply")).count());
    }

    @Test
    void restart_appliesTargetDefaultsAndPersistsAssignmentListenerVariables() {
        TestFlows.serialFlowWithApplyConfig("restart-vars", "{\"variables\":{\"targetDefault\":\"from-node\"}}");
        Instance instance = finished("restart-vars", "biz-restart-vars");
        harness.register(org.dromara.warm.flow.core.spi.GlobalListener.class, MutatingGlobalListener.instance());
        FlowEngine.initGlobalListener();
        MutatingGlobalListener.onAssignment = listener -> listener.getVariable().put("assignmentListener", "updated");
        FlowEngine.workflow().restart(command(instance.getId(), "apply", List.of("lisi")));

        Instance restart = FlowEngine.insService().getById(instance.getId());
        assertEquals("from-node", restart.getVariableMap().get("targetDefault"));
        assertEquals("updated", restart.getVariableMap().get("assignmentListener"));
    }

    @Test
    void restart_usesOrdinaryHumanTaskWithAnyCooperationMode() {
        for (String ratio : List.of("0", "100")) {
            Instance instance = finished("restart-cooperate-" + ratio, "biz-restart-cooperate-" + ratio);
            Node target = FlowEngine.nodeService().getByDefIdAndNodeCode(instance.getDefinitionId(), "apply");
            target.setNodeRatio(ratio);
            FlowEngine.nodeService().updateById(target);

            FlowEngine.workflow().restart(command(instance.getId(), "apply", List.of("lisi", "wangwu")));

            Task task = TestFlows.currentTask(instance.getId());
            assertEquals("apply", task.getNodeCode());
            assertEquals(List.of("lisi", "wangwu"), FlowEngine.userService()
                .getPermission(task.getId(), UserType.APPROVAL.getKey()));
        }
    }

    @Test
    void restart_rejectsServiceTask() {
        Instance instance = finished("restart-service", "biz-restart-service");
        Node service = FlowEngine.newNode()
            .setDefinitionId(instance.getDefinitionId())
            .setNodeCode("service-target")
            .setNodeName("service-target")
            .setNodeType(NodeType.SERVICE.getKey());
        FlowEngine.nodeService().save(service);

        assertThrows(FlowException.class, () -> FlowEngine.workflow().restart(
            command(instance.getId(), service.getNodeCode(), List.of("lisi"))));
        assertEquals(List.of(), TestFlows.pendingTasks(instance.getId()));
    }

    @Test
    void restart_runsConfiguredAutoCopy() {
        TestFlows.serialFlowWithApplyConfig("restart-copy"
            , "{\"copy\":{\"handlers\":[\"copy-user\"]}}");
        Instance instance = FlowEngine.insService().start("biz-restart-copy", "restart-copy"
            , TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        long copiesBefore = FlowEngine.hisTaskService().getByInsId(instance.getId()).stream()
            .filter(task -> TaskStatus.COPY.getKey().equals(task.getFlowStatus())).count();

        FlowEngine.workflow().restart(command(instance.getId(), "apply", List.of("lisi")));

        Task restartTask = TestFlows.currentTask(instance.getId());
        List<HisTask> copies = FlowEngine.hisTaskService().getByInsId(instance.getId()).stream()
            .filter(task -> TaskStatus.COPY.getKey().equals(task.getFlowStatus()))
            .collect(Collectors.toList());
        assertEquals(copiesBefore + 1, copies.size());
        assertEquals(restartTask.getId(), copies.get(copies.size() - 1).getTaskId());
    }

    @Test
    void restart_clearsOtherStaleTodoMarkers() {
        Definition definition = TestFlows.serialFlow("restart-chart");
        FlowEngine.nodeService().save(FlowEngine.newNode()
            .setDefinitionId(definition.getId())
            .setNodeCode("orphan")
            .setNodeName("orphan")
            .setNodeType(NodeType.BETWEEN.getKey()));
        Instance instance = FlowEngine.insService().start("biz-restart-chart", "restart-chart"
            , TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        instance = FlowEngine.insService().getById(instance.getId());
        DefJson before = FlowEngine.jsonConvert.strToBean(instance.getDefJson(), DefJson.class);
        chartNode(before, "orphan").setStatus(ChartStatus.TO_DO.getKey());
        instance.setDefJson(FlowEngine.jsonConvert.objToStr(before));
        FlowEngine.insService().updateById(instance);

        FlowEngine.workflow().restart(command(instance.getId(), "apply", List.of("lisi")));

        DefJson after = FlowEngine.jsonConvert.strToBean(
            FlowEngine.insService().getById(instance.getId()).getDefJson(), DefJson.class);
        assertEquals(ChartStatus.TO_DO.getKey(), chartNode(after, "apply").getStatus());
        assertEquals(ChartStatus.NOT_DONE.getKey(), chartNode(after, "orphan").getStatus());
    }

    @Test
    void restart_invalidStatusWithExistingTask_rejectsWithoutWrites() {
        Instance instance = TestFlows.start("restart-invalid-with-task", "biz-restart-invalid-with-task");
        Task oldTask = TestFlows.currentTask(instance.getId());
        instance.setFlowStatus(FlowStatus.INVALID.getKey());
        FlowEngine.insService().updateById(instance);
        List<String> historyBefore = historySnapshot(instance.getId());
        List<String> usersBefore = FlowEngine.userService().getPermission(oldTask.getId(), UserType.APPROVAL.getKey());

        assertThrows(FlowException.class, () -> FlowEngine.workflow().restart(
            command(instance.getId(), "apply", List.of("lisi"))));

        assertEquals(historyBefore, historySnapshot(instance.getId()));
        assertEquals(oldTask.getId(), TestFlows.currentTask(instance.getId()).getId());
        assertEquals(usersBefore, FlowEngine.userService()
            .getPermission(oldTask.getId(), UserType.APPROVAL.getKey()));
        assertEquals(FlowStatus.INVALID.getKey(), FlowEngine.insService().getById(instance.getId()).getFlowStatus());
    }

    @Test
    void restart_rejectsNonTerminalAndExistingTaskWithoutWrites() {
        Instance instance = TestFlows.start("restart-running", "biz-restart-running");
        instance.setFlowStatus(FlowStatus.FINISHED.getKey());
        FlowEngine.insService().updateById(instance);
        int taskCount = harness.taskDao.all().size();

        assertThrows(FlowException.class, () -> FlowEngine.workflow().restart(
            command(instance.getId(), "apply", List.of("lisi"))));

        assertEquals(taskCount, harness.taskDao.all().size());
        assertEquals(FlowStatus.FINISHED.getKey(), FlowEngine.insService().getById(instance.getId()).getFlowStatus());
    }

    @Test
    void restart_rejectsInactiveDefinitionOrInstance() {
        Instance definitionInactive = finished("restart-def-off", "biz-restart-def-off");
        Definition definition = FlowEngine.defService().getById(definitionInactive.getDefinitionId());
        definition.setActivityStatus(ActivityStatus.SUSPENDED.getKey());
        FlowEngine.defService().updateById(definition);
        assertThrows(FlowException.class, () -> FlowEngine.workflow().restart(
            command(definitionInactive.getId(), "apply", List.of("lisi"))));

        Instance instanceInactive = finished("restart-ins-off", "biz-restart-ins-off");
        instanceInactive.setActivityStatus(ActivityStatus.SUSPENDED.getKey());
        FlowEngine.insService().updateById(instanceInactive);
        assertThrows(FlowException.class, () -> FlowEngine.workflow().restart(
            command(instanceInactive.getId(), "apply", List.of("lisi"))));
    }

    @Test
    void restart_rejectsTargetMissingFromInstanceSnapshot() {
        Instance instance = finished("restart-snapshot-missing", "biz-restart-snapshot-missing");
        Node lateNode = FlowEngine.newNode()
            .setDefinitionId(instance.getDefinitionId())
            .setNodeCode("late-node")
            .setNodeName("late-node")
            .setNodeType(NodeType.BETWEEN.getKey());
        FlowEngine.nodeService().save(lateNode);

        assertThrows(FlowException.class, () -> FlowEngine.workflow().restart(
            command(instance.getId(), lateNode.getNodeCode(), List.of("lisi"))));
        assertEquals(List.of(), TestFlows.pendingTasks(instance.getId()));
    }

    @Test
    void restart_rejectsWrongDefinitionNonApprovalAndEventNodes() {
        Instance instance = finished("restart-target", "biz-restart-target");
        Definition other = TestFlows.serialFlow("restart-other");
        Node crossDefinition = FlowEngine.newNode()
            .setDefinitionId(other.getId())
            .setNodeCode("other-only")
            .setNodeName("other-only")
            .setNodeType(NodeType.BETWEEN.getKey());
        FlowEngine.nodeService().save(crossDefinition);
        assertThrows(FlowException.class, () -> FlowEngine.workflow().restart(
            command(instance.getId(), crossDefinition.getNodeCode(), List.of("lisi"))));
        assertThrows(FlowException.class, () -> FlowEngine.workflow().restart(
            command(instance.getId(), "end", List.of("lisi"))));

        Node eventNode = FlowEngine.newNode()
            .setDefinitionId(instance.getDefinitionId())
            .setNodeCode("message")
            .setNodeName("message")
            .setNodeType(NodeType.BETWEEN.getKey())
            .setEventType(EventType.MESSAGE_CATCH.getValue());
        FlowEngine.nodeService().save(eventNode);
        assertThrows(FlowException.class, () -> FlowEngine.workflow().restart(
            command(instance.getId(), "message", List.of("lisi"))));
        assertEquals(List.of(), TestFlows.pendingTasks(instance.getId()));
    }

    @Test
    void restart_normalizesExplicitHandlersThroughPermissionHandler() {
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
        Instance instance = finished("restart-normalize", "biz-restart-normalize");

        FlowEngine.workflow().restart(command(instance.getId(), "apply", List.of("user:lisi")));

        assertEquals(List.of("lisi"), FlowEngine.userService().getPermission(
            TestFlows.currentTask(instance.getId()).getId(), UserType.APPROVAL.getKey()));
    }

    @Test
    void restart_usesCallerFlowStatusWhenProvided() {
        Instance instance = finished("restart-status", "biz-restart-status");
        RestartCommand command = command(instance.getId(), "apply", List.of("lisi"));
        command.setFlowStatus("reopened_review");

        FlowEngine.workflow().restart(command);

        assertEquals("reopened_review", FlowEngine.insService().getById(instance.getId()).getFlowStatus());
    }

    @Test
    void restart_usesMappedInstanceAndTaskStatuses() {
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
        Instance instance = finished("restart-mapped", "biz-restart-mapped");

        FlowEngine.workflow().restart(command(instance.getId(), "apply", List.of("lisi")));

        assertEquals("ins_" + FlowStatus.WAITING.getKey()
            , FlowEngine.insService().getById(instance.getId()).getFlowStatus());
        assertEquals("task_" + TaskStatus.PENDING.getKey()
            , TestFlows.currentTask(instance.getId()).getFlowStatus());
        assertEquals("task_" + TaskStatus.RESTART.getKey(), FlowEngine.hisTaskService()
            .getByInsId(instance.getId()).stream()
            .filter(item -> item.getTaskId() == -1L)
            .reduce((first, second) -> second).orElseThrow().getFlowStatus());
    }

    @Test
    void restart_explicitTaskStatusOverridesDefaultHistoryStatus() {
        Instance instance = finished("restart-manual-status", "biz-restart-manual-status");
        RestartCommand command = command(instance.getId(), "apply", List.of("lisi"));
        command.setTaskStatus("manual_restart");

        FlowEngine.workflow().restart(command);

        assertEquals("manual_restart", FlowEngine.hisTaskService().getByInsId(instance.getId()).stream()
            .filter(item -> item.getTaskId() == -1L)
            .reduce((first, second) -> second).orElseThrow().getFlowStatus());
    }

    @Test
    void restart_mapperReturningNullForRestartStatusFallsBackToBuiltInCode() {
        FlowEngine.setStatusMapper(new StatusMapper() {
            @Override
            public String taskStatus(TaskStatus semantic) {
                return semantic == TaskStatus.RESTART ? null : semantic.getKey();
            }
        });
        Instance instance = finished("restart-null-mapper", "biz-restart-null-mapper");

        FlowEngine.workflow().restart(command(instance.getId(), "apply", List.of("lisi")));

        assertEquals(TaskStatus.RESTART.getKey(), FlowEngine.hisTaskService()
            .getByInsId(instance.getId()).stream()
            .filter(item -> item.getTaskId() == -1L)
            .findFirst().orElseThrow().getFlowStatus());
    }

    @Test
    void restart_emptyHandlersRejectsWithoutHistory() {
        Instance instance = finished("restart-empty-handlers", "biz-restart-empty-handlers");
        List<String> historyBefore = historySnapshot(instance.getId());

        assertThrows(FlowException.class, () -> FlowEngine.workflow().restart(
            command(instance.getId(), "apply", List.of())));

        assertEquals(historyBefore, historySnapshot(instance.getId()));
    }

    @Test
    void restart_repeatedlyAppendsOneHistoryRecordPerOperation() {
        Instance instance = finished("restart-repeat", "biz-restart-repeat");

        FlowEngine.workflow().restart(command(instance.getId(), "apply", List.of("lisi")));
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), "lisi");
        FlowEngine.workflow().restart(command(instance.getId(), "apply", List.of("wangwu")));

        assertEquals(2, FlowEngine.hisTaskService().getByInsId(instance.getId()).stream()
            .filter(item -> TaskStatus.RESTART.getKey().equals(item.getFlowStatus())).count());
    }

    @Test
    void restart_rejectsRunningAndArbitraryTerminalStatuses() {
        Instance running = finished("restart-running-no-task", "biz-restart-running-no-task");
        running.setFlowStatus(FlowStatus.WAITING.getKey());
        FlowEngine.insService().updateById(running);
        assertThrows(FlowException.class, () -> FlowEngine.workflow().restart(
            command(running.getId(), "apply", List.of("lisi"))));

        Instance custom = finished("restart-custom", "biz-restart-custom");
        custom.setFlowStatus("custom_terminal");
        FlowEngine.insService().updateById(custom);
        assertThrows(FlowException.class, () -> FlowEngine.workflow().restart(
            command(custom.getId(), "apply", List.of("lisi"))));

        assertEquals(List.of(), TestFlows.pendingTasks(running.getId()));
        assertEquals(List.of(), TestFlows.pendingTasks(custom.getId()));
    }

    private Instance finished(String flowCode, String businessId) {
        Instance instance = TestFlows.start(flowCode, businessId);
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        return FlowEngine.insService().getById(instance.getId());
    }

    private RestartCommand command(Long instanceId, String targetNodeCode, List<String> handlers) {
        RestartCommand command = new RestartCommand();
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
