package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 跳转（targetNodeCode）边界特征测试：锁定任意跳转到普通节点的直达语义、
 * 网关节点禁跳、开始节点禁跳三类路径解析边界。
 *
 * @author warm
 */
class PathJumpCharacteristicTest {

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    private WorkflowContext jumpContext(String handler, String targetNodeCode) {
        WorkflowContext context = TestFlows.context(handler);
        context.setTargetNodeCode(targetNodeCode);
        return context;
    }

    @Test
    void jumpToEnd_completesFlow() {
        Instance instance = TestFlows.start("pj1", "biz-j1");
        Task task = TestFlows.currentTask(instance.getId());

        FlowEngine.taskService().execute(task.getId()
                , jumpContext(TestFlows.HANDLER, "end")
                , SkipType.PASS.getKey());

        assertEquals(0, harness.taskDao.size(), "跳转 end 后无待办");
        assertEquals(FlowStatus.FINISHED.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
        assertEquals("end", harness.insDao.raw(instance.getId()).getNodeCode());
    }

    @Test
    void jumpToGateway_throwsTarNotGateway() {
        Instance instance = TestFlows.startParallelFlow("pj2", "biz-j2");
        Task task = TestFlows.pendingTasks(instance.getId()).get(0);

        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().execute(task.getId()
                        , jumpContext(TestFlows.HANDLER, "forkP")
                        , SkipType.PASS.getKey()));
        assertEquals(ExceptionCons.TAR_NOT_GATEWAY, ex.getMessage());
    }

    @Test
    void jumpToStart_throwsStartNodeNotAllowedJump() {
        Instance instance = TestFlows.start("pj3", "biz-j3");
        Task task = TestFlows.currentTask(instance.getId());

        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().execute(task.getId()
                        , jumpContext(TestFlows.HANDLER, "start")
                        , SkipType.PASS.getKey()));
        assertEquals(ExceptionCons.START_NODE_NOT_ALLOW_JUMP, ex.getMessage());
    }
}
