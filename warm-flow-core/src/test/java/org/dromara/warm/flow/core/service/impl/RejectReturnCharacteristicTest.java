package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.test.listener.RecordingListener;
import org.dromara.warm.flow.core.utils.FlowConfigUtil;
import org.dromara.warm.flow.core.workflow.command.*;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/** 驳回直返的待办、历史、监听器和生命周期契约。 @author may */
class RejectReturnCharacteristicTest {
    private FlowTestHarness harness;
    static final String DIRECT = "{\"reject\":{\"schemaVersion\":1,\"afterApproval\":\"RETURN_TO_REJECTOR\"}}";

    @BeforeEach
    void setUp() { harness = new FlowTestHarness(); }

    @AfterEach
    void tearDown() { harness.close(); }

    @Test
    void defaultReplaysABCD() {
        Instance instance = atD(null);
        TestFlows.reject(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("A", task(instance).getNodeCode());
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("B", task(instance).getNodeCode());
    }

    @Test
    void configuredDDoesNotChangeNormalAToB() {
        Instance instance = start(DIRECT);
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("B", task(instance).getNodeCode());
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
    }

    @Test
    void rejectCreatesFreshATaskWithReturnIntent() {
        Instance instance = atD(DIRECT);
        Long source = task(instance).getId();
        TestFlows.reject(source, TestFlows.HANDLER);
        Task a = task(instance);
        var intent = RejectReturnRuntime.find(stored(instance), "A", a.getId()).orElseThrow();
        assertEquals(source, intent.sourceTaskId());
        assertEquals("D", intent.returnNodeCode());
        assertNotEquals(source, a.getId());
        assertNull(FlowEngine.taskService().getById(source));
        assertTrue(RejectReturnRuntime.find(stored(instance), "A", source).isEmpty());
    }

    @Test
    void completeAReturnsToFreshDWithoutBC() {
        Instance instance = rejected();
        Task a = task(instance);
        long histories = harness.hisTaskDao.size();
        RecordingListener.EVENTS.clear();
        TestFlows.pass(a.getId(), TestFlows.HANDLER);
        Task d = task(instance);
        assertEquals("D", d.getNodeCode());
        assertNotEquals(a.getId(), d.getId());
        assertEquals(histories + 1, harness.hisTaskDao.size());
        assertTrue(harness.hisTaskDao.all().stream().anyMatch(h -> a.getId().equals(h.getTaskId())
            && SkipType.isPass(h.getSkipType()) && "D".equals(h.getTargetNodeCode())));
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)), "最终落库不复活旧关联");
        assertTrue(RecordingListener.EVENTS.stream().noneMatch(e -> e.contains("node=B") || e.contains("node=C")));
        assertEquals(1, FlowEngine.taskService().getByInsId(instance.getId()).size());
        assertEquals(1, harness.userDao.all().stream().filter(u -> d.getId().equals(u.getAssociated())).count());
    }

    @Test
    void secondRejectionUsesNewTaskIds() {
        Instance instance = rejected();
        Long firstA = task(instance).getId();
        TestFlows.pass(firstA, TestFlows.HANDLER);
        Long secondD = task(instance).getId();
        TestFlows.reject(secondD, TestFlows.HANDLER);
        Task secondA = task(instance);
        assertNotEquals(firstA, secondA.getId());
        assertEquals(secondD, RejectReturnRuntime.find(stored(instance), "A", secondA.getId())
            .orElseThrow().sourceTaskId());
        TestFlows.pass(secondA.getId(), TestFlows.HANDLER);
        assertEquals("D", task(instance).getNodeCode());
    }

    @Test
    void pendingRejectJumpAndRevokeFailWithoutWrites() {
        Instance instance = rejected();
        Task a = task(instance);
        unchangedOnFailure(() -> TestFlows.reject(a.getId(), TestFlows.HANDLER));
        org.dromara.warm.flow.core.workflow.context.WorkflowContext jump = TestFlows.context(TestFlows.HANDLER);
        jump.setTargetNodeCode("B");
        unchangedOnFailure(() -> FlowEngine.taskService().execute(a.getId(), jump, SkipType.PASS.getKey()));
        unchangedOnFailure(() -> FlowEngine.taskService().revoke(instance.getId(), TestFlows.context(TestFlows.HANDLER)));
        assertTrue(RejectReturnRuntime.hasPending(stored(instance)));
    }

    @Test
    void delegateShortCircuitKeepsIntentAndTransferKeepsTaskId() {
        Instance instance = rejected();
        Long a = task(instance).getId();
        DelegateCommand delegate = new DelegateCommand();
        delegate.setTaskId(a);
        delegate.setTargetHandler("lisi");
        delegate.setOperator(operator(TestFlows.HANDLER));
        FlowEngine.workflow().delegate(delegate);
        TestFlows.pass(a, "lisi");
        assertEquals(a, task(instance).getId());
        assertTrue(RejectReturnRuntime.hasPending(stored(instance)));
        TransferCommand transfer = new TransferCommand();
        transfer.setTaskId(a);
        transfer.setTargetHandler("wangwu");
        transfer.setOperator(operator(TestFlows.HANDLER));
        FlowEngine.workflow().transfer(transfer);
        assertEquals(a, task(instance).getId());
        TestFlows.pass(a, "wangwu");
        assertEquals("D", task(instance).getNodeCode());
    }

    @Test
    void delegatedNegativeOpinionReturnsToDelegatorWithoutRouting() {
        Instance instance = rejected();
        Long a = task(instance).getId();
        DelegateCommand delegate = new DelegateCommand();
        delegate.setTaskId(a);
        delegate.setTargetHandler("lisi");
        delegate.setOperator(operator(TestFlows.HANDLER));
        FlowEngine.workflow().delegate(delegate);
        String before = snapshot(instance);
        unchangedOnFailure(() -> TestFlows.reject(a, "stranger"));
        TestFlows.reject(a, "lisi");
        assertEquals(a, task(instance).getId());
        assertEquals(before, snapshot(instance));
        assertTrue(harness.hisTaskDao.all().stream().anyMatch(h -> a.equals(h.getTaskId())
            && "lisi".equals(h.getApprover()) && SkipType.isReject(h.getSkipType())
            && org.dromara.warm.flow.core.enums.CooperateType.DELEGATE.getKey().equals(h.getCooperateType())));
        assertFalse(harness.userDao.all().stream().anyMatch(u -> a.equals(u.getAssociated())
            && "lisi".equals(u.getProcessedBy())
            && org.dromara.warm.flow.core.enums.UserType.DEPUTE.getKey().equals(u.getType())));
        unchangedOnFailure(() -> TestFlows.reject(a, TestFlows.HANDLER));
        TestFlows.pass(a, TestFlows.HANDLER);
        assertEquals("D", task(instance).getNodeCode());
    }

    @Test
    void invalidSourceHistoryFailsBeforeListener() {
        Instance instance = rejected();
        Task a = task(instance);
        Instance stored = FlowEngine.insService().getById(instance.getId());
        RejectReturnRuntime.clear(stored);
        RejectReturnRuntime.put(stored, "A", a.getId(), 999999L, "D");
        FlowEngine.insService().updateById(stored);
        unchangedOnFailure(() -> TestFlows.pass(a.getId(), TestFlows.HANDLER));
    }

    @Test
    void changedReturnNodeAndMultipleActiveTasksFailWithoutWrites() {
        Instance instance = rejected();
        Node d = FlowEngine.nodeService().getByDefIdAndNodeCode(instance.getDefinitionId(), "D");
        d.setNodeType(6);
        FlowEngine.nodeService().updateById(d);
        unchangedOnFailure(() -> TestFlows.pass(task(instance).getId(), TestFlows.HANDLER));
        d.setNodeType(1);
        FlowEngine.nodeService().updateById(d);
        Task a = task(instance);
        FlowEngine.taskService().save(FlowEngine.newTask().setDefinitionId(instance.getDefinitionId())
            .setInstanceId(instance.getId()).setNodeCode("B").setNodeType(1));
        unchangedOnFailure(() -> TestFlows.pass(a.getId(), TestFlows.HANDLER));
    }

    @Test
    void activeEventWaitBlocksReturnBeforeAnySideEffects() {
        Instance instance = rejected();
        FlowEngine.eventWaitService().save(FlowEngine.newEventWait()
            .setDefinitionId(instance.getDefinitionId()).setInstanceId(instance.getId()).setNodeCode("hidden")
            .setEventType("MESSAGE_CATCH").setStatus("WAITING"));
        int waits = harness.eventWaitDao.size();
        unchangedOnFailure(() -> TestFlows.pass(task(instance).getId(), TestFlows.HANDLER));
        assertEquals(waits, harness.eventWaitDao.size());
    }

    @Test
    void staleTaskIdentityAndMissingReturnNodeFailBeforeWrites() {
        Instance instance = rejected();
        Task a = task(instance);
        var intent = RejectReturnRuntime.find(stored(instance), "A", a.getId()).orElseThrow();
        Instance stored = FlowEngine.insService().getById(instance.getId());
        RejectReturnRuntime.clear(stored);
        RejectReturnRuntime.put(stored, "A", a.getId() + 1L, intent.sourceTaskId(), "D");
        FlowEngine.insService().updateById(stored);
        unchangedOnFailure(() -> TestFlows.pass(a.getId(), TestFlows.HANDLER));
        RejectReturnRuntime.clear(stored);
        RejectReturnRuntime.put(stored, "A", a.getId(), intent.sourceTaskId(), "D");
        FlowEngine.insService().updateById(stored);
        FlowEngine.nodeService().removeById(FlowEngine.nodeService().getByDefIdAndNodeCode(instance.getDefinitionId(), "D").getId());
        unchangedOnFailure(() -> TestFlows.pass(a.getId(), TestFlows.HANDLER));
    }

    @Test
    void autoPassStillLeavesDirectReturnDPending() {
        Instance instance = rejected();
        Definition definition = FlowEngine.defService().getById(instance.getDefinitionId());
        definition.setDefConfig("{\"autoPass\":true}");
        FlowEngine.defService().updateById(definition);
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("D", task(instance).getNodeCode());
    }

    @Test
    void businessVariablesExtAndSharedNodeAreNotRuntimeStorage() {
        Instance instance = rejected();
        Instance stored = FlowEngine.insService().getById(instance.getId());
        stored.setVariable("{\"custom\":\"business\"}").setExt("business-ext");
        FlowEngine.insService().updateById(stored);
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        stored = FlowEngine.insService().getById(instance.getId());
        assertEquals("business", stored.getVariableMap().get("custom"));
        assertEquals("business-ext", stored.getExt());
        assertNull(FlowEngine.nodeService().getByDefIdAndNodeCode(instance.getDefinitionId(), "A").getNodeConfig());
        assertEquals(DIRECT, FlowEngine.nodeService().getByDefIdAndNodeCode(instance.getDefinitionId(), "D").getNodeConfig());
    }

    @Test
    void terminateAndRestartAndResetClearRuntime() {
        for (boolean reset : List.of(false, true)) {
            Instance instance = rejected();
            String pending = snapshot(instance);
            TerminateCommand terminate = new TerminateCommand();
            terminate.setTaskId(task(instance).getId());
            terminate.setOperator(operator(TestFlows.HANDLER));
            FlowEngine.workflow().terminate(terminate);
            assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
            // 模拟旧版本终态残留关联，重启不得把它带入新的 A 待办。
            Instance ended = FlowEngine.insService().getById(instance.getId());
            ended.setDefJson(pending);
            FlowEngine.insService().updateById(ended);
            RestartCommand command = reset ? new ResetCommand() : new RestartCommand();
            command.setInstanceId(instance.getId());
            command.setTargetNodeCode("A");
            command.setHandlers(List.of(TestFlows.HANDLER));
            command.setOperator(operator(TestFlows.HANDLER));
            if (reset) {
                FlowEngine.workflow().reset((ResetCommand) command);
            } else {
                FlowEngine.workflow().restart(command);
            }
            assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
            TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
            assertEquals("B", task(instance).getNodeCode());
        }
    }

    @Test
    void consumedIntentDoesNotReturnAfterRevokeOrFinish() {
        Instance instance = rejected();
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        FlowEngine.taskService().revoke(instance.getId(), TestFlows.context(TestFlows.HANDLER));
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
        assertEquals("A", task(instance).getNodeCode());
        for (int i = 0; i < 4; i++) { TestFlows.pass(task(instance).getId(), TestFlows.HANDLER); }
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
        assertTrue(FlowEngine.taskService().getByInsId(instance.getId()).isEmpty());
    }

    @Test
    void countersignNodeInIntervalIsSkippedOnDirectReturn() {
        Definition def = TestFlows.rejectReturnFlow("rr-coop-1", DIRECT);
        countersign(def.getId(), "B", "zhangsan@@lisi");
        Instance instance = FlowEngine.insService().start("biz-coop-1", def.getFlowCode(), TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("B", task(instance).getNodeCode());
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("B", task(instance).getNodeCode());
        TestFlows.pass(task(instance).getId(), "lisi");
        assertEquals("C", task(instance).getNodeCode());
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("D", task(instance).getNodeCode());
        long bHistories = harness.hisTaskDao.all().stream().filter(h -> "B".equals(h.getNodeCode())).count();
        TestFlows.reject(task(instance).getId(), TestFlows.HANDLER);
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("D", task(instance).getNodeCode());
        assertEquals(bHistories, harness.hisTaskDao.all().stream().filter(h -> "B".equals(h.getNodeCode())).count());
        assertEquals(0, harness.taskDao.all().stream().filter(t -> "B".equals(t.getNodeCode())).count());
        assertEquals(0, harness.taskDao.all().stream().filter(t -> "C".equals(t.getNodeCode())).count());
    }

    @Test
    void countersignSourceRejectsWholeNodeAndCreatesFreshRound() {
        Definition def = TestFlows.rejectReturnFlow("rr-coop-2", DIRECT);
        countersign(def.getId(), "D", "zhangsan@@lisi");
        Instance instance = FlowEngine.insService().start("biz-coop-2", def.getFlowCode(), TestFlows.context(TestFlows.HANDLER));
        for (int i = 0; i < 3; i++) {
            TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        }
        assertEquals("D", task(instance).getNodeCode());
        TestFlows.reject(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("A", task(instance).getNodeCode());
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        Task d = task(instance);
        assertEquals("D", d.getNodeCode());
        assertEquals(2, harness.userDao.all().stream().filter(u -> d.getId().equals(u.getAssociated())).count());
    }

    @Test
    void countersignReturnNodeCompletesWholeNodeBeforeDirectReturn() {
        Definition def = TestFlows.rejectReturnFlow("rr-coop-3", DIRECT);
        countersign(def.getId(), "A", "zhangsan@@lisi");
        Instance instance = FlowEngine.insService().start("biz-coop-3", def.getFlowCode(), TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("A", task(instance).getNodeCode());
        TestFlows.pass(task(instance).getId(), "lisi");
        assertEquals("B", task(instance).getNodeCode());
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("D", task(instance).getNodeCode());
        TestFlows.reject(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("A", task(instance).getNodeCode());
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("A", task(instance).getNodeCode(), "会签未办结不应直达 D");
        TestFlows.pass(task(instance).getId(), "lisi");
        assertEquals("D", task(instance).getNodeCode());
    }

    @Test
    void parallelIntervalFallsBackToWholeRejectionReplay() {
        Definition def = parallelRejectReturnFlow("rr-par-1");
        Instance instance = FlowEngine.insService().start("biz-par-1", def.getFlowCode(), TestFlows.context(TestFlows.HANDLER));
        assertDoesNotThrow(() -> FlowConfigUtil.validateRejectReturnPath(
            FlowEngine.defService().getFlowCombine(def.getId()), "D", "A"));
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals(List.of("B", "C"), pendingNodes(instance));
        TestFlows.pass(taskAt(instance, "B").getId(), TestFlows.HANDLER);
        TestFlows.pass(taskAt(instance, "C").getId(), TestFlows.HANDLER);
        assertEquals("D", task(instance).getNodeCode());
        TestFlows.reject(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("A", task(instance).getNodeCode());
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)), "并行区间不记录直返关联");
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals(List.of("B", "C"), pendingNodes(instance), "A 提交后应重新分叉重走");
    }

    @Test
    void groupSignSourceRejectsWholeNodeAndCreatesFreshRound() {
        Definition def = TestFlows.rejectReturnFlow("rr-group-1",
            "{\"reject\":{\"afterApproval\":\"RETURN_TO_REJECTOR\"},"
                + "\"groupSign\":{\"groups\":[{\"id\":\"g1\",\"members\":[\"zhangsan\"]}],"
                + "\"order\":\"parallel\"}}");
        Node d = FlowEngine.nodeService().getByDefIdAndNodeCode(def.getId(), "D");
        d.setCooperateType("group").setNodeRatio("0").setPermissionFlag(null);
        FlowEngine.nodeService().updateById(d);
        Instance instance = FlowEngine.insService().start("biz-group-1", def.getFlowCode(), TestFlows.context(TestFlows.HANDLER));
        for (int i = 0; i < 3; i++) {
            TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        }
        assertEquals("D", task(instance).getNodeCode());
        TestFlows.reject(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("A", task(instance).getNodeCode());
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("D", task(instance).getNodeCode());
    }

    @Test
    void voteSignSourceRejectsOnlyAfterThreshold() {
        Definition def = TestFlows.rejectReturnFlow("rr-vote-1", DIRECT);
        Node d = FlowEngine.nodeService().getByDefIdAndNodeCode(def.getId(), "D");
        d.setNodeRatio("50").setPermissionFlag("zhangsan@@lisi");
        FlowEngine.nodeService().updateById(d);
        Instance instance = FlowEngine.insService().start("biz-vote-1", def.getFlowCode(), TestFlows.context(TestFlows.HANDLER));
        for (int i = 0; i < 3; i++) {
            TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        }
        assertEquals("D", task(instance).getNodeCode());
        // 第一票驳回未达阈值：节点继续，不记录直返关联
        TestFlows.reject(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("D", task(instance).getNodeCode());
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
        // 第二票驳回达到阈值：整节点退回并记录关联
        TestFlows.reject(task(instance).getId(), "lisi");
        assertEquals("A", task(instance).getNodeCode());
        assertTrue(RejectReturnRuntime.hasPending(stored(instance)));
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("D", task(instance).getNodeCode());
    }

    private Definition parallelRejectReturnFlow(String code) {
        Definition def = FlowEngine.newDef().setFlowCode(code).setFlowName("并行直返测试流程")
            .setVersion("1").setIsPublish(PublishStatus.PUBLISHED.getKey())
            .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(def);
        for (String nodeCode : List.of("start", "A", "forkP", "B", "C", "joinP", "D", "end")) {
            int type = switch (nodeCode) {
                case "start" -> NodeType.START.getKey();
                case "end" -> NodeType.END.getKey();
                case "forkP", "joinP" -> NodeType.PARALLEL.getKey();
                default -> NodeType.BETWEEN.getKey();
            };
            FlowEngine.nodeService().save(FlowEngine.newNode().setDefinitionId(def.getId())
                .setNodeCode(nodeCode).setNodeName(nodeCode).setNodeType(type).setNodeRatio("0")
                .setPermissionFlag(type == NodeType.BETWEEN.getKey() ? TestFlows.HANDLER : null));
        }
        Node d = FlowEngine.nodeService().getByDefIdAndNodeCode(def.getId(), "D");
        d.setNodeConfig(DIRECT);
        FlowEngine.nodeService().updateById(d);
        for (String[] edge : new String[][]{{"start", "A"}, {"A", "forkP"}, {"forkP", "B"}, {"forkP", "C"},
            {"B", "joinP"}, {"C", "joinP"}, {"joinP", "D"}, {"D", "end"}}) {
            FlowEngine.skipService().save(FlowEngine.newSkip().setDefinitionId(def.getId())
                .setNowNodeCode(edge[0]).setNextNodeCode(edge[1]).setSkipType(SkipType.PASS.getKey()));
        }
        FlowEngine.skipService().save(FlowEngine.newSkip().setDefinitionId(def.getId())
            .setNowNodeCode("D").setNextNodeCode("A").setSkipType(SkipType.REJECT.getKey()));
        return def;
    }

    private List<String> pendingNodes(Instance instance) {
        return TestFlows.pendingTasks(instance.getId()).stream().map(Task::getNodeCode).sorted().toList();
    }

    private Task taskAt(Instance instance, String nodeCode) {
        return TestFlows.pendingTasks(instance.getId()).stream()
            .filter(t -> nodeCode.equals(t.getNodeCode())).findFirst().orElseThrow();
    }

    private void countersign(Long definitionId, String nodeCode, String users) {
        Node node = FlowEngine.nodeService().getByDefIdAndNodeCode(definitionId, nodeCode);
        node.setNodeRatio("100").setPermissionFlag(users);
        FlowEngine.nodeService().updateById(node);
    }

    private Instance start(String config) {
        return TestFlows.startRejectReturnFlow("return-" + harness.insDao.size(), "biz-" + harness.insDao.size(), config);
    }

    private Instance atD(String config) {
        Instance instance = start(config);
        for (int i = 0; i < 3; i++) { TestFlows.pass(task(instance).getId(), TestFlows.HANDLER); }
        assertEquals("D", task(instance).getNodeCode());
        return instance;
    }

    private Instance rejected() {
        Instance instance = atD(DIRECT);
        TestFlows.reject(task(instance).getId(), TestFlows.HANDLER);
        return instance;
    }

    private Task task(Instance instance) { return TestFlows.currentTask(instance.getId()); }
    private Instance stored(Instance instance) { return FlowEngine.insService().getById(instance.getId()); }

    private String snapshot(Instance instance) { return stored(instance).getDefJson(); }
    private static OperatorContext operator(String handler) {
        OperatorContext operator = new OperatorContext();
        operator.setHandler(handler);
        operator.setIgnorePermission(true);
        return operator;
    }

    private void unchangedOnFailure(Runnable action) {
        long tasks = harness.taskDao.size(), users = harness.userDao.size(), history = harness.hisTaskDao.size();
        List<String> snapshots = harness.insDao.all().stream().map(Instance::getDefJson).toList();
        RecordingListener.EVENTS.clear();
        assertThrows(FlowException.class, action::run);
        assertEquals(tasks, harness.taskDao.size());
        assertEquals(users, harness.userDao.size());
        assertEquals(history, harness.hisTaskDao.size());
        assertEquals(snapshots, harness.insDao.all().stream().map(Instance::getDefJson).toList());
        assertTrue(RecordingListener.EVENTS.isEmpty(), RecordingListener.EVENTS.toString());
    }
}
