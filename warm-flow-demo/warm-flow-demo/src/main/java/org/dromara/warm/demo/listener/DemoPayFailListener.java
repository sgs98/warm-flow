package org.dromara.warm.demo.listener;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.listener.Listener;
import org.dromara.warm.flow.core.listener.ListenerVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Demo「付款失败」判定监听器：读取流程变量 {@link #VAR_PAY_AMOUNT}（付款金额），
 * 金额大于限额 {@link #PAY_AMOUNT_LIMIT} 时显式调用 {@code TaskService.throwError}
 * 抛出错误码 {@code PAY_FAILED}，命中挂在「财务执行付款」节点上的错误边界，自动中断
 * 付款任务并转到「失败善后处理」；金额不超过限额（或未传、非数字）时不处理，付款任务
 * 按正常待办办理。
 * <p>
 * warm-flow 不会自动把监听器抛出的 Java 异常转成错误事件（见事件设计 §9A.4），
 * 因此这里必须调用抛出 API 而不是 {@code throw}；抛出后由引擎完成边界路由，
 * 无需前端再手动抛错。
 * <p>
 * 变量可随发起流程传入，或办理「发起付款申请」时通过流程变量传入；监听器先取本次操作
 * 变量，再回退实例累积变量。
 *
 * @author may
 * @since 2026/10/17
 */
@Component
public class DemoPayFailListener implements Listener {

    /**
     * 付款金额变量名。
     */
    public static final String VAR_PAY_AMOUNT = "payAmount";

    /**
     * 付款限额：金额大于该值即判定失败进错误边界。
     */
    public static final BigDecimal PAY_AMOUNT_LIMIT = new BigDecimal("1000");

    /**
     * 付款失败错误码，需与「付款失败善后」错误边界节点配置的 {@code eventName} 一致。
     */
    public static final String ERROR_CODE_PAY_FAILED = "PAY_FAILED";

    /**
     * Demo 付款失败判定监听器日志。
     */
    private static final Logger log = LoggerFactory.getLogger(DemoPayFailListener.class);

    /**
     * 付款任务创建时判定：金额超过限额则抛错进错误边界，否则不处理。
     *
     * @param listenerVariable 监听器变量
     */
    @Override
    public void notify(ListenerVariable listenerVariable) {
        Task task = listenerVariable == null ? null : listenerVariable.getTask();
        if (task == null || task.getId() == null) {
            return;
        }
        BigDecimal amount = readPayAmount(listenerVariable);
        if (amount == null || amount.compareTo(PAY_AMOUNT_LIMIT) <= 0) {
            return;
        }
        log.info("Demo 付款金额 {} 超过限额 {}，抛出 {} 转入错误边界: instanceId={}, taskId={}",
            amount, PAY_AMOUNT_LIMIT, ERROR_CODE_PAY_FAILED, task.getInstanceId(), task.getId());
        FlowEngine.taskService().throwError(task.getId(), ERROR_CODE_PAY_FAILED,
            "付款金额 " + amount.toPlainString() + " 超过限额 " + PAY_AMOUNT_LIMIT.toPlainString() + "，转入善后处理");
    }

    /**
     * 读取付款金额：优先取本次操作变量，回退实例累积变量。
     *
     * @param listenerVariable 监听器上下文
     * @return 付款金额；未传或非数字时返回 {@code null}
     */
    private BigDecimal readPayAmount(ListenerVariable listenerVariable) {
        BigDecimal amount = parseAmount(listenerVariable.getVariable());
        if (amount != null) {
            return amount;
        }
        Instance instance = listenerVariable.getInstance();
        return parseAmount(instance == null ? null : instance.getVariableMap());
    }

    /**
     * 从变量表解析金额，兼容前端传入的字符串。
     *
     * @param variable 变量表
     * @return 金额；不存在或非数字时返回 {@code null}
     */
    private BigDecimal parseAmount(Map<String, Object> variable) {
        if (variable == null || variable.get(VAR_PAY_AMOUNT) == null) {
            return null;
        }
        Object value = variable.get(VAR_PAY_AMOUNT);
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            log.warn("Demo 付款金额无法解析为数字，按不超限处理: {}={}", VAR_PAY_AMOUNT, text);
            return null;
        }
    }
}
