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

import java.util.ArrayList;
import java.util.List;

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
        d.setNodeConfig("{\"reject\":{\"schemaVersion\":1,\"afterApproval\":\"RETURN_TO_REJECTOR\"}}");
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
    void gatewayServiceEventAndBoundaryInReturnIntervalRejected() {
        for (int type : List.of(3, 4, 5, 6)) {
            b.setNodeType(type);
            rejected();
        }
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
    void cooperativeNodesRejected() {
        for (String ratio : List.of("100", "50", "passCount:2", "0sequence")) {
            b.setNodeRatio(ratio);
            rejected();
        }
        b.setNodeRatio("0").setCooperateType("sequence");
        rejected();
        b.setCooperateType("group");
        rejected();
        b.setCooperateType(null).setNodeConfig("{\"groupSign\":{\"enabled\":true}}");
        rejected();
    }

    @Test
    void ambiguousOrNonPreviousTargetRejected() {
        d.setAnyNodeSkip("end");
        rejected();
        d.setAnyNodeSkip("D");
        rejected();
        d.setAnyNodeSkip(null);
        Skip reject = graph.getAllSkips().stream().filter(s -> SkipType.isReject(s.getSkipType())).findFirst().orElseThrow();
        graph.getAllSkips().remove(reject);
        d.getSkipList().remove(reject);
        rejected();
        edge("D", "A", SkipType.REJECT.getKey());
        b.getSkipList().get(0).setNextNodeCode("A");
        rejected();
    }

    @Test
    void runtimeSegmentInEnabledDesignRejected() {
        node("A").setNodeConfig("{\"__rejectReturnRuntime\":{\"version\":1,\"byTaskId\":{}}}");
        rejected();
        node("A").setNodeConfig("{\"__rejectReturnRuntime\":\"legacy\"}");
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
