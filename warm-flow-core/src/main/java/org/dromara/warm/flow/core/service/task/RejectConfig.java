package org.dromara.warm.flow.core.service.task;

import lombok.Getter;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 节点设计期驳回规则；历史无版本自定义属性不解释为引擎配置。
 *
 * @author may
 */
@Getter
public final class RejectConfig {
    public static final String RUNTIME_KEY = "__rejectReturnRuntime";
    public static final String RETURN_TO_REJECTOR = "RETURN_TO_REJECTOR";
    private final boolean returnToRejector;

    private RejectConfig(boolean returnToRejector) {
        this.returnToRejector = returnToRejector;
    }

    public static RejectConfig parse(String nodeConfig) {
        Map<String, Object> config = configMap(nodeConfig);
        Object value = config.get("reject");
        if (!(value instanceof Map<?, ?> section) || !section.containsKey("schemaVersion")) {
            return new RejectConfig(false);
        }
        requireVersionOne(section.get("schemaVersion"), "reject.schemaVersion");
        Object mode = section.get("afterApproval");
        if (mode == null || "REPLAY".equals(mode)) {
            return new RejectConfig(false);
        }
        if (!RETURN_TO_REJECTOR.equals(mode)) {
            throw new FlowException("reject.afterApproval 必须为 REPLAY 或 RETURN_TO_REJECTOR");
        }
        return new RejectConfig(true);
    }

    /** starter 在缺少事务管理器时的写前门禁，不在 core 猜测宿主事务。 */
    public static boolean requiresAtomicExecution(String defJson) {
        if (StringUtils.isEmpty(defJson)) {
            return false;
        }
        DefJson definition;
        try {
            definition = FlowEngine.jsonConvert.strToBean(defJson, DefJson.class);
        } catch (RuntimeException e) {
            throw new FlowException("驳回直返实例快照格式错误: " + e.getMessage());
        }
        if (definition == null || definition.getNodeList() == null) {
            throw new FlowException("驳回直返实例快照缺少节点");
        }
        boolean required = false;
        for (NodeJson node : definition.getNodeList()) {
            required |= parse(node.getNodeConfig()).isReturnToRejector();
            Object runtime = configMap(node.getNodeConfig()).get(RUNTIME_KEY);
            if (runtime instanceof Map<?, ?> section && section.containsKey("version")) {
                requireVersionOne(section.get("version"), RUNTIME_KEY + ".version");
                if (!(section.get("byTaskId") instanceof Map<?, ?> tasks)) {
                    throw new FlowException("驳回直返运行段 byTaskId 必须为对象");
                }
                required |= !tasks.isEmpty();
            }
        }
        return required;
    }

    /** 复用配置解析，同时拒绝非对象 / 非法 JSON，不把解析失败降级为重走。 */
    public static Map<String, Object> configMap(String nodeConfig) {
        if (StringUtils.isEmpty(nodeConfig)) {
            return new java.util.LinkedHashMap<>();
        }
        String json = nodeConfig.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new FlowException("驳回直返 nodeConfig 必须为 JSON 对象");
        }
        try {
            Map<String, Object> map = exactMap(json);
            if (map == null) {
                throw new FlowException("驳回直返 nodeConfig 必须为 JSON 对象");
            }
            return new java.util.LinkedHashMap<>(map);
        } catch (RuntimeException e) {
            throw new FlowException("驳回直返 nodeConfig 格式错误: " + e.getMessage());
        }
    }

    private static final java.util.regex.Pattern JSON_TOKEN = java.util.regex.Pattern.compile(
        "\"(?:\\\\.|[^\"\\\\])*\"|-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

    /** 数字先作字符串占位，再按原 token 还原，避免 Map SPI 的 Double 舍入和 snack3 指数缺陷。 */
    private static Map<String, Object> exactMap(String json) {
        var strings = new java.util.HashSet<String>();
        var matcher = JSON_TOKEN.matcher(json);
        while (matcher.find()) {
            if (matcher.group().startsWith("\"")) {
                strings.add(FlowEngine.jsonConvert.strToBean(matcher.group(), String.class));
            }
        }
        String prefix = "__rejectNumber__";
        while (hasPrefix(strings, prefix)) prefix += "_";
        Map<String, Number> numbers = new java.util.HashMap<>();
        StringBuilder masked = new StringBuilder(json.length());
        matcher.reset();
        while (matcher.find()) {
            String token = matcher.group();
            if (token.startsWith("\"")) continue;
            String key = prefix + numbers.size();
            Number number;
            if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
                number = new BigDecimal(token);
            } else {
                try { number = Long.valueOf(token); }
                catch (NumberFormatException e) { number = new java.math.BigInteger(token); }
            }
            numbers.put(key, number);
            matcher.appendReplacement(masked, java.util.regex.Matcher.quoteReplacement("\"" + key + "\""));
        }
        matcher.appendTail(masked);
        Map<String, Object> map = FlowEngine.jsonConvert.strToMap(masked.toString());
        if (map == null) return null;
        map.replaceAll((key, value) -> restoreNumbers(value, numbers));
        return map;
    }

    private static boolean hasPrefix(java.util.Set<String> strings, String prefix) {
        return strings.stream().anyMatch(value -> value.startsWith(prefix));
    }

    private static Object restoreNumbers(Object value, Map<String, Number> numbers) {
        if (value instanceof String text && numbers.containsKey(text)) return numbers.get(text);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> restored = new java.util.LinkedHashMap<>();
            map.forEach((key, item) -> restored.put((String) key, restoreNumbers(item, numbers)));
            return restored;
        }
        if (value instanceof java.util.List<?> list) {
            java.util.List<Object> restored = new java.util.ArrayList<>();
            for (Object item : list) restored.add(restoreNumbers(item, numbers));
            return restored;
        }
        return value;
    }

    /** JSON 实现可能返回 Integer / Long / Double / BigDecimal，协议判断数值而非 Java 类型。 */
    public static void requireVersionOne(Object version, String field) {
        if (version instanceof Number) {
            try {
                if (new BigDecimal(version.toString()).compareTo(BigDecimal.ONE) == 0) {
                    return;
                }
            } catch (NumberFormatException ignored) {
                // NaN / Infinity 不属于 JSON 整数版本。
            }
        }
        throw new FlowException(field + " 必须为数值整数 1");
    }
}
