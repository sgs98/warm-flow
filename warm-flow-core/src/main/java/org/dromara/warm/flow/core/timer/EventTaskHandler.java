package org.dromara.warm.flow.core.timer;

import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

/**
 * 事件执行扩展点：core 只调度，宿主持事务。
 * <p>
 * {@link TimerExecutor} 抢占成功后通过 {@code FrameInvoker.getBean(EventTaskHandler.class)}
 * 取实现执行事件。SB3 / SB4 starter 默认注册 {@code EventRuntimeHandler}：容器存在
 * {@code PlatformTransactionManager} 时自动包上事务（{@code TransactionalEventTaskHandler}），
 * 把「推进流程 + 标记等待记录完成」收进同一事务；不存在时不注册（{@code FrameInvoker}
 * 取不到实现即释放抢占，事件运行时静默停摆并告警，见设计 §6.6：无事务宿主不在事件
 * 运行时可靠性支持范围内）。非 Spring 集成方用 {@code FrameInvoker.setBeanFunction}
 * 注册实现，并自行保证数据库事务边界。
 * <p>
 * 事务边界说明：调用方不应在触发消息或错误事件前开启外围事务；CAS 抢占与失败记账
 * （RETRY / DEAD）在事件推进事务外，由调度底座持有；
 * 完成回调在租约丢失时抛 {@link EventLeaseLostException}，带事务的实现让它连着
 * 推进结果一起回滚。
 *
 * @author may
 */
public interface EventTaskHandler {

    /** 消息或错误事件抢占前检查调用方事务；默认留给非 Spring 宿主自行保证。 */
    default void checkTriggerTransaction() {
    }

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

    /**
     * 执行一条已抢占的事件，完成动作由调度底座以回调传入。
     * <p>
     * 推进（{@link #handle(EventWait, WorkflowContext)}）与「等待记录完成」（一次性删除 /
     * 重复重排期）拆成两段：带事务的实现把两段包进同一事务，消除「推进已提交、完成标记
     * 丢失」的重放窗口；默认实现先后执行（无事务路径保持 at-least-once，重放安全由各
     * 触发路径的幂等闸保证）。完成回调在租约丢失时抛 {@link EventLeaseLostException}，
     * 带事务的实现随异常回滚整段推进。
     *
     * @param wait       已抢占的等待记录（{@code RUNNING}，版本号为抢占后的新版本）
     * @param context    调用方上下文；定时调度路径为 {@code null}
     * @param completion 完成动作：成功推进后标记等待记录完成（删除 / 重排期）
     */
    default void handle(EventWait wait, WorkflowContext context, Runnable completion) {
        handle(wait, context);
        completion.run();
    }
}
