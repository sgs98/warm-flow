package org.dromara.warm.demo.servicetask;

import org.dromara.warm.flow.core.service.task.ServiceTaskContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * expression 模式服务任务示例：调用 Bean 方法，返回值由 resultVariable 写回流程变量。
 * <p>节点配置：{@code {"serviceTask":{"type":"expression","expression":"#{@demoOrderService.createOrder(#execution)}","resultVariable":"orderNo"}}}</p>
 *
 * @author may
 */
@Component("demoOrderService")
public class DemoOrderService {

    /**
     * Demo 服务任务日志。
     */
    private static final Logger log = LoggerFactory.getLogger(DemoOrderService.class);

    /**
     * 演示用单号序列。
     */
    private final AtomicLong sequence = new AtomicLong(1000L);

    /**
     * 创建订单，返回单号写入流程变量 orderNo。
     *
     * @param context 服务任务上下文
     * @return 订单号
     */
    public String createOrder(ServiceTaskContext context) {
        log.info("Demo 服务任务[expression 模式]开始执行: instanceId={}, nodeCode={}, expression={}",
            context.getInstance().getId(), context.getNode().getNodeCode(), "createOrder(#execution)");
        context.setVariable("expressionTaskNode", context.getNode().getNodeCode());
        context.setVariable("expressionTaskResult", "expression模式已执行");
        String orderNo = "SO" + sequence.incrementAndGet();
        log.info("Demo 服务任务[expression 模式]执行完成: instanceId={}, nodeCode={}, orderNo={}",
            context.getInstance().getId(), context.getNode().getNodeCode(), orderNo);
        return orderNo;
    }

    /**
     * 计算流程变量 day，供后续互斥网关条件分支使用；入参 inputDay 缺省为 5。
     *
     * @param context 服务任务上下文
     * @return 天数
     */
    public Integer evalDay(ServiceTaskContext context) {
        Object inputDay = context.getVariable("inputDay");
        Integer day = inputDay == null ? 5 : Integer.parseInt(String.valueOf(inputDay));
        log.info("Demo 服务任务[expression 模式]计算天数: instanceId={}, nodeCode={}, inputDay={}, day={}",
            context.getInstance().getId(), context.getNode().getNodeCode(), inputDay, day);
        return day;
    }
}
