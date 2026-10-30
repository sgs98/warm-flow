package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.entity.TestInstance;
import org.dromara.warm.flow.core.test.entity.TestTask;
import org.dromara.warm.flow.core.timer.TimerScanner;
import org.dromara.warm.flow.core.workflow.command.ReviveEventCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 等待记录复活特征测试：只允许 {@code DEAD → WAITING}，复活前校验实例 / 附着待办仍然有效，
 * 复活后重试计数归零、租约清空、定时类事件重算下次触发时间，条件更新按版本号防并发。
 *
 * @author may
 */
class EventWaitReviveCharacteristicTest {

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
        WarmFlow config = FlowEngine.getFlowConfig();
        config.setTimerEnabled(false);
    }

    @AfterEach
    void tearDown() {
        FlowEngine.getFlowConfig().setTimerEnabled(false);
        harness.close();
    }

    @Test
    void revivedTimerCatchResetsCountersAndRecomputesNextTriggerAt() {
        TestInstance instance = activeInstance(null);
        EventWait wait = deadWait(instance.getId(), null, EventType.TIMER_CATCH, "FIXED", "PT1H");
        Date before = new Date();

        assertTrue(FlowEngine.eventWaitService().revive(wait.getId()));

        EventWait row = harness.eventWaitDao.raw(wait.getId());
        assertEquals(EventCons.STATUS_WAITING, row.getStatus());
        assertEquals(0, row.getRetryCount().intValue(), "复活后重试计数应归零");
        assertNull(row.getRetryAt(), "复活后重试时间应清空");
        assertNull(row.getLockOwner(), "复活后租约持有者应清空");
        assertNull(row.getLockUntil(), "复活后租约到期时间应清空");
        assertEquals("重试耗尽", row.getLastError(), "保留最后一次死亡原因，便于追溯");
        assertTrue(row.getNextTriggerAt().after(before), "定时类事件应从当前时间重算下次触发时间");
        assertEquals(wait.getVersion() + 1, row.getVersion().intValue(), "条件更新后版本号递增");
    }

    @Test
    void revivedMessageWaitKeepsNextTriggerAtNull() {
        TestInstance instance = activeInstance(null);
        EventWait wait = deadWait(instance.getId(), null, EventType.MESSAGE_CATCH, null, null);

        assertTrue(FlowEngine.eventWaitService().revive(wait.getId()));

        EventWait row = harness.eventWaitDao.raw(wait.getId());
        assertEquals(EventCons.STATUS_WAITING, row.getStatus());
        assertNull(row.getNextTriggerAt(), "消息类事件不参与扫描，下次触发时间保持为空");
    }

    @Test
    void reviveRejectsRecordsThatAreNotDead() {
        TestInstance instance = activeInstance(null);
        for (String status : List.of(EventCons.STATUS_WAITING, EventCons.STATUS_RETRY,
            EventCons.STATUS_RUNNING, EventCons.STATUS_CANCELLED)) {
            EventWait wait = wait(instance.getId(), null, EventType.TIMER_CATCH, "AFTER", "PT1H", status);
            FlowException ex = assertThrows(FlowException.class
                , () -> FlowEngine.eventWaitService().revive(wait.getId()));
            assertEquals(ExceptionCons.EVENT_WAIT_REVIVE_NOT_DEAD, ex.getMessage());
        }
    }

    @Test
    void reviveRejectsTimerStartWait() {
        EventWait wait = deadWait(null, null, EventType.TIMER_START, "FIXED", "PT1H");

        FlowException ex = assertThrows(FlowException.class
            , () -> FlowEngine.eventWaitService().revive(wait.getId()));

        assertEquals(ExceptionCons.EVENT_WAIT_REVIVE_TIMER_START, ex.getMessage());
        assertEquals(EventCons.STATUS_DEAD, harness.eventWaitDao.raw(wait.getId()).getStatus());
    }

    @Test
    void reviveRejectsBoundaryWaitWithoutAttachedTask() {
        TestInstance instance = activeInstance(null);
        EventWait wait = deadWait(instance.getId(), 888_888L, EventType.TIMER_BOUNDARY, "AFTER", "PT1H");

        FlowException ex = assertThrows(FlowException.class
            , () -> FlowEngine.eventWaitService().revive(wait.getId()));

        assertEquals(ExceptionCons.EVENT_WAIT_REVIVE_TASK_MISSING, ex.getMessage());
    }

    @Test
    void reviveSucceedsForBoundaryWaitWithLiveTask() {
        TestInstance instance = activeInstance(null);
        TestTask task = new TestTask();
        task.setInstanceId(instance.getId());
        task.setNodeCode("n1");
        harness.taskDao.save(task);
        EventWait wait = deadWait(instance.getId(), task.getId(), EventType.TIMER_BOUNDARY, "AFTER", "PT1H");

        assertTrue(FlowEngine.eventWaitService().revive(wait.getId()));
        assertEquals(EventCons.STATUS_WAITING, harness.eventWaitDao.raw(wait.getId()).getStatus());
    }

    @Test
    void reviveRejectsInstanceThatAlreadyPassedTheEventNode() {
        TestInstance instance = activeInstance("{\"nodeList\":[{\"nodeCode\":\"n1\",\"status\":2}]}");
        EventWait wait = deadWait(instance.getId(), null, EventType.TIMER_CATCH, "AFTER", "PT1H");

        FlowException ex = assertThrows(FlowException.class
            , () -> FlowEngine.eventWaitService().revive(wait.getId()));

        assertEquals(ExceptionCons.EVENT_WAIT_REVIVE_NODE_PASSED, ex.getMessage());
    }

    @Test
    void reviveRejectsSuspendedOrMissingInstance() {
        TestInstance suspended = new TestInstance()
            .setActivityStatus(ActivityStatus.SUSPENDED.getKey());
        harness.insDao.save(suspended);
        EventWait suspendedWait = deadWait(suspended.getId(), null, EventType.TIMER_CATCH, "AFTER", "PT1H");
        EventWait orphanWait = deadWait(777_777L, null, EventType.TIMER_CATCH, "AFTER", "PT1H");

        FlowException suspendedEx = assertThrows(FlowException.class
            , () -> FlowEngine.eventWaitService().revive(suspendedWait.getId()));
        FlowException orphanEx = assertThrows(FlowException.class
            , () -> FlowEngine.eventWaitService().revive(orphanWait.getId()));

        assertEquals(ExceptionCons.EVENT_WAIT_REVIVE_INSTANCE_UNAVAILABLE, suspendedEx.getMessage());
        assertEquals(ExceptionCons.EVENT_WAIT_REVIVE_INSTANCE_UNAVAILABLE, orphanEx.getMessage());
    }

    @Test
    void reviveConditionalUpdateRequiresMatchingVersion() {
        TestInstance instance = activeInstance(null);
        EventWait wait = deadWait(instance.getId(), null, EventType.TIMER_CATCH, "FIXED", "PT1H");
        Date now = new Date();

        // 版本号失配（并发复活 / 其它写入推进了版本）时条件更新不命中，记录保持 DEAD
        assertFalse(harness.eventWaitDao.revive(wait.getId(), wait.getVersion() - 1, now, now));
        assertEquals(EventCons.STATUS_DEAD, harness.eventWaitDao.raw(wait.getId()).getStatus());

        assertTrue(harness.eventWaitDao.revive(wait.getId(), wait.getVersion(), now, now));
        EventWait row = harness.eventWaitDao.raw(wait.getId());
        assertEquals(EventCons.STATUS_WAITING, row.getStatus());
        // 复活成功后再按同一版本复活一次同样不命中，避免重复生效
        assertFalse(harness.eventWaitDao.revive(wait.getId(), wait.getVersion(), now, now));
    }

    @Test
    void facadeRevivesDeadRecordAndReturnsInstance() {
        TestInstance instance = activeInstance(null);
        EventWait wait = deadWait(instance.getId(), null, EventType.TIMER_CATCH, "FIXED", "PT1H");
        ReviveEventCommand command = new ReviveEventCommand();
        command.setEventWaitId(wait.getId());

        assertDoesNotThrow(() -> FlowEngine.workflow().reviveEvent(command));
        assertEquals(EventCons.STATUS_WAITING, harness.eventWaitDao.raw(wait.getId()).getStatus());
    }

    @Test
    void revivedRecordIsDueAgainForScanner() {
        TestInstance instance = activeInstance(null);
        EventWait wait = deadWait(instance.getId(), null, EventType.TIMER_CATCH, "FIXED", "PT1M");
        FlowEngine.eventWaitService().revive(wait.getId());

        // 复活后按策略重算为「当前时间 + 间隔」，把扫描时钟推到间隔之后应重新被扫到
        Date later = new Date(System.currentTimeMillis() + 120_000L);
        List<EventWait> due = new TimerScanner(harness.eventWaitDao, FlowEngine.getFlowConfig())
            .scanBatch(later);

        assertEquals(List.of(wait.getId()), due.stream().map(EventWait::getId).toList());
    }

    /**
     * 构造一条已死记录：预置重试计数、重试时间、租约与失败原因，验证复活后的清理效果。
     */
    private EventWait deadWait(Long instanceId, Long attachedTaskId, EventType eventType
        , String triggerType, String triggerInterval) {
        return wait(instanceId, attachedTaskId, eventType, triggerType, triggerInterval, EventCons.STATUS_DEAD);
    }

    private EventWait wait(Long instanceId, Long attachedTaskId, EventType eventType
        , String triggerType, String triggerInterval, String status) {
        EventWait row = FlowEngine.newEventWait()
            .setDefinitionId(1L)
            .setInstanceId(instanceId)
            .setAttachedTaskId(attachedTaskId)
            .setNodeCode("n1")
            .setEventType(eventType.getValue())
            .setStatus(status)
            .setTriggerType(triggerType)
            .setTriggerInterval(triggerInterval)
            .setNextTriggerAt(triggerType == null ? null : new Date(System.currentTimeMillis() - 3_600_000L))
            .setRepeatCount(0)
            .setRetryCount(3)
            .setRetryAt(EventCons.STATUS_DEAD.equals(status) ? null : new Date())
            .setLastError(EventCons.STATUS_DEAD.equals(status) ? "重试耗尽" : null)
            .setMaxRetryCount(3)
            .setVersion(7)
            .setLockOwner(EventCons.STATUS_DEAD.equals(status) ? null : "timer-worker")
            .setLockUntil(EventCons.STATUS_DEAD.equals(status) ? null
                : new Date(System.currentTimeMillis() + 60_000L));
        harness.eventWaitDao.save(row);
        return row;
    }

    private TestInstance activeInstance(String defJson) {
        TestInstance instance = new TestInstance()
            .setActivityStatus(ActivityStatus.ACTIVITY.getKey())
            .setDefJson(defJson);
        harness.insDao.save(instance);
        return instance;
    }
}
