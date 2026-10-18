package org.dromara.warm.flow.core.constant;

/**
 * 流程事件与等待记录常量。
 * <p>
 * 事件等待状态独立于 {@link org.dromara.warm.flow.core.enums.FlowStatus}，不复用流程状态枚举。
 * 定时触发策略常量（AFTER / FIXED / POINT_IN_TIME）由唯一解析方
 * {@link org.dromara.warm.flow.core.service.event.EventConfig} 维护，此处不再重复定义。
 *
 * @author may
 */
public class EventCons {

    /**
     * 等待触发。
     */
    public static final String STATUS_WAITING = "WAITING";

    /**
     * 已被某个调度线程抢占。
     */
    public static final String STATUS_RUNNING = "RUNNING";

    /**
     * 单次事件执行成功；仅作为执行结果语义，不作为持久化状态保留。
     */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * 已取消，例如任务已正常完成。
     */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /**
     * 等待失败重试。
     */
    public static final String STATUS_RETRY = "RETRY";

    /**
     * 达到最大重试次数。
     */
    public static final String STATUS_DEAD = "DEAD";

    /**
     * 定时边界事件中断提示。
     */
    public static final String TIMER_BOUNDARY_MESSAGE = "定时边界事件中断";

    /**
     * 消息边界事件中断提示。
     */
    public static final String MESSAGE_BOUNDARY_MESSAGE = "消息边界事件中断";

    /**
     * 错误边界事件中断提示。
     */
    public static final String ERROR_BOUNDARY_MESSAGE = "错误边界事件中断";

    private EventCons() {
    }
}
