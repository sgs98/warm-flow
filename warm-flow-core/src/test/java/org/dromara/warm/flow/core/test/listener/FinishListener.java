package org.dromara.warm.flow.core.test.listener;

/**
 * 单测用完成监听器。
 *
 * @author warm
 */
public class FinishListener extends RecordingListener {
    @Override
    protected String tag() {
        return "finish";
    }
}
