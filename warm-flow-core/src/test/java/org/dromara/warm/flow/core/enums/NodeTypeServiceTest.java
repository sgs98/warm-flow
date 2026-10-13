package org.dromara.warm.flow.core.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeTypeServiceTest {

    @Test
    void existingCodesUnchanged() {
        assertEquals(0, NodeType.START.getKey());
        assertEquals(1, NodeType.BETWEEN.getKey());
        assertEquals(2, NodeType.END.getKey());
        assertEquals(3, NodeType.SERIAL.getKey());
        assertEquals(4, NodeType.PARALLEL.getKey());
        assertEquals(5, NodeType.INCLUSIVE.getKey());
        assertEquals(6, NodeType.SERVICE.getKey());
        assertEquals("service", NodeType.SERVICE.getValue());
    }

    @Test
    void isServiceOnlyMatchesService() {
        assertTrue(NodeType.isService(NodeType.SERVICE.getKey()));
        assertFalse(NodeType.isService(NodeType.BETWEEN.getKey()));
        assertFalse(NodeType.isService(null));
    }

    @Test
    void isBetweenExcludesService() {
        assertFalse(NodeType.isBetween(NodeType.SERVICE.getKey()));
    }

    @Test
    void isWorkNodeCoversBetweenAndService() {
        assertTrue(NodeType.isWorkNode(NodeType.BETWEEN.getKey()));
        assertTrue(NodeType.isWorkNode(NodeType.SERVICE.getKey()));
        assertFalse(NodeType.isWorkNode(NodeType.START.getKey()));
        assertFalse(NodeType.isWorkNode(NodeType.END.getKey()));
        assertFalse(NodeType.isWorkNode(NodeType.SERIAL.getKey()));
        assertFalse(NodeType.isWorkNode(null));
    }
}
