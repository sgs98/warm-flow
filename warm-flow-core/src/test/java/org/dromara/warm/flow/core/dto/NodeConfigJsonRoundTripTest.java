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
    void copyKeepsNodeConfig() {
        Node node = FlowEngine.newNode().setNodeCode("s1").setNodeName("s1")
            .setNodeType(NodeType.SERVICE.getKey())
            .setNodeConfig("{\"serviceTask\":{\"type\":\"class\",\"handler\":\"a.B\"}}");
        assertEquals(node.getNodeConfig(), node.copy().getNodeConfig());
    }

    @Test
    void defJsonRoundTripKeepsNodeConfig() {
        String config = "{\"serviceTask\":{\"type\":\"expression\",\"expression\":\"#{@x.y()}\"}}";
        Definition def = FlowEngine.newDef().setFlowCode("f").setFlowName("f")
            .setVersion("1").setIsPublish(1);
        Node node = FlowEngine.newNode().setDefinitionId(1L).setNodeCode("s1").setNodeName("s1")
            .setNodeType(NodeType.SERVICE.getKey()).setNodeConfig(config);
        def.setNodeList(java.util.List.of(node));

        DefJson json = DefJson.copyDef(def);
        Node back = DefJson.copyDef(json).getNodeList().get(0);
        assertEquals(config, back.getNodeConfig());
    }
}
