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
            "{\"reject\":null}", "{\"reject\":[1]}", "{\"reject\":{\"business\":true}}"}) {
            assertFalse(RejectConfig.parse(json).isReturnToRejector(), json);
        }
    }

    @Test
    void recognizedModesAndNumericVersion() {
        for (String version : List.of("1")) {
            assertTrue(RejectConfig.parse(config(version, "\"RETURN_TO_REJECTOR\""))
                .isReturnToRejector());
        }
        for (Number version : List.of(1, 1L, 1.0, java.math.BigDecimal.ONE)) {
            assertDoesNotThrow(() -> RejectConfig.requireVersionOne(version, "schemaVersion"));
        }
        assertFalse(RejectConfig.parse(config("1", "\"REPLAY\"")).isReturnToRejector());
        assertFalse(RejectConfig.parse(config("1", "null")).isReturnToRejector());
        assertFalse(RejectConfig.parse("{\"reject\":{\"schemaVersion\":1}}").isReturnToRejector());
    }

    @Test
    void recognizedInvalidVersionOrModeFails() {
        for (String version : List.of("2", "1.1", "\"1\"", "null", "true", "{}", "[]")) {
            assertThrows(FlowException.class, () -> RejectConfig.parse(config(version, "\"REPLAY\"")));
        }
        for (String mode : List.of("\"UNKNOWN\"", "1", "false", "{}", "[]")) {
            assertThrows(FlowException.class, () -> RejectConfig.parse(config("1", mode)));
        }
        assertThrows(FlowException.class, () -> RejectConfig.parse("[]"));
        assertThrows(FlowException.class, () -> RejectConfig.parse("{broken"));
    }

    @Test
    void atomicExecutionDetectedInSnapshotWithoutBusinessVariables() {
        assertFalse(RejectConfig.requiresAtomicExecution(snapshot("{\"reject\":\"legacy\"}")));
        assertFalse(RejectConfig.requiresAtomicExecution(snapshot(config("1", "\"REPLAY\""))));
        assertTrue(RejectConfig.requiresAtomicExecution(snapshot(config("1", "\"RETURN_TO_REJECTOR\""))));
        assertTrue(RejectConfig.requiresAtomicExecution(snapshot("{\"__rejectReturnRuntime\":{"
            + "\"version\":1,\"byTaskId\":{\"9007199254740993\":{\"sourceTaskId\":\"100\","
            + "\"returnNodeCode\":\"D\"}}}}")));
    }

    private static String config(String version, String mode) {
        return "{\"reject\":{\"schemaVersion\":" + version + ",\"afterApproval\":" + mode + "}}";
    }

    private static String snapshot(String config) {
        DefJson def = new DefJson().setNodeList(new ArrayList<>(List.of(
            new NodeJson().setNodeCode("D").setNodeConfig(config))));
        return FlowEngine.jsonConvert.objToStr(def);
    }
}
