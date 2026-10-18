package org.dromara.warm.flow.core.test.listener;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.listener.Listener;
import org.dromara.warm.flow.core.listener.ListenerVariable;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 单测用监听器：读取流程变量 {@code payAmount}（付款金额），大于限额 {@code 1000}
 * 时显式调用 {@code FlowEngine.taskService().throwError(taskId, errorCode, message)}
 * 抛出错误，用于验证「业务在监听器里读变量判定后显式抛错 → 命中错误边界」这一
 * 宿主集成姿势。
 * <p>
 * core 不会自动把监听器抛出的异常转为错误事件（见事件设计 §9A.4），因此这里必须
 * 调用抛出 API 而不是 {@code throw}。
 *
 * @author warm
 */
public class ThrowErrorOnVariableListener implements Listener {

    /**
     * 付款金额变量名。
     */
    public static final String VAR_PAY_AMOUNT = "payAmount";

    /**
     * 付款限额：金额大于该值即抛错进边界。
     */
    public static final BigDecimal PAY_AMOUNT_LIMIT = new BigDecimal("1000");

    @Override
    public void notify(ListenerVariable variable) {
        Task task = variable == null ? null : variable.getTask();
        if (task == null || task.getId() == null) {
            return;
        }
        BigDecimal amount = readPayAmount(variable);
        if (amount == null || amount.compareTo(PAY_AMOUNT_LIMIT) <= 0) {
            return;
        }
        FlowEngine.taskService().throwError(task.getId(), "sys_err", "boom");
    }

    /**
     * 读取付款金额：优先取本次操作变量，回退实例累积变量。
     *
     * @param variable 监听器上下文
     * @return 付款金额；未传或非数字时返回 {@code null}
     */
    private BigDecimal readPayAmount(ListenerVariable variable) {
        BigDecimal amount = parseAmount(variable.getVariable());
        if (amount != null) {
            return amount;
        }
        Instance instance = variable.getInstance();
        return parseAmount(instance == null ? null : instance.getVariableMap());
    }

    /**
     * 从变量表解析金额，兼容字符串取值。
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
            return null;
        }
    }
}
