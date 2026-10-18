package org.dromara.warm.demo.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 事件等待记录状态统计。
 * <p>与列表共用同一组筛选条件（状态条件除外），用于页面顶部各状态计数。</p>
 *
 * @author may
 * @since 2026/10/17
 */
@Getter
@Setter
public class EventWaitSummaryVo {

    /**
     * 待触发数量（WAITING）。
     */
    private long waiting;

    /**
     * 等待重试数量（RETRY）。
     */
    private long retry;

    /**
     * 执行中数量（RUNNING）。
     */
    private long running;

    /**
     * 重试耗尽数量（DEAD）。
     */
    private long dead;

    /**
     * 已取消数量（CANCELLED）。
     */
    private long cancelled;
}
