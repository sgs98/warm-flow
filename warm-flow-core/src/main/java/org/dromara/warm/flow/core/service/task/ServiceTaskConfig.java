package org.dromara.warm.flow.core.service.task;

import lombok.Getter;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.MapUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.Map;

/**
 * 服务任务节点配置：解析并校验 node_config.serviceTask。
 *
 * @author warm
 */
@Getter
public class ServiceTaskConfig {

    /**
     * class 模式。
     */
    public static final String TYPE_CLASS = "class";
    /**
     * 委托表达式模式。
     */
    public static final String TYPE_DELEGATE_EXPRESSION = "delegateExpression";
    /**
     * 方法表达式模式。
     */
    public static final String TYPE_EXPRESSION = "expression";

    private final String type;
    private final String handler;
    private final String expression;
    private final String resultVariable;

    private ServiceTaskConfig(String type, String handler, String expression, String resultVariable) {
        this.type = type;
        this.handler = handler;
        this.expression = expression;
        this.resultVariable = resultVariable;
    }

    /**
     * 解析服务任务配置，格式非法即抛出流程异常（保存期与运行期共用）。
     *
     * @param nodeConfig 节点配置 JSON
     * @return 服务任务配置
     */
    @SuppressWarnings("unchecked")
    public static ServiceTaskConfig parse(String nodeConfig) {
        AssertUtil.isTrue(StringUtils.isEmpty(nodeConfig), ExceptionCons.NULL_SERVICE_TASK_CONFIG);
        Map<String, Object> root = FlowEngine.jsonConvert.strToMap(nodeConfig);
        AssertUtil.isTrue(MapUtil.isEmpty(root), ExceptionCons.NULL_SERVICE_TASK_CONFIG);
        Object section = root.get("serviceTask");
        AssertUtil.isTrue(!(section instanceof Map), ExceptionCons.NULL_SERVICE_TASK_SECTION);
        Map<String, Object> task = (Map<String, Object>) section;

        String type = str(task.get("type"));
        String handler = str(task.get("handler"));
        String expression = str(task.get("expression"));
        String resultVariable = str(task.get("resultVariable"));

        boolean typeOk = TYPE_CLASS.equals(type)
            || TYPE_DELEGATE_EXPRESSION.equals(type) || TYPE_EXPRESSION.equals(type);
        AssertUtil.isTrue(!typeOk, ExceptionCons.ILLEGAL_SERVICE_TASK_TYPE);
        if (TYPE_CLASS.equals(type)) {
            AssertUtil.isTrue(StringUtils.isEmpty(handler), ExceptionCons.NULL_SERVICE_TASK_EXPRESSION);
        } else {
            AssertUtil.isTrue(StringUtils.isEmpty(expression), ExceptionCons.NULL_SERVICE_TASK_EXPRESSION);
        }
        if (StringUtils.isNotEmpty(resultVariable)) {
            AssertUtil.isTrue(!TYPE_EXPRESSION.equals(type), ExceptionCons.ILLEGAL_RESULT_VARIABLE);
        }
        return new ServiceTaskConfig(type, handler, expression, resultVariable);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
