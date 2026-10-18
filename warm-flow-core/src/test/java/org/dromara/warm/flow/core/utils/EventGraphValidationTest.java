package org.dromara.warm.flow.core.utils;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.FlowCombine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventGraphValidationTest {

    private static final String TIMER_CONFIG = "{\"event\":{\"timerType\":\"AFTER\",\"timerValue\":\"P3D\"}}";

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void validStartEventFlowPasses() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        eventNode(def, "ts", EventType.TIMER_START.getValue()).setNodeConfig(TIMER_CONFIG);
        node(def, "n1", NodeType.BETWEEN.getKey());
        node(def, "end", NodeType.END.getKey());
        skip(def, "start", "ts", NodeType.START.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "ts", "n1", NodeType.BETWEEN.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "n1", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(combine(def)));
    }

    @Test
    void validMessageCatchFlowPasses() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        eventNode(def, "mc", EventType.MESSAGE_CATCH.getValue()).setEventName("order.paid");
        node(def, "end", NodeType.END.getKey());
        skip(def, "start", "mc", NodeType.START.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "mc", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        assertDoesNotThrow(() -> FlowConfigUtil.validateGraph(combine(def)));
    }

    @Test
    void fixedStartCannotCarryEvent() {
        Definition def = def();
        eventNode(def, "start", EventType.TIMER_START.getValue()).setNodeType(NodeType.START.getKey())
            .setNodeConfig(TIMER_CONFIG);
        node(def, "end", NodeType.END.getKey());
        skip(def, "start", "end", NodeType.START.getKey(), NodeType.END.getKey());
        assertMessage(ExceptionCons.FIXED_NODE_NO_EVENT, def);
    }

    @Test
    void fixedEndCannotCarryEvent() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        eventNode(def, "end", EventType.TERMINATE_END.getValue()).setNodeType(NodeType.END.getKey());
        skip(def, "start", "end", NodeType.START.getKey(), NodeType.END.getKey());
        assertMessage(ExceptionCons.FIXED_NODE_NO_EVENT, def);
    }

    @Test
    void fixedStartCannotHaveIncoming() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        node(def, "n1", NodeType.BETWEEN.getKey());
        node(def, "end", NodeType.END.getKey());
        skip(def, "n1", "start", NodeType.BETWEEN.getKey(), NodeType.START.getKey());
        skip(def, "start", "n1", NodeType.START.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "n1", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        assertMessage(ExceptionCons.FIXED_NODE_LINE_ERROR, def);
    }

    @Test
    void fixedEndCannotHaveOutgoing() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        node(def, "n1", NodeType.BETWEEN.getKey());
        node(def, "end", NodeType.END.getKey());
        skip(def, "start", "n1", NodeType.START.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "n1", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        // 固定结束节点出现出线
        skip(def, "end", "n1", NodeType.END.getKey(), NodeType.BETWEEN.getKey());
        assertMessage(ExceptionCons.FIXED_NODE_LINE_ERROR, def);
    }

    @Test
    void startEventMustBeAdjacentToStart() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        node(def, "n1", NodeType.BETWEEN.getKey());
        eventNode(def, "ts", EventType.TIMER_START.getValue()).setNodeConfig(TIMER_CONFIG);
        node(def, "end", NodeType.END.getKey());
        skip(def, "start", "n1", NodeType.START.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "n1", "ts", NodeType.BETWEEN.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "ts", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        assertMessage(ExceptionCons.START_EVENT_TOPOLOGY, def);
    }

    @Test
    void endEventMustPointToFixedEnd() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        node(def, "n1", NodeType.BETWEEN.getKey());
        eventNode(def, "te", EventType.TERMINATE_END.getValue());
        node(def, "n2", NodeType.BETWEEN.getKey());
        node(def, "end", NodeType.END.getKey());
        skip(def, "start", "n1", NodeType.START.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "n1", "te", NodeType.BETWEEN.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "te", "n2", NodeType.BETWEEN.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "n2", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        assertMessage(ExceptionCons.END_EVENT_TOPOLOGY, def);
    }

    @Test
    void messageEventNeedsName() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        eventNode(def, "mc", EventType.MESSAGE_CATCH.getValue());
        node(def, "end", NodeType.END.getKey());
        skip(def, "start", "mc", NodeType.START.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "mc", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        assertMessage(ExceptionCons.MESSAGE_EVENT_NO_NAME, def);
    }

    @Test
    void timerEventNeedsTimerConfig() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        eventNode(def, "tc", EventType.TIMER_CATCH.getValue()).setNodeConfig("{}");
        node(def, "end", NodeType.END.getKey());
        skip(def, "start", "tc", NodeType.START.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "tc", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        assertMessage(ExceptionCons.TIMER_EVENT_NO_CONFIG, def);
    }

    @Test
    void boundaryEventNeedsAttachedNode() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        node(def, "end", NodeType.END.getKey());
        eventNode(def, "tb", EventType.TIMER_BOUNDARY.getValue()).setNodeConfig(TIMER_CONFIG)
            .setInterrupting(true);
        skip(def, "start", "end", NodeType.START.getKey(), NodeType.END.getKey());
        skip(def, "tb", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        assertMessage(ExceptionCons.BOUNDARY_EVENT_NO_ATTACHED, def);
    }

    @Test
    void boundaryEventMustBeInterrupting() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        node(def, "n1", NodeType.BETWEEN.getKey());
        node(def, "end", NodeType.END.getKey());
        eventNode(def, "tb", EventType.TIMER_BOUNDARY.getValue()).setNodeConfig(TIMER_CONFIG)
            .setAttachedToNodeCode("n1").setInterrupting(false);
        skip(def, "start", "n1", NodeType.START.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "n1", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        skip(def, "tb", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        assertMessage(ExceptionCons.BOUNDARY_EVENT_NOT_INTERRUPTING, def);
    }

    @Test
    void boundaryEventAttachedNodeMustExist() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        node(def, "end", NodeType.END.getKey());
        eventNode(def, "tb", EventType.TIMER_BOUNDARY.getValue()).setNodeConfig(TIMER_CONFIG)
            .setAttachedToNodeCode("ghost").setInterrupting(true);
        skip(def, "start", "end", NodeType.START.getKey(), NodeType.END.getKey());
        skip(def, "tb", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        assertMessage(ExceptionCons.BOUNDARY_EVENT_ATTACHED_NOT_EXIST, def);
    }

    @Test
    void boundaryEventAttachedNodeMustBePlainTaskNode() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        node(def, "end", NodeType.END.getKey());
        eventNode(def, "tb", EventType.TIMER_BOUNDARY.getValue()).setNodeConfig(TIMER_CONFIG)
            .setAttachedToNodeCode("mc").setInterrupting(true);
        // 附着目标本身是消息捕获事件节点：链在主线上，拓扑自身合法，用于验证附着校验拒绝事件节点
        eventNode(def, "mc", EventType.MESSAGE_CATCH.getValue()).setEventName("order.paid");
        skip(def, "start", "mc", NodeType.START.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "mc", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        skip(def, "tb", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        assertMessage(ExceptionCons.BOUNDARY_EVENT_ATTACHED_INVALID, def);
    }

    @Test
    void throwEventNeedsIncoming() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        node(def, "end", NodeType.END.getKey());
        eventNode(def, "mt", EventType.MESSAGE_THROW.getValue()).setEventName("order.sent");
        skip(def, "start", "end", NodeType.START.getKey(), NodeType.END.getKey());
        skip(def, "mt", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        assertMessage(ExceptionCons.THROW_EVENT_NO_INCOMING, def);
    }

    @Test
    void illegalEventTypeRejected() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        eventNode(def, "x", "NOT_A_REAL_EVENT");
        node(def, "end", NodeType.END.getKey());
        skip(def, "start", "x", NodeType.START.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "x", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        assertMessage(ExceptionCons.ILLEGAL_EVENT_TYPE, def);
    }

    @Test
    void publishGateAllowsTimerEventsSinceStageC() {
        // 阶段 C 起定时类事件运行时已开放，带合法定时配置的定义可通过能力门禁
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        eventNode(def, "ts", EventType.TIMER_START.getValue()).setNodeConfig(TIMER_CONFIG);
        node(def, "end", NodeType.END.getKey());
        skip(def, "start", "ts", NodeType.START.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "ts", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        FlowCombine combine = combine(def);
        assertDoesNotThrow(() -> FlowConfigUtil.validateEventCapability(combine));
    }

    @Test
    void publishGateAllowsPlainFlow() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey());
        node(def, "n1", NodeType.BETWEEN.getKey());
        node(def, "end", NodeType.END.getKey());
        skip(def, "start", "n1", NodeType.START.getKey(), NodeType.BETWEEN.getKey());
        skip(def, "n1", "end", NodeType.BETWEEN.getKey(), NodeType.END.getKey());
        FlowCombine combine = combine(def);
        assertDoesNotThrow(() -> FlowConfigUtil.validateEventCapability(combine));
    }

    private void assertMessage(String expected, Definition def) {
        FlowCombine combine = combine(def);
        FlowException ex = assertThrows(FlowException.class, () -> {
            FlowConfigUtil.validateGraph(combine);
            FlowConfigUtil.validateEventCapability(combine);
        });
        assertTrue(ex.getMessage().contains(expected), "期望包含【" + expected + "】，实际为：" + ex.getMessage());
    }

    private Definition def() {
        return FlowEngine.newDef()
            .setFlowCode("eventValidateFlow")
            .setFlowName("事件校验流程")
            .setVersion("1")
            .setIsPublish(PublishStatus.UNPUBLISHED.getKey())
            .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
    }

    private Node node(Definition def, String code, Integer nodeType) {
        Node node = FlowEngine.newNode()
            .setDefinitionId(def.getId())
            .setNodeCode(code)
            .setNodeName(code)
            .setNodeType(nodeType)
            .setNodeRatio("0");
        def.getNodeList().add(node);
        return node;
    }

    private Node eventNode(Definition def, String code, String eventType) {
        return node(def, code, NodeType.BETWEEN.getKey()).setEventType(eventType);
    }

    private void skip(Definition def, String now, String next, Integer nowType, Integer nextType) {
        Skip skip = FlowEngine.newSkip()
            .setDefinitionId(def.getId())
            .setNowNodeCode(now)
            .setNowNodeType(nowType)
            .setNextNodeCode(next)
            .setNextNodeType(nextType)
            .setSkipName(now + "_" + next)
            .setSkipType(SkipType.PASS.getKey());
        for (Node node : def.getNodeList()) {
            if (now.equals(node.getNodeCode())) {
                node.getSkipList().add(skip);
            }
        }
    }

    private FlowCombine combine(Definition def) {
        FlowCombine combine = new FlowCombine();
        combine.setDefinition(def);
        combine.setAllNodes(def.getNodeList());
        List<Skip> allSkips = new ArrayList<>();
        for (Node node : def.getNodeList()) {
            allSkips.addAll(node.getSkipList());
        }
        combine.setAllSkips(allSkips);
        return combine;
    }
}
