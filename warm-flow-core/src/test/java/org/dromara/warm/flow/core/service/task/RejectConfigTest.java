package org.dromara.warm.flow.core.service.task;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 版本化驳回配置兼容契约。 @author may */
class RejectConfigTest {
    private FlowTestHarness harness;

    @BeforeEach
    void setUp() { harness = new FlowTestHarness(); }

    @AfterEach
    void tearDown() { harness.close(); }

    @Test
    void missingAndLegacySectionsKeepReplay() {
        for (String json : new String[]{null, "", "{}", "{\"reject\":\"legacy\"}",
            "{\"reject\":null}", "{\"reject\":[1]}", "{\"reject\":{\"business\":true}}",
            "{\"reject\":{\"schemaVersion\":1}}",
            "{\"reject\":{\"schemaVersion\":1,\"business\":true}}"}) {
            assertFalse(RejectConfig.parse(json).isReturnToRejector(), json);
        }
    }

    @Test
    void recognizedModes() {
        assertTrue(RejectConfig.parse(config("\"RETURN_TO_REJECTOR\"")).isReturnToRejector());
        assertFalse(RejectConfig.parse(config("\"REPLAY\"")).isReturnToRejector());
        assertFalse(RejectConfig.parse(config("null")).isReturnToRejector());
        assertFalse(RejectConfig.parse("{\"reject\":{}}").isReturnToRejector());
    }

    @Test
    void invalidModeFails() {
        for (String mode : List.of("\"UNKNOWN\"", "1", "false", "{}", "[]")) {
            assertThrows(FlowException.class, () -> RejectConfig.parse(config(mode)));
        }
        assertThrows(FlowException.class, () -> RejectConfig.parse("[]"));
        assertThrows(FlowException.class, () -> RejectConfig.parse("{broken"));
    }

    @Test
    void atomicExecutionDetectedInSnapshotWithoutBusinessVariables() {
        assertFalse(RejectConfig.requiresAtomicExecution(snapshot("{\"reject\":\"legacy\"}")));
        assertFalse(RejectConfig.requiresAtomicExecution(snapshot(config("\"REPLAY\""))));
        assertTrue(RejectConfig.requiresAtomicExecution(snapshot(config("\"RETURN_TO_REJECTOR\""))));
        // 运行关联存放在实例流程变量中；快照里的旧运行段是惰性数据，不再触发事务门禁
        assertFalse(RejectConfig.requiresAtomicExecution(snapshot(
            "{\"__rejectReturnRuntime\":{\"version\":1,\"byTaskId\":{}}}")));
    }

    private static String config(String mode) {
        return "{\"reject\":{\"afterApproval\":" + mode + "}}";
    }

    private static String snapshot(String config) {
        DefJson def = new DefJson().setNodeList(new ArrayList<>(List.of(
            new NodeJson().setNodeCode("D").setNodeConfig(config))));
        return FlowEngine.jsonConvert.objToStr(def);
    }
}
