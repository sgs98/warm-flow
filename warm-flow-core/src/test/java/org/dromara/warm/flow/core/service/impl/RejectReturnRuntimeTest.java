package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.service.task.RejectConfig;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 一次性运行关联存放在实例流程变量的保留键中，并与业务变量隔离。 @author may */
class RejectReturnRuntimeTest {
    private FlowTestHarness harness;
    private static final long TASK = 9007199254740993L;
    private static final String BUSINESS = "{\"business\":{\"k\":\"v\"},\"amount\":100}";

    @BeforeEach
    void setUp() { harness = new FlowTestHarness(); }

    @AfterEach
    void tearDown() { harness.close(); }

    @Test
    void putPreservesBusinessVariablesAndExactTaskIdentity() {
        Instance instance = instance(BUSINESS);
        RejectReturnRuntime.put(instance, "A", TASK, 100L, "D");
        var intent = RejectReturnRuntime.find(instance, "A", TASK).orElseThrow();
        assertEquals(TASK, intent.taskId());
        assertEquals(100L, intent.sourceTaskId());
        assertEquals("D", intent.returnNodeCode());
        assertTrue(RejectReturnRuntime.find(instance, "A", TASK - 1).isEmpty());
        assertTrue(RejectReturnRuntime.find(instance, "B", TASK).isEmpty());
        Map<String, Object> variables = instance.getVariableMap();
        assertTrue(variables.containsKey(RejectConfig.FLOW_VARIABLE_KEY));
        assertEquals(FlowEngine.jsonConvert.strToMap(BUSINESS).get("business"), variables.get("business"));
        assertEquals(100, ((Number) variables.get("amount")).intValue());
        assertEquals(TASK, Long.parseLong((String) ((Map<?, ?>) variables.get(RejectConfig.FLOW_VARIABLE_KEY)).get("taskId")));
        assertThrows(FlowException.class, () -> RejectReturnRuntime.put(instance, "D", 20L, 21L, "A"));
    }

    @Test
    void removeOnlyMatchesNodeAndTask() {
        Instance instance = instance(BUSINESS);
        RejectReturnRuntime.put(instance, "A", TASK, 100L, "D");
        RejectReturnRuntime.remove(instance, "A", TASK - 1);
        assertTrue(RejectReturnRuntime.hasPending(instance));
        RejectReturnRuntime.remove(instance, "B", TASK);
        assertTrue(RejectReturnRuntime.hasPending(instance));
        RejectReturnRuntime.remove(instance, "A", TASK);
        assertFalse(RejectReturnRuntime.hasPending(instance));
        assertEquals(FlowEngine.jsonConvert.strToMap(BUSINESS), instance.getVariableMap());
    }

    @Test
    void clearIsIdempotentAndKeepsBusinessVariables() {
        Instance instance = instance(BUSINESS);
        RejectReturnRuntime.put(instance, "A", TASK, 100L, "D");
        RejectReturnRuntime.clear(instance);
        assertFalse(RejectReturnRuntime.hasPending(instance));
        RejectReturnRuntime.clear(instance);
        assertFalse(RejectReturnRuntime.hasPending(instance));
        assertEquals(FlowEngine.jsonConvert.strToMap(BUSINESS), instance.getVariableMap());
    }

    @Test
    void malformedRuntimeFails() {
        for (String section : List.of("[]", "\"x\"",
            "{\"taskId\":1,\"sourceTaskId\":\"2\",\"nodeCode\":\"A\",\"returnNodeCode\":\"D\"}",
            "{\"taskId\":\"01\",\"sourceTaskId\":\"2\",\"nodeCode\":\"A\",\"returnNodeCode\":\"D\"}",
            "{\"taskId\":\"1\",\"sourceTaskId\":\"2\",\"nodeCode\":\"\",\"returnNodeCode\":\"D\"}",
            "{\"taskId\":\"1\",\"sourceTaskId\":\"2\",\"nodeCode\":\"A\",\"returnNodeCode\":null}")) {
            Instance instance = instance("{\"" + RejectConfig.FLOW_VARIABLE_KEY + "\":" + section + "}");
            assertThrows(FlowException.class, () -> RejectReturnRuntime.hasPending(instance), section);
        }
    }

    @Test
    void missingNodeOrIdFails() {
        Instance instance = instance(BUSINESS);
        assertThrows(FlowException.class, () -> RejectReturnRuntime.put(instance, "A", null, 100L, "D"));
        assertThrows(FlowException.class, () -> RejectReturnRuntime.put(instance, "A", TASK, 0L, "D"));
        assertThrows(FlowException.class, () -> RejectReturnRuntime.put(instance, "A", TASK, 100L, ""));
        assertThrows(FlowException.class, () -> RejectReturnRuntime.put(instance, "A", TASK, 100L, "A"));
        assertFalse(RejectReturnRuntime.hasPending(instance(FlowEngine.jsonConvert.objToStr(null))));
    }

    @Test
    void inboundBusinessVariablesCannotForgeRuntime() {
        Instance instance = instance(BUSINESS);
        Map<String, Object> forged = new java.util.LinkedHashMap<>();
        forged.put(RejectConfig.FLOW_VARIABLE_KEY, Map.of("version", 1, "taskId", "1",
            "sourceTaskId", "2", "nodeCode", "A", "returnNodeCode", "D"));
        FlowEngine.taskService().mergeVariable(instance, forged);
        assertFalse(RejectReturnRuntime.hasPending(instance));
    }

    @Test
    void hostCodeCannotWriteReservedVariable() {
        var context = new org.dromara.warm.flow.core.service.task.AutoNodeContext(
            null, null, null, new java.util.HashMap<>(), null);
        assertThrows(FlowException.class,
            () -> context.setVariable(RejectConfig.FLOW_VARIABLE_KEY, "forged"));
    }

    private static Instance instance(String variables) {
        Instance instance = FlowEngine.newIns().setVariable(variables);
        return instance;
    }
}
