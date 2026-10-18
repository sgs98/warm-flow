package org.dromara.warm.flow.core.timer;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.orm.dao.FlowEventWaitDao;
import org.dromara.warm.flow.core.service.EventWaitService;
import org.dromara.warm.flow.core.service.impl.EventWaitServiceImpl;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.dao.EventWaitMemDao;
import org.dromara.warm.flow.core.test.entity.TestEventWait;
import org.dromara.warm.flow.core.test.entity.TestInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置定时器底座特征测试（阶段 B 退出门禁）：
 * 到期扫描、CAS 唯一抢占、租约续期与过期恢复、退避重试与 DEAD、
 * 一次性删除与重复重排期、游标分页、释放抢占与引擎生命周期。
 *
 * @author may
 */
class TimerEngineCharacteristicTest {

    private static final long LEASE_MILLIS = 60_000L;

    private FlowTestHarness harness;
    private EventWaitMemDao eventWaitDao;
    private WarmFlow config;

    /**
     * 测试事件处理器：按 eventType 分派行为，可注入失败与延迟。
     */
    private final Map<String, Consumer<EventWait>> handlers = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> handled = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
        eventWaitDao = new EventWaitMemDao(TestEventWait::new, harness.daoLog);
        harness.register(FlowEventWaitDao.class, eventWaitDao);
        EventWaitServiceImpl eventWaitService = new EventWaitServiceImpl();
        eventWaitService.setDao(eventWaitDao);
        harness.register(EventWaitService.class, eventWaitService);
        harness.register(EventTaskHandler.class, this::handle);

        FlowEngine.setNewEventWait(TestEventWait::new);
        config = FlowEngine.getFlowConfig();
        config.setTimerEnabled(false);
        config.setTimerPoolSize(2);
        config.setTimerScanInterval(200L);
        config.setTimerBatchSize(10);
        config.setTimerMaxScanPages(10);
        config.setTimerLeaseSeconds(LEASE_MILLIS / 1000);
        config.setTimerMaxRetryCount(3);
    }

    @AfterEach
    void tearDown() {
        TimerEngine.getInstance().shutdown();
        config.setTimerEnabled(false);
        harness.close();
    }

    /**
     * 测试事件处理器入口：记录触发次数并按 eventType 分派行为。
     */
    private void handle(EventWait wait) {
        handled.computeIfAbsent(wait.getId() + ":" + wait.getEventType(),
            k -> new AtomicInteger()).incrementAndGet();
        Consumer<EventWait> behavior = handlers.get(wait.getEventType());
        if (behavior != null) {
            behavior.accept(wait);
        }
    }

    private TestEventWait newWaitingWait(Date dueAt, String triggerType, String triggerInterval) {
        return newWaitingWait(dueAt, triggerType, triggerInterval, null);
    }

    /**
     * 内存 DAO 保存的是快照副本，记录级字段（如 maxRetryCount）必须在保存前设置才会生效。
     */
    private TestEventWait newWaitingWait(Date dueAt, String triggerType, String triggerInterval,
                                         Integer maxRetryCount) {
        TestEventWait wait = new TestEventWait()
            .setEventType("TIMER_CATCH")
            .setTriggerType(triggerType)
            .setTriggerInterval(triggerInterval)
            .setStatus(EventCons.STATUS_WAITING)
            .setNextTriggerAt(dueAt)
            .setRepeatCount(0)
            .setRetryCount(0)
            .setMaxRetryCount(maxRetryCount)
            .setVersion(0);
        eventWaitDao.save(wait);
        return wait;
    }

    @Test
    void scannerPicksDueWaitingAndRetrySeparately() {
        Date now = new Date();
        newWaitingWait(new Date(now.getTime() - 1000), "AFTER", "PT1H");
        newRetryWait(new Date(now.getTime() - 1000));

        TimerScanner scanner = new TimerScanner(eventWaitDao, config);
        List<EventWait> due = scanner.scanBatch(now);

        assertEquals(2, due.size());
        assertTrue(due.stream().anyMatch(w -> EventCons.STATUS_WAITING.equals(w.getStatus())));
        assertTrue(due.stream().anyMatch(w -> EventCons.STATUS_RETRY.equals(w.getStatus())));
    }

    @Test
    void scannerSkipsNotDueRecords() {
        Date now = new Date();
        newWaitingWait(new Date(now.getTime() + 60_000), "AFTER", "PT1H");

        TimerScanner scanner = new TimerScanner(eventWaitDao, config);
        assertTrue(scanner.scanBatch(now).isEmpty());
    }

    @Test
    void scannerSkipsSuspendedInstanceButContinuesPaging() {
        Date now = new Date();
        TestInstance suspended = new TestInstance()
            .setActivityStatus(ActivityStatus.SUSPENDED.getKey());
        harness.insDao.save(suspended);
        TestEventWait suspendedWait = newWaitingWait(new Date(now.getTime() - 1000), "AFTER", "PT1H");
        suspendedWait.setInstanceId(suspended.getId());
        eventWaitDao.updateById(suspendedWait);
        TestEventWait activeWait = newWaitingWait(new Date(now.getTime() - 500), "AFTER", "PT1H");

        TimerScanner scanner = new TimerScanner(eventWaitDao, config);
        List<EventWait> due = scanner.scanBatch(now);

        assertEquals(1, due.size());
        assertEquals(activeWait.getId(), due.get(0).getId());
    }

    @Test
    void scannerCursorPaginationCoversAllRecords() {
        Date now = new Date();
        for (int i = 3; i >= 1; i--) {
            newWaitingWait(new Date(now.getTime() - i * 1000), "AFTER", "PT1H");
        }
        // 单页 2 条，迫使扫描跨页；轮次间用 claim 模拟真实调度（抢占后不再重复扫描）
        config.setTimerBatchSize(2);
        TimerScanner scanner = new TimerScanner(eventWaitDao, config);
        TimerClaim claim = new TimerClaim(eventWaitDao);

        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int round = 0; round < 5 && seen.size() < 3; round++) {
            for (EventWait wait : scanner.scanBatch(new Date())) {
                seen.add(wait.getId());
                assertTrue(claim.claim(wait, new Date()));
            }
        }
        assertEquals(3, seen.size());
    }

    @Test
    void claimOnlyOneWinner() {
        Date now = new Date();
        TestEventWait wait = newWaitingWait(new Date(now.getTime() - 1000), "AFTER", "PT1H");

        TimerClaim claim = new TimerClaim(eventWaitDao);
        assertTrue(claim.claim(wait, now));
        // 版本号已被抢占递增，同快照再次抢占失败
        assertFalse(claim.claim(wait, now));
        assertEquals(EventCons.STATUS_RUNNING, eventWaitDao.raw(wait.getId()).getStatus());
        assertEquals(1, eventWaitDao.raw(wait.getId()).getVersion());
    }

    @Test
    void claimRejectsNotDueRecord() {
        Date now = new Date();
        TestEventWait wait = newWaitingWait(new Date(now.getTime() + 60_000), "AFTER", "PT1H");

        assertFalse(new TimerClaim(eventWaitDao).claim(wait, now));
        assertEquals(EventCons.STATUS_WAITING, eventWaitDao.raw(wait.getId()).getStatus());
    }

    @Test
    void oneShotSuccessDeletesRecord() {
        Date now = new Date();
        TestEventWait wait = newWaitingWait(new Date(now.getTime() - 1000), "AFTER", "PT1H");
        assertTrue(new TimerClaim(eventWaitDao).claim(wait, now));

        new TimerExecutor(eventWaitDao, config).execute(wait, EventCons.STATUS_WAITING,
            wait.getNextTriggerAt(), null);

        assertNull(eventWaitDao.raw(wait.getId()));
        assertEquals(1, handled.get(wait.getId() + ":TIMER_CATCH").get());
    }

    @Test
    void repeatSuccessReschedulesAndIncrementsRepeatCount() {
        Date now = new Date();
        TestEventWait wait = newWaitingWait(new Date(now.getTime() - 1000), "FIXED", "PT1H");
        assertTrue(new TimerClaim(eventWaitDao).claim(wait, now));

        new TimerExecutor(eventWaitDao, config).execute(wait, EventCons.STATUS_WAITING,
            wait.getNextTriggerAt(), null);

        EventWait row = eventWaitDao.raw(wait.getId());
        assertEquals(EventCons.STATUS_WAITING, row.getStatus());
        assertEquals(1, row.getRepeatCount());
        assertEquals(2, row.getVersion());
        // FIXED 按上一次计划时间 + 间隔，无漂移
        assertEquals(new Date(wait.getNextTriggerAt().getTime() + 3_600_000L), row.getNextTriggerAt());
        assertNull(row.getLockOwner());
    }

    @Test
    void repeatStopsAfterMaxRepeatCount() {
        Date now = new Date();
        TestEventWait wait = newWaitingWait(new Date(now.getTime() - 1000), "FIXED", "PT1H");
        wait.setMaxRepeatCount(1);
        assertTrue(new TimerClaim(eventWaitDao).claim(wait, now));

        new TimerExecutor(eventWaitDao, config).execute(wait, EventCons.STATUS_WAITING,
            wait.getNextTriggerAt(), null);

        assertNull(eventWaitDao.raw(wait.getId()));
    }

    @Test
    void failureRetriesWithBackoffThenDead() {
        assertEquals(10_000L, EventWaitProcessor.backoffMillis(1));
        assertEquals(30_000L, EventWaitProcessor.backoffMillis(2));
        assertEquals(60_000L, EventWaitProcessor.backoffMillis(3));
        assertEquals(60_000L, EventWaitProcessor.backoffMillis(9));

        Date now = new Date();
        TestEventWait wait = newWaitingWait(new Date(now.getTime() - 1000), "AFTER", "PT1H");
        handlers.put("TIMER_CATCH", w -> {
            throw new IllegalStateException("业务处理失败");
        });
        TimerExecutor executor = new TimerExecutor(eventWaitDao, config);

        assertTrue(new TimerClaim(eventWaitDao).claim(wait, now));
        executor.execute(wait, EventCons.STATUS_WAITING, wait.getNextTriggerAt(), null);
        EventWait row = eventWaitDao.raw(wait.getId());
        assertEquals(EventCons.STATUS_RETRY, row.getStatus());
        assertEquals(1, row.getRetryCount());
        assertEquals("业务处理失败", row.getLastError());
        assertTrue(row.getRetryAt().getTime() > System.currentTimeMillis() + 5_000L);

        // 连续失败到第 3 次仍 RETRY，第 4 次转 DEAD
        for (int expectRetry = 2; expectRetry <= 3; expectRetry++) {
            // 模拟重试时间已到；快照须在 claim 前获取（执行器使用抢占前版本号）
            row.setRetryAt(new Date(System.currentTimeMillis() - 1000L));
            EventWait snapshot = eventWaitDao.selectById(wait.getId());
            assertTrue(new TimerClaim(eventWaitDao).claim(snapshot, new Date()));
            executor.execute(snapshot, EventCons.STATUS_RETRY, null, snapshot.getRetryAt());
            row = eventWaitDao.raw(wait.getId());
            assertEquals(EventCons.STATUS_RETRY, row.getStatus());
            assertEquals(expectRetry, row.getRetryCount());
        }
        row.setRetryAt(new Date(System.currentTimeMillis() - 1000L));
        EventWait deadSnapshot = eventWaitDao.selectById(wait.getId());
        assertTrue(new TimerClaim(eventWaitDao).claim(deadSnapshot, new Date()));
        executor.execute(deadSnapshot, EventCons.STATUS_RETRY, null, deadSnapshot.getRetryAt());
        row = eventWaitDao.raw(wait.getId());
        assertEquals(EventCons.STATUS_DEAD, row.getStatus());
        assertNull(row.getRetryAt());
        assertEquals("业务处理失败", row.getLastError());
    }

    @Test
    void recordLevelMaxRetryCountWinsOverConfig() {
        Date now = new Date();
        TestEventWait wait = newWaitingWait(new Date(now.getTime() - 1000), "AFTER", "PT1H", 1);
        handlers.put("TIMER_CATCH", w -> {
            throw new IllegalStateException("boom");
        });
        TimerExecutor executor = new TimerExecutor(eventWaitDao, config);

        assertTrue(new TimerClaim(eventWaitDao).claim(wait, now));
        executor.execute(wait, EventCons.STATUS_WAITING, wait.getNextTriggerAt(), null);
        EventWait row = eventWaitDao.raw(wait.getId());
        assertEquals(EventCons.STATUS_RETRY, row.getStatus());

        row.setRetryAt(new Date(System.currentTimeMillis() - 1000L));
        EventWait snapshot = eventWaitDao.selectById(wait.getId());
        assertTrue(new TimerClaim(eventWaitDao).claim(snapshot, new Date()));
        executor.execute(snapshot, EventCons.STATUS_RETRY, null, snapshot.getRetryAt());
        assertEquals(EventCons.STATUS_DEAD, eventWaitDao.raw(wait.getId()).getStatus());
    }

    @Test
    void leaseRecoveryConsumesRetryCountAndBlocksOldExecutor() {
        Date now = new Date();
        TestEventWait wait = newWaitingWait(new Date(now.getTime() - 1000), "AFTER", "PT1H");
        TimerClaim claim = new TimerClaim(eventWaitDao);

        assertTrue(claim.claim(wait, now));
        EventWait running = eventWaitDao.raw(wait.getId());
        Integer claimedVersion = running.getVersion();

        // 租约过期后可被恢复：递增 retry_count、清租约、版本号再递增
        Date afterLease = new Date(running.getLockUntil().getTime() + 1000L);
        List<EventWait> expired = eventWaitDao.selectExpired(afterLease, 10);
        assertEquals(1, expired.size());
        assertTrue(eventWaitDao.recoverExpired(wait.getId(), claimedVersion, 1,
            EventCons.STATUS_RETRY, new Date(afterLease.getTime() + 10_000L),
            afterLease, "租约过期"));
        EventWait recovered = eventWaitDao.raw(wait.getId());
        assertEquals(EventCons.STATUS_RETRY, recovered.getStatus());
        assertEquals(1, recovered.getRetryCount());
        assertNull(recovered.getLockOwner());

        // 旧执行器使用旧版本无法提交任何结果
        assertFalse(eventWaitDao.renewLease(wait.getId(), claimedVersion, TimerWorkerId.get(),
            new Date(), new Date(System.currentTimeMillis() + LEASE_MILLIS)));
        assertFalse(eventWaitDao.deleteCompleted(wait.getId(), claimedVersion, TimerWorkerId.get()));
        assertFalse(eventWaitDao.markRetry(wait.getId(), claimedVersion, TimerWorkerId.get(),
            new Date(), new Date(), "late"));
    }

    @Test
    void releaseClaimRestoresPreClaimStateWithoutRetryCost() {
        Date now = new Date();
        TestEventWait wait = newWaitingWait(new Date(now.getTime() - 1000), "AFTER", "PT1H");
        Date originalNext = wait.getNextTriggerAt();

        TimerClaim claim = new TimerClaim(eventWaitDao);
        assertTrue(claim.claim(wait, now));
        EventWait running = eventWaitDao.raw(wait.getId());
        Integer claimedVersion = running.getVersion();

        assertTrue(eventWaitDao.releaseClaim(wait.getId(), claimedVersion, TimerWorkerId.get(),
            EventCons.STATUS_WAITING, originalNext, null, new Date()));

        EventWait row = eventWaitDao.raw(wait.getId());
        assertEquals(EventCons.STATUS_WAITING, row.getStatus());
        assertEquals(originalNext, row.getNextTriggerAt());
        assertEquals(0, row.getRetryCount());
        assertNull(row.getLockOwner());
        assertNull(row.getLockUntil());
    }

    @Test
    void missingHandlerReleasesClaim() {
        Date now = new Date();
        TestEventWait wait = newWaitingWait(new Date(now.getTime() - 1000), "AFTER", "PT1H");
        // 以空注册表替换 Bean 查找，模拟宿主未注册 EventTaskHandler
        org.dromara.warm.flow.core.invoker.FrameInvoker.setBeanFunction(
            clazz -> new java.util.HashMap<Class<?>, Object>().get(clazz));

        assertTrue(new TimerClaim(eventWaitDao).claim(wait, now));
        new TimerExecutor(eventWaitDao, config).execute(wait, EventCons.STATUS_WAITING,
            wait.getNextTriggerAt(), null);

        EventWait row = eventWaitDao.raw(wait.getId());
        assertEquals(EventCons.STATUS_WAITING, row.getStatus());
        assertEquals(0, row.getRetryCount());
    }

    @Test
    void engineStartsAndStopsWithConfigSwitch() {
        config.setTimerEnabled(false);
        TimerEngine.getInstance().start();
        assertFalse(TimerEngine.getInstance().isStarted());

        config.setTimerEnabled(true);
        TimerEngine.getInstance().start();
        assertTrue(TimerEngine.getInstance().isStarted());

        TimerEngine.getInstance().shutdown();
        assertFalse(TimerEngine.getInstance().isStarted());
        // 幂等
        TimerEngine.getInstance().shutdown();
        assertFalse(TimerEngine.getInstance().isStarted());
    }

    @Test
    void engineExecutesDueRecordEndToEnd() throws Exception {
        config.setTimerEnabled(true);
        config.setTimerScanInterval(100L);
        Date now = new Date();
        TestEventWait wait = newWaitingWait(new Date(now.getTime() - 1000), "AFTER", "PT1H");

        try {
            TimerEngine.getInstance().start();
            long deadline = System.currentTimeMillis() + 5_000L;
            while (System.currentTimeMillis() < deadline) {
                if (eventWaitDao.raw(wait.getId()) == null
                    && handled.get(wait.getId() + ":TIMER_CATCH") != null) {
                    break;
                }
                Thread.sleep(50L);
            }
            assertEquals(1, handled.get(wait.getId() + ":TIMER_CATCH").get());
            assertNull(eventWaitDao.raw(wait.getId()));
        } finally {
            TimerEngine.getInstance().shutdown();
        }
    }

    @Test
    void tableMissingErrorStopsScanSoftly() {
        assertTrue(TimerEngine.isTableMissing(
            new RuntimeException("Table 'warm.flow_event_wait' doesn't exist")));
        assertTrue(TimerEngine.isTableMissing(
            new RuntimeException("ORA-00942: 表或视图不存在")));
        assertFalse(TimerEngine.isTableMissing(new RuntimeException("connection refused")));
    }

    /**
     * 构造一条 RETRY 状态的等待记录（retry_at 已到期）。
     */
    private TestEventWait newRetryWait(Date retryAt) {
        TestEventWait wait = new TestEventWait()
            .setEventType("TIMER_CATCH")
            .setTriggerType("AFTER")
            .setTriggerInterval("PT1H")
            .setStatus(EventCons.STATUS_RETRY)
            .setRetryAt(retryAt)
            .setRepeatCount(0)
            .setRetryCount(1)
            .setVersion(0);
        eventWaitDao.save(wait);
        return wait;
    }
}
