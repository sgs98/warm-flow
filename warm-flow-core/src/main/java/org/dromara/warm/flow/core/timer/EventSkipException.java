package org.dromara.warm.flow.core.timer;

/**
 * 事件跳过信号：抢占后发现记录暂不可执行（如实例挂起、定义已失效）时抛出。
 * <p>
 * 调度底座捕获后释放租约并恢复抢占前状态，<b>不消耗</b> {@code retry_count}，
 * 等待下次条件满足时再触发。
 *
 * @author may
 */
public class EventSkipException extends RuntimeException {

    public EventSkipException(String message) {
        super(message);
    }
}
