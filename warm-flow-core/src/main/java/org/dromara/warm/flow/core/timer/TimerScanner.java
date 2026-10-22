package org.dromara.warm.flow.core.timer;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.orm.dao.FlowEventWaitDao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 定时记录扫描器。
 * <p>
 * 扫描规则：{@code WAITING} 按 {@code next_trigger_at <= now}、{@code RETRY} 按
 * {@code retry_at <= now} 分别使用稳定的 {@code (dueAt, id)} 游标分页扫描，不用预扫描窗口；
 * 单轮每个状态最多扫描 {@code timerMaxScanPages} 页（两状态预算独立），
 * 达到上限后下一轮从上轮游标继续，扫描到末尾后重置游标。
 * 实例级记录按页批量校验实例激活状态，跳过挂起实例后继续翻页，跳过不能结束本轮扫描。
 *
 * @author may
 */
public class TimerScanner {

    /**
     * 事件等待记录 DAO。
     */
    private final FlowEventWaitDao<EventWait> dao;

    /**
     * 单轮扫描目标条数（{@code timerBatchSize}）。
     */
    private final int batchSize;

    /**
     * 单轮每个状态最多扫描页数（{@code timerMaxScanPages}）。
     */
    private final int maxScanPages;

    /**
     * WAITING 扫描游标：上一次已处理记录的到期时间。
     */
    private Date waitingCursorAt;

    /**
     * WAITING 扫描游标：上一次已处理记录的主键。
     */
    private Long waitingCursorId;

    /**
     * RETRY 扫描游标：上一次已处理记录的重试时间。
     */
    private Date retryCursorAt;

    /**
     * RETRY 扫描游标：上一次已处理记录的主键。
     */
    private Long retryCursorId;

    /** 两类记录持续积压时轮流获得首批名额。 */
    private boolean retryFirst;

    public TimerScanner(FlowEventWaitDao<EventWait> dao, WarmFlow config) {
        this.dao = dao;
        this.batchSize = config != null && config.getTimerBatchSize() != null
            ? config.getTimerBatchSize() : 100;
        this.maxScanPages = config != null && config.getTimerMaxScanPages() != null
            ? config.getTimerMaxScanPages() : 10;
    }

    /**
     * 执行一轮扫描，取出一批到期记录。
     * <p>
     * 两种状态轮换扫描优先级，且各自独立消耗页数预算，
     * 避免持续积压的一方占满批次导致另一方饥饿；
     * 方法线程安全由调用方保证单线程调度。
     *
     * @param now 当前时间
     * @return 本轮取出的到期记录（最多 {@code batchSize} 条）
     */
    public synchronized List<EventWait> scanBatch(Date now) {
        List<EventWait> due = new ArrayList<>();
        boolean firstWaiting = !retryFirst;
        retryFirst = !retryFirst;
        scanState(now, firstWaiting, due);
        if (due.size() < batchSize) {
            scanState(now, !firstWaiting, due);
        }
        return due;
    }

    /**
     * 扫描单个状态（WAITING 或 RETRY）的到期记录，返回消耗的页数。
     */
    private int scanState(Date now, boolean waiting, List<EventWait> due) {
        int pages = 0;
        while (due.size() < batchSize && pages < maxScanPages) {
            List<EventWait> page = waiting
                ? dao.selectWaitingDue(now, waitingCursorAt, waitingCursorId, batchSize)
                : dao.selectRetryDue(now, retryCursorAt, retryCursorId, batchSize);
            if (page.isEmpty()) {
                resetCursor(waiting);
                break;
            }
            pages++;
            // 本页实例状态一次批量取出，避免逐条 getById 的 N+1 查询
            Map<Long, Instance> instanceMap = loadInstances(page);
            for (EventWait wait : page) {
                boolean executable = isExecutable(wait, instanceMap);
                if (executable) {
                    due.add(wait);
                }
                // 游标只推进到「最后一条已纳入或已校验」的记录；批次满时停在最后纳入的记录，
                // 未纳入的剩余行下一轮重新扫描，保证不遗漏
                if (executable) {
                    advanceCursor(waiting, wait);
                }
                if (due.size() >= batchSize) {
                    return pages;
                }
            }
            if (page.size() < batchSize) {
                resetCursor(waiting);
                break;
            }
            // 整页都被跳过（如全部挂起）时，游标推进到页尾，避免死循环
            advanceCursor(waiting, page.get(page.size() - 1));
        }
        return pages;
    }

    /**
     * 推进游标到指定记录。
     */
    private void advanceCursor(boolean waiting, EventWait wait) {
        if (waiting) {
            waitingCursorAt = wait.getNextTriggerAt();
            waitingCursorId = wait.getId();
        } else {
            retryCursorAt = wait.getRetryAt();
            retryCursorId = wait.getId();
        }
    }

    /**
     * 重置游标，下一轮从头扫描。
     */
    private void resetCursor(boolean waiting) {
        if (waiting) {
            waitingCursorAt = null;
            waitingCursorId = null;
        } else {
            retryCursorAt = null;
            retryCursorId = null;
        }
    }

    /**
     * 判断记录本轮是否可抢占：定义级记录（如 TIMER_START，无实例）直接可执行；
     * 实例级记录须实例仍处于激活状态，挂起实例跳过（恢复后补发当前计划触发）。
     * <p>
     * 实例不在批量结果中（不存在或批量查询失败退化）时按可执行处理，
     * 由执行器在推进前再次校验，与单条查询失败的兜底语义一致。
     */
    private boolean isExecutable(EventWait wait, Map<Long, Instance> instanceMap) {
        if (wait.getInstanceId() == null) {
            return true;
        }
        Instance instance = instanceMap.get(wait.getInstanceId());
        if (instance == null) {
            return true;
        }
        return ActivityStatus.isActivity(instance.getActivityStatus());
    }

    /**
     * 批量加载本页记录涉及的流程实例（定义级记录无实例，不进入查询集合）。
     *
     * @param page 本页到期记录
     * @return 实例主键到实例的索引；查询失败时返回空映射，由调用方按可执行兜底
     */
    private Map<Long, Instance> loadInstances(List<EventWait> page) {
        Set<Long> instanceIds = new HashSet<>();
        for (EventWait wait : page) {
            if (wait.getInstanceId() != null) {
                instanceIds.add(wait.getInstanceId());
            }
        }
        if (instanceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Map<Long, Instance> instanceMap = new HashMap<>();
            for (Instance instance : FlowEngine.insService().getByIds(instanceIds)) {
                instanceMap.put(instance.getId(), instance);
            }
            return instanceMap;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
