package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.test.listener.RecordingGlobalListener;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * start 发起路径特征测试：锁定入参校验、定义查找/挂起校验、发起后的实例/待办/历史/
 * 办理人初始状态、变量持久化、全局监听器触发序，以及 businessId 不去重的现状。
 *
 * @author warm
 */
class StartCharacteristicTest {

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
    void nullFlowCodeOrEmptyBusinessId_throws() {
        FlowException exCode = assertThrows(FlowException.class,
                () -> FlowEngine.insService().start("biz", null, TestFlows.context(TestFlows.HANDLER)));
        assertEquals(ExceptionCons.NULL_FLOW_CODE, exCode.getMessage());

        FlowException exBiz = assertThrows(FlowException.class,
                () -> FlowEngine.insService().start("", "biz", TestFlows.context(TestFlows.HANDLER)));
        assertEquals(ExceptionCons.NULL_BUSINESS_ID, exBiz.getMessage());
    }

    @Test
    void unknownFlowCode_throwsNotFoundDef() {
        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.insService().start("biz-x", "no-such-code", TestFlows.context(TestFlows.HANDLER)));
        assertEquals(ExceptionCons.NOT_FOUNT_DEF, ex.getMessage());
    }

    @Test
    void suspendedDefinition_throwsNotDefinitionActivity() {
        Definition def = TestFlows.serialFlow("sc3");
        harness.defDao.raw(def.getId()).setActivityStatus(ActivityStatus.SUSPENDED.getKey());

        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.insService().start("biz-s3", "sc3", TestFlows.context(TestFlows.HANDLER)));
        assertEquals(ExceptionCons.NOT_DEFINITION_ACTIVITY, ex.getMessage());
        assertEquals(0, harness.insDao.size(), "失败路径不应产生实例");
    }

    @Test
    void happyPath_locksInitialState() {
        TestFlows.serialFlow("sc1");
        WorkflowContext context = TestFlows.context(TestFlows.HANDLER);
        Map<String, Object> variables = new HashMap<>();
        variables.put("days", 3);
        context.setVariables(variables);
        harness.reset();

        Instance instance = FlowEngine.insService().start("biz-s1", "sc1", context);

        // 实例：首中间节点、待提交状态（启动状态由调用方决定，不被首任务状态反向覆盖）
        assertEquals("apply", instance.getNodeCode());
        assertEquals(FlowStatus.TOBESUBMIT.getKey(), instance.getFlowStatus());
        assertEquals(TestFlows.HANDLER, instance.getCreateBy());
        assertNotNull(instance.getDefJson(), "发起后应写入流程图元数据");
        assertEquals(3, ((Number) instance.getVariableMap().get("days")).intValue());

        // 待办：apply 节点、审批中（permissionList 为瞬态列，查询后不回显，办理人由 user 行断言）
        Task task = TestFlows.currentTask(instance.getId());
        assertEquals("apply", task.getNodeCode());
        assertEquals(FlowStatus.APPROVAL.getKey(), task.getFlowStatus());

        // 办理人：1 条 APPROVAL
        List<org.dromara.warm.flow.core.entity.User> users = harness.userDao.all();
        assertEquals(1, users.size());
        assertEquals(UserType.APPROVAL.getKey(), users.get(0).getType());

        // 历史：开始节点转历史（PASS）
        assertEquals(1, harness.hisTaskDao.size());
        assertEquals("start", harness.hisTaskDao.all().get(0).getNodeCode());

        // 全局监听器：start → assignment → finish → create 全触发（create 仅非 end 节点）
        List<String> global = RecordingGlobalListener.EVENTS.stream().map(e -> e.substring(0, e.indexOf('{'))).toList();
        assertEquals(List.of("g:start", "g:assignment", "g:finish", "g:create"), global,
                "发起阶段全局监听器触发序: " + global);
    }

    @Test
    void duplicateBusinessId_isNotDeduplicated() {
        TestFlows.serialFlow("sc2");
        Instance first = FlowEngine.insService().start("biz-dup", "sc2", TestFlows.context(TestFlows.HANDLER));
        Instance second = FlowEngine.insService().start("biz-dup", "sc2", TestFlows.context(TestFlows.HANDLER));

        // 现状：start 不按 businessId 去重，两次发起生成两个独立实例
        assertEquals(2, harness.insDao.size());
        assertEquals(2, harness.taskDao.size());
        assertNotNull(first);
        assertNotNull(second);
    }
}
