package org.dromara.warm.flow.core.timer;

import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.orm.dao.FlowEventWaitDao;

import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
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
 *
 * @author may
 */
public class TimerExecutor {

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
            new LinkedBlockingQueue<>(), factory);
        this.pool.allowCoreThreadTimeOut(true);
    }

    /**
     * 提交一条已抢占的记录异步执行。
     *
     * @param wait           抢占成功的记录（版本号为抢占前版本，执行时使用版本号 + 1）
     * @param preClaimStatus 抢占前状态（WAITING / RETRY）
     * @param preClaimNext   抢占前的下次触发时间（WAITING 时非空）
     * @param preClaimRetry  抢占前的下次重试时间（RETRY 时非空）
     */
    public void submit(EventWait wait, String preClaimStatus, Date preClaimNext, Date preClaimRetry) {
        pool.execute(() -> execute(wait, preClaimStatus, preClaimNext, preClaimRetry));
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
