package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.json.JsonConvert;
import org.dromara.warm.flow.core.service.task.RejectConfig;
import org.dromara.warm.plugin.json.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 JSON provider 检查版本、长 ID 与无损运行段更新。 @author may */
class RejectReturnJsonRoundTripTest {
    @TestFactory
    Stream<DynamicTest> providers() {
        return List.<JsonConvert>of(new JsonConvertJackson3()).stream()
            .map(provider -> DynamicTest.dynamicTest(provider.getClass().getSimpleName(), () -> verify(provider)));
    }

    @TestFactory
    Stream<DynamicTest> preciseProtocolVersions() {
        return jsonProviders().stream().map(provider -> DynamicTest.dynamicTest(
            provider.getClass().getSimpleName() + " exact versions", () -> withProvider(provider, () -> {
                for (String version : List.of("1.0000000000000001", "0.99999999999999999",
                    "1.0000000000000001e0", "9.9999999999999999e-1")) {
                    assertThrows(org.dromara.warm.flow.core.exception.FlowException.class,
                        () -> RejectConfig.parse("{\"reject\":{\"schemaVersion\":" + version + "}}"), version);
                    String config = "{\"__rejectReturnRuntime\":{\"version\":" + version
                        + ",\"byTaskId\":{}}}";
                    assertThrows(org.dromara.warm.flow.core.exception.FlowException.class,
                        () -> RejectReturnRuntime.hasPending(snapshot(provider, config)), version);
                }
            })));
    }

    @TestFactory
    Stream<DynamicTest> exactBusinessNumbers() {
        return jsonProviders().stream().map(provider -> DynamicTest.dynamicTest(
            provider.getClass().getSimpleName() + " exact business numbers", () -> withProvider(provider, () -> {
                String config = "{\"businessId\":9007199254740993,"
                    + "\"big\":123456789012345678901234567890,"
                    + "\"marker\":\"\\u005f\\u005frejectNumber__0\","
                    + "\"nested\":[{\"arrayNumber\":9007199254740993},null],"
                    + "\"decimal\":0.123456789012345678901234567890,"
                    + "\"exponent\":1.234567890123456789e30,"
                    + "\"literal\":\"1e0 \\\"number\\\" \\\\ 9007199254740993\"}";
                String pending = RejectReturnRuntime.put(snapshot(provider, config), "A", 10L, 20L, "D");
                for (String result : List.of(pending, RejectReturnRuntime.remove(pending, "A", 10L),
                    RejectReturnRuntime.clear(pending))) {
                    String stored = RejectReturnRuntime.configForNode(result, "A");
                    assertExactNumber(stored, "businessId", "9007199254740993");
                    assertExactNumber(stored, "big", "123456789012345678901234567890");
                    assertExactNumber(stored, "arrayNumber", "9007199254740993");
                    assertEquals("__rejectNumber__0", provider.strToMap(stored).get("marker"));
                    assertExactNumber(stored, "decimal", "0.123456789012345678901234567890");
                    assertExactNumber(stored, "exponent", "1.234567890123456789e30");
                    assertExactNumber(result, "futureNumber", "9007199254740993");
                    assertEquals(provider.strToMap(config).get("literal"), provider.strToMap(stored).get("literal"));
                }
            })));
    }

    private List<JsonConvert> jsonProviders() {
        return List.of(new JsonConvertJackson3());
    }

    private String snapshot(JsonConvert provider, String config) {
        return "{\"futureNumber\":9007199254740993,\"nodeList\":["
            + "{\"nodeCode\":\"A\",\"nodeConfig\":" + provider.objToStr(config) + "},"
            + "{\"nodeCode\":\"D\",\"nodeConfig\":null}]}";
    }

    /** 独立按输出原文取数值，不用同一个 provider 解析两遍而掩盖共同舍入。 */
    private void assertExactNumber(String json, String field, String expected) {
        var matcher = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*([^,}\\s]+)").matcher(json);
        assertTrue(matcher.find(), field);
        assertEquals(0, new java.math.BigDecimal(expected).compareTo(new java.math.BigDecimal(matcher.group(1))), field);
    }

    private void withProvider(JsonConvert provider, Runnable work) {
        JsonConvert previous = FlowEngine.jsonConvert;
        FlowEngine.jsonConvert = provider;
        try { work.run(); } finally { FlowEngine.jsonConvert = previous; }
    }

    private void verify(JsonConvert provider) {
        JsonConvert previous = FlowEngine.jsonConvert;
        FlowEngine.jsonConvert = provider;
        try {
            for (String version : List.of("1", "1.0", "1e0", "1E0", "1.0e0")) {
                assertTrue(RejectConfig.parse("{\"reject\":{\"schemaVersion\":" + version
                    + ",\"afterApproval\":\"RETURN_TO_REJECTOR\"}}").isReturnToRejector());
            }
            assertThrows(org.dromara.warm.flow.core.exception.FlowException.class,
                () -> RejectConfig.parse("{\"reject\":{\"schemaVersion\":\"1\"}}"));
            String config = """
                {"variables":{"empty":null,"items":[null,"",{"a":null}]},"copy":{"handlers":["user:a"]},
                "groupSign":{"groups":[],"custom":null},"custom":{"emptyList":[],"emptyMap":{},"null":null},
                "empty":"","business":null,"token":"1e0 and 1E+0"}
                """;
            String snapshot = "{\"flowCode\":\"roundTrip\",\"futureRoot\":null,\"nodeList\":["
                + "{\"nodeCode\":\"A\",\"futureNode\":null,\"nodeConfig\":" + provider.objToStr(config) + "},"
                + "{\"nodeCode\":\"D\",\"nodeConfig\":" + provider.objToStr("{\"reject\":{\"schemaVersion\":1,\"afterApproval\":\"RETURN_TO_REJECTOR\"}}") + "}]}";
            Long id = 9007199254740993L, source = 9007199254740995L;
            String pending = RejectReturnRuntime.put(snapshot, "A", id, source, "D");
            var intent = RejectReturnRuntime.find(pending, "A", id).orElseThrow();
            assertEquals(source, intent.sourceTaskId()); assertEquals("D", intent.returnNodeCode());
            assertTrue(RejectReturnRuntime.find(pending, "A", id + 1).isEmpty());
            Map<String, Object> stored = RejectConfig.configMap(RejectReturnRuntime.configForNode(pending, "A"));
            Map<?, ?> runtime = (Map<?, ?>) stored.get(RejectConfig.RUNTIME_KEY);
            Map<?, ?> tasks = (Map<?, ?>) runtime.get("byTaskId");
            assertEquals(source.toString(), ((Map<?, ?>) tasks.get(id.toString())).get("sourceTaskId"));
            assertTrue(RejectConfig.requiresAtomicExecution(pending));
            for (String result : List.of(RejectReturnRuntime.remove(pending, "A", id), RejectReturnRuntime.clear(pending))) {
                assertEquals(provider.strToMap(config), RejectConfig.configMap(RejectReturnRuntime.configForNode(result, "A")));
                Map<String, Object> graph = provider.strToMap(result);
                assertTrue(graph.containsKey("futureRoot")); assertNull(graph.get("futureRoot"));
                assertTrue(((Map<?, ?>) ((List<?>) graph.get("nodeList")).get(0)).containsKey("futureNode"));
                assertFalse(RejectReturnRuntime.hasPending(result));
                assertTrue(RejectConfig.parse(RejectReturnRuntime.configForNode(result, "D")).isReturnToRejector());
                assertEquals(result, RejectReturnRuntime.clear(result));
            }
        } finally { FlowEngine.jsonConvert = previous; }
    }
}
