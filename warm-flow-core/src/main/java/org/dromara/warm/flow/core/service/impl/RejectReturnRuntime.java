package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.service.task.RejectConfig;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 驳回直返的一次性运行关联，存放于实例流程变量的引擎保留键中。
 * <p>
 * 关联只由引擎写入（驳回事务内），业务变量入口会过滤 {@code _wf_} 前缀的键，避免调用方伪造直返；
 * A 节点办结消费后立即清除。任务 ID 以字符串保存，避免 JSON 数字精度丢失。
 *
 * @author may
 */
final class RejectReturnRuntime {

    private RejectReturnRuntime() {
    }

    record ReturnIntent(Long taskId, Long sourceTaskId, String returnNodeCode) {
    }

    static String configForNode(String defJson, String nodeCode) {
        return RejectConfig.configForNode(defJson, nodeCode);
    }

    static Optional<ReturnIntent> find(Instance instance, String nodeCode, Long taskId) {
        Map<String, Object> runtime = runtime(instance);
        if (runtime == null) {
            return Optional.empty();
        }
        validateFields(runtime);
        Long storedTask = decimalId(runtime.get("taskId"), "taskId");
        Long sourceTask = decimalId(runtime.get("sourceTaskId"), "sourceTaskId");
        String returnNode = (String) runtime.get("returnNodeCode");
        String fromNode = (String) runtime.get("nodeCode");
        if (!fromNode.equals(nodeCode) || !storedTask.equals(taskId)) {
            return Optional.empty();
        }
        return Optional.of(new ReturnIntent(storedTask, sourceTask, returnNode));
    }

    static void put(Instance instance, String nodeCode, Long taskId, Long sourceTaskId, String returnNodeCode) {
        if (runtime(instance) != null) {
            throw invalid("首版不支持嵌套或多条返回关联");
        }
        requireId(taskId);
        requireId(sourceTaskId);
        if (StringUtils.isEmpty(nodeCode) || StringUtils.isEmpty(returnNodeCode)
            || returnNodeCode.equals(nodeCode)) {
            throw invalid("返回节点必须为另一个人工节点");
        }
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("nodeCode", nodeCode);
        runtime.put("taskId", taskId.toString());
        runtime.put("sourceTaskId", sourceTaskId.toString());
        runtime.put("returnNodeCode", returnNodeCode);
        write(instance, runtime);
    }

    static void remove(Instance instance, String nodeCode, Long taskId) {
        Optional<ReturnIntent> intent = findOrEmpty(instance, nodeCode, taskId);
        if (intent.isPresent()) {
            erase(instance);
        }
    }

    static void clear(Instance instance) {
        erase(instance);
    }

    static boolean hasPending(Instance instance) {
        Map<String, Object> runtime = runtime(instance);
        if (runtime == null) {
            return false;
        }
        validateFields(runtime);
        return true;
    }

    private static void validateFields(Map<String, Object> runtime) {
        decimalId(runtime.get("taskId"), "taskId");
        decimalId(runtime.get("sourceTaskId"), "sourceTaskId");
        if (!(runtime.get("returnNodeCode") instanceof String returnNode) || StringUtils.isEmpty(returnNode)
            || !(runtime.get("nodeCode") instanceof String fromNode) || StringUtils.isEmpty(fromNode)) {
            throw invalid("returnNodeCode / nodeCode 必须为非空字符串");
        }
    }

    private static Optional<ReturnIntent> findOrEmpty(Instance instance, String nodeCode, Long taskId) {
        return find(instance, nodeCode, taskId);
    }

    private static Map<String, Object> runtime(Instance instance) {
        if (instance == null) {
            return null;
        }
        Object value = instance.getVariableMap().get(RejectConfig.FLOW_VARIABLE_KEY);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw invalid("运行关联必须为对象");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (!(key instanceof String text)) {
                throw invalid("运行关联键必须为字符串");
            }
            copy.put(text, item);
        });
        return copy;
    }

    private static void write(Instance instance, Map<String, Object> runtime) {
        Map<String, Object> variables = new LinkedHashMap<>(instance.getVariableMap());
        variables.put(RejectConfig.FLOW_VARIABLE_KEY, runtime);
        instance.setVariable(FlowEngine.jsonConvert.objToStr(variables));
    }

    private static void erase(Instance instance) {
        if (instance == null) {
            return;
        }
        Map<String, Object> variables = new LinkedHashMap<>(instance.getVariableMap());
        if (variables.remove(RejectConfig.FLOW_VARIABLE_KEY) != null) {
            instance.setVariable(FlowEngine.jsonConvert.objToStr(variables));
        }
    }

    private static Long decimalId(Object value, String field) {
        if (!(value instanceof String text) || !text.matches("[1-9][0-9]*")) {
            throw invalid(field + " 必须为十进制正整数字符串");
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            throw invalid(field + " 超出 Long 范围");
        }
    }

    private static void requireId(Long value) {
        if (value == null || value <= 0) {
            throw invalid("任务 ID 不能为空或非正数");
        }
    }

    private static FlowException invalid(String message) {
        return new FlowException("驳回直返运行关联错误: " + message);
    }
}
