package org.dromara.warm.flow.core.test.listener;

import org.dromara.warm.flow.core.listener.GlobalListener;
import org.dromara.warm.flow.core.listener.ListenerVariable;

import java.util.function.Consumer;

/**
 * 单测用可写库全局监听器：在 start 触发点执行测试装配的库写钩子，
 * 用于锁定「监听器中途写库不进入本次操作快照」的执行作用域语义（R2/R3/R5）。
 *
 * @author warm
 */
public class MutatingGlobalListener extends RecordingGlobalListener {

    /**
     * start 事件触发的库写钩子，测试在操作前装配、操作后必须清理。
     */
    public static Consumer<ListenerVariable> onStart;

    private static final GlobalListener INSTANCE = new MutatingGlobalListener();

    private MutatingGlobalListener() {
    }

    /**
     * 返回支持测试注入回调的全局监听器单例。
     *
     * @return 全局监听器实例
     */
    public static GlobalListener instance() {
        return INSTANCE;
    }

    /**
     * 记录开始事件后执行测试注入回调。
     *
     * @param variable 监听器上下文
     */
    @Override
    public void start(ListenerVariable variable) {
        super.start(variable);
        if (onStart != null) {
            onStart.accept(variable);
        }
    }
}
