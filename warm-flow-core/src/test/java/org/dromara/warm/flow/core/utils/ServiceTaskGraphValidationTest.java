package org.dromara.warm.flow.core.utils;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.FlowCombine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.ActivityStatus;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceTaskGraphValidationTest {

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
    void serviceNodeMustHaveValidConfig() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey(), null);
        node(def, "s1", NodeType.SERVICE.getKey(), "{}");
        node(def, "end", NodeType.END.getKey(), null);
        skip(def, "start", "s1", NodeType.START.getKey(), NodeType.SERVICE.getKey(), SkipType.PASS.getKey());
        skip(def, "s1", "end", NodeType.SERVICE.getKey(), NodeType.END.getKey(), SkipType.PASS.getKey());
        FlowException ex = assertThrows(FlowException.class, () -> FlowConfigUtil.validateGraph(combine(def)));
        assertTrue(ex.getMessage().contains(ExceptionCons.NULL_SERVICE_TASK_CONFIG));
    }

    @Test
    void serviceNodeOutgoingMustBePass() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey(), null);
        node(def, "s1", NodeType.SERVICE.getKey(),
            "{\"serviceTask\":{\"type\":\"class\",\"handler\":\"a.B\"}}");
        node(def, "end", NodeType.END.getKey(), null);
        skip(def, "start", "s1", NodeType.START.getKey(), NodeType.SERVICE.getKey(), SkipType.PASS.getKey());
        skip(def, "s1", "end", NodeType.SERVICE.getKey(), NodeType.END.getKey(), SkipType.REJECT.getKey());
        FlowException ex = assertThrows(FlowException.class, () -> FlowConfigUtil.validateGraph(combine(def)));
        assertTrue(ex.getMessage().contains(ExceptionCons.SERVICE_TASK_ONLY_PASS));
    }

    @Test
    void validServiceFlowPasses() {
        Definition def = def();
        node(def, "start", NodeType.START.getKey(), null);
        node(def, "s1", NodeType.SERVICE.getKey(),
            "{\"serviceTask\":{\"type\":\"class\",\"handler\":\"a.B\"}}");
        node(def, "end", NodeType.END.getKey(), null);
        skip(def, "start", "s1", NodeType.START.getKey(), NodeType.SERVICE.getKey(), SkipType.PASS.getKey());
        skip(def, "s1", "end", NodeType.SERVICE.getKey(), NodeType.END.getKey(), SkipType.PASS.getKey());
        FlowConfigUtil.validateGraph(combine(def));
    }

    private Definition def() {
        return FlowEngine.newDef()
            .setFlowCode("validateFlow")
            .setFlowName("校验流程")
            .setVersion("1")
            .setIsPublish(PublishStatus.PUBLISHED.getKey())
            .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
    }

    private void node(Definition def, String code, Integer nodeType, String nodeConfig) {
        Node node = FlowEngine.newNode()
            .setDefinitionId(def.getId())
            .setNodeCode(code)
            .setNodeName(code)
            .setNodeType(nodeType)
            .setNodeRatio("0");
        if (nodeConfig != null) {
            node.setNodeConfig(nodeConfig);
        }
        def.getNodeList().add(node);
    }

    private void skip(Definition def, String now, String next, Integer nowType, Integer nextType, String skipType) {
        Skip skip = FlowEngine.newSkip()
            .setDefinitionId(def.getId())
            .setNowNodeCode(now)
            .setNowNodeType(nowType)
            .setNextNodeCode(next)
            .setNextNodeType(nextType)
            .setSkipName(now + "_" + next)
            .setSkipType(skipType);
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
