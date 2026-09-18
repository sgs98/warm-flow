package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 终止收尾（多待办场景）特征测试：锁定 terminateByInstanceId 按
 * 首个待办终止、其余待办经 handUndoneTask 静默清理（不转历史）的现状，
 * 以及无待办时的前置校验。
 *
 * @author warm
 */
class TerminateCleanupCharacteristicTest {

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
    void multiTaskTerminate_archivesActing_silentlyDropsRest() {
        Instance instance = TestFlows.startParallelFlow("tc1", "biz-t1");
        Task acting = TestFlows.pendingTasks(instance.getId()).get(0);
        Task rest = TestFlows.pendingTasks(instance.getId()).stream()
                .filter(t -> !Objects.equals(t.getId(), acting.getId())).findFirst().orElseThrow();

        FlowEngine.taskService().terminateByInstanceId(instance.getId(), TestFlows.context(TestFlows.HANDLER));

        // 全部待办与办理人清理，实例终止
        assertEquals(0, harness.taskDao.size());
        assertEquals(0, harness.userDao.size());
        Instance persisted = harness.insDao.raw(instance.getId());
        assertEquals(FlowStatus.TERMINATE.getKey(), persisted.getFlowStatus());
        assertEquals("end", persisted.getNodeCode());

        // 历史：发起 + 终止单条 = 2；其余待办静默删除、不转历史
        assertEquals(2, harness.hisTaskDao.size());
        assertTrue(harness.hisTaskDao.all().stream()
                        .noneMatch(h -> Objects.equals(h.getTaskId(), rest.getId())),
                "handUndoneTask 清理的待办不应产生历史: " + harness.hisTaskDao.all());
    }

    @Test
    void noPendingTask_throwsNotFoundTask() {
        Instance instance = TestFlows.startParallelFlow("tc2", "biz-t2");
        TestFlows.pendingTasks(instance.getId()).forEach(t -> harness.taskDao.removeRaw(t.getId()));

        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().terminateByInstanceId(instance.getId()
                        , TestFlows.context(TestFlows.HANDLER)));
        assertEquals(ExceptionCons.NOT_FOUNT_TASK, ex.getMessage());
    }
}
