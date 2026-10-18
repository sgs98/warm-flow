package org.dromara.warm.flow.core.timer;

import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

/**
 * 事件执行扩展点：core 只调度，宿主持事务。
 * <p>
 * {@link TimerExecutor} 抢占成功后通过 {@code FrameInvoker.getBean(EventTaskHandler.class)}
 * 取实现执行事件；SB3 / SB4 starter 提供带事务的实现，把「改等待记录状态 + 推进流程」
 * 收进同一事务。非 Spring 集成方用 {@code FrameInvoker.setBeanFunction} 注册实现，
 * 并自行保证数据库事务边界（一次事件 = 一个事务，CAS 抢占在事务外）。
 *
 * @author may
 */
public interface EventTaskHandler {

    /**
     * 执行一条已抢占的事件。
     * <p>
     * 实现内部推进流程前必须再次校验实例 / 任务 / 定义状态；抛出任意异常视为本次执行失败，
     * 由调度底座按退避策略重试或转 {@code DEAD}。
     *
     * @param wait 已抢占的等待记录（{@code RUNNING}，版本号为抢占后的新版本）
     */
    void handle(EventWait wait);

    /**
     * 执行一条已抢占的事件，并携带调用方上下文。
     * <p>
     * 默认实现忽略上下文、委托 {@link #handle(EventWait)}；需要把消息触发等调用方
     * 显式指定的操作人 / 租户透传到流程推进的实现（如 {@code EventRuntimeHandler}）
     * 覆盖本方法。定时调度路径没有调用方上下文，传入 {@code null}。
     *
     * @param wait    已抢占的等待记录（{@code RUNNING}，版本号为抢占后的新版本）
     * @param context 调用方上下文；定时调度路径为 {@code null}
     */
    default void handle(EventWait wait, WorkflowContext context) {
        handle(wait);
    }
}
