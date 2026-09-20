package org.dromara.warm.flow.core.test.listener;

import org.dromara.warm.flow.core.listener.GlobalListener;
import org.dromara.warm.flow.core.listener.ListenerVariable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 单测用全局监听器：记录四类全局触发点，前缀 g: 与节点级监听器区分。
 *
 * @author warm
 */
public class RecordingGlobalListener implements GlobalListener {

    /**
     * 全局监听器事件记录，供测试断言回调顺序。
     */
    public static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    private static final GlobalListener INSTANCE = new RecordingGlobalListener();

    protected RecordingGlobalListener() {
    }

    /**
     * 返回当前测试使用的全局监听器单例。
     *
     * @return 全局监听器实例
     */
    public static GlobalListener instance() {
        return INSTANCE;
    }

    /**
     * 记录全局开始事件。
     *
     * @param variable 监听器上下文
     */
    @Override
    public void start(ListenerVariable variable) {
        EVENTS.add(RecordingListener.describe("g:start", variable));
    }

    /**
     * 记录全局分派事件。
     *
     * @param variable 监听器上下文
     */
    @Override
    public void assignment(ListenerVariable variable) {
        EVENTS.add(RecordingListener.describe("g:assignment", variable));
    }

    /**
     * 记录全局完成事件。
     *
     * @param variable 监听器上下文
     */
    @Override
    public void finish(ListenerVariable variable) {
        EVENTS.add(RecordingListener.describe("g:finish", variable));
    }

    /**
     * 记录全局创建事件。
     *
     * @param variable 监听器上下文
     */
    @Override
    public void create(ListenerVariable variable) {
        EVENTS.add(RecordingListener.describe("g:create", variable));
    }
}
