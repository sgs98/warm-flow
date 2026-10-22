package org.dromara.warm.flow.core.timer;

/**
 * 完成提交前租约已丢失：本次执行结果不得提交。
 * <p>
 * 完成动作（一次性删除 / 重复重排期）前的续租校验失败时抛出——租约已被恢复流程接管，
 * 带事务的调用方应让推进结果随异常一并回滚（设计 §6.5「续租失败不得提交结果」）；
 * 无事务路径无法回滚，记录保持 {@code RUNNING}，由租约恢复流程重放（at-least-once，
 * 各触发路径自带幂等闸）。调度底座捕获后只记日志：不计失败、不重试、不覆盖接管方状态。
 *
 * @author may
 */
public class EventLeaseLostException extends RuntimeException {

    /**
     * @param message 租约丢失说明（记录 id 与版本）
     */
    public EventLeaseLostException(String message) {
        super(message);
    }
}
