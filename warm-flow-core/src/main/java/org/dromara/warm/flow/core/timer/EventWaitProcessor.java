package org.dromara.warm.flow.core.timer;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.orm.dao.FlowEventWaitDao;
import org.dromara.warm.flow.core.service.event.EventConfig;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 事件等待记录处理器：一条已抢占记录的执行、完成与失败处理。
 * <p>
 * 定时路径由 {@link TimerExecutor} 在独立线程池中调用，消息路径由消息触发入口
 * 在调用方线程中同步调用，二者共用同一套完成语义：成功后一次性记录删除、
 * 重复事件按策略重排期；失败按 10s / 30s / 60s 退避转 {@code RETRY}，超过上限转
 * {@code DEAD}；抢占后发现记录暂不可执行（{@link EventSkipException}）则释放租约
 * 恢复抢占前状态，不消耗 {@code retry_count}。所有完成类更新前先续租，无法续租时
 * 不提交结果，交由租约恢复流程处理（至少一次语义）。
 * <p>
 * 执行期间按 {@code timerLeaseRenewInterval} 周期续租（续租看门狗），避免长流程
 * 推进超过租约时长后被恢复流程误回收；看门狗续租失败不中断执行，由完成前的
 * 续租校验决定是否放弃提交结果。
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
     * 续租看门狗调度线程：懒创建守护线程，定时与消息两条路径共用；
     * {@code TimerEngine.shutdown()} 时显式关闭，之后再次执行事件会按需重建。
     */
    private static volatile ScheduledExecutorService renewalScheduler;

    private static final ThreadLocal<Long> CURRENT_WAIT_ID = new ThreadLocal<>();

    /** 当前线程正在处理的等待记录，用于范围取消时排除自身。 */
    public static Long currentWaitId() {
        return CURRENT_WAIT_ID.get();
    }

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
     * 续租看门狗间隔（毫秒）；非正数或大于等于租约时长时不启用。
     */
    private final long renewIntervalMillis;

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
        this.renewIntervalMillis = config != null && config.getTimerLeaseRenewInterval() != null
            ? config.getTimerLeaseRenewInterval() : 20_000L;
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
            Long previousWaitId = CURRENT_WAIT_ID.get();
            CURRENT_WAIT_ID.set(id);
            ScheduledFuture<?> renewal = scheduleLeaseRenewal(wait, version);
            try {
                if (!renewOrSkip(wait, version, new Date())) {
                    throw new EventLeaseLostException("执行前租约已丢失 id=" + id + " version=" + version);
                }
                // 推进与完成收进同一次 handle 调用：带事务的实现把两段包进同一事务（设计 §6.6），
                // 完成阶段租约丢失抛 EventLeaseLostException 连推进结果一并回滚；
                // 默认实现先后执行，无事务路径无法回滚，交由租约恢复流程重放
                handler.handle(wait, context, () -> complete(wait, version));
            } finally {
                if (renewal != null) {
                    renewal.cancel(false);
                }
                if (previousWaitId == null) {
                    CURRENT_WAIT_ID.remove();
                } else {
                    CURRENT_WAIT_ID.set(previousWaitId);
                }
            }
        } catch (EventLeaseLostException ex) {
            // 租约已被恢复流程接管：不提交结果、不计失败、不覆盖接管方写入的状态
            log.info("执行期间租约丢失，放弃本次结果，等待租约恢复重放 id={} version={}", id, version);
        } catch (EventCancelException ex) {
            boolean cancelled = dao.cancelClaimed(id, version, TimerWorkerId.get(), new Date(), ex.getMessage());
            if (!cancelled) {
                log.info("失效等待记录已由其它执行器接管 id={} version={}", id, version);
            }
        } catch (EventSkipException ex) {
            log.info("事件记录暂不可执行，释放抢占并恢复等待记录 id={} reason={}", id, ex.getMessage());
            releaseClaim(wait, preClaimStatus, preClaimNext, preClaimRetry);
        } catch (Throwable ex) {
            log.warn("事件执行失败 id={} eventType={}", id, wait.getEventType(), ex);
            fail(wait, version, ex);
        }
    }

    /**
     * 恢复租约过期的执行中记录（由扫描周期调用）。
     * <p>
     * 租约过期视为一次执行失败：递增 {@code retry_count} 与 {@code version}、清空旧租约，
     * 按退避转 {@code RETRY}，超过上限转 {@code DEAD}（保留失败原因）。恢复成功后，
     * 旧执行器即使随后返回，也无法通过旧版本提交结果。
     *
     * @param wait 租约过期的记录（status = RUNNING 且 lock_until 已过期）
     * @param now  当前时间
     */
    public void recoverExpired(EventWait wait, Date now) {
        Long id = wait.getId();
        int version = nvl(wait.getVersion());
        String workerId = wait.getLockOwner();
        String error = "租约过期：执行器 " + (workerId != null ? workerId : "unknown")
            + " 未在租约期内提交结果";
        int nextRetryCount = nvl(wait.getRetryCount()) + 1;
        int maxRetryCount = wait.getMaxRetryCount() != null
            ? wait.getMaxRetryCount() : defaultMaxRetryCount;
        boolean dead = nextRetryCount > maxRetryCount;
        Date retryAt = dead ? null : new Date(now.getTime() + backoffMillis(nextRetryCount));
        boolean recovered = dao.recoverExpired(id, version, nextRetryCount,
            dead ? EventCons.STATUS_DEAD : EventCons.STATUS_RETRY, retryAt, now, error);
        if (recovered) {
            log.warn("租约过期记录已恢复 id={} 转为 {} retryCount={} worker={}", id,
                dead ? EventCons.STATUS_DEAD : EventCons.STATUS_RETRY, nextRetryCount, workerId);
        }
        // 未命中说明已被其它节点恢复或已完成，无需处理
    }

    /**
     * 释放本节点持有的抢占，恢复抢占前状态（供调度底座拒绝投递等场景复用）。
     *
     * @param wait           已抢占的记录（版本号为抢占前版本）
     * @param preClaimStatus 抢占前状态（WAITING / RETRY）
     * @param preClaimNext   抢占前的下次触发时间
     * @param preClaimRetry  抢占前的下次重试时间
     */
    void releaseClaim(EventWait wait, String preClaimStatus, Date preClaimNext, Date preClaimRetry) {
        dao.releaseClaim(wait.getId(), nvl(wait.getVersion()) + 1, TimerWorkerId.get(),
            preClaimStatus, preClaimNext, preClaimRetry, new Date());
    }

    /**
     * 执行期间按 {@code timerLeaseRenewInterval} 周期续租的看门狗。
     * <p>
     * 间隔配置非正数或不小于租约时长时不启用（完成前的续租校验仍兜底）；
     * 续租失败只告警不中断执行，是否提交结果由完成前的续租校验决定。
     *
     * @param wait    已抢占的记录
     * @param version 抢占后的新版本号
     * @return 看门狗任务句柄；未启用时为 {@code null}
     */
    ScheduledFuture<?> scheduleLeaseRenewal(EventWait wait, int version) {
        if (renewIntervalMillis <= 0 || renewIntervalMillis >= leaseMillis) {
            return null;
        }
        Long id = wait.getId();
        String workerId = TimerWorkerId.get();
        try {
            return renewalScheduler().scheduleWithFixedDelay(() -> {
                try {
                    Date now = new Date();
                    boolean renewed = dao.renewLease(id, version, workerId, now,
                        new Date(now.getTime() + leaseMillis));
                    if (!renewed) {
                        log.warn("执行期间续租失败，租约可能已被恢复流程接管 id={} version={}", id, version);
                    }
                } catch (Exception ex) {
                    log.warn("执行期间续租异常 id={}", id, ex);
                }
            }, renewIntervalMillis, renewIntervalMillis, TimeUnit.MILLISECONDS);
        } catch (Throwable ex) {
            // 调度器不可用时降级为无看门狗，不影响本次执行
            log.debug("续租看门狗提交失败，跳过周期续租 id={}", id, ex);
            return null;
        }
    }

    /**
     * 懒创建续租看门狗调度线程（守护线程，定时 / 消息两条路径共用）。
     */
    private static synchronized ScheduledExecutorService renewalScheduler() {
        if (renewalScheduler == null || renewalScheduler.isShutdown()) {
            WarmFlow config = FlowEngine.getFlowConfig();
            int poolSize = config != null && config.getTimerPoolSize() != null
                ? config.getTimerPoolSize() : 2;
            renewalScheduler = Executors.newScheduledThreadPool(Math.max(2, Math.min(poolSize, 8)), r -> {
                Thread thread = new Thread(r, "warm-flow-lease-renewal");
                thread.setDaemon(true);
                return thread;
            });
        }
        return renewalScheduler;
    }

    /**
     * 关闭续租看门狗调度线程（{@code TimerEngine.shutdown()} 调用，幂等）。
     * <p>
     * 关闭后若消息路径再次执行事件，看门狗会按需重建，避免应用重载时线程泄漏。
     */
    static synchronized void shutdownRenewalScheduler() {
        if (renewalScheduler != null) {
            renewalScheduler.shutdownNow();
            renewalScheduler = null;
        }
    }

    /**
     * 执行成功后的完成处理：先续租校验，一次性记录删除、重复事件重排期。
     * <p>
     * 续租失败（租约已被恢复流程接管）时抛 {@link EventLeaseLostException}：
     * 带事务的调用方随异常回滚整段推进（设计 §6.5「续租失败不得提交结果」）；
     * 无事务路径无法回滚，记录保持 {@code RUNNING}，由租约恢复流程重放。
     */
    private void complete(EventWait wait, int version) {
        Long id = wait.getId();
        Date now = new Date();
        if (!renewOrSkip(wait, version, now)) {
            throw new EventLeaseLostException("租约已丢失 id=" + id + " version=" + version);
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
                throw new EventLeaseLostException("重复事件重排期条件更新未命中 id=" + id + " version=" + version);
            }
            return;
        }
        boolean deleted = dao.deleteCompleted(id, version, TimerWorkerId.get());
        if (!deleted) {
            throw new EventLeaseLostException("一次性事件完成删除条件更新未命中 id=" + id + " version=" + version);
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
