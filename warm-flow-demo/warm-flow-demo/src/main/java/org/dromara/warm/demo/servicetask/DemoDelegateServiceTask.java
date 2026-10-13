package org.dromara.warm.demo.servicetask;

import org.dromara.warm.flow.core.service.task.ServiceTaskContext;
import org.dromara.warm.flow.core.service.task.ServiceTaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * delegateExpression 模式服务任务示例：表达式求值结果必须是 {@link ServiceTaskHandler}。
 * <p>节点配置：{@code {"serviceTask":{"type":"delegateExpression","expression":"#{@demoDelegateServiceTask}"}}}</p>
 *
 * @author may
 */
@Component("demoDelegateServiceTask")
public class DemoDelegateServiceTask implements ServiceTaskHandler {

    /**
     * Demo 服务任务日志。
     */
    private static final Logger log = LoggerFactory.getLogger(DemoDelegateServiceTask.class);

    @Override
    public void execute(ServiceTaskContext context) {
        log.info("Demo 服务任务[delegateExpression 模式]开始执行: instanceId={}, nodeCode={}, nodeName={}",
            context.getInstance().getId(), context.getNode().getNodeCode(), context.getNode().getNodeName());
        context.setVariable("delegateTaskNode", context.getNode().getNodeCode());
        context.setVariable("delegateTaskResult", "delegateExpression模式已执行");
        log.info("Demo 服务任务[delegateExpression 模式]执行完成: instanceId={}, nodeCode={}, variables={}",
            context.getInstance().getId(), context.getNode().getNodeCode(), context.getVariables());
    }
}
