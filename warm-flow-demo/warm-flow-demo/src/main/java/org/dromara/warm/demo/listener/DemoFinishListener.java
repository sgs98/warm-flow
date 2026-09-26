package org.dromara.warm.demo.listener;

import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.listener.Listener;
import org.dromara.warm.flow.core.listener.ListenerVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Demo 节点完成监听器。
 * <p>
 * 该示例只适合挂在节点的「完成」事件上，因此在设计器的监听器候选里带上 {@code type=finish}，
 * 选中后监听器类型会自动联动，使用者不用再手动挑一次。与 {@link DemoTaskListener} 形成对照：
 * 后者四种事件都适用，候选里 type 留空，由使用者自行选择。
 *
 * @author may
 * @since 2026/9/26
 */
@Component
public class DemoFinishListener implements Listener {

    /** Demo 节点完成监听器日志。 */
    private static final Logger log = LoggerFactory.getLogger(DemoFinishListener.class);

    /**
     * 输出节点完成事件的核心上下文，示例只读不改流程状态。
     *
     * @param listenerVariable 监听器变量
     */
    @Override
    public void notify(ListenerVariable listenerVariable) {
        Instance instance = listenerVariable == null ? null : listenerVariable.getInstance();
        Node node = listenerVariable == null ? null : listenerVariable.getNode();
        log.info("Demo 节点完成监听器触发: eventType={}, instanceId={}, nodeCode={}",
            listenerVariable == null ? null : listenerVariable.getEventType(),
            instance == null ? null : instance.getId(),
            node == null ? null : node.getNodeCode());
    }
}
