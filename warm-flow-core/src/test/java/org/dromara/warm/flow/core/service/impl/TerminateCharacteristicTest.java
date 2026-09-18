package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * terminate 终止路径特征测试：任务主键查询基线（R1 后复用门面已加载对象，1 次；
 * 改造前为 getById + getAndCheck 双查）、start 监听器时 userList 尚未注入（时序锁）、
 * 实例终止状态回写与收尾清理顺序。
 *
 * @author warm
 */
class TerminateCharacteristicTest {

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
    void taskMissing_throwsNotFoundTask() {
        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().terminateByTaskId(9999L, TestFlows.context(TestFlows.HANDLER)));
        assertEquals(ExceptionCons.NOT_FOUNT_TASK, ex.getMessage());
    }

    @Test
    void happyPath_locksListenerTimingAndFinalState() {
        Instance instance = TestFlows.start("term1", "biz-t1");
        Task task = TestFlows.currentTask(instance.getId());
        harness.reset();

        WorkflowContext context = TestFlows.context(TestFlows.HANDLER);
        FlowEngine.taskService().terminateByTaskId(task.getId(), context);

        List<String> events = org.dromara.warm.flow.core.test.listener.RecordingListener.EVENTS.stream()
                .filter(e -> !e.startsWith("g:"))
                .toList();
        // P2 时序锁：terminate 的 start 监听器先于 setUserList 执行，taskUsers=false
        assertTrue(events.get(0).startsWith("start{node=apply,task=apply,ctx=true,taskUsers=false"),
                "terminate 的 start 监听器应看不到 task.userList，实际: " + events.get(0));
        assertTrue(events.stream().anyMatch(e -> e.startsWith("finish{node=apply,task=apply,ctx=true")),
                "terminate 应触发 finish 监听器: " + events);

        // 终态
        assertEquals(0, harness.taskDao.size(), "终止后不应残留待办");
        assertEquals(0, harness.userDao.size(), "终止后办理人应清理");
        Instance persisted = harness.insDao.raw(instance.getId());
        assertEquals(FlowStatus.TERMINATE.getKey(), persisted.getFlowStatus());
        // start 阶段 PASS 历史 + 终止历史 = 2，其中终止历史状态为 TERMINATE
        assertEquals(2, harness.hisTaskDao.size());
        assertEquals(1, harness.hisTaskDao.all().stream()
                .filter(h -> FlowStatus.TERMINATE.getKey().equals(h.getFlowStatus())).count(),
                "应存在一条 TERMINATE 历史");
        // 状态回写：终止后 context.instanceStatus 带回实例终态
        assertNotNull(context.getInstanceStatus());
        assertEquals(persisted.getFlowStatus(), context.getInstanceStatus());
    }

    @Test
    void happyPath_locksDoubleTaskQueryBaseline() {
        Instance instance = TestFlows.start("term2", "biz-t2");
        Task task = TestFlows.currentTask(instance.getId());
        harness.reset();

        FlowEngine.taskService().terminateByTaskId(task.getId(), TestFlows.context(TestFlows.HANDLER));

        // R1 后基线：门面查询后直接复用任务对象，仅 1 次 selectById（改造前为 2 次）
        assertEquals(1, harness.daoLog.stream().filter(l -> l.startsWith("TaskMemDao.selectById[")).count(),
                "terminate 任务主键查询基线（R1 改造后为 1 次）: " + harness.daoLog);
    }

    @Test
    void withoutPermission_throwsNullRoleNode() {
        Instance instance = TestFlows.start("term3", "biz-t3");
        Task task = TestFlows.currentTask(instance.getId());
        WorkflowContext context = new WorkflowContext();
        context.setHandler("lisi");
        context.setPermissions(List.of("lisi"));

        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().terminateByTaskId(task.getId(), context));
        assertEquals(ExceptionCons.NULL_ROLE_NODE, ex.getMessage());
        assertEquals(1, harness.taskDao.size(), "失败路径不应清理待办");
    }
}
