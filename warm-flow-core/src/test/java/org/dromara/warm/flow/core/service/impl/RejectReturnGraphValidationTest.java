package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.FlowCombine;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.utils.FlowConfigUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 直返仅允许可证明的线性人工返回区间。 @author may */
class RejectReturnGraphValidationTest {
    private FlowTestHarness harness;
    private FlowCombine graph;
    private Node d;
    private Node b;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
        graph = new FlowCombine();
        graph.setDefinition(FlowEngine.newDef().setId(1L).setFlowName("直返测试"));
        for (String code : List.of("start", "A", "B", "C", "D", "end")) {
            int type = "start".equals(code) ? 0 : "end".equals(code) ? 2 : 1;
            graph.getAllNodes().add(FlowEngine.newNode().setNodeCode(code).setNodeName(code)
                .setNodeType(type).setNodeRatio("0"));
        }
        b = node("B");
        d = node("D");
        d.setNodeConfig("{\"reject\":{\"afterApproval\":\"RETURN_TO_REJECTOR\"}}");
        List<String> codes = List.of("start", "A", "B", "C", "D", "end");
        for (int i = 0; i < codes.size() - 1; i++) {
            edge(codes.get(i), codes.get(i + 1), SkipType.PASS.getKey());
        }
        edge("D", "A", SkipType.REJECT.getKey());
    }

    @AfterEach
    void tearDown() { harness.close(); }

    @Test
    void linearHumanPathAccepted() {
        assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(graph));
        d.setAnyNodeSkip("B");
        assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(graph));
    }

    @Test
    void serviceEventAndBoundaryInReturnIntervalRejected() {
        // 并行 / 包容网关区间按整体退回重走处理（见 RejectReturnCharacteristicTest 的并行用例）；
        // 排他网关允许（见下游用例），服务任务、事件与边界事件仍拒绝。
        b.setNodeType(NodeType.SERVICE.getKey());
        rejected();
        b.setNodeType(NodeType.BETWEEN.getKey()).setEventType("MESSAGE_CATCH").setEventName("m");
        rejected();
        b.setEventType(null);
        Node boundary = FlowEngine.newNode().setNodeCode("timer").setNodeName("timer")
            .setNodeType(1).setEventType("TIMER_BOUNDARY").setAttachedToNodeCode("C")
            .setInterrupting(true).setSkipList(new ArrayList<>());
        graph.getAllNodes().add(boundary);
        edge("timer", "end", SkipType.PASS.getKey());
        rejected();
    }

    @Test
    void nodeInsideParallelBranchIsDetected() {
        // 并行分叉之后、汇聚之前的节点判定为分支内；分叉之前与汇聚之后的节点不属于分支
        Node fork = FlowEngine.newNode().setNodeCode("P").setNodeName("P")
            .setNodeType(NodeType.PARALLEL.getKey()).setSkipList(new ArrayList<>());
        Node sibling = FlowEngine.newNode().setNodeCode("X").setNodeName("X")
            .setNodeType(NodeType.BETWEEN.getKey()).setNodeRatio("0").setSkipList(new ArrayList<>());
        graph.getAllNodes().add(fork);
        graph.getAllNodes().add(sibling);
        edge("start", "P", SkipType.PASS.getKey());
        edge("P", "B", SkipType.PASS.getKey());
        edge("P", "X", SkipType.PASS.getKey());
        assertTrue(FlowConfigUtil.inParallelBranch(graph, "B"));
        assertTrue(FlowConfigUtil.inParallelBranch(graph, "X"));
        assertFalse(FlowConfigUtil.inParallelBranch(graph, "A"));
    }

    @Test
    void nestedGatewaysRemainInsideOuterBranchUntilOuterJoin() {
        for (int outer : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
            for (int inner : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
                nestedGatewayGraph(outer, inner);
                for (String code : List.of("left", "right", "sibling", "A", "B", "D")) {
                    assertTrue(FlowConfigUtil.inParallelBranch(graph, code), outer + "/" + inner + "/" + code);
                }
                assertFalse(FlowConfigUtil.inParallelBranch(graph, "after"));
                assertFalse(FlowConfigUtil.inParallelBranch(graph, "start"));
                assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(graph));
            }
        }
    }

    @Test
    void singleInputGatewayDoesNotCloseAnOuterBranch() {
        nestedGatewayGraph(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey());
        Skip skip = graph.getAllSkips().stream().filter(edge -> "right".equals(edge.getNowNodeCode()))
            .findFirst().orElseThrow();
        skip.setNextNodeCode("end");
        assertTrue(FlowConfigUtil.inParallelBranch(graph, "D"));
    }

    @Test
    void partialJoinDoesNotCloseRemainingParallelOrInclusiveBranches() {
        for (int outer : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
            for (int join : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
                nestedGatewayGraph(outer, join);
                List<Skip> removed = graph.getAllSkips().stream().filter(skip -> "innerFork".equals(skip.getNowNodeCode())
                    || "innerFork".equals(skip.getNextNodeCode())).collect(java.util.stream.Collectors.toList());
                for (Skip skip : removed) {
                    node(skip.getNowNodeCode()).getSkipList().remove(skip);
                    graph.getAllSkips().remove(skip);
                }
                graph.getAllNodes().remove(node("innerFork"));
                edge("outerFork", "left", SkipType.PASS.getKey());
                edge("outerFork", "right", SkipType.PASS.getKey());
                assertTrue(FlowConfigUtil.inParallelBranch(graph, "D"), outer + "/" + join);
                assertFalse(FlowConfigUtil.inParallelBranch(graph, "after"));
            }
        }
    }

    @Test
    void exclusiveBranchesJoiningInsideAnOuterBranchDoNotCloseIt() {
        nestedGatewayGraph(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey());
        node("innerFork").setNodeType(NodeType.SERIAL.getKey());
        assertTrue(FlowConfigUtil.inParallelBranch(graph, "D"));
        assertFalse(FlowConfigUtil.inParallelBranch(graph, "after"));
    }

    @Test
    void loopIntoAnInnerForkDoesNotCloseTheOuterBranch() {
        nestedGatewayGraph(NodeType.INCLUSIVE.getKey(), NodeType.PARALLEL.getKey());
        edge("D", "innerFork", SkipType.PASS.getKey());
        assertTrue(FlowConfigUtil.inParallelBranch(graph, "D"));
        assertFalse(FlowConfigUtil.inParallelBranch(graph, "after"));
    }

    @Test
    void branchDetectionTerminatesForGatewayCycles() {
        graph.getAllNodes().clear();
        graph.getAllSkips().clear();
        for (String code : List.of("J", "A", "X", "D")) {
            graph.getAllNodes().add(FlowEngine.newNode().setNodeCode(code).setNodeName(code)
                .setNodeType("J".equals(code) ? NodeType.PARALLEL.getKey() : NodeType.BETWEEN.getKey()));
        }
        for (String[] link : List.of(new String[]{"J", "A"}, new String[]{"A", "J"},
            new String[]{"A", "X"}, new String[]{"X", "J"}, new String[]{"A", "D"})) {
            edge(link[0], link[1], SkipType.PASS.getKey());
        }
        assertTimeout(Duration.ofSeconds(2), () -> assertFalse(FlowConfigUtil.inParallelBranch(graph, "D")));
    }

    @Test
    void completeJoinClosesAReopeningLoopWithoutAnEndPath() {
        for (int type : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
            graph.getAllNodes().clear();
            graph.getAllSkips().clear();
            for (String code : List.of("start", "S", "A", "F", "L", "R", "J", "B", "C", "D", "end")) {
                int nodeType = "start".equals(code) ? NodeType.START.getKey() : "end".equals(code)
                    ? NodeType.END.getKey() : "S".equals(code) ? NodeType.SERIAL.getKey()
                    : List.of("F", "J").contains(code) ? type : NodeType.BETWEEN.getKey();
                graph.getAllNodes().add(FlowEngine.newNode().setNodeCode(code).setNodeName(code)
                    .setNodeType(nodeType).setNodeRatio("0"));
            }
            node("D").setNodeConfig("{\"reject\":{\"afterApproval\":\"RETURN_TO_REJECTOR\"}}").setAnyNodeSkip("B");
            for (String[] link : List.of(new String[]{"start", "S"}, new String[]{"S", "A"}, new String[]{"S", "end"},
                new String[]{"A", "F"}, new String[]{"F", "L"}, new String[]{"F", "R"},
                new String[]{"L", "J"}, new String[]{"R", "J"}, new String[]{"J", "B"},
                new String[]{"B", "C"}, new String[]{"C", "D"}, new String[]{"D", "A"})) {
                edge(link[0], link[1], SkipType.PASS.getKey());
            }
            edge("D", "B", SkipType.REJECT.getKey());
            assertTrue(FlowConfigUtil.inParallelBranch(graph, "L"));
            assertFalse(FlowConfigUtil.inParallelBranch(graph, "D"));
            assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(graph));
        }
    }

    @Test
    void completeJoinBeforeAMultiEntryClosedCycleIsOutsideBranches() {
        for (int fork : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
            for (int join : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
                multiEntryCycleGraph(fork, join);
                assertTrue(FlowConfigUtil.inParallelBranch(graph, "L"));
                assertTrue(FlowConfigUtil.inParallelBranch(graph, "R"));
                for (String code : List.of("J", "A", "B", "D", "S", "H", "K")) {
                    assertFalse(FlowConfigUtil.inParallelBranch(graph, code), fork + "/" + join + "/" + code);
                }
                assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(graph));
            }
        }
    }

    @Test
    void branchEnteringAClosedCycleBeforeTheJoinKeepsTheForkOpen() {
        multiEntryCycleGraph(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey());
        graph.getAllNodes().add(FlowEngine.newNode().setNodeCode("Q").setNodeName("Q")
            .setNodeType(NodeType.BETWEEN.getKey()).setNodeRatio("0"));
        edge("F", "Q", SkipType.PASS.getKey());
        edge("Q", "J", SkipType.PASS.getKey());
        graph.getAllSkips().stream().filter(skip -> "R".equals(skip.getNowNodeCode()))
            .findFirst().orElseThrow().setNextNodeCode("H");
        assertTrue(FlowConfigUtil.inParallelBranch(graph, "D"));
        assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(graph));
    }

    @Test
    void completedGatewayIntervalsValidateBothBranchesAndForbidSideEffects() {
        for (int gateway : List.of(NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey())) {
            graph.getAllNodes().clear();
            graph.getAllSkips().clear();
            for (String code : List.of("start", "A", "P", "L", "R", "J", "D", "end")) {
                int type = "start".equals(code) ? 0 : "end".equals(code) ? 2
                    : List.of("P", "J").contains(code) ? gateway : 1;
                graph.getAllNodes().add(FlowEngine.newNode().setNodeCode(code).setNodeName(code).setNodeType(type).setNodeRatio("0"));
            }
            node("D").setNodeConfig("{\"reject\":{\"afterApproval\":\"RETURN_TO_REJECTOR\"}}");
            for (String[] link : List.of(new String[]{"start", "A"}, new String[]{"A", "P"}, new String[]{"P", "L"},
                new String[]{"P", "R"}, new String[]{"L", "J"}, new String[]{"R", "J"}, new String[]{"J", "D"}, new String[]{"D", "end"})) {
                edge(link[0], link[1], SkipType.PASS.getKey());
            }
            edge("D", "A", SkipType.REJECT.getKey());
            assertFalse(FlowConfigUtil.inParallelBranch(graph, "D"));
            assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(graph));
            node("R").setNodeType(NodeType.SERVICE.getKey());
            rejected();
            node("R").setNodeType(NodeType.BETWEEN.getKey()).setEventType("MESSAGE_CATCH").setEventName("m");
            rejected();
            node("R").setEventType(null);
            graph.getAllNodes().add(FlowEngine.newNode().setNodeCode("timer").setNodeName("timer").setNodeType(1)
                .setEventType("TIMER_BOUNDARY").setAttachedToNodeCode("L").setInterrupting(true));
            edge("timer", "end", SkipType.PASS.getKey());
            rejected();
        }
    }

    @Test
    void batchCompletedGatewayValidationDoesNotRescanEveryReturnPath() {
        graph.getAllNodes().clear();
        graph.getAllSkips().clear();
        List<String> codes = new ArrayList<>(List.of("start", "A", "F", "L", "R", "J"));
        for (int index = 0; index < 64; index++) codes.add("C" + index);
        codes.add("end");
        for (String code : codes) {
            int type = "start".equals(code) ? 0 : "end".equals(code) ? 2
                : "F".equals(code) ? NodeType.PARALLEL.getKey() : "J".equals(code) ? NodeType.INCLUSIVE.getKey() : 1;
            graph.getAllNodes().add(FlowEngine.newNode().setNodeCode(code).setNodeName(code).setNodeType(type).setNodeRatio("0")
                .setNodeConfig(code.startsWith("C") ? "{\"reject\":{\"afterApproval\":\"RETURN_TO_REJECTOR\"}}" : null)
                .setAnyNodeSkip(code.startsWith("C") ? "A" : null));
        }
        for (String[] link : List.of(new String[]{"start", "A"}, new String[]{"A", "F"}, new String[]{"F", "L"},
            new String[]{"F", "R"}, new String[]{"L", "J"}, new String[]{"R", "J"}, new String[]{"J", "C0"})) {
            edge(link[0], link[1], SkipType.PASS.getKey());
        }
        for (int index = 0; index < 63; index++) edge("C" + index, "C" + (index + 1), SkipType.PASS.getKey());
        edge("C63", "end", SkipType.PASS.getKey());
        List<Skip> original = new ArrayList<>(graph.getAllSkips());
        AtomicInteger inspections = new AtomicInteger();
        graph.setAllSkips(new AbstractList<>() {
            @Override public Skip get(int index) { inspections.incrementAndGet(); return original.get(index); }
            @Override public int size() { return original.size(); }
        });
        assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(graph));
        assertTrue(inspections.get() < 100000, "full-edge inspections: " + inspections.get());
    }

    @Test
    void directReturnSourcesMustBeOrdinaryHumanNodes() {
        for (int type : List.of(NodeType.START.getKey(), NodeType.END.getKey(), NodeType.SERIAL.getKey(),
            NodeType.PARALLEL.getKey(), NodeType.INCLUSIVE.getKey(), NodeType.SERVICE.getKey())) {
            d.setNodeType(type);
            assertThrows(FlowException.class, () -> FlowConfigUtil.validateRejectReturnPath(graph, "D", "A"), String.valueOf(type));
        }
        d.setNodeType(NodeType.BETWEEN.getKey()).setEventType("MESSAGE_CATCH");
        assertThrows(FlowException.class, () -> FlowConfigUtil.validateRejectReturnPath(graph, "D", "A"));
    }

    @Test
    void gatewayCannotBeReturnTarget() {
        // 网关只能作为区间中转，不能作为驳回目标
        b.setNodeType(NodeType.SERIAL.getKey());
        d.setAnyNodeSkip("B");
        rejected();
    }

    @Test
    void exclusiveGatewayWithUniqueBranchAllowed() {
        // 排他网关单令牌选路：直返不执行网关，唯一一条通往驳回节点的分支即可
        b.setNodeType(NodeType.SERIAL.getKey());
        assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(graph));
        // 分支条件不参与直返判定（直返不重新求值网关条件）
        b.getSkipList().get(0).setSkipCondition("gt@@day|3");
        assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(graph));
    }

    @Test
    void exclusiveGatewayWithMultipleBranchesToTargetRejected() {
        b.setNodeType(NodeType.SERIAL.getKey());
        Node x = FlowEngine.newNode().setNodeCode("X").setNodeName("X").setNodeType(1)
            .setNodeRatio("0").setPermissionFlag("user");
        graph.getAllNodes().add(x);
        edge("B", "X", SkipType.PASS.getKey());
        edge("X", "D", SkipType.PASS.getKey());
        rejected();
    }

    @Test
    void cooperativeNodesAllowedInReturnInterval() {
        for (String ratio : List.of("100", "50", "passCount:2", "0sequence")) {
            b.setNodeRatio(ratio);
            assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(graph), ratio);
        }
        b.setNodeRatio("0").setCooperateType("sequence");
        assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(graph));
        b.setCooperateType("group");
        assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(graph));
    }

    @Test
    void ambiguousOrNonPreviousTargetRejected() {
        d.setAnyNodeSkip("end");
        rejected();
        d.setAnyNodeSkip("D");
        rejected();
        // 无静态目标（无 anyNodeSkip、无退回线）允许保存：目标可由办理人驳回时指定，
        // 路径合法性由运行时 rejectReturnPreflight 兜底校验。
        d.setAnyNodeSkip(null);
        Skip reject = graph.getAllSkips().stream().filter(s -> SkipType.isReject(s.getSkipType())).findFirst().orElseThrow();
        graph.getAllSkips().remove(reject);
        d.getSkipList().remove(reject);
        assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(graph));
        edge("D", "A", SkipType.REJECT.getKey());
        b.getSkipList().get(0).setNextNodeCode("A");
        rejected();
    }

    @Test
    void legacyReplayDoesNotAcquireNewRestrictions() {
        d.setNodeConfig("{\"reject\":{\"business\":true}}");
        b.setNodeRatio("100");
        assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(graph));
    }

    private void rejected() {
        FlowException error = assertThrows(FlowException.class, () -> FlowConfigUtil.validateGraph(graph));
        assertTrue(error.getMessage().contains("D"), error.getMessage());
    }

    private void multiEntryCycleGraph(int fork, int join) {
        graph.getAllNodes().clear();
        graph.getAllSkips().clear();
        for (String code : List.of("start", "T", "F", "L", "R", "J", "A", "B", "D", "S", "H", "K", "end")) {
            int type = "start".equals(code) ? NodeType.START.getKey() : "end".equals(code) ? NodeType.END.getKey()
                : List.of("T", "S").contains(code) ? NodeType.SERIAL.getKey() : "F".equals(code) ? fork
                : "J".equals(code) ? join : NodeType.BETWEEN.getKey();
            graph.getAllNodes().add(FlowEngine.newNode().setNodeCode(code).setNodeName(code).setNodeType(type)
                .setNodeRatio("0"));
        }
        node("D").setNodeConfig("{\"reject\":{\"afterApproval\":\"RETURN_TO_REJECTOR\"}}").setAnyNodeSkip("A");
        for (String[] link : List.of(new String[]{"start", "T"}, new String[]{"T", "F"}, new String[]{"T", "end"},
            new String[]{"F", "L"}, new String[]{"F", "R"}, new String[]{"L", "J"}, new String[]{"R", "J"},
            new String[]{"J", "A"}, new String[]{"A", "B"}, new String[]{"B", "D"}, new String[]{"D", "S"},
            new String[]{"S", "H"}, new String[]{"S", "K"}, new String[]{"H", "K"}, new String[]{"K", "H"})) {
            edge(link[0], link[1], SkipType.PASS.getKey());
        }
        edge("D", "A", SkipType.REJECT.getKey());
    }

    private void nestedGatewayGraph(int outer, int inner) {
        graph.getAllNodes().clear();
        graph.getAllSkips().clear();
        for (String code : List.of("start", "A", "B", "D", "left", "right", "sibling", "after", "end")) {
            int type = "start".equals(code) ? NodeType.START.getKey()
                : "end".equals(code) ? NodeType.END.getKey() : NodeType.BETWEEN.getKey();
            graph.getAllNodes().add(FlowEngine.newNode().setNodeCode(code).setNodeName(code)
                .setNodeType(type).setNodeRatio("0"));
        }
        for (String code : List.of("outerFork", "innerFork", "innerJoin", "outerJoin")) {
            graph.getAllNodes().add(FlowEngine.newNode().setNodeCode(code).setNodeName(code)
                .setNodeType(code.startsWith("inner") ? inner : outer));
        }
        node("D").setNodeConfig("{\"reject\":{\"afterApproval\":\"RETURN_TO_REJECTOR\"}}");
        for (String[] link : List.of(new String[]{"start", "outerFork"},
            new String[]{"outerFork", "innerFork"}, new String[]{"outerFork", "sibling"},
            new String[]{"innerFork", "left"}, new String[]{"innerFork", "right"},
            new String[]{"left", "innerJoin"}, new String[]{"right", "innerJoin"},
            new String[]{"innerJoin", "A"}, new String[]{"A", "B"}, new String[]{"B", "D"},
            new String[]{"D", "outerJoin"}, new String[]{"sibling", "outerJoin"},
            new String[]{"outerJoin", "after"}, new String[]{"after", "end"})) {
            edge(link[0], link[1], SkipType.PASS.getKey());
        }
        edge("D", "A", SkipType.REJECT.getKey());
    }

    private Node node(String code) {
        return graph.getAllNodes().stream().filter(n -> code.equals(n.getNodeCode())).findFirst().orElseThrow();
    }

    private void edge(String from, String to, String type) {
        Skip skip = FlowEngine.newSkip().setNowNodeCode(from).setNextNodeCode(to).setSkipType(type)
            .setNowNodeType(node(from).getNodeType()).setNextNodeType(node(to).getNodeType());
        node(from).getSkipList().add(skip);
        graph.getAllSkips().add(skip);
    }
}
