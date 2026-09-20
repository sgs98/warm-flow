package org.dromara.warm.demo.listener;

import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.listener.Listener;
import org.dromara.warm.flow.core.listener.ListenerVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Demo 节点任务监听器。
 * <p>
 * 该示例可以配置到节点的开始、分派、完成或创建事件中，用于展示监听器上下文。
 * 示例只读上下文并输出日志，不修改流程状态、办理人或流程变量。
 *
 * @author may
 * @since 2026/9/20
 */
@Component
public class DemoTaskListener implements Listener {

    /** Demo 任务监听器日志。 */
    private static final Logger log = LoggerFactory.getLogger(DemoTaskListener.class);

    /**
     * 输出节点监听器可读取的核心上下文。
     *
     * @param listenerVariable 监听器变量
     */
    @Override
    public void notify(ListenerVariable listenerVariable) {
        Definition definition = listenerVariable == null ? null : listenerVariable.getDefinition();
        Instance instance = listenerVariable == null ? null : listenerVariable.getInstance();
        Node node = listenerVariable == null ? null : listenerVariable.getNode();
        Task task = listenerVariable == null ? null : listenerVariable.getTask();
        log.info("Demo 任务监听器触发: eventType={}, flowCode={}, instanceId={}, nodeCode={}, taskId={}, variables={}",
            listenerVariable == null ? null : listenerVariable.getEventType(),
            definition == null ? null : definition.getFlowCode(),
            instance == null ? null : instance.getId(),
            node == null ? null : node.getNodeCode(),
            task == null ? null : task.getId(),
            listenerVariable == null ? null : listenerVariable.getVariable());
    }
}
