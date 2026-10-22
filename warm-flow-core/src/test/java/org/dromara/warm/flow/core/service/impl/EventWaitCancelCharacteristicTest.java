package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 等待记录条件取消语义特征测试：范围取消按状态集合（WAITING / RETRY / RUNNING）过滤并
 * 递增版本号、清空租约字段，终态记录不被触碰；定义级取消不波及实例级记录，
 * 任务级取消按附着任务精确圈定。在途执行器持有旧版本号，条件递增后必然提交失配。
 *
 * @author may
 */
class EventWaitCancelCharacteristicTest {

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
    void cancelByInstanceIdCoversWaitingRetryRunningOnly() {
        EventWait waiting = wait(9001L, 100L, null, EventCons.STATUS_WAITING, 3);
        EventWait retry = wait(9002L, 100L, null, EventCons.STATUS_RETRY, 2);
        EventWait running = wait(9003L, 100L, null, EventCons.STATUS_RUNNING, 5);
        EventWait success = wait(9004L, 100L, null, EventCons.STATUS_SUCCESS, 7);
        EventWait cancelled = wait(9005L, 100L, null, EventCons.STATUS_CANCELLED, 9);
        EventWait otherInstance = wait(9006L, 200L, null, EventCons.STATUS_WAITING, 1);

        FlowEngine.eventWaitService().cancelByInstanceIds(List.of(100L));

        assertCancelled(waiting, 4);
        assertCancelled(retry, 3);
        assertCancelled(running, 6);
        assertUntouched(success, EventCons.STATUS_SUCCESS);
        assertUntouched(cancelled, EventCons.STATUS_CANCELLED);
        assertUntouched(otherInstance, EventCons.STATUS_WAITING);
    }

    @Test
    void cancelByDefinitionIdOnlyCoversInstanceLessRows() {
        EventWait defLevel = wait(300L, null, null, EventCons.STATUS_WAITING, 0);
        EventWait instanceLevel = wait(300L, 400L, null, EventCons.STATUS_WAITING, 0);
        EventWait otherDef = wait(301L, null, null, EventCons.STATUS_WAITING, 0);

        FlowEngine.eventWaitService().cancelByDefinitionId(300L);

        assertCancelled(defLevel, 1);
        assertUntouched(instanceLevel, EventCons.STATUS_WAITING);
        assertUntouched(otherDef, EventCons.STATUS_WAITING);
    }

    @Test
    void cancelByTaskIdsScopeToAttachedTask() {
        EventWait attached = wait(500L, 600L, 5000L, EventCons.STATUS_WAITING, 0);
        EventWait attachedTerminal = wait(500L, 600L, 5000L, EventCons.STATUS_SUCCESS, 4);
        EventWait otherTask = wait(500L, 601L, 5001L, EventCons.STATUS_WAITING, 0);

        FlowEngine.eventWaitService().cancelByTaskIds(List.of(5000L));

        assertCancelled(attached, 1);
        assertUntouched(attachedTerminal, EventCons.STATUS_SUCCESS);
        assertUntouched(otherTask, EventCons.STATUS_WAITING);
    }

    /**
     * 构造并保存一条等待记录：租约字段预置为已抢占形态，验证取消时被清空。
     */
    private EventWait wait(Long definitionId, Long instanceId, Long attachedTaskId, String status
        , int version) {
        EventWait row = FlowEngine.newEventWait()
            .setDefinitionId(definitionId)
            .setInstanceId(instanceId)
            .setAttachedTaskId(attachedTaskId)
            .setNodeCode("n1")
            .setEventType(EventType.TIMER_CATCH.getValue())
            .setStatus(status)
            .setTriggerType("AFTER")
            .setTriggerInterval("PT1H")
            .setNextTriggerAt(new Date(System.currentTimeMillis() + 3_600_000L))
            .setRepeatCount(0)
            .setRetryCount(0)
            .setVersion(version)
            .setLockOwner("timer-worker")
            .setLockUntil(new Date(System.currentTimeMillis() + 60_000L));
        harness.eventWaitDao.save(row);
        return row;
    }

    private void assertCancelled(EventWait saved, int expectedVersion) {
        EventWait row = harness.eventWaitDao.raw(saved.getId());
        assertEquals(EventCons.STATUS_CANCELLED, row.getStatus());
        assertEquals(expectedVersion, row.getVersion().intValue());
        assertNull(row.getLockOwner());
        assertNull(row.getLockUntil());
    }

    private void assertUntouched(EventWait saved, String expectedStatus) {
        EventWait row = harness.eventWaitDao.raw(saved.getId());
        assertEquals(expectedStatus, row.getStatus());
        assertEquals(saved.getVersion().intValue(), row.getVersion().intValue());
    }
}
