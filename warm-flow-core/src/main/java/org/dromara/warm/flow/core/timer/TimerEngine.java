package org.dromara.warm.flow.core.timer;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.orm.dao.FlowEventWaitDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 内置定时器引擎。
 * <p>
 * core 自建守护线程 + 显式 {@link #shutdown()}，不依赖 Spring 生命周期回调。
 * {@code timerEnabled} 默认关闭：只有显式开启才启动扫描线程，避免只升级 jar、
 * 未建表的宿主周期报错。扫描线程按 {@code timerScanInterval} 周期执行：
 * 分页扫描到期记录 → 逐条 CAS 抢占 → 投递 {@link TimerExecutor} 执行。
 * 「表不存在」类异常只报一次错误日志后自动停用扫描（fail-soft），不周期刷屏。
 *
 * @author may
 */
public class TimerEngine {

    private static final Logger log = LoggerFactory.getLogger(TimerEngine.class);

    /**
     * 默认扫描间隔（毫秒）。
     */
    private static final long DEFAULT_SCAN_INTERVAL = 5_000L;

    /**
     * 表缺失异常的识别关键字（覆盖 MySQL / Oracle / PostgreSQL / SQL Server 常见报错）。
     */
    private static final String[] TABLE_MISSING_PATTERNS = {
        "doesn't exist", "doesn’t exist", "does not exist", "no such table",
        "unknown table", "ora-00942", "invalid object name", "表或视图不存在", "不存在"
    };

    /**
     * 单例：引擎生命周期跟随 {@code WarmFlow.init()} / {@code shutdown()}。
     */
    private static final TimerEngine INSTANCE = new TimerEngine();

    /**
     * 扫描调度线程。
     */
    private ScheduledExecutorService scheduler;

    /**
     * 事件执行线程池。
     */
    private TimerExecutor executor;

    /**
     * 扫描器。
     */
    private TimerScanner scanner;

    /**
     * 抢占器。
     */
    private TimerClaim claim;

    /**
     * 当前扫描周期任务句柄（fail-soft 停用时取消）。
     */
    private ScheduledFuture<?> scanFuture;

    /**
     * 事件等待记录 DAO，扫描周期内懒加载（兼容 starter 中 DAO 晚于配置初始化注册）。
     */
    private volatile FlowEventWaitDao<EventWait> dao;

    /**
     * 引擎是否已启动。
     */
    private volatile boolean started;

    private TimerEngine() {
    }

    /**
     * 获取引擎单例。
     *
     * @return 引擎实例
     */
    public static TimerEngine getInstance() {
        return INSTANCE;
    }

    /**
     * 启动定时器（幂等；使用已注册到 {@link FlowEngine} 的引擎配置，未注册或未开启时为空操作）。
     */
    public synchronized void start() {
        start(FlowEngine.getFlowConfig());
    }

    /**
     * 启动定时器（幂等；{@code config} 为 {@code null} 或 {@code timerEnabled} 未开启时为空操作）。
     * <p>
     * 由 {@link org.dromara.warm.flow.core.config.WarmFlow#init()} 显式传入自身配置，
     * 避免依赖「全局配置已注册」的顺序：starter 通常在 {@code init()} 之后才
     * {@code FlowEngine.setFlowConfig(...)}，若不传参会因全局配置为 {@code null} 而静默不启动。
     *
     * @param config 引擎配置
     */
    public synchronized void start(WarmFlow config) {
        if (started) {
            return;
        }
        if (config == null || !config.isTimerEnabled()) {
            return;
        }
        // DAO 此处尽力解析一次；未注册时由扫描周期内懒加载兜底
        dao = resolveDao();
        scanner = new TimerScanner(dao, config);
        executor = new TimerExecutor(dao, config);
        claim = new TimerClaim(dao);

        long interval = config.getTimerScanInterval() != null
            ? config.getTimerScanInterval() : DEFAULT_SCAN_INTERVAL;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "warm-flow-timer-scanner");
            thread.setDaemon(true);
            return thread;
        });
        scanFuture = scheduler.scheduleWithFixedDelay(this::scanCycle, interval, interval,
            TimeUnit.MILLISECONDS);
        started = true;
        log.info("Warm-Flow 内置定时器已启动，扫描间隔 {}ms，工作节点 {}", interval, TimerWorkerId.get());
    }

    /**
     * 关闭定时器（幂等）：停止扫描、关闭执行线程池。
     */
    public synchronized void shutdown() {
        if (!started && scheduler == null) {
            return;
        }
        if (scanFuture != null) {
            scanFuture.cancel(false);
            scanFuture = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        scanner = null;
        claim = null;
        started = false;
        log.info("Warm-Flow 内置定时器已关闭");
    }

    /**
     * 引擎是否已启动。
     *
     * @return 已启动返回 {@code true}
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * 单个扫描周期：扫描 → 抢占 → 投递。
     */
    private void scanCycle() {
        try {
            if (dao == null) {
                dao = resolveDao();
                if (dao == null) {
                    return;
                }
                scanner = new TimerScanner(dao, FlowEngine.getFlowConfig());
                claim = new TimerClaim(dao);
            }
            Date now = new Date();
            List<EventWait> due = scanner.scanBatch(now);
            for (EventWait wait : due) {
                try {
                    if (!claim.claim(wait, now)) {
                        continue;
                    }
                    executor.submit(wait, wait.getStatus(), wait.getNextTriggerAt(),
                        wait.getRetryAt());
                } catch (Exception ex) {
                    log.warn("定时记录抢占或投递失败 id={}", wait.getId(), ex);
                }
            }
        } catch (Throwable t) {
            if (isTableMissing(t)) {
                log.error("flow_event_wait 表不存在，内置定时器扫描自动停用；"
                    + "请执行建表脚本后重启或显式调用 TimerEngine.getInstance().start()", t);
                stopScan();
            } else {
                log.warn("内置定时器扫描周期执行异常", t);
            }
        }
    }

    /**
     * fail-soft 停用：取消扫描周期任务，等待宿主修复后显式重启。
     */
    private synchronized void stopScan() {
        if (scanFuture != null) {
            scanFuture.cancel(false);
            scanFuture = null;
        }
        started = false;
    }

    /**
     * 解析事件等待记录 DAO，未注册时返回 {@code null}。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private FlowEventWaitDao<EventWait> resolveDao() {
        FlowEventWaitDao raw = FrameInvoker.getBean(FlowEventWaitDao.class);
        return raw;
    }

    /**
     * 判断异常链中是否包含「表不存在」类错误。
     */
    static boolean isTableMissing(Throwable t) {
        Throwable current = t;
        int depth = 0;
        while (current != null && depth < 10) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                for (String pattern : TABLE_MISSING_PATTERNS) {
                    if (lower.contains(pattern)) {
                        return true;
                    }
                }
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }
}
