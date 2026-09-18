package org.dromara.warm.flow.core.test.listener;

/**
 * 单测用开始监听器。
 *
 * @author warm
 */
public class StartListener extends RecordingListener {
    @Override
    protected String tag() {
        return "start";
    }
}
