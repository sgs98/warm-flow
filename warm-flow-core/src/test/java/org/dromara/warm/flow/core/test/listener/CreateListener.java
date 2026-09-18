package org.dromara.warm.flow.core.test.listener;

/**
 * 单测用创建监听器。
 *
 * @author warm
 */
public class CreateListener extends RecordingListener {
    @Override
    protected String tag() {
        return "create";
    }
}
