package org.dromara.warm.flow.core.timer;

/**
 * 已抢占的事件等待记录失效，应在事件事务回滚后取消。
 *
 * @author may
 */
public class EventCancelException extends EventSkipException {

    public EventCancelException(String message) {
        super(message);
    }
}
