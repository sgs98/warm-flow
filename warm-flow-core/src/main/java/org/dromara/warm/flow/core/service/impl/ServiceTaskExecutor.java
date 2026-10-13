package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.service.task.AutoNodeContext;
import org.dromara.warm.flow.core.service.task.AutoNodeExecutor;
import org.dromara.warm.flow.core.service.task.ServiceTaskConfig;
import org.dromara.warm.flow.core.service.task.ServiceTaskContext;
import org.dromara.warm.flow.core.service.task.ServiceTaskHandler;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务任务自动节点执行器：解析 node_config.serviceTask 并执行三种模式。
 *
 * @author warm
 */
public final class ServiceTaskExecutor implements AutoNodeExecutor {

    /**
     * 服务任务上下文在流程变量中的保留键，供表达式以 {@code #execution} 访问。
     */
    private static final String SERVICE_TASK_EXECUTION_KEY = "execution";

    @Override
    public boolean supports(Node node) {
        return NodeType.isService(node.getNodeType());
    }

    @Override
    public void execute(AutoNodeContext context) {
        ServiceTaskConfig config = ServiceTaskConfig.parse(context.getNode().getNodeConfig());
        ServiceTaskHandler handler = resolveHandler(config, context);
        handler.execute(new ServiceTaskContext(context));
    }

    private ServiceTaskHandler resolveHandler(ServiceTaskConfig config, AutoNodeContext context) {
        if (ServiceTaskConfig.TYPE_CLASS.equals(config.getType())) {
            return resolveClass(config.getHandler(), context);
        }
        // 表达式模式：以独立变量视图注入 #execution 后求值，避免上下文对象写入流程变量
        Map<String, Object> variables = context.getVariables() == null
            ? new HashMap<>() : new HashMap<>(context.getVariables());
        variables.put(SERVICE_TASK_EXECUTION_KEY, new ServiceTaskContext(context));
        Object result = ExpressionUtil.evalServiceTask(config.getExpression(), variables);
        if (ServiceTaskConfig.TYPE_DELEGATE_EXPRESSION.equals(config.getType())) {
            AssertUtil.isTrue(!(result instanceof ServiceTaskHandler)
                , detail(context, config, "delegateExpression 结果必须是 ServiceTaskHandler"));
            return (ServiceTaskHandler) result;
        }
        // expression：写回 resultVariable
        if (StringUtils.isNotEmpty(config.getResultVariable())) {
            context.setVariable(config.getResultVariable(), result);
        }
        return ctx -> { };
    }

    private ServiceTaskHandler resolveClass(String className, AutoNodeContext context) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new FlowException(detail(context, null, "处理器类不存在: " + className), e);
        }
        Object bean = FrameInvoker.getBean(clazz);
        if (bean == null) {
            try {
                bean = clazz.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new FlowException(detail(context, null, "处理器无法实例化: " + className), e);
            }
        }
        AssertUtil.isTrue(!(bean instanceof ServiceTaskHandler)
            , detail(context, null, "处理器未实现 ServiceTaskHandler: " + className));
        return (ServiceTaskHandler) bean;
    }

    /**
     * 组装带流程编码、实例 ID、节点编码与服务任务模式的错误信息，不输出流程变量内容。
     *
     * @param context 自动节点上下文
     * @param config  服务任务配置，可为空
     * @param message 细节信息
     * @return 完整错误信息
     */
    private String detail(AutoNodeContext context, ServiceTaskConfig config, String message) {
        Definition definition = context.getDefinition();
        Instance instance = context.getInstance();
        Node node = context.getNode();
        return "[服务任务] flowCode=" + (definition == null ? null : definition.getFlowCode())
            + ", instanceId=" + (instance == null ? null : instance.getId())
            + ", nodeCode=" + (node == null ? null : node.getNodeCode())
            + ", type=" + (config == null ? null : config.getType())
            + ": " + message;
    }
}
