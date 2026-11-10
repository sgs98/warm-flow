package org.dromara.warm.flow.core.utils;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.GroupSignConfig;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.exception.FlowException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 组签配置解析工具，只读取 {@code node_config.groupSign} 命名空间。
 *
 * @author may
 */
public final class GroupSignUtil {

    private GroupSignUtil() {
    }

    /**
     * 解析组签配置；没有 {@code groupSign} 段时返回 {@code null}。
     *
     * @param nodeConfig 节点配置 JSON
     * @return 组签配置或 {@code null}
     */
    public static GroupSignConfig parse(String nodeConfig) {
        if (StringUtils.isEmpty(nodeConfig)) {
            return null;
        }
        Map<String, Object> root = FlowEngine.jsonConvert.strToMap(nodeConfig);
        if (MapUtil.isEmpty(root) || !root.containsKey("groupSign")) {
            return null;
        }
        Object sectionValue = root.get("groupSign");
        if (!(sectionValue instanceof Map<?, ?> section)) {
            throw malformed("groupSign 必须是对象");
        }

        String order = stringValue(section.get("order"), "groupSign.order");
        if (StringUtils.isEmpty(order)) {
            order = CooperateType.PARALLEL;
        }
        List<GroupSignConfig.Group> groups = new ArrayList<>();
        Object groupsValue = section.get("groups");
        if (groupsValue != null) {
            if (!(groupsValue instanceof List<?> groupList)) {
                throw malformed("groupSign.groups 必须是数组");
            }
            for (Object groupValue : groupList) {
                if (!(groupValue instanceof Map<?, ?> groupMap)) {
                    throw malformed("groupSign.groups 的元素必须是对象");
                }
                groups.add(new GroupSignConfig.Group()
                    .setId(stringValue(groupMap.get("id"), "groupSign.groups[].id"))
                    .setName(stringValue(groupMap.get("name"), "groupSign.groups[].name"))
                    .setMembers(members(groupMap.get("members"))));
            }
        }
        return new GroupSignConfig().setOrder(order).setGroups(groups);
    }

    /**
     * 读取节点必需的组签配置，缺失或格式错误时抛出带节点编码的流程异常。
     *
     * @param node 组签节点
     * @return 组签配置
     */
    public static GroupSignConfig require(Node node) {
        try {
            GroupSignConfig config = parse(node.getNodeConfig());
            if (config == null) {
                throw malformed("缺少 groupSign 配置段");
            }
            return config;
        } catch (FlowException e) {
            throw new FlowException("[" + node.getNodeCode() + "]" + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new FlowException("[" + node.getNodeCode() + "]组签配置不是合法 JSON", e);
        }
    }

    /**
     * 判断组签节点是否按组顺序办理。
     *
     * @param node 组签节点
     * @return 顺序办理时返回 {@code true}
     */
    public static boolean isSequence(Node node) {
        return CooperateType.SEQUENCE.equalsIgnoreCase(require(node).getOrder());
    }

    private static List<String> members(Object value) {
        List<String> members = new ArrayList<>();
        if (value == null) {
            return members;
        }
        if (!(value instanceof List<?> memberList)) {
            throw malformed("groupSign.groups[].members 必须是数组");
        }
        for (Object member : memberList) {
            if (!(member instanceof String)) {
                throw malformed("groupSign.groups[].members 的元素必须是字符串");
            }
            members.add((String) member);
        }
        return members;
    }

    private static String stringValue(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw malformed(path + " 必须是字符串");
        }
        return (String) value;
    }

    private static FlowException malformed(String message) {
        return new FlowException("组签配置错误：" + message);
    }
}
