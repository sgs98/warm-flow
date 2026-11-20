package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    void dynamicTargetRejectWithoutStaticAnchorCreatesReturnIntent() {
        // 无静态目标（anyNodeSkip 与退回线均无）的直返节点：办理人驳回时指定目标，
        // 运行时兜底校验通过后建立直返关联——与静态目标路径同一契约。
        Instance instance = atD(DIRECT);
        removeStaticAnchors(instance, "D", "A");
        Long source = task(instance).getId();
        var command = new org.dromara.warm.flow.core.workflow.command.RejectCommand();
        command.setTaskId(source);
        command.setTargetNodeCode("A");
        command.setOperator(operator(TestFlows.HANDLER));
        FlowEngine.workflow().reject(command);
        Task a = task(instance);
        assertEquals("A", a.getNodeCode());
        var intent = RejectReturnRuntime.find(stored(instance), "A", a.getId()).orElseThrow();
        assertEquals(source, intent.sourceTaskId());
        assertEquals("D", intent.returnNodeCode());
        // A 重新审批通过后直接返回 D
        TestFlows.pass(a.getId(), TestFlows.HANDLER);
        assertEquals("D", task(instance).getNodeCode());
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
    }

    /** 删除节点的 anyNodeSkip 与 REJECT 退回线，构造「无静态目标」的直返节点。 */
    private void removeStaticAnchors(Instance instance, String nodeCode, String rejectTarget) {
        org.dromara.warm.flow.core.entity.Node node = FlowEngine.nodeService()
            .getByDefIdAndNodeCode(instance.getDefinitionId(), nodeCode);
        node.setAnyNodeSkip(null);
        FlowEngine.nodeService().updateById(node);
        for (org.dromara.warm.flow.core.entity.Skip skip : node.getSkipList()) {
            if (org.dromara.warm.flow.core.enums.SkipType.isReject(skip.getSkipType())
                && rejectTarget.equals(skip.getNextNodeCode())) {
                harness.skipDao.deleteById(skip.getId());
            }
        }
    }

    @Test
    void missingTransactionFailsWithoutChangingTheConfiguredRoute() {
        Instance instance = atD(DIRECT);
        Long source = task(instance).getId();
        org.dromara.warm.flow.core.service.task.RejectConfig.setAtomicExecutionSupported(false);
        try {
            unchangedOnFailure(() -> TestFlows.reject(source, TestFlows.HANDLER));
        } finally {
            org.dromara.warm.flow.core.service.task.RejectConfig.setAtomicExecutionSupported(null);
        }
        assertEquals(source, task(instance).getId());
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
    }

    @Test
    void missingTransactionDoesNotCancelBoundaryWaitOrSilentlyReplay() {
        Instance instance = atD(DIRECT);
        Long source = task(instance).getId();
        FlowEngine.eventWaitService().save(FlowEngine.newEventWait().setInstanceId(instance.getId())
            .setDefinitionId(instance.getDefinitionId()).setNodeCode("boundary").setAttachedTaskId(source)
            .setStatus("WAITING").setEventType("TIMER_BOUNDARY").setVersion(0));
        org.dromara.warm.flow.core.service.task.RejectConfig.setAtomicExecutionSupported(false);
        try {
            unchangedOnFailure(() -> TestFlows.reject(source, TestFlows.HANDLER));
        } finally {
            org.dromara.warm.flow.core.service.task.RejectConfig.setAtomicExecutionSupported(null);
        }
        assertEquals(source, task(instance).getId());
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
        assertEquals(1, FlowEngine.eventWaitService().getActiveByInstanceId(instance.getId()).size());
    }

    @Test
    void exclusiveGatewayInReturnIntervalIsSkippedOnDirectReturn() {
        // 返回区间 A → 排他网关 G → B → C → D：直返不执行网关，A 通过后直接回到 D
        Instance instance = TestFlows.startRejectReturnGatewayFlow("gatewayDirect", "bizGateway", DIRECT);
        for (int i = 0; i < 3; i++) { TestFlows.pass(task(instance).getId(), TestFlows.HANDLER); }
        assertEquals("D", task(instance).getNodeCode());
        TestFlows.reject(task(instance).getId(), TestFlows.HANDLER);
        Task a = task(instance);
        assertEquals("A", a.getNodeCode());
        assertEquals("D", RejectReturnRuntime.find(stored(instance), "A", a.getId())
            .orElseThrow().returnNodeCode());
        long histories = harness.hisTaskDao.size();
        RecordingListener.EVENTS.clear();
        TestFlows.pass(a.getId(), TestFlows.HANDLER);
        assertEquals("D", task(instance).getNodeCode());
        assertEquals(histories + 1, harness.hisTaskDao.size());
        assertTrue(RecordingListener.EVENTS.stream().noneMatch(e -> e.contains("node=B") || e.contains("node=C")));
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
    }

    @Test
    void parallelBranchRejectDegradesEvenWithoutActiveSibling() {
        // 驳回节点位于并行分支内：即使兄弟分支已办结（无其它活动待办），也不建立直返关联，
        // 申请节点通过后沿分支正常重走，不直达并行区间。
        Instance instance = TestFlows.startParallelRejectReturnFlow("parallelDirect", "bizParallel", DIRECT);
        TestFlows.pass(taskOf(instance, "a1").getId(), TestFlows.HANDLER);
        TestFlows.pass(taskOf(instance, "b1").getId(), TestFlows.HANDLER);
        TestFlows.reject(taskOf(instance, "a2").getId(), TestFlows.HANDLER);
        assertEquals("a1", task(instance).getNodeCode());
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("a2", task(instance).getNodeCode());
    }

    @Test
    void nestedParallelAndInclusiveBranchesReplayWithoutReturnIntent() {
        for (int outer : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
            for (int inner : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
                Instance instance = startNestedReturnFlow("nested" + outer + inner, outer, inner);
                TestFlows.reject(task(instance).getId(), TestFlows.HANDLER);
                assertEquals("A", task(instance).getNodeCode());
                assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
                TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
                assertEquals("B", task(instance).getNodeCode());
                assertEquals(DIRECT, FlowEngine.nodeService()
                    .getByDefIdAndNodeCode(instance.getDefinitionId(), "D").getNodeConfig());
            }
        }
    }

    @Test
    void partialJoinBranchesReplayWithoutReturnIntent() {
        for (int outer : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
            for (int join : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
                Instance instance = startNestedReturnFlow("partial" + outer + join, outer, join, true);
                TestFlows.reject(task(instance).getId(), TestFlows.HANDLER);
                assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
                TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
                assertEquals("B", task(instance).getNodeCode());
            }
        }
    }

    @Test
    void directReturnStillWorksAfterACompleteJoinInAnEndlessLoop() {
        for (int type : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
            Definition definition = FlowEngine.newDef().setFlowCode("closedLoop" + type).setFlowName("闭环汇聚后直返")
                .setVersion("1").setIsPublish(PublishStatus.PUBLISHED.getKey())
                .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
            FlowEngine.defService().save(definition);
            Map<String, Integer> types = new LinkedHashMap<>();
            for (String code : List.of("start", "S", "A", "F", "L", "R", "J", "B", "C", "D", "end")) {
                int nodeType = "start".equals(code) ? NodeType.START.getKey() : "end".equals(code)
                    ? NodeType.END.getKey() : "S".equals(code) ? NodeType.SERIAL.getKey()
                    : List.of("F", "J").contains(code) ? type : NodeType.BETWEEN.getKey();
                types.put(code, nodeType);
                FlowEngine.nodeService().save(FlowEngine.newNode().setDefinitionId(definition.getId()).setNodeCode(code)
                    .setNodeName(code).setNodeType(nodeType).setNodeRatio("0").setPermissionFlag(TestFlows.HANDLER)
                    .setNodeConfig("D".equals(code) ? DIRECT : null).setAnyNodeSkip("D".equals(code) ? "B" : null));
            }
            for (String[] link : List.of(new String[]{"start", "S"}, new String[]{"S", "A"}, new String[]{"S", "end"},
                new String[]{"A", "F"}, new String[]{"F", "L"}, new String[]{"F", "R"},
                new String[]{"L", "J"}, new String[]{"R", "J"}, new String[]{"J", "B"},
                new String[]{"B", "C"}, new String[]{"C", "D"}, new String[]{"D", "A"}, new String[]{"D", "B"})) {
                FlowEngine.skipService().save(FlowEngine.newSkip().setDefinitionId(definition.getId())
                    .setNowNodeCode(link[0]).setNextNodeCode(link[1]).setNowNodeType(types.get(link[0]))
                    .setNextNodeType(types.get(link[1])).setSkipType("D".equals(link[0]) && "B".equals(link[1])
                        ? SkipType.REJECT.getKey() : SkipType.PASS.getKey()));
            }
            Instance instance = FlowEngine.insService().start("bizLoop" + type, definition.getFlowCode(), TestFlows.context(TestFlows.HANDLER));
            for (String code : List.of("A", "L", "R", "B", "C")) {
                TestFlows.pass(taskOf(instance, code).getId(), TestFlows.HANDLER);
            }
            TestFlows.reject(task(instance).getId(), TestFlows.HANDLER);
            assertEquals("B", task(instance).getNodeCode());
            assertTrue(RejectReturnRuntime.hasPending(stored(instance)));
            TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
            assertEquals("D", task(instance).getNodeCode());
        }
    }

    @Test
    void directReturnWorksAfterACompleteJoinBeforeAMultiEntryClosedCycle() {
        for (int fork : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
            for (int join : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
                Definition definition = FlowEngine.newDef().setFlowCode("multiEntryCycle" + fork + join)
                    .setFlowName("多入口闭环前汇聚直返").setVersion("1").setIsPublish(PublishStatus.PUBLISHED.getKey())
                    .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
                FlowEngine.defService().save(definition);
                Map<String, Integer> types = new LinkedHashMap<>();
                for (String code : List.of("start", "T", "F", "L", "R", "J", "A", "B", "D", "S", "H", "K", "end")) {
                    int nodeType = "start".equals(code) ? NodeType.START.getKey() : "end".equals(code) ? NodeType.END.getKey()
                        : List.of("T", "S").contains(code) ? NodeType.SERIAL.getKey() : "F".equals(code) ? fork
                        : "J".equals(code) ? join : NodeType.BETWEEN.getKey();
                    types.put(code, nodeType);
                    FlowEngine.nodeService().save(FlowEngine.newNode().setDefinitionId(definition.getId()).setNodeCode(code)
                        .setNodeName(code).setNodeType(nodeType).setNodeRatio("0").setPermissionFlag(TestFlows.HANDLER)
                        .setNodeConfig("D".equals(code) ? DIRECT : null).setAnyNodeSkip("D".equals(code) ? "A" : null));
                }
                for (String[] link : List.of(new String[]{"start", "T"}, new String[]{"T", "F"}, new String[]{"T", "end"},
                    new String[]{"F", "L"}, new String[]{"F", "R"}, new String[]{"L", "J"}, new String[]{"R", "J"},
                    new String[]{"J", "A"}, new String[]{"A", "B"}, new String[]{"B", "D"}, new String[]{"D", "S"},
                    new String[]{"S", "H"}, new String[]{"S", "K"}, new String[]{"H", "K"}, new String[]{"K", "H"},
                    new String[]{"D", "A"})) {
                    FlowEngine.skipService().save(FlowEngine.newSkip().setDefinitionId(definition.getId())
                        .setNowNodeCode(link[0]).setNextNodeCode(link[1]).setNowNodeType(types.get(link[0]))
                        .setNextNodeType(types.get(link[1])).setSkipType("D".equals(link[0]) && "A".equals(link[1])
                            ? SkipType.REJECT.getKey() : SkipType.PASS.getKey()));
                }
                Instance instance = FlowEngine.insService().start("bizMultiEntry" + fork + join, definition.getFlowCode()
                    , TestFlows.context(TestFlows.HANDLER));
                for (String code : List.of("L", "R", "A", "B")) {
                    TestFlows.pass(taskOf(instance, code).getId(), TestFlows.HANDLER);
                }
                TestFlows.reject(task(instance).getId(), TestFlows.HANDLER);
                assertEquals("A", task(instance).getNodeCode());
                assertTrue(RejectReturnRuntime.hasPending(stored(instance)));
                TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
                assertEquals("D", task(instance).getNodeCode());
                assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
            }
        }
    }

    @Test
    void legacyReturnIntentFromNestedBranchReplaysAndClearsAssociation() {
        Instance instance = startNestedReturnFlow("legacyNested", NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey());
        Long source = task(instance).getId();
        TestFlows.reject(source, TestFlows.HANDLER);
        Instance legacy = stored(instance);
        RejectReturnRuntime.clear(legacy);
        RejectReturnRuntime.put(legacy, "A", task(instance).getId(), source, "D");
        FlowEngine.insService().updateById(legacy);
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("B", task(instance).getNodeCode());
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
        assertEquals(DIRECT, FlowEngine.nodeService()
            .getByDefIdAndNodeCode(instance.getDefinitionId(), "D").getNodeConfig());
    }

    @Test
    void legacyBranchReturnIntentSurvivesDelegatedOpinionUntilCompletion() {
        Instance instance = startNestedReturnFlow("legacyDelegated", NodeType.INCLUSIVE.getKey(), NodeType.PARALLEL.getKey());
        Long source = task(instance).getId();
        TestFlows.reject(source, TestFlows.HANDLER);
        Instance legacy = stored(instance);
        RejectReturnRuntime.clear(legacy);
        Long returned = task(instance).getId();
        RejectReturnRuntime.put(legacy, "A", returned, source, "D");
        FlowEngine.insService().updateById(legacy);
        DelegateCommand delegate = new DelegateCommand();
        delegate.setTaskId(returned);
        delegate.setTargetHandler("lisi");
        delegate.setOperator(operator(TestFlows.HANDLER));
        FlowEngine.workflow().delegate(delegate);
        TestFlows.pass(returned, "lisi");
        assertEquals(returned, task(instance).getId());
        assertTrue(RejectReturnRuntime.hasPending(stored(instance)));
        TestFlows.pass(returned, TestFlows.HANDLER);
        assertEquals("B", task(instance).getNodeCode());
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
    }

    @Test
    void legacyBranchReturnIntentSurvivesPartialCooperativeApproval() {
        for (int gateway : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
            for (String mode : List.of("vote", "countersign", "sequence", "group-parallel", "group-sequence")) {
                Definition definition = TestFlows.parallelRejectReturnFlow("legacyCooperative" + gateway + mode, DIRECT);
                for (String code : List.of("forkP", "joinP")) {
                    Node node = FlowEngine.nodeService().getByDefIdAndNodeCode(definition.getId(), code);
                    FlowEngine.nodeService().updateById(node.setNodeType(gateway));
                }
                Node target = FlowEngine.nodeService().getByDefIdAndNodeCode(definition.getId(), "a1");
                if (mode.startsWith("group")) {
                    target.setCooperateType("group").setNodeRatio("0").setPermissionFlag(null)
                        .setNodeConfig("{\"groupSign\":{\"groups\":[{\"id\":\"g1\",\"members\":[\"zhangsan\"]},"
                            + "{\"id\":\"g2\",\"members\":[\"lisi\"]}],\"order\":\""
                            + (mode.endsWith("sequence") ? "sequence" : "parallel") + "\"}}");
                } else {
                    target.setNodeRatio("vote".equals(mode) ? "75" : "100").setPermissionFlag("zhangsan@@lisi")
                        .setCooperateType("sequence".equals(mode) ? "sequence" : "parallel");
                }
                FlowEngine.nodeService().updateById(target);
                FlowEngine.nodeService().save(FlowEngine.newNode().setDefinitionId(definition.getId())
                    .setNodeCode("middle").setNodeName("middle").setNodeType(NodeType.BETWEEN.getKey())
                    .setNodeRatio("0").setPermissionFlag(TestFlows.HANDLER));
                for (Skip skip : FlowEngine.skipService().getByDefId(definition.getId())) {
                    if ("a1".equals(skip.getNowNodeCode()) && SkipType.isPass(skip.getSkipType())) {
                        skip.setNextNodeCode("middle");
                    }
                    if (List.of("forkP", "joinP").contains(skip.getNowNodeCode())) {
                        skip.setNowNodeType(gateway);
                    }
                    if (List.of("forkP", "joinP").contains(skip.getNextNodeCode())) {
                        skip.setNextNodeType(gateway);
                    }
                    FlowEngine.skipService().updateById(skip);
                }
                FlowEngine.skipService().save(FlowEngine.newSkip().setDefinitionId(definition.getId())
                    .setNowNodeCode("middle").setNextNodeCode("a2").setNowNodeType(NodeType.BETWEEN.getKey())
                    .setNextNodeType(NodeType.BETWEEN.getKey()).setSkipType(SkipType.PASS.getKey()));
                Instance instance = FlowEngine.insService().start("biz" + definition.getFlowCode(), definition.getFlowCode()
                    , TestFlows.context(TestFlows.HANDLER));
                TestFlows.pass(taskOf(instance, "b1").getId(), TestFlows.HANDLER);
                TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
                assertEquals("a1", task(instance).getNodeCode());
                TestFlows.pass(task(instance).getId(), "lisi");
                TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
                Long source = task(instance).getId();
                TestFlows.reject(source, TestFlows.HANDLER);
                Long returned = task(instance).getId();
                Instance legacy = stored(instance);
                RejectReturnRuntime.put(legacy, "a1", returned, source, "a2");
                FlowEngine.insService().updateById(legacy);
                FlowEngine.eventWaitService().save(FlowEngine.newEventWait().setInstanceId(instance.getId())
                    .setDefinitionId(definition.getId()).setNodeCode("boundary").setAttachedTaskId(returned)
                    .setStatus("WAITING").setEventType("TIMER_BOUNDARY").setVersion(0));

                TestFlows.pass(returned, TestFlows.HANDLER);

                assertEquals(returned, task(instance).getId(), mode);
                assertTrue(RejectReturnRuntime.find(stored(instance), "a1", returned).isPresent(), mode);
                assertEquals(1, FlowEngine.eventWaitService().getActiveByInstanceId(instance.getId()).size(), mode);
                assertEquals(List.of("lisi"), harness.userDao.all().stream()
                    .filter(user -> returned.equals(user.getAssociated())).map(User::getProcessedBy).toList(), mode);

                TestFlows.pass(returned, "lisi");

                assertEquals("middle", task(instance).getNodeCode(), mode);
                assertFalse(RejectReturnRuntime.hasPending(stored(instance)), mode);
                assertTrue(FlowEngine.eventWaitService().getActiveByInstanceId(instance.getId()).isEmpty(), mode);
                assertEquals(DIRECT, FlowEngine.nodeService()
                    .getByDefIdAndNodeCode(definition.getId(), "a2").getNodeConfig(), mode);
            }
        }
    }

    @Test
    void branchReplayDoesNotApplyDirectReturnEventWaitRestriction() {
        Instance instance = startNestedReturnFlow("branchBoundary", NodeType.INCLUSIVE.getKey(), NodeType.PARALLEL.getKey());
        Long source = task(instance).getId();
        FlowEngine.eventWaitService().save(FlowEngine.newEventWait().setInstanceId(instance.getId())
            .setDefinitionId(instance.getDefinitionId()).setNodeCode("boundary").setAttachedTaskId(source)
            .setStatus("WAITING").setEventType("TIMER_BOUNDARY").setVersion(0));
        assertDoesNotThrow(() -> TestFlows.reject(source, TestFlows.HANDLER));
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
        assertTrue(FlowEngine.eventWaitService().getActiveByInstanceId(instance.getId()).isEmpty());
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("B", task(instance).getNodeCode());
    }

    @Test
    void legacyBranchReturnWithActiveBoundaryWaitReplaysNormally() {
        Instance instance = startNestedReturnFlow("legacyBoundary", NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey());
        Long source = task(instance).getId();
        TestFlows.reject(source, TestFlows.HANDLER);
        Instance legacy = stored(instance);
        Long returned = task(instance).getId();
        RejectReturnRuntime.put(legacy, "A", returned, source, "D");
        FlowEngine.insService().updateById(legacy);
        FlowEngine.eventWaitService().save(FlowEngine.newEventWait().setInstanceId(instance.getId())
            .setDefinitionId(instance.getDefinitionId()).setNodeCode("boundary").setAttachedTaskId(returned)
            .setStatus("WAITING").setEventType("TIMER_BOUNDARY").setVersion(0));
        assertDoesNotThrow(() -> TestFlows.pass(returned, TestFlows.HANDLER));
        assertEquals("B", task(instance).getNodeCode());
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
        assertTrue(FlowEngine.eventWaitService().getActiveByInstanceId(instance.getId()).isEmpty());
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
    void completeGatewayIntervalReturnsDirectlyWithoutRebuildingBranches() {
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
        assertTrue(RejectReturnRuntime.hasPending(stored(instance)), "完整汇聚后的节点应记录直返关联");
        long history = harness.hisTaskDao.all().stream().filter(task -> List.of("B", "C").contains(task.getNodeCode())).count();
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        assertEquals("D", task(instance).getNodeCode(), "A 提交后直接返回完整汇聚后的 D");
        assertEquals(history, harness.hisTaskDao.all().stream().filter(task -> List.of("B", "C").contains(task.getNodeCode())).count());
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
    }

    @Test
    void groupTargetDirectReturnSkipsCompletedParallelAndInclusiveIntervals() {
        for (int first : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
            for (int second : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
                Definition definition = FlowEngine.newDef().setFlowCode("completedIntervals" + first + second)
                    .setFlowName("完整网关后直返组签").setVersion("1").setIsPublish(PublishStatus.PUBLISHED.getKey())
                    .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
                FlowEngine.defService().save(definition);
                Map<String, Integer> types = new LinkedHashMap<>();
                for (String code : List.of("start", "A", "P", "L", "R", "J", "B", "Q", "U", "V", "K", "D", "end")) {
                    int type = "start".equals(code) ? 0 : "end".equals(code) ? 2 : List.of("P", "J").contains(code)
                        ? first : List.of("Q", "K").contains(code) ? second : 1;
                    types.put(code, type);
                    String config = "D".equals(code) ? DIRECT : "A".equals(code)
                        ? "{\"groupSign\":{\"groups\":[{\"id\":\"g1\",\"members\":[\"zhangsan\"]},{\"id\":\"g2\",\"members\":[\"lisi\"]}],\"order\":\"parallel\"}}" : null;
                    FlowEngine.nodeService().save(FlowEngine.newNode().setDefinitionId(definition.getId()).setNodeCode(code)
                        .setNodeName(code).setNodeType(type).setNodeRatio("0").setPermissionFlag("A".equals(code) ? null : TestFlows.HANDLER)
                        .setCooperateType("A".equals(code) ? "group" : null).setNodeConfig(config));
                }
                for (String[] link : List.of(new String[]{"start", "A"}, new String[]{"A", "P"}, new String[]{"P", "L"},
                    new String[]{"P", "R"}, new String[]{"L", "J"}, new String[]{"R", "J"}, new String[]{"J", "B"},
                    new String[]{"B", "Q"}, new String[]{"Q", "U"}, new String[]{"Q", "V"}, new String[]{"U", "K"},
                    new String[]{"V", "K"}, new String[]{"K", "D"}, new String[]{"D", "end"}, new String[]{"D", "A"})) {
                    FlowEngine.skipService().save(FlowEngine.newSkip().setDefinitionId(definition.getId())
                        .setNowNodeCode(link[0]).setNowNodeType(types.get(link[0])).setNextNodeCode(link[1]).setNextNodeType(types.get(link[1]))
                        .setSkipType("D".equals(link[0]) && "A".equals(link[1]) ? SkipType.REJECT.getKey() : SkipType.PASS.getKey()));
                }
                Instance instance = FlowEngine.insService().start("biz" + definition.getFlowCode(), definition.getFlowCode(), TestFlows.context(TestFlows.HANDLER));
                TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
                TestFlows.pass(task(instance).getId(), "lisi");
                for (String code : List.of("L", "R", "B", "U", "V")) {
                    TestFlows.pass(taskOf(instance, code).getId(), TestFlows.HANDLER);
                }
                Long source = task(instance).getId();
                long history = harness.hisTaskDao.all().stream().filter(task -> List.of("L", "R", "B", "U", "V").contains(task.getNodeCode())).count();
                TestFlows.reject(source, TestFlows.HANDLER);
                Long returned = task(instance).getId();
                assertTrue(RejectReturnRuntime.hasPending(stored(instance)));
                TestFlows.pass(returned, TestFlows.HANDLER);
                assertEquals(returned, task(instance).getId());
                assertTrue(RejectReturnRuntime.hasPending(stored(instance)));
                TestFlows.pass(returned, "lisi");
                assertEquals("D", task(instance).getNodeCode());
                assertNotEquals(source, task(instance).getId());
                assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
                assertEquals(history, harness.hisTaskDao.all().stream().filter(task -> List.of("L", "R", "B", "U", "V").contains(task.getNodeCode())).count());
            }
        }
    }

    @Test
    void directReturnAcrossCompletedGatewaysStillRejectsActiveBoundaryWait() {
        Definition definition = parallelRejectReturnFlow("parallelIntervalBoundary");
        Instance instance = FlowEngine.insService().start("biz-parallel-interval-boundary", definition.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(task(instance).getId(), TestFlows.HANDLER);
        TestFlows.pass(taskAt(instance, "B").getId(), TestFlows.HANDLER);
        TestFlows.pass(taskAt(instance, "C").getId(), TestFlows.HANDLER);
        Long source = task(instance).getId();
        assertEquals("D", task(instance).getNodeCode());
        FlowEngine.eventWaitService().save(FlowEngine.newEventWait().setInstanceId(instance.getId())
            .setDefinitionId(definition.getId()).setNodeCode("boundary").setAttachedTaskId(source)
            .setStatus("WAITING").setEventType("TIMER_BOUNDARY").setVersion(0));

        unchangedOnFailure(() -> TestFlows.reject(source, TestFlows.HANDLER));

        assertEquals(source, task(instance).getId());
        assertFalse(RejectReturnRuntime.hasPending(stored(instance)));
        assertEquals(1, FlowEngine.eventWaitService().getActiveByInstanceId(instance.getId()).size());
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

    private Instance startNestedReturnFlow(String flowCode, int outer, int inner) {
        return startNestedReturnFlow(flowCode, outer, inner, false);
    }

    private Instance startNestedReturnFlow(String flowCode, int outer, int inner, boolean partial) {
        Definition definition = FlowEngine.newDef().setFlowCode(flowCode).setFlowName("嵌套分支驳回重走")
            .setVersion("1").setIsPublish(PublishStatus.PUBLISHED.getKey())
            .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(definition);
        Map<String, Integer> types = new LinkedHashMap<>();
        types.put("start", NodeType.START.getKey());
        for (String code : List.of("outerFork", "innerFork", "innerJoin", "outerJoin")) {
            if (partial && "innerFork".equals(code)) {
                continue;
            }
            types.put(code, code.startsWith("inner") ? inner : outer);
        }
        for (String code : List.of("left", "right", "sibling", "A", "B", "D")) {
            types.put(code, NodeType.BETWEEN.getKey());
        }
        types.put("end", NodeType.END.getKey());
        for (Map.Entry<String, Integer> entry : types.entrySet()) {
            FlowEngine.nodeService().save(FlowEngine.newNode().setDefinitionId(definition.getId())
                .setNodeCode(entry.getKey()).setNodeName(entry.getKey()).setNodeType(entry.getValue())
                .setNodeRatio("0").setPermissionFlag(TestFlows.HANDLER)
                .setNodeConfig("D".equals(entry.getKey()) ? DIRECT : null));
        }
        List<String[]> links = new ArrayList<>(List.of(new String[]{"start", "outerFork"},
            new String[]{"outerFork", "sibling"},
            new String[]{"left", "innerJoin"}, new String[]{"right", "innerJoin"},
            new String[]{"innerJoin", "A"}, new String[]{"A", "B"}, new String[]{"B", "D"},
            new String[]{"D", "outerJoin"}, new String[]{"sibling", "outerJoin"},
            new String[]{"outerJoin", "end"}, new String[]{"D", "A"}));
        if (partial) {
            links.add(new String[]{"outerFork", "left"});
            links.add(new String[]{"outerFork", "right"});
        } else {
            links.add(new String[]{"outerFork", "innerFork"});
            links.add(new String[]{"innerFork", "left"});
            links.add(new String[]{"innerFork", "right"});
        }
        for (String[] link : links) {
            FlowEngine.skipService().save(FlowEngine.newSkip().setDefinitionId(definition.getId())
                .setNowNodeCode(link[0]).setNextNodeCode(link[1]).setNowNodeType(types.get(link[0]))
                .setNextNodeType(types.get(link[1])).setSkipType("D".equals(link[0]) && "A".equals(link[1])
                    ? SkipType.REJECT.getKey() : SkipType.PASS.getKey()));
        }
        Instance instance = FlowEngine.insService().start("biz" + flowCode, flowCode, TestFlows.context(TestFlows.HANDLER));
        for (String code : List.of("sibling", "left", "right", "A", "B")) {
            TestFlows.pass(taskOf(instance, code).getId(), TestFlows.HANDLER);
        }
        return instance;
    }

    private Task taskOf(Instance instance, String nodeCode) {
        return FlowEngine.taskService().getByInsId(instance.getId()).stream()
            .filter(t -> nodeCode.equals(t.getNodeCode())).findFirst().orElseThrow();
    }
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
