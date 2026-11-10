package org.dromara.warm.flow.core.dto;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeConfigJsonRoundTripTest {

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
    void copyKeepsNodeConfigAndCooperateType() {
        Node node = FlowEngine.newNode().setNodeCode("s1").setNodeName("s1")
            .setNodeType(NodeType.SERVICE.getKey())
            .setNodeConfig("{\"serviceTask\":{\"type\":\"class\",\"handler\":\"a.B\"}}")
            .setCooperateType("sequence");
        assertEquals(node.getNodeConfig(), node.copy().getNodeConfig());
        assertEquals("sequence", node.copy().getCooperateType());
    }

    @Test
    void defJsonRoundTripKeepsNodeConfig() {
        String config = "{\"serviceTask\":{\"type\":\"expression\",\"expression\":\"#{@x.y()}\"}}";
        Definition def = FlowEngine.newDef().setFlowCode("f").setFlowName("f")
            .setVersion("1").setIsPublish(1);
        Node node = FlowEngine.newNode().setDefinitionId(1L).setNodeCode("s1").setNodeName("s1")
            .setNodeType(NodeType.SERVICE.getKey()).setNodeConfig(config).setCooperateType("sequence");
        def.setNodeList(java.util.List.of(node));

        DefJson json = DefJson.copyDef(def);
        Node back = DefJson.copyDef(json).getNodeList().get(0);
        assertEquals(config, back.getNodeConfig());
        assertEquals("sequence", json.getNodeList().get(0).getCooperateType());
        assertEquals("sequence", back.getCooperateType());
    }

    @Test
    void roundTripPreservesGroupSignAndLegacyCustomKeys() {
        String config = "{\"groups\":{\"legacy\":true},\"groupOrder\":\"legacy\","
            + "\"groupSign\":{\"groups\":[{\"id\":\"legal\",\"members\":[\"user:alice\"]}],"
            + "\"order\":\"parallel\"}}";
        Definition def = FlowEngine.newDef().setFlowCode("group").setFlowName("group")
            .setVersion("1").setIsPublish(0);
        Node node = FlowEngine.newNode().setDefinitionId(1L).setNodeCode("approve").setNodeName("approve")
            .setNodeType(NodeType.BETWEEN.getKey()).setNodeConfig(config).setCooperateType("group");
        def.setNodeList(java.util.List.of(node));

        assertEquals(config, node.copy().getNodeConfig());
        DefJson json = DefJson.copyDef(def);
        Node back = DefJson.copyDef(json).getNodeList().get(0);
        assertEquals(config, back.getNodeConfig());
        assertEquals("group", back.getCooperateType());
    }
}
