package org.dromara.warm.flow.core.timer;

import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.orm.dao.FlowEventWaitDao;
import org.dromara.warm.flow.core.service.event.EventConfig;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * 事件等待记录处理器：一条已抢占记录的执行、完成与失败处理。
 * <p>
 * 定时路径由 {@link TimerExecutor} 在独立线程池中调用，消息路径由消息触发入口
 * 在调用方线程中同步调用，二者共用同一套完成语义：成功后一次性记录删除、
 * 重复事件按策略重排期；失败按 10s / 30s / 60s 退避转 {@code RETRY}，超过上限转
 * {@code DEAD}；抢占后发现记录暂不可执行（{@link EventSkipException}）则释放租约
 * 恢复抢占前状态，不消耗 {@code retry_count}。所有完成类更新前先续租，无法续租时
 * 不提交结果，交由租约恢复流程处理（至少一次语义）。
 *
 * @author may
 */
public class EventWaitProcessor {

    private static final Logger log = LoggerFactory.getLogger(EventWaitProcessor.class);

    /**
     * 失败重试退避（毫秒）：第 1 次 10s、第 2 次 30s、第 3 次及以后 60s。
     */
    static final long[] RETRY_BACKOFF_MILLIS = {10_000L, 30_000L, 60_000L};

    /**
     * 事件等待记录 DAO。
     */
    private final FlowEventWaitDao<EventWait> dao;

    /**
     * 默认租约时长（毫秒）。
     */
    private final long leaseMillis;

    /**
     * 默认最大重试次数。
     */
    private final int defaultMaxRetryCount;

    /**
     * @param dao    事件等待记录 DAO
     * @param config 引擎配置
     */
    public EventWaitProcessor(FlowEventWaitDao<EventWait> dao, WarmFlow config) {
        this.dao = dao;
        this.leaseMillis = config != null && config.getTimerLeaseSeconds() != null
            ? config.getTimerLeaseSeconds() * 1000L : 60_000L;
        this.defaultMaxRetryCount = config != null && config.getTimerMaxRetryCount() != null
            ? config.getTimerMaxRetryCount() : 3;
    }

    /**
     * 执行单条已抢占的事件（无调用方上下文）。
     *
     * @param wait           已抢占的记录（版本号为抢占前版本，执行时使用版本号 + 1）
     * @param preClaimStatus 抢占前状态（WAITING / RETRY）
     * @param preClaimNext   抢占前的下次触发时间（WAITING 时非空）
     * @param preClaimRetry  抢占前的下次重试时间（RETRY 时非空）
     */
    public void process(EventWait wait, String preClaimStatus, Date preClaimNext, Date preClaimRetry) {
        process(wait, preClaimStatus, preClaimNext, preClaimRetry, null);
    }

    /**
     * 执行单条已抢占的事件，并透传调用方上下文。
     *
     * @param wait           已抢占的记录（版本号为抢占前版本，执行时使用版本号 + 1）
     * @param preClaimStatus 抢占前状态（WAITING / RETRY）
     * @param preClaimNext   抢占前的下次触发时间（WAITING 时非空）
     * @param preClaimRetry  抢占前的下次重试时间（RETRY 时非空）
     * @param context        调用方上下文（消息 / 错误触发携带操作人与租户）；定时调度路径为 {@code null}
     */
    public void process(EventWait wait, String preClaimStatus, Date preClaimNext, Date preClaimRetry
        , WorkflowContext context) {
        // 抢占条件更新已把版本号 +1，执行器必须使用本地递增后的新版本
        int version = nvl(wait.getVersion()) + 1;
        Long id = wait.getId();
        Date now = new Date();

        EventTaskHandler handler = FrameInvoker.getBean(EventTaskHandler.class);
        if (handler == null) {
            log.warn("未注册 EventTaskHandler，释放抢占并恢复等待记录 id={}", id);
            dao.releaseClaim(id, version, TimerWorkerId.get(), preClaimStatus,
                preClaimNext, preClaimRetry, now);
            return;
        }

        try {
            handler.handle(wait, context);
            complete(wait, version);
        } catch (EventSkipException ex) {
            log.info("事件记录暂不可执行，释放抢占并恢复等待记录 id={} reason={}", id, ex.getMessage());
            dao.releaseClaim(id, version, TimerWorkerId.get(), preClaimStatus,
                preClaimNext, preClaimRetry, new Date());
        } catch (Throwable ex) {
            log.warn("事件执行失败 id={} eventType={}", id, wait.getEventType(), ex);
            fail(wait, version, ex);
        }
    }

    /**
     * 执行成功后的完成处理：先续租校验，一次性记录删除、重复事件重排期。
     */
    private void complete(EventWait wait, int version) {
        Long id = wait.getId();
        Date now = new Date();
        if (!renewOrSkip(wait, version, now)) {
            return;
        }

        boolean repeat = isRepeatable(wait);
        Integer nextRepeat = repeat ? nvl(wait.getRepeatCount()) + 1 : null;
        if (repeat && !reachedMaxRepeat(wait, nextRepeat)) {
            Date nextTriggerAt = TimerScheduleStrategies.nextTriggerAt(wait.getTriggerType(),
                new TimerContext()
                    .setPreviousTriggerAt(wait.getNextTriggerAt())
                    .setCurrentTime(now)
                    .setTriggerInterval(wait.getTriggerInterval())
                    .setTimezone(wait.getTimezone())
                    .setRepeatCount(nextRepeat));
            boolean rescheduled = dao.reschedule(id, version, TimerWorkerId.get(), now,
                nextTriggerAt, nextRepeat);
            if (!rescheduled) {
                log.warn("重复事件重排期条件更新未命中 id={} version={}", id, version);
            }
            return;
        }
        boolean deleted = dao.deleteCompleted(id, version, TimerWorkerId.get());
        if (!deleted) {
            log.warn("一次性事件完成删除条件更新未命中 id={} version={}", id, version);
        }
    }

    /**
     * 执行失败后的重试处理：先续租校验，按退避转 RETRY、超上限转 DEAD。
     */
    private void fail(EventWait wait, int version, Throwable ex) {
        Long id = wait.getId();
        Date now = new Date();
        if (!renewOrSkip(wait, version, now)) {
            return;
        }
        String error = summarize(ex);
        int nextRetryCount = nvl(wait.getRetryCount()) + 1;
        int maxRetryCount = wait.getMaxRetryCount() != null
            ? wait.getMaxRetryCount() : defaultMaxRetryCount;
        if (nextRetryCount > maxRetryCount) {
            dao.markDead(id, version, TimerWorkerId.get(), now, error);
            log.error("事件等待记录已达最大重试次数，转为 DEAD id={} retryCount={} error={}",
                id, nextRetryCount - 1, error);
            return;
        }
        Date retryAt = new Date(now.getTime() + backoffMillis(nextRetryCount));
        dao.markRetry(id, version, TimerWorkerId.get(), now, retryAt, error);
    }

    /**
     * 完成类更新前的续租校验：无法续租说明租约已被恢复流程接管，
     * 本次执行不得提交任何结果，视为失败等待恢复重试。
     */
    private boolean renewOrSkip(EventWait wait, int version, Date now) {
        boolean renewed = dao.renewLease(wait.getId(), version, TimerWorkerId.get(), now,
            new Date(now.getTime() + leaseMillis));
        if (!renewed) {
            log.warn("续租失败，放弃提交执行结果，等待租约恢复重试 id={} version={}",
                wait.getId(), version);
        }
        return renewed;
    }

    /**
     * 是否为可重复事件：AFTER 为单次延迟，FIXED / POINT_IN_TIME 按策略重复触发。
     */
    private boolean isRepeatable(EventWait wait) {
        return !EventConfig.TIMER_AFTER.equals(wait.getTriggerType())
            && wait.getTriggerType() != null;
    }

    /**
     * 是否达到最大重复触发次数，达到后随完成处理一并删除。
     */
    private boolean reachedMaxRepeat(EventWait wait, Integer nextRepeat) {
        return wait.getMaxRepeatCount() != null && nextRepeat >= wait.getMaxRepeatCount();
    }

    /**
     * 按重试序号计算退避时长，超过预设计档位后取最后一档。
     */
    static long backoffMillis(int retryCount) {
        int index = Math.min(Math.max(retryCount, 1), RETRY_BACKOFF_MILLIS.length) - 1;
        return RETRY_BACKOFF_MILLIS[index];
    }

    /**
     * 异常摘要，截断到合理长度用于持久化 {@code last_error}。
     */
    static String summarize(Throwable ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private static int nvl(Integer value) {
        return value != null ? value : 0;
    }
}
