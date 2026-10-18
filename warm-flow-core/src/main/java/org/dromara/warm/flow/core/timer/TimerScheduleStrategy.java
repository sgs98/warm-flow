package org.dromara.warm.flow.core.timer;

import java.util.Date;

/**
 * 定时等待策略接口。
 * <p>
 * 计算下一次触发时间，与具体事件类型解耦；实现须无状态、线程安全。
 *
 * @author may
 */
public interface TimerScheduleStrategy {

    /**
     * 计算下一次触发时间。
     *
     * @param context 策略计算入参
     * @return 下一次触发时间
     */
    Date nextTriggerAt(TimerContext context);
}
