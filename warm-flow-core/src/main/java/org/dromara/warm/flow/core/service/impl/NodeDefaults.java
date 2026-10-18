package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.utils.MapUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点默认流程变量：解析 {@code node_config.variables} 段并在缺少同名变量时补充进流程变量。
 * <p>
 * 每个节点都可以配置，不局限于事件节点。语义是「**兜底默认值**」而不是「赋值」：
 * 流程运行到该节点时，只有流程变量里**还没有**这个键才会写入，
 * 办理时由宿主传入的同名变量优先——这也是「默认」二字的含义，
 * 否则先到节点的默认值会把业务实时传入的值盖掉。
 * <p>
 * 典型用途：条件网关依赖某个变量做选路，但按费率、赔付金额这类变量并非每次必传时，
 * 在网关前的节点上配一个兜底值，避免求值时抛「跳转条件不能为空」。
 *
 * @author may
 */
final class NodeDefaults {

    private NodeDefaults() {
    }

    /**
     * 读取节点配置的默认流程变量。
     * <p>
     * 优先取 {@code node_config.variables}；为空时回落 {@code node_config.event.variables}
     * （早期版本只有事件节点支持默认变量，存量 JSON 无需迁移）。
     *
     * @param node 节点
     * @return 默认变量；未配置时返回空 Map
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> of(Node node) {
        if (node == null || StringUtils.isEmpty(node.getNodeConfig())) {
            return Collections.emptyMap();
        }
        Map<String, Object> root = FlowEngine.jsonConvert.strToMap(node.getNodeConfig());
        if (MapUtil.isEmpty(root)) {
            return Collections.emptyMap();
        }
        Object section = root.get("variables");
        if (!(section instanceof Map)) {
            Object event = root.get("event");
            section = event instanceof Map ? ((Map<String, Object>) event).get("variables") : null;
        }
        if (!(section instanceof Map) || ((Map<String, Object>) section).isEmpty()) {
            return Collections.emptyMap();
        }
        return new LinkedHashMap<>((Map<String, Object>) section);
    }

    /**
     * 把节点默认变量补充进流程变量，已有同名键不覆盖。
     *
     * @param variables 本次流程变量，原地补充
     * @param node      刚到达 / 正在路由经过的节点
     * @return 是否补充了至少一个键
     */
    static boolean apply(Map<String, Object> variables, Node node) {
        return apply(variables, of(node));
    }

    /**
     * 把节点默认变量补充进流程变量，已有同名键不覆盖。
     *
     * @param variables 本次流程变量，原地补充
     * @param defaults  已解析的节点默认变量
     * @return 是否补充了至少一个键
     */
    static boolean apply(Map<String, Object> variables, Map<String, Object> defaults) {
        if (variables == null || defaults.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (!variables.containsKey(entry.getKey())) {
                variables.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        return changed;
    }
}
