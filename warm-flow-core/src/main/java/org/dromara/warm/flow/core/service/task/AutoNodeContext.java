package org.dromara.warm.flow.core.service.task;

import lombok.Getter;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.Map;

/**
 * 自动节点引擎侧上下文。
 *
 * @author warm
 */
@Getter
public class AutoNodeContext {

    private final Definition definition;
    private final Instance instance;
    private final Node node;
    private final Map<String, Object> variables;
    private final WorkflowContext workflowContext;

    public AutoNodeContext(Definition definition, Instance instance, Node node
        , Map<String, Object> variables, WorkflowContext workflowContext) {
        this.definition = definition;
        this.instance = instance;
        this.node = node;
        this.variables = variables;
        this.workflowContext = workflowContext;
    }

    /**
     * 读取流程变量。
     *
     * @param key 变量名
     * @return 变量值
     */
    public Object getVariable(String key) {
        return variables.get(key);
    }

    /**
     * 写入流程变量，结果对后续网关、办理人表达式与历史快照可见。
     *
     * @param key   变量名
     * @param value 变量值
     */
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    /**
     * 读取节点配置原始 JSON。
     *
     * @return 节点配置
     */
    public String getNodeConfig() {
        return node.getNodeConfig();
    }
}
