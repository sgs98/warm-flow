package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.service.task.RejectConfig;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 实例 def_json 中的一次性任务关联；不修改共享定义，也不使用业务变量。
 * 原始 Map 读改写保留未知图字段，调用方须在同实例锁与事务内提供最新快照。
 *
 * @author may
 */
final class RejectReturnRuntime {
    private RejectReturnRuntime() {
    }

    record ReturnIntent(Long taskId, Long sourceTaskId, String returnNodeCode) {
    }

    static String configForNode(String defJson, String nodeCode) {
        return nodeConfig(node(snapshot(defJson), nodeCode));
    }

    static Optional<ReturnIntent> find(String defJson, String nodeCode, Long taskId) {
        if (StringUtils.isEmpty(defJson)) {
            return Optional.empty();
        }
        Map<String, Object> graph = snapshot(defJson);
        countPending(graph);
        Map<String, Object> tasks = ownedTasks(RejectConfig.configMap(nodeConfig(node(graph, nodeCode))));
        if (tasks == null || taskId == null || !tasks.containsKey(taskId.toString())) {
            return Optional.empty();
        }
        return Optional.of(intent(taskId.toString(), tasks.get(taskId.toString())));
    }

    static String put(String defJson, String nodeCode, Long taskId, Long sourceTaskId, String returnNodeCode) {
        Map<String, Object> graph = snapshot(defJson);
        if (countPending(graph) != 0) {
            throw invalid("首版不支持嵌套或多条返回关联");
        }
        requireId(taskId);
        requireId(sourceTaskId);
        if (StringUtils.isEmpty(returnNodeCode) || returnNodeCode.equals(nodeCode)) {
            throw invalid("返回节点必须为另一个人工节点");
        }
        node(graph, returnNodeCode);
        Map<String, Object> target = node(graph, nodeCode);
        Map<String, Object> config = RejectConfig.configMap(nodeConfig(target));
        if (config.containsKey(RejectConfig.RUNTIME_KEY)) {
            throw invalid("运行段同名属性冲突，请先迁移旧业务属性");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sourceTaskId", sourceTaskId.toString());
        fields.put("returnNodeCode", returnNodeCode);
        Map<String, Object> tasks = new LinkedHashMap<>();
        tasks.put(taskId.toString(), fields);
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("version", 1);
        runtime.put("byTaskId", tasks);
        config.put(RejectConfig.RUNTIME_KEY, runtime);
        target.put("nodeConfig", encode(config));
        return encode(graph);
    }

    static String remove(String defJson, String nodeCode, Long taskId) {
        if (StringUtils.isEmpty(defJson)) {
            return defJson;
        }
        Map<String, Object> graph = snapshot(defJson);
        countPending(graph);
        Map<String, Object> target = node(graph, nodeCode);
        Map<String, Object> config = RejectConfig.configMap(nodeConfig(target));
        Map<String, Object> tasks = ownedTasks(config);
        if (tasks == null || taskId == null || !tasks.containsKey(taskId.toString())) {
            return defJson;
        }
        tasks.remove(taskId.toString());
        if (tasks.isEmpty()) {
            config.remove(RejectConfig.RUNTIME_KEY);
        }
        target.put("nodeConfig", encode(config));
        return encode(graph);
    }

    static String clear(String defJson) {
        if (StringUtils.isEmpty(defJson)) {
            return defJson;
        }
        Map<String, Object> graph = snapshot(defJson);
        countPending(graph);
        boolean changed = false;
        for (Object value : nodes(graph)) {
            Map<String, Object> node = object(value);
            Map<String, Object> config = RejectConfig.configMap(nodeConfig(node));
            if (ownedTasks(config) != null) {
                config.remove(RejectConfig.RUNTIME_KEY);
                node.put("nodeConfig", encode(config));
                changed = true;
            }
        }
        return changed ? encode(graph) : defJson;
    }

    static boolean hasPending(String defJson) {
        return StringUtils.isNotEmpty(defJson) && countPending(snapshot(defJson)) > 0;
    }

    private static int countPending(Map<String, Object> graph) {
        int count = 0;
        for (Object value : nodes(graph)) {
            Map<String, Object> tasks = ownedTasks(RejectConfig.configMap(nodeConfig(object(value))));
            if (tasks == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : tasks.entrySet()) {
                intent(entry.getKey(), entry.getValue());
                count++;
            }
        }
        if (count > 1) {
            throw invalid("首版仅允许一条有效返回关联");
        }
        return count;
    }

    private static ReturnIntent intent(String key, Object value) {
        Long taskId = decimalId(key);
        Map<String, Object> fields = object(value);
        Long sourceId = decimalId(fields.get("sourceTaskId"));
        Object returnCode = fields.get("returnNodeCode");
        if (!(returnCode instanceof String code) || StringUtils.isEmpty(code)) {
            throw invalid("returnNodeCode 必须为非空字符串");
        }
        return new ReturnIntent(taskId, sourceId, code);
    }

    /**
     * 未标记版本的历史同名属性不清理、不覆盖；已识别的协议严格校验。
     */
    private static Map<String, Object> ownedTasks(Map<String, Object> config) {
        Object value = config.get(RejectConfig.RUNTIME_KEY);
        if (!(value instanceof Map<?, ?> runtime) || !runtime.containsKey("version")) {
            return null;
        }
        RejectConfig.requireVersionOne(runtime.get("version"), RejectConfig.RUNTIME_KEY + ".version");
        return object(runtime.get("byTaskId"));
    }

    private static Long decimalId(Object value) {
        if (!(value instanceof String text) || !text.matches("[1-9][0-9]*")) {
            throw invalid("任务 ID 必须为十进制正整数字符串");
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            throw invalid("任务 ID 超出 Long 范围");
        }
    }

    private static void requireId(Long value) {
        if (value == null || value <= 0) {
            throw invalid("任务 ID 不能为空或非正数");
        }
    }

    private static Map<String, Object> snapshot(String json) {
        if (StringUtils.isEmpty(json)) {
            throw invalid("实例快照为空");
        }
        Map<String, Object> graph = RejectConfig.configMap(json);
        nodes(graph);
        return graph;
    }

    private static List<?> nodes(Map<String, Object> graph) {
        if (!(graph.get("nodeList") instanceof List<?> nodes)) {
            throw invalid("实例快照 nodeList 必须为数组");
        }
        return nodes;
    }

    private static Map<String, Object> node(Map<String, Object> graph, String code) {
        Map<String, Object> found = null;
        for (Object value : nodes(graph)) {
            Map<String, Object> node = object(value);
            if (code != null && code.equals(node.get("nodeCode"))) {
                if (found != null) {
                    throw invalid("实例快照节点编码重复: " + code);
                }
                found = node;
            }
        }
        if (found == null) {
            throw invalid("实例快照节点不存在: " + code);
        }
        return found;
    }

    private static String nodeConfig(Map<String, Object> node) {
        Object config = node.get("nodeConfig");
        if (config == null || config instanceof String) {
            return (String) config;
        }
        throw invalid("实例 nodeConfig 必须为 JSON 字符串");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.keySet().stream().anyMatch(k -> !(k instanceof String))) {
            throw invalid("运行段 / 节点字段必须为对象");
        }
        return (Map<String, Object>) map;
    }

    /**
     * 仅本功能的 JSON 树保留 null/空值；不改变各 SPI 对普通 DTO 的省略策略。
     */
    private static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) {
            java.util.StringJoiner fields = new java.util.StringJoiner(",", "{", "}");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw invalid("JSON 对象键必须为字符串");
                fields.add(FlowEngine.jsonConvert.objToStr(key) + ":" + encode(entry.getValue()));
            }
            return fields.toString();
        }
        if (value instanceof List<?> list) {
            java.util.StringJoiner items = new java.util.StringJoiner(",", "[", "]");
            for (Object item : list) items.add(encode(item));
            return items.toString();
        }
        if (value instanceof Number number) {
            // 数字已按原 token 精确解析；SPI 标量编码仍可能先转 Double。
            return number.toString();
        }
        if (value instanceof String || value instanceof Boolean) {
            return FlowEngine.jsonConvert.objToStr(value);
        }
        throw invalid("运行快照包含非 JSON 值");
    }

    private static FlowException invalid(String message) {
        return new FlowException("驳回直返运行段错误: " + message);
    }
}
