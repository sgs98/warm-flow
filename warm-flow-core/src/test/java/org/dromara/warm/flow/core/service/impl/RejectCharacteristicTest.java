package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * reject 驳回路径特征测试：锁定驳回线解析前置校验、一票否决对退回目标后置待办的
 * 清理语义（直接删除、不转历史）与驳回路径任务查询基线（oneVoteVeto ×1、
 * handUndoneTask 因实例未到 end 不触发）。
 *
 * @author warm
 */
class RejectCharacteristicTest {

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
    void rejectWithoutRejectLine_throwsNullSkipType() {
        Instance instance = TestFlows.start("rej0", "biz-r0");
        Task task = TestFlows.currentTask(instance.getId());

        // 串行流程只有 PASS 线，驳回找不到匹配连线
        FlowException ex = assertThrows(FlowException.class,
                () -> TestFlows.reject(task.getId(), TestFlows.HANDLER));
        assertEquals(ExceptionCons.NULL_SKIP_TYPE, ex.getMessage());
        assertEquals(1, harness.taskDao.size(), "失败路径不应清理待办");
    }

    @Test
    void oneVoteVeto_removesSuffixSiblingTasksWithoutHistory() {
        Instance instance = TestFlows.startRejectFlow("rej1", "biz-r1");
        Task applyTask = TestFlows.currentTask(instance.getId());
        TestFlows.pass(applyTask.getId(), TestFlows.HANDLER);
        Task auditTask = TestFlows.currentTask(instance.getId());
        // 种入 audit 节点的兄弟待办（模拟并行分支遗留），位于驳回目标 apply 的后置节点上
        Task sibling = FlowEngine.newTask()
                .setDefinitionId(auditTask.getDefinitionId())
                .setInstanceId(instance.getId())
                .setNodeCode("audit")
                .setNodeName("audit")
                .setNodeType(auditTask.getNodeType())
                .setFlowStatus(FlowStatus.APPROVAL.getKey());
        FlowEngine.taskService().save(sibling);
        FlowEngine.userService().save(FlowEngine.userService().structureUser(sibling.getId()
                , "wangwu", UserType.APPROVAL.getKey(), TestFlows.HANDLER));
        harness.reset();

        TestFlows.reject(auditTask.getId(), TestFlows.HANDLER);

        // 驳回目标待办：apply 节点、REJECT 状态
        List<Task> pending = FlowEngine.taskService().list(
                FlowEngine.newTask().setInstanceId(instance.getId()));
        assertEquals(1, pending.size(), "驳回后应仅有驳回目标待办: " + pending);
        assertEquals("apply", pending.get(0).getNodeCode());
        assertEquals(FlowStatus.REJECT.getKey(), pending.get(0).getFlowStatus());
        Instance persisted = harness.insDao.raw(instance.getId());
        assertEquals("apply", persisted.getNodeCode());
        assertEquals(FlowStatus.REJECT.getKey(), persisted.getFlowStatus());

        // 一票否决现状：后置兄弟待办直接删除，不转历史（注释称转历史，实现是纯删除）
        assertNull(harness.taskDao.raw(sibling.getId()), "后置兄弟待办应被一票否决删除");
        assertTrue(harness.userDao.all().stream()
                        .noneMatch(u -> Objects.equals(u.getAssociated(), sibling.getId())),
                "兄弟待办办理人应一并清理");
        assertTrue(harness.hisTaskDao.all().stream()
                        .noneMatch(h -> Objects.equals(h.getTaskId(), sibling.getId())),
                "兄弟待办不应产生历史记录");
        // 驳回动作本身：当前任务转 REJECT 历史
        assertEquals(1, harness.hisTaskDao.all().stream()
                .filter(h -> Objects.equals(h.getTaskId(), auditTask.getId()))
                .filter(h -> SkipType.REJECT.getKey().equals(h.getSkipType())).count());
    }

    @Test
    void locksTaskQueryBaseline_rejectPathQueriesOnce() {
        Instance instance = TestFlows.startRejectFlow("rej2", "biz-r2");
        Task applyTask = TestFlows.currentTask(instance.getId());
        TestFlows.pass(applyTask.getId(), TestFlows.HANDLER);
        Task auditTask = TestFlows.currentTask(instance.getId());
        Task sibling = FlowEngine.newTask()
                .setDefinitionId(auditTask.getDefinitionId())
                .setInstanceId(instance.getId())
                .setNodeCode("audit")
                .setNodeName("audit")
                .setNodeType(auditTask.getNodeType())
                .setFlowStatus(FlowStatus.APPROVAL.getKey());
        FlowEngine.taskService().save(sibling);
        harness.reset();

        TestFlows.reject(auditTask.getId(), TestFlows.HANDLER);

        // 驳回路径任务列表查询基线：仅 oneVoteVeto ×1；
        // handUndoneTask 因实例未流转到 end 节点不触发（R4 的两处同语句查询实际处于互斥分支）
        List<String> taskLists = harness.daoLog.stream()
                .filter(l -> l.startsWith("TaskMemDao.selectList")).toList();
        assertEquals(1, taskLists.size(), "驳回路径任务列表查询基线: " + harness.daoLog);
        // 驳回不走汇聚等待，不按节点编码查待办
        assertEquals(0, harness.daoLog.stream()
                .filter(l -> l.startsWith("TaskMemDao.getByInsIdAndNodeCodes")).count());
    }
}
