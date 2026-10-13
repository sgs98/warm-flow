package org.dromara.warm.flow.core.service.task;

import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.Map;

/**
 * 服务任务用户侧上下文视图（{@link AutoNodeContext} 的收窄视图）。
 *
 * @author warm
 */
public class ServiceTaskContext {

    private final AutoNodeContext delegate;

    public ServiceTaskContext(AutoNodeContext delegate) {
        this.delegate = delegate;
    }

    /**
     * 获取当前流程定义。
     *
     * @return 流程定义
     */
    public Definition getDefinition() {
        return delegate.getDefinition();
    }

    /**
     * 获取当前流程实例。
     *
     * @return 流程实例
     */
    public Instance getInstance() {
        return delegate.getInstance();
    }

    /**
     * 获取当前服务任务节点。
     *
     * @return 服务任务节点
     */
    public Node getNode() {
        return delegate.getNode();
    }

    /**
     * 获取当前节点配置原始 JSON。
     *
     * @return 节点配置
     */
    public String getNodeConfig() {
        return delegate.getNodeConfig();
    }

    /**
     * 获取当前合并后的流程变量。
     *
     * @return 流程变量
     */
    public Map<String, Object> getVariables() {
        return delegate.getVariables();
    }

    /**
     * 读取流程变量。
     *
     * @param key 变量名
     * @return 变量值
     */
    public Object getVariable(String key) {
        return delegate.getVariable(key);
    }

    /**
     * 写入流程变量。
     *
     * @param key   变量名
     * @param value 变量值
     */
    public void setVariable(String key, Object value) {
        delegate.setVariable(key, value);
    }

    /**
     * 获取本次流程动作上下文（只读语义）。
     *
     * @return 流程动作上下文
     */
    public WorkflowContext getWorkflowContext() {
        return delegate.getWorkflowContext();
    }
}
