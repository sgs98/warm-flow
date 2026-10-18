package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 节点默认流程变量特征测试：每个节点都能配，语义是「缺失时的兜底默认值」。
 *
 * @author may
 */
class NodeDefaultVariableCharacteristicTest {

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
    void nodeDefaultVariable_feedsGatewayCondition() {
        buildFlow("ndVarGateway", nodeConfig("{\"variables\":{\"days\":5}}"), null);

        Instance instance = approve("ndVarGateway", null);
        // 办理没传 days，网关靠节点的默认变量命中 conditions 分支
        assertEquals("a1", instance.getNodeCode());
        assertEquals(5, ((Number) instance.getVariableMap().get("days")).intValue(),
            "默认变量应写入实例变量供后续读取");
    }

    @Test
    void callerVariableWinsOverNodeDefault() {
        buildFlow("ndVarCaller", nodeConfig("{\"variables\":{\"days\":5}}"), null);

        Instance instance = approve("ndVarCaller", Map.of("days", 8));
        // 宿主传入的 days=8 不被节点的默认值 5 覆盖
        assertEquals("b1", instance.getNodeCode());
        assertEquals(8, ((Number) instance.getVariableMap().get("days")).intValue());
    }

    @Test
    void gatewayOwnDefaultFillsMissingVariable() {
        buildFlow("ndVarGatewayNode", null, nodeConfig("{\"variables\":{\"days\":5}}"));

        Instance instance = approve("ndVarGatewayNode", null);
        // 默认变量配在网关自身上，同样在到达该网关时补充
        assertEquals("a1", instance.getNodeCode());
    }

    @Test
    void legacyEventVariableSectionStillRead() {
        buildFlow("ndVarLegacy", nodeConfig("{\"event\":{\"variables\":{\"days\":5}}}"), null);

        Instance instance = approve("ndVarLegacy", null);
        // 早期版本把默认变量放在 event 段下，存量 JSON 无需迁移
        assertEquals("a1", instance.getNodeCode());
    }

    private String nodeConfig(String json) {
        return json;
    }

    /**
     * 办理 start 后的首个申请节点并推进过网关。
     */
    private Instance approve(String flowCode, Map<String, Object> variables) {
        Instance instance = FlowEngine.insService().start("BIZ-" + flowCode, flowCode
            , TestFlows.context(TestFlows.HANDLER));
        Task apply = tasks(instance.getId()).stream()
            .filter(t -> "apply".equals(t.getNodeCode())).findFirst().orElseThrow();
        WorkflowContext context = TestFlows.context(TestFlows.HANDLER);
        if (variables != null) {
            context.setVariables(new java.util.HashMap<>(variables));
        }
        return FlowEngine.taskService().execute(apply.getId(), context, SkipType.PASS.getKey());
    }

    private List<Task> tasks(Long instanceId) {
        return FlowEngine.taskService().list(FlowEngine.newTask().setInstanceId(instanceId));
    }

    /**
     * 构建 start → apply → gw(互斥) → [a1(eq@@days|5), b1(默认)] → end，
     * 默认变量可分别配在 apply 节点或网关上。
     */
    private Definition buildFlow(String flowCode, String applyConfig, String gatewayConfig) {
        Definition def = FlowEngine.newDef()
            .setFlowCode(flowCode).setFlowName(flowCode).setVersion("1")
            .setIsPublish(PublishStatus.PUBLISHED.getKey())
            .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(def);
        node(def, "start", NodeType.START.getKey(), null, null);
        node(def, "apply", NodeType.BETWEEN.getKey(), TestFlows.HANDLER, applyConfig);
        node(def, "gw", NodeType.SERIAL.getKey(), null, gatewayConfig);
        node(def, "a1", NodeType.BETWEEN.getKey(), TestFlows.HANDLER, null);
        node(def, "b1", NodeType.BETWEEN.getKey(), TestFlows.HANDLER, null);
        node(def, "end", NodeType.END.getKey(), null, null);

        skip(def, "start", NodeType.START.getKey(), "apply", NodeType.BETWEEN.getKey(), null);
        skip(def, "apply", NodeType.BETWEEN.getKey(), "gw", NodeType.SERIAL.getKey(), null);
        skip(def, "gw", NodeType.SERIAL.getKey(), "a1", NodeType.BETWEEN.getKey()
            , "eq@@days|5");
        skip(def, "gw", NodeType.SERIAL.getKey(), "b1", NodeType.BETWEEN.getKey(), null);
        skip(def, "a1", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey(), null);
        skip(def, "b1", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey(), null);
        return def;
    }

    private Node node(Definition def, String code, Integer type, String permission, String nodeConfig) {
        Node node = FlowEngine.newNode().setDefinitionId(def.getId()).setNodeCode(code)
            .setNodeName(code).setNodeType(type).setNodeRatio("0")
            .setNodeConfig(nodeConfig);
        if (permission != null) {
            node.setPermissionFlag(permission);
        }
        FlowEngine.nodeService().save(node);
        return node;
    }

    private void skip(Definition def, String now, Integer nowType, String next, Integer nextType
        , String condition) {
        FlowEngine.skipService().save(FlowEngine.newSkip().setDefinitionId(def.getId())
            .setNowNodeCode(now).setNowNodeType(nowType).setNextNodeCode(next)
            .setNextNodeType(nextType).setSkipName(now + "_" + next)
            .setSkipType(SkipType.PASS.getKey()).setSkipCondition(condition));
    }
}
