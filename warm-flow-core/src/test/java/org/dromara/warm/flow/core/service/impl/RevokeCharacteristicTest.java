package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * revoke 撤回路径特征测试：锁定发起人校验、任务列表查询基线（R3 后复用监听器前快照，仅 1 次）、
 * 撤回后回到起始节点的终态与监听器可见性。
 *
 * @author warm
 */
class RevokeCharacteristicTest {

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
    void nonPromoterWithoutIgnore_throwsNotDefPromoter() {
        Instance instance = TestFlows.start("revoke1", "biz-r1");

        // 发起人校验仅在未忽略权限时执行：非发起人（createBy != handler）拦截
        WorkflowContext context = new WorkflowContext();
        context.setHandler("lisi");
        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().revoke(instance.getId(), context));
        assertEquals(ExceptionCons.NOT_DEF_PROMOTER_NOT_CANCEL, ex.getMessage());
    }

    @Test
    void finishedFlow_throwsFlowFinish() {
        Instance instance = TestFlows.start("revoke2", "biz-r2");
        Task task = TestFlows.currentTask(instance.getId());
        TestFlows.pass(task.getId(), TestFlows.HANDLER);

        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().revoke(instance.getId(), TestFlows.context(TestFlows.HANDLER)));
        assertEquals(ExceptionCons.FLOW_FINISH, ex.getMessage());
    }

    @Test
    void happyPath_locksFinalStateAndListenerVisibility() {
        Instance instance = TestFlows.start("revoke3", "biz-r3");
        harness.reset();

        FlowEngine.taskService().revoke(instance.getId(), TestFlows.context(TestFlows.HANDLER));

        // 实际撤回语义：回到「开始节点的下一节点」（apply），仅一条待办
        assertEquals(1, harness.taskDao.size(), "撤回后应有一条待办");
        Task revoked = harness.taskDao.all().get(0);
        assertEquals("apply", revoked.getNodeCode());
        Instance persisted = harness.insDao.raw(instance.getId());
        assertEquals("apply", persisted.getNodeCode());
        assertTrue(harness.userDao.size() >= 1, "撤回后应生成新待办办理人");

        // start 阶段 PASS 历史 + 撤回历史
        assertTrue(harness.hisTaskDao.size() >= 2, "start 历史 + 撤回历史");
    }

    @Test
    void happyPath_locksSingleTaskQueryBaseline() {
        Instance instance = TestFlows.start("revoke4", "biz-r4");
        harness.reset();

        FlowEngine.taskService().revoke(instance.getId(), TestFlows.context(TestFlows.HANDLER));

        // R3 后基线：复用监听器执行前的待办快照，仅 1 次任务列表查询（改造前为 2 次）
        assertEquals(1, harness.daoLog.stream().filter(l -> l.startsWith("TaskMemDao.selectList")).count(),
                "revoke 任务列表查询基线（R3 改造后为 1 次）: " + harness.daoLog);
    }

    @Test
    void noPendingTask_throwsNotFoundFlowTask() {
        Instance instance = TestFlows.start("revoke5", "biz-r5");
        TestFlows.pendingTasks(instance.getId()).forEach(t -> harness.taskDao.removeRaw(t.getId()));

        // 实例仍激活，但待办被清空：撤回在待办空校验处拦截
        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().revoke(instance.getId(), TestFlows.context(TestFlows.HANDLER)));
        assertEquals(ExceptionCons.NOT_FOUND_FLOW_TASK, ex.getMessage());
    }
}
