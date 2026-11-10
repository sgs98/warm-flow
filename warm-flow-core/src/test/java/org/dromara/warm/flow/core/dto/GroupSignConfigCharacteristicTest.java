package org.dromara.warm.flow.core.dto;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.utils.GroupSignUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组签配置契约特征测试。
 *
 * @author may
 */
class GroupSignConfigCharacteristicTest {

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
    void parsesNamespacedGroupsAndDefaultsOrderToParallel() {
        GroupSignConfig config = GroupSignUtil.parse("{\"groupSign\":{\"groups\":["
            + "{\"id\":\"legal\",\"name\":\"法务组\",\"members\":[\"user:alice\"]},"
            + "{\"id\":\"finance\",\"members\":[\"role:cfo\"]}]}}");

        assertEquals(CooperateType.PARALLEL, config.getOrder());
        assertEquals("legal", config.getGroups().get(0).getId());
        assertEquals("法务组", config.getGroups().get(0).getName());
        assertEquals("finance", config.getGroups().get(1).getId(), "数组顺序即组顺序");
    }

    @Test
    void ignoresLegacyTopLevelGroupKeys() {
        assertNull(GroupSignUtil.parse("{\"groups\":\"legacy\",\"groupOrder\":\"legacy\"}"));
    }

    @Test
    void requireRejectsMalformedObjectArrayAndMemberTypes() {
        Node node = FlowEngine.newNode().setNodeCode("approve").setNodeType(NodeType.BETWEEN.getKey());

        node.setNodeConfig("{\"groupSign\":[]}");
        assertThrows(FlowException.class, () -> GroupSignUtil.require(node));
        node.setNodeConfig("{\"groupSign\":{\"groups\":{}}}");
        assertThrows(FlowException.class, () -> GroupSignUtil.require(node));
        node.setNodeConfig("{\"groupSign\":{\"groups\":[{\"id\":\"legal\",\"members\":[1]}]}}");
        assertThrows(FlowException.class, () -> GroupSignUtil.require(node));
    }

    @Test
    void groupPredicatesUseTheAdditiveStringContract() {
        assertEquals("group", CooperateType.GROUP);
        assertTrue(CooperateType.isGroup("group"));
        assertTrue(CooperateType.isGroup("GROUP"));
        assertFalse(CooperateType.isGroup("parallel"));

        Node node = FlowEngine.newNode().setNodeCode("approve").setNodeType(NodeType.BETWEEN.getKey())
            .setNodeConfig("{\"groupSign\":{\"groups\":[{\"id\":\"legal\",\"members\":[\"alice\"]}],"
                + "\"order\":\"sequence\"}}");
        assertTrue(GroupSignUtil.isSequence(node));
    }
}
