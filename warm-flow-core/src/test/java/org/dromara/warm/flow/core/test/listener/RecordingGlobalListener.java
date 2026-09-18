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

    public static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    private static final GlobalListener INSTANCE = new RecordingGlobalListener();

    private RecordingGlobalListener() {
    }

    public static GlobalListener instance() {
        return INSTANCE;
    }

    @Override
    public void start(ListenerVariable variable) {
        EVENTS.add(RecordingListener.describe("g:start", variable));
    }

    @Override
    public void assignment(ListenerVariable variable) {
        EVENTS.add(RecordingListener.describe("g:assignment", variable));
    }

    @Override
    public void finish(ListenerVariable variable) {
        EVENTS.add(RecordingListener.describe("g:finish", variable));
    }

    @Override
    public void create(ListenerVariable variable) {
        EVENTS.add(RecordingListener.describe("g:create", variable));
    }
}
