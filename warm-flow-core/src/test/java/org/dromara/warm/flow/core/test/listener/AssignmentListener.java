package org.dromara.warm.flow.core.test.listener;

/**
 * 单测用分派监听器。
 *
 * @author warm
 */
public class AssignmentListener extends RecordingListener {
    @Override
    protected String tag() {
        return "assignment";
    }
}
