package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.constant.FlowCons;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 发起人办理人标识特征测试：锁定节点办理人配置为 {@code warmFlowInitiator} 时，
 * 创建待办统一替换为流程实例发起人（createBy）的行为，以及"谁发起、谁提交"的权限语义。
 *
 * @author warm
 */
class InitiatorPermissionCharacteristicTest {

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
    void initiatorFlag_replacedWithStarter() {
        initiatorFlow("ini1", FlowCons.WARMFLOWINITIATOR);

        Instance instance = FlowEngine.insService().start("biz-i1", "ini1", TestFlows.context("starter-1"));

        List<User> users = harness.userDao.all();
        assertEquals(1, users.size());
        assertEquals("starter-1", users.get(0).getProcessedBy(), "发起人标识应替换为实例 createBy");
    }

    @Test
    void initiatorFlag_mixedWithFixedHandler_keepsBoth() {
        initiatorFlow("ini2", FlowCons.WARMFLOWINITIATOR + FlowCons.SPLIT_AT + "fixed-handler");

        FlowEngine.insService().start("biz-i2", "ini2", TestFlows.context("starter-2"));

        List<String> processedBy = harness.userDao.all().stream().map(User::getProcessedBy).sorted().toList();
        assertEquals(List.of("fixed-handler", "starter-2"), processedBy,
                "发起人标识替换后其余固定办理人应保留");
    }

    @Test
    void initiatorNode_onlyStarterCanPass() {
        initiatorFlow("ini3", FlowCons.WARMFLOWINITIATOR);
        Instance instance = FlowEngine.insService().start("biz-i3", "ini3", TestFlows.context("starter-3"));
        Long taskId = TestFlows.currentTask(instance.getId()).getId();

        // 非发起人无权限办理
        WorkflowContext other = noIgnoreContext("other-user", List.of("other-user"));
        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().execute(taskId, other, SkipType.PASS.getKey()));
        assertEquals(ExceptionCons.NULL_ROLE_NODE, ex.getMessage());

        // 发起人本人可提交
        WorkflowContext starter = noIgnoreContext("starter-3", List.of("starter-3"));
        Instance after = FlowEngine.taskService().execute(taskId, starter, SkipType.PASS.getKey());
        assertEquals("end", after.getNodeCode());
    }

    /**
     * 构建并保存 start → apply(发起人办理) → end 的已发布串行流程。
     *
     * @param flowCode       流程编码
     * @param applyPermission apply 节点办理人权限
     * @return 已保存的流程定义
     */
    private Definition initiatorFlow(String flowCode, String applyPermission) {
        Definition def = FlowEngine.newDef()
                .setFlowCode(flowCode)
                .setFlowName("发起人办理测试流程")
                .setVersion("1")
                .setIsPublish(PublishStatus.PUBLISHED.getKey())
                .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(def);
        saveNode(def, "start", NodeType.START.getKey(), null);
        saveNode(def, "apply", NodeType.BETWEEN.getKey(), applyPermission);
        saveNode(def, "end", NodeType.END.getKey(), null);
        saveSkip(def, "start", NodeType.START.getKey(), "apply", NodeType.BETWEEN.getKey());
        saveSkip(def, "apply", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        return def;
    }

    private void saveNode(Definition def, String code, Integer nodeType, String permissionFlag) {
        Node node = FlowEngine.newNode()
                .setDefinitionId(def.getId())
                .setNodeCode(code)
                .setNodeName(code)
                .setNodeType(nodeType)
                .setNodeRatio("0");
        if (permissionFlag != null) {
            node.setPermissionFlag(permissionFlag);
        }
        FlowEngine.nodeService().save(node);
    }

    private void saveSkip(Definition def, String now, Integer nowType, String next, Integer nextType) {
        FlowEngine.skipService().save(FlowEngine.newSkip()
                .setDefinitionId(def.getId())
                .setNowNodeCode(now)
                .setNowNodeType(nowType)
                .setNextNodeCode(next)
                .setNextNodeType(nextType)
                .setSkipName(now + "_" + next)
                .setSkipType(SkipType.PASS.getKey()));
    }

    /**
     * 构建开启权限校验、指定办理人权限集合的执行上下文。
     */
    private WorkflowContext noIgnoreContext(String handler, List<String> permissions) {
        WorkflowContext context = TestFlows.context(handler);
        context.setIgnorePermission(false);
        context.setPermissions(permissions);
        return context;
    }
}
