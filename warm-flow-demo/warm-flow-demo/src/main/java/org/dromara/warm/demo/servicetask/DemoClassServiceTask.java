package org.dromara.warm.demo.servicetask;

import org.dromara.warm.flow.core.service.task.ServiceTaskContext;
import org.dromara.warm.flow.core.service.task.ServiceTaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * class 模式服务任务示例：按类全限定名实例化，不要求注册为 Spring Bean。
 * <p>节点配置：{@code {"serviceTask":{"type":"class","handler":"org.dromara.warm.demo.servicetask.DemoClassServiceTask"}}}</p>
 *
 * @author may
 */
public class DemoClassServiceTask implements ServiceTaskHandler {

    /**
     * Demo 服务任务日志。
     */
    private static final Logger log = LoggerFactory.getLogger(DemoClassServiceTask.class);

    @Override
    public void execute(ServiceTaskContext context) {
        log.info("Demo 服务任务[class 模式]开始执行: instanceId={}, nodeCode={}, nodeName={}",
            context.getInstance().getId(), context.getNode().getNodeCode(), context.getNode().getNodeName());
        context.setVariable("classTaskNode", context.getNode().getNodeCode());
        context.setVariable("classTaskResult", "class模式已执行");
        log.info("Demo 服务任务[class 模式]执行完成: instanceId={}, nodeCode={}, variables={}",
            context.getInstance().getId(), context.getNode().getNodeCode(), context.getVariables());
    }
}
