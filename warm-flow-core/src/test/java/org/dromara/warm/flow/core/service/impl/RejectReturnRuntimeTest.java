package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.service.task.RejectConfig;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 一次性任务关联只修改实例图，并保留未知 JSON 内容。 @author may */
class RejectReturnRuntimeTest {
    private FlowTestHarness harness;
    private static final long TASK = 9007199254740993L;
    private static final String CONFIG = "{\"variables\":{\"items\":[1,null,\"x\"]},"
        + "\"copy\":{\"enabled\":true},\"business\":null,\"custom\":{\"k\":\"v\"}}";

    @BeforeEach
    void setUp() { harness = new FlowTestHarness(); }

    @AfterEach
    void tearDown() { harness.close(); }

    @Test
    void putPreservesConfigAndExactTaskIdentity() {
        String base = snapshot(CONFIG);
        String updated = RejectReturnRuntime.put(base, "A", TASK, 100L, "D");
        var intent = RejectReturnRuntime.find(updated, "A", TASK).orElseThrow();
        assertEquals(TASK, intent.taskId());
        assertEquals(100L, intent.sourceTaskId());
        assertEquals("D", intent.returnNodeCode());
        assertTrue(RejectReturnRuntime.find(updated, "A", TASK - 1).isEmpty());
        String nodeConfig = RejectReturnRuntime.configForNode(updated, "A");
        assertTrue(nodeConfig.contains("\"9007199254740993\""));
        assertTrue(nodeConfig.contains("\"sourceTaskId\":\"100\""));
        Map<String, Object> node = config(updated, "A");
        node.remove(RejectConfig.RUNTIME_KEY);
        assertEquals(FlowEngine.jsonConvert.strToMap(CONFIG), node);
        assertEquals(config(base, "D"), config(updated, "D"));
        assertThrows(FlowException.class, () -> RejectReturnRuntime.put(updated, "D", 20L, 21L, "A"));
    }

    @Test
    void removePreservesOtherSections() {
        String updated = RejectReturnRuntime.put(snapshot(CONFIG), "A", TASK, 100L, "D");
        assertTrue(RejectReturnRuntime.hasPending(RejectReturnRuntime.remove(updated, "A", TASK - 1)));
        String consumed = RejectReturnRuntime.remove(updated, "A", TASK);
        assertFalse(RejectReturnRuntime.hasPending(consumed));
        assertEquals(FlowEngine.jsonConvert.strToMap(CONFIG), config(consumed, "A"));
    }

    @Test
    void clearIsIdempotentAndKeepsRules() {
        String updated = RejectReturnRuntime.put(snapshot(CONFIG), "A", TASK, 100L, "D");
        String cleared = RejectReturnRuntime.clear(updated);
        assertFalse(RejectReturnRuntime.hasPending(cleared));
        assertEquals(cleared, RejectReturnRuntime.clear(cleared));
        assertTrue(RejectConfig.parse(RejectReturnRuntime.configForNode(cleared, "D")).isReturnToRejector());
        assertEquals(FlowEngine.jsonConvert.strToMap(CONFIG), config(cleared, "A"));
    }

    @Test
    void malformedRuntimeFails() {
        for (String section : List.of("{\"version\":2,\"byTaskId\":{}}",
            "{\"version\":1,\"byTaskId\":null}", "{\"version\":1,\"byTaskId\":[]}",
            "{\"version\":1,\"byTaskId\":{\"1\":null}}",
            "{\"version\":1,\"byTaskId\":{\"1\":{\"sourceTaskId\":100,\"returnNodeCode\":\"D\"}}}",
            "{\"version\":1,\"byTaskId\":{\"01\":{\"sourceTaskId\":\"100\",\"returnNodeCode\":\"D\"}}}",
            "{\"version\":1,\"byTaskId\":{\"1\":{\"sourceTaskId\":\"100\",\"returnNodeCode\":null}}}")) {
            String json = snapshot("{\"__rejectReturnRuntime\":" + section + "}");
            assertThrows(FlowException.class, () -> RejectReturnRuntime.hasPending(json), section);
        }
        assertThrows(FlowException.class, () -> RejectReturnRuntime.put(snapshot(CONFIG), "missing", TASK, 100L, "D"));
        assertThrows(FlowException.class, () -> RejectReturnRuntime.put(snapshot(CONFIG), "A", null, 100L, "D"));
    }

    @Test
    void legacyReservedCollisionNotOverwritten() {
        String base = snapshot("{\"__rejectReturnRuntime\":{\"custom\":\"legacy\"}}");
        assertThrows(FlowException.class, () -> RejectReturnRuntime.put(base, "A", TASK, 100L, "D"));
        assertEquals(base, RejectReturnRuntime.clear(base));
    }

    @Test
    void unknownSnapshotFieldsSurvive() {
        Map<String, Object> base = FlowEngine.jsonConvert.strToMap(snapshot(CONFIG));
        base.put("futureGraphField", Map.of("v", List.of(1, 2)));
        String updated = RejectReturnRuntime.put(FlowEngine.jsonConvert.objToStr(base), "A", TASK, 100L, "D");
        assertEquals(base.get("futureGraphField"), FlowEngine.jsonConvert.strToMap(updated).get("futureGraphField"));
    }

    private static String snapshot(String config) {
        return FlowEngine.jsonConvert.objToStr(new DefJson().setNodeList(new ArrayList<>(List.of(
            new NodeJson().setNodeCode("A").setNodeConfig(config),
            new NodeJson().setNodeCode("D").setNodeConfig("{\"reject\":{\"schemaVersion\":1,"
                + "\"afterApproval\":\"RETURN_TO_REJECTOR\"}}")))));
    }

    private static Map<String, Object> config(String snapshot, String code) {
        return FlowEngine.jsonConvert.strToMap(RejectReturnRuntime.configForNode(snapshot, code));
    }
}
