package org.dromara.warm.flow.core.timer;

import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.orm.dao.FlowEventWaitDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 定时事件执行器。
 * <p>
 * 与扫描线程解耦：扫描器只负责发现与抢占，执行在独立线程池中完成。
 * 单条记录的执行与完成语义统一收敛在 {@link EventWaitProcessor}（消息触发路径
 * 同步复用同一实现）；本类只负责异步投递。
 * <p>
 * 队列容量受 {@code timerQueueCapacity} 限制：积压占满后新投递被拒绝，此时立即
 * 释放抢占恢复等待状态，由后续扫描周期重投——排队记录的看门狗尚未启动，若任其
 * 在队列中滞留超过租约，会被恢复流程接管后再次投递，形成重复执行。
 *
 * @author may
 */
public class TimerExecutor {

    private static final Logger log = LoggerFactory.getLogger(TimerExecutor.class);

    /**
     * 共享的记录处理器。
     */
    private final EventWaitProcessor processor;

    /**
     * 执行线程池，线程数不超过 {@code timerPoolSize}。
     */
    private final ThreadPoolExecutor pool;

    public TimerExecutor(FlowEventWaitDao<EventWait> dao, WarmFlow config) {
        this.processor = new EventWaitProcessor(dao, config);
        int poolSize = config != null && config.getTimerPoolSize() != null
            ? config.getTimerPoolSize() : 2;
        int queueCapacity = config != null && config.getTimerQueueCapacity() != null
            ? config.getTimerQueueCapacity() : 1000;
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "warm-flow-timer-exec-" + seq.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        this.pool = new ThreadPoolExecutor(poolSize, poolSize, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueCapacity), factory);
        this.pool.allowCoreThreadTimeOut(true);
    }

    /**
     * 提交一条已抢占的记录异步执行。
     * <p>
     * 队列已满被拒绝时释放抢占：记录恢复抢占前状态，由后续扫描周期重投，
     * 不在队列中滞留（滞留超过租约会被恢复流程接管，造成重复投递）。
     *
     * @param wait           抢占成功的记录（版本号为抢占前版本，执行时使用版本号 + 1）
     * @param preClaimStatus 抢占前状态（WAITING / RETRY）
     * @param preClaimNext   抢占前的下次触发时间（WAITING 时非空）
     * @param preClaimRetry  抢占前的下次重试时间（RETRY 时非空）
     */
    public void submit(EventWait wait, String preClaimStatus, Date preClaimNext, Date preClaimRetry) {
        int claimedVersion = (wait.getVersion() == null ? 0 : wait.getVersion()) + 1;
        ScheduledFuture<?> queuedRenewal = processor.scheduleLeaseRenewal(wait, claimedVersion);
        try {
            pool.execute(() -> {
                if (queuedRenewal != null) {
                    queuedRenewal.cancel(false);
                }
                execute(wait, preClaimStatus, preClaimNext, preClaimRetry);
            });
        } catch (RejectedExecutionException ex) {
            if (queuedRenewal != null) {
                queuedRenewal.cancel(false);
            }
            log.warn("事件执行队列已满，释放抢占等待下轮重投 id={}", wait.getId(), ex);
            processor.releaseClaim(wait, preClaimStatus, preClaimNext, preClaimRetry);
        }
    }

    /**
     * 关闭执行线程池（幂等）。
     */
    public void shutdown() {
        pool.shutdownNow();
    }

    /**
     * 同步执行单条已抢占的事件（测试与消息触发路径复用）。
     */
    void execute(EventWait wait, String preClaimStatus, Date preClaimNext, Date preClaimRetry) {
        processor.process(wait, preClaimStatus, preClaimNext, preClaimRetry);
    }
}
