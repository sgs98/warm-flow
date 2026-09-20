package org.dromara.warm.flow.core.test.listener;

import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.listener.Listener;
import org.dromara.warm.flow.core.listener.ListenerVariable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 单测用记录型监听器基类：按 tag 记录监听触发时的上下文快照，
 * 供特征测试断言监听器顺序、context 是否可见、task.userList 注入时机等对外行为。
 *
 * @author warm
 */
public abstract class RecordingListener implements Listener {

    /**
     * 全部监听事件记录，测试启动前由 {@code FlowTestHarness} 清空。
     */
    public static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    /**
     * 记录监听器标签及触发时的上下文快照。
     *
     * @param variable 监听器上下文
     */
    @Override
    public void notify(ListenerVariable variable) {
        EVENTS.add(describe(tag(), variable));
    }

    protected abstract String tag();

    static String describe(String tag, ListenerVariable v) {
        Node node = v.getNode();
        Task task = v.getTask();
        return tag
                + "{node=" + (node == null ? null : node.getNodeCode())
                + ",task=" + (task == null ? null : task.getNodeCode())
                + ",ctx=" + (v.getContext() != null)
                + ",taskUsers=" + (task != null && task.getUserList() != null)
                + ",vars=" + v.getVariable()
                + "}";
    }

    static Map<String, Object> variablesOf(ListenerVariable v) {
        return v.getVariable();
    }
}
