package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并行网关特征测试：锁定分叉多待办生成、汇聚等待（未齐不推进、实例不动、
 * 汇聚路径 previousNodeList 重查节点+连线的现状基线——R2 接入的观测锚点）、
 * 末支汇合后完成流程。
 *
 * @author warm
 */
class ParallelGatewayCharacteristicTest {

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
    void fork_createsBothBranchTasks() {
        Instance instance = TestFlows.startParallelFlow("pg1", "biz-p1");

        List<Task> pending = TestFlows.pendingTasks(instance.getId());
        Set<String> nodeCodes = pending.stream().map(Task::getNodeCode).collect(Collectors.toSet());
        assertEquals(Set.of("a1", "b1"), nodeCodes, "并行分叉应同时生成两支待办: " + nodeCodes);
        assertTrue(pending.stream().allMatch(t -> FlowStatus.APPROVAL.getKey().equals(t.getFlowStatus())));
        assertEquals(2, harness.userDao.size(), "两支待办各一名办理人");

        // 实例停留在发起状态，未被任一分支反向覆盖
        assertEquals(FlowStatus.TOBESUBMIT.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
    }

    @Test
    void join_waitsForRemainingBranch_rebuildsDefinitionGraph() {
        Instance instance = TestFlows.startParallelFlow("pg2", "biz-p2");
        Task a1 = TestFlows.pendingTasks(instance.getId()).stream()
                .filter(t -> "a1".equals(t.getNodeCode())).findFirst().orElseThrow();
        harness.reset();

        TestFlows.pass(a1.getId(), TestFlows.HANDLER);

        // 汇聚等待：a1 归档、b1 仍在、实例不推进
        List<Task> pending = TestFlows.pendingTasks(instance.getId());
        assertEquals(Set.of("b1"), pending.stream().map(Task::getNodeCode).collect(Collectors.toSet()));
        Instance persisted = harness.insDao.raw(instance.getId());
        assertNotEquals("end", persisted.getNodeCode());
        assertEquals(FlowStatus.TOBESUBMIT.getKey(), persisted.getFlowStatus());
        assertEquals(2, harness.hisTaskDao.size(), "发起 + a1 归档");

        // 汇聚判定的定义图重查基线（R2 未接入，previousNodeList 走全量重查）：
        // 节点 ×3 = nowNode 单查 + combine 全量 + previousNodeList 全量重查；
        // 连线 ×2 = combine 全量 + previousNodeList 全量重查；另按节点编码查一次活动待办
        assertEquals(3, harness.daoLog.stream().filter(l -> l.startsWith("NodeMemDao.selectList")).count()
                , "汇聚路径定义图查询基线: " + harness.daoLog);
        assertEquals(2, harness.daoLog.stream().filter(l -> l.startsWith("SkipMemDao.selectList")).count()
                , "汇聚路径连线查询基线: " + harness.daoLog);
        assertEquals(1, harness.daoLog.stream()
                .filter(l -> l.startsWith("TaskMemDao.getByInsIdAndNodeCodes")).count());
    }

    @Test
    void join_lastBranch_completesFlow() {
        Instance instance = TestFlows.startParallelFlow("pg3", "biz-p3");
        Task a1 = TestFlows.pendingTasks(instance.getId()).stream()
                .filter(t -> "a1".equals(t.getNodeCode())).findFirst().orElseThrow();
        TestFlows.pass(a1.getId(), TestFlows.HANDLER);
        Task b1 = TestFlows.currentTask(instance.getId());

        TestFlows.pass(b1.getId(), TestFlows.HANDLER);

        assertEquals(0, harness.taskDao.size(), "末支汇合后无待办");
        assertEquals(0, harness.userDao.size());
        Instance persisted = harness.insDao.raw(instance.getId());
        assertEquals("end", persisted.getNodeCode());
        assertEquals(FlowStatus.FINISHED.getKey(), persisted.getFlowStatus());
        // 历史：发起 + a1 + b1 = 3
        assertEquals(3, harness.hisTaskDao.size());
    }
}
