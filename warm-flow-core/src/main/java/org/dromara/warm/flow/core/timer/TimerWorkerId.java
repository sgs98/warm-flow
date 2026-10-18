package org.dromara.warm.flow.core.timer;

import java.lang.management.ManagementFactory;
import java.util.UUID;

/**
 * 定时器工作节点标识。
 * <p>
 * 标识当前 JVM 的调度实例，写入 {@code flow_event_wait.lock_owner}，
 * 用于租约校验（只有持有者本人可续租、提交结果）。同一 JVM 内共享一个标识。
 *
 * @author may
 */
public final class TimerWorkerId {

    /**
     * 当前 JVM 的调度实例标识。
     */
    private static final String WORKER_ID = buildWorkerId();

    private TimerWorkerId() {
    }

    /**
     * 获取当前工作节点标识。
     *
     * @return 形如 {@code timer-PID@host-uuid} 的标识串
     */
    public static String get() {
        return WORKER_ID;
    }

    /**
     * 生成工作节点标识：运行时名称（pid@host）+ 随机后缀，避免同机多 JVM 冲突。
     */
    private static String buildWorkerId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "timer-" + runtimeName + "-" + suffix;
    }
}
