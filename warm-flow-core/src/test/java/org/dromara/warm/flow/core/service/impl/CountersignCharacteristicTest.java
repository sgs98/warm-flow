package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.test.listener.RecordingListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会签（nodeRatio=100）特征测试：锁定首票暂存（签票历史+办理人剔除+不流转、
 * 监听器只到 start）、末票流转、会签驳回立即生效并清退其余办理人。
 *
 * @author warm
 */
class CountersignCharacteristicTest {

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    /** 发起并推进到 apply 会签节点，返回该待办 */
    private Task atApply(String flowCode, String businessId) {
        Instance instance = TestFlows.startCooperateFlow(flowCode, "100"
                , TestFlows.HANDLER + "@@lisi", businessId);
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        return TestFlows.currentTask(instance.getId());
    }

    @Test
    void firstVote_holdsTask_archivesSignHis() {
        Task task = atApply("cs1", "biz-c1");
        Instance instance = harness.insDao.raw(task.getInstanceId());
        harness.reset();

        TestFlows.pass(task.getId(), TestFlows.HANDLER);

        // 任务保留（会签未满不流转），办理人剔除当前签票人
        assertNotNull(harness.taskDao.raw(task.getId()), "会签首票后任务应保留");
        assertEquals("apply", harness.taskDao.raw(task.getId()).getNodeCode());
        List<String> processedBys = harness.userDao.all().stream().map(u -> u.getProcessedBy()).toList();
        assertEquals(List.of("lisi"), processedBys, "签票人应被剔除: " + processedBys);

        // 签票历史：COUNTERSIGN + PASS，flowStatus 为 PASS
        assertEquals(1, harness.hisTaskDao.all().stream()
                .filter(h -> Objects.equals(h.getTaskId(), task.getId()))
                .filter(h -> CooperateType.COUNTERSIGN.getKey().equals(h.getCooperateType())).count());
        assertEquals(1, harness.hisTaskDao.all().stream()
                .filter(h -> Objects.equals(h.getTaskId(), task.getId()))
                .filter(h -> SkipType.PASS.getKey().equals(h.getSkipType())).count());

        // 短路：只触发 start 监听器，assignment/finish/create 不触发
        List<String> nodeEvents = RecordingListener.EVENTS.stream()
                .filter(e -> !e.startsWith("g:")).map(e -> e.substring(0, e.indexOf('{'))).toList();
        assertEquals(List.of("start"), nodeEvents, "会签暂存应短路在 start 之后: " + nodeEvents);

        // 实例未被推进
        assertEquals("apply", instance.getNodeCode());
        assertEquals(FlowStatus.APPROVAL.getKey(), instance.getFlowStatus());
    }

    @Test
    void lastVote_flowsToEnd() {
        Task task = atApply("cs2", "biz-c2");
        Instance instance = harness.insDao.raw(task.getInstanceId());
        TestFlows.pass(task.getId(), TestFlows.HANDLER);

        TestFlows.pass(task.getId(), "lisi");

        assertEquals(0, harness.taskDao.size(), "末票后任务应流转删除");
        assertEquals(0, harness.userDao.size(), "任务删除后办理人应清理");
        assertEquals("end", instance.getNodeCode());
        assertEquals(FlowStatus.FINISHED.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
        // 历史：start 发起 + draft 办理 + 首票签票 + 末票归档 = 4
        assertEquals(4, harness.hisTaskDao.size());
        assertEquals(1, harness.hisTaskDao.all().stream()
                .filter(h -> CooperateType.COUNTERSIGN.getKey().equals(h.getCooperateType())).count());
    }

    @Test
    void countersignReject_flowsImmediately_dropsRestUsers() {
        Task task = atApply("cs3", "biz-c3");
        Instance instance = harness.insDao.raw(task.getInstanceId());
        int hisBefore = harness.hisTaskDao.size();
        harness.reset();

        TestFlows.reject(task.getId(), TestFlows.HANDLER);

        // 会签驳回立即生效：退回 draft，其余办理人直接清退，新待办携带新办理人
        List<Task> pending = TestFlows.pendingTasks(instance.getId());
        assertEquals(1, pending.size());
        assertEquals("draft", pending.get(0).getNodeCode());
        assertEquals(FlowStatus.REJECT.getKey(), pending.get(0).getFlowStatus());
        assertEquals(1, harness.userDao.size(), "仅新 draft 待办的办理人保留，原任务办理人随删");
        assertEquals(TestFlows.HANDLER, harness.userDao.all().get(0).getProcessedBy());
        assertEquals("draft", harness.insDao.raw(instance.getId()).getNodeCode());
        assertEquals(FlowStatus.REJECT.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());

        // 驳回动作只产生流转历史，不产生签票历史
        assertEquals(hisBefore + 1, harness.hisTaskDao.size());
        assertEquals(0, harness.hisTaskDao.all().stream()
                .filter(h -> CooperateType.COUNTERSIGN.getKey().equals(h.getCooperateType())).count());
        assertEquals(1, harness.hisTaskDao.all().stream()
                .filter(h -> Objects.equals(h.getTaskId(), task.getId()))
                .filter(h -> SkipType.REJECT.getKey().equals(h.getSkipType())).count());
    }
}
