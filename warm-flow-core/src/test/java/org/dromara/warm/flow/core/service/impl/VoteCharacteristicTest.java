package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 票签特征测试：锁定通过率（50%）与固定通过人数（passCount=2）两种规则下
 * 的暂存/流转分界，以及未投办理人的静默清退。
 *
 * @author warm
 */
class VoteCharacteristicTest {

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
    void passRatio50_flowsOnSecondOfThree_dropsNonVoters() {
        Instance instance = TestFlows.startCooperateFlow("vt1", "50"
                , TestFlows.HANDLER + "@@lisi@@wangwu", "biz-v1");
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        Task task = TestFlows.currentTask(instance.getId());

        // 第一票 1/3 = 33% < 50%：暂存
        TestFlows.pass(task.getId(), TestFlows.HANDLER);
        assertNotNull(harness.taskDao.raw(task.getId()), "未达通过率任务应保留");
        assertEquals(2, harness.userDao.size(), "签票人剔除后应剩 2 人");

        // 第二票 2/3 = 67% >= 50%：流转，第三票人静默清退
        TestFlows.pass(task.getId(), "lisi");

        assertEquals(0, harness.taskDao.size());
        assertEquals(0, harness.userDao.size(), "wangwu 应随流转静默清退");
        assertEquals(FlowStatus.FINISHED.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
        assertEquals(1, harness.hisTaskDao.all().stream()
                .filter(h -> CooperateType.VOTE.getKey().equals(h.getCooperateType())).count());
    }

    @Test
    void passCount2_flowsOnSecondVote() {
        Instance instance = TestFlows.startCooperateFlow("vt2", "passCount=2"
                , TestFlows.HANDLER + "@@lisi", "biz-v2");
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        Task task = TestFlows.currentTask(instance.getId());

        // 第一票不满足 passCount=2：暂存
        TestFlows.pass(task.getId(), TestFlows.HANDLER);
        assertNotNull(harness.taskDao.raw(task.getId()), "固定人数未达任务应保留");

        // 第二票达到 passCount=2：流转
        TestFlows.pass(task.getId(), "lisi");

        assertEquals(0, harness.taskDao.size());
        assertEquals(FlowStatus.FINISHED.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
    }
}
