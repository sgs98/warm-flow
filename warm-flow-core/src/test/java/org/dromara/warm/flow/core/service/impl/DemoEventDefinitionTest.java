package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.dto.SkipJson;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * demo 事件案例定义的导入与发布门禁校验：六个案例 JSON 必须全部通过引擎解析与发布校验，
 * 且流程图无孤岛节点（除开始节点外每个节点都必须从开始节点可达）。
 *
 * @author may
 */
class DemoEventDefinitionTest {

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
        harness.register(org.dromara.warm.flow.core.timer.EventTaskHandler.class, new EventRuntimeHandler());
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void importAndPublishAllDemoEventDefinitions() throws Exception {
        Path dir = Paths.get("..", "warm-flow-demo", "warm-flow-demo", "src", "main", "resources", "json");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith("demo_event_"))
                .sorted().toList()) {
                String json = Files.readString(file);
                Definition def = FlowEngine.defService().importJson(json);
                assertNotNull(def.getId(), file.getFileName() + " 导入后应有主键");
                FlowEngine.defService().publish(def.getId());
                Definition published = FlowEngine.defService().getById(def.getId());
                assertNotNull(published, file.getFileName() + " 发布后应可查询");
                assertTrue(published.getId() != null, file.getFileName() + " 应发布成功");
                assertNoOrphanNodes(file.getFileName().toString(), json);
                FlowEngine.defService().removeDef(java.util.List.of(def.getId()));
            }
        }
    }

    /**
     * 校验流程图无孤岛节点：从开始节点沿 PASS 连线广度优先遍历，
     * 除开始节点外的所有节点都必须可达，防止连线接错导致节点被静默跳过。
     *
     * @param fileName 案例文件名，用于断言信息定位
     * @param json     流程设计 JSON
     */
    private void assertNoOrphanNodes(String fileName, String json) {
        DefJson defJson = FlowEngine.jsonConvert.strToBean(json, DefJson.class);
        List<NodeJson> nodes = defJson.getNodeList();
        assertTrue(nodes != null && !nodes.isEmpty(), fileName + " 应包含节点");

        Map<String, List<SkipJson>> skipsBySource = new LinkedHashMap<>();
        String startCode = null;
        for (NodeJson node : nodes) {
            if (node.getNodeType() != null && node.getNodeType() == 0) {
                startCode = node.getNodeCode();
            }
            skipsBySource.put(node.getNodeCode(), node.getSkipList() == null ? List.of() : node.getSkipList());
        }
        assertNotNull(startCode, fileName + " 应包含开始节点");

        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        visited.add(startCode);
        queue.add(startCode);
        while (!queue.isEmpty()) {
            for (SkipJson skip : skipsBySource.getOrDefault(queue.poll(), List.of())) {
                if (skip.getNextNodeCode() != null && visited.add(skip.getNextNodeCode())) {
                    queue.add(skip.getNextNodeCode());
                }
            }
        }

        for (NodeJson node : nodes) {
            if (visited.contains(node.getNodeCode())) {
                continue;
            }
            // 边界事件通过 attachedToNodeCode 附着在宿主节点上，宿主可达即视为可达
            if (StringUtils.isNotEmpty(node.getAttachedToNodeCode())
                && visited.contains(node.getAttachedToNodeCode())) {
                continue;
            }
            assertTrue(false, fileName + " 存在从开始节点不可达的孤岛节点: "
                + node.getNodeCode() + "(" + node.getNodeName() + ")");
        }
    }
}
