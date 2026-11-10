package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.GroupSignConfig;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.spi.PermissionHandler;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.GroupSignUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * 一次节点到达的写前计划：先构造完整任务批次并解析组签办理人，再按原有顺序执行监听器和持久化动作。
 *
 * @author may
 */
final class ArrivalPlan {

    private final List<Task> tasks = new ArrayList<>();
    private final List<Runnable> commitActions = new ArrayList<>();
    private final List<Node> publisherChecks = new ArrayList<>();
    private final Set<String> catchNodes = new HashSet<>();

    List<Task> tasks() {
        return tasks;
    }

    void addTask(Task task) {
        tasks.add(task);
    }

    void validateCommitActions() {
        publisherChecks.forEach(node -> EventArrivalDispatcher.validatePublisher(node));
    }

    void validatePublisher(Node node) {
        publisherChecks.add(node);
    }

    boolean addCatchNode(Long instanceId, String nodeCode) {
        return catchNodes.add(instanceId + ":" + nodeCode);
    }

    void deferCommit(Runnable action) {
        commitActions.add(action);
    }

    void commit() {
        List<Runnable> actions = new ArrayList<>(commitActions);
        commitActions.clear();
        actions.forEach(Runnable::run);
    }

    /**
     * 在任何到达写入前解析整批组签办理人；普通任务仍留给 assignment 监听器之后的既有构造点。
     */
    void resolveGroupUsers() {
        prepareGroupUsers(tasks);
    }

    /**
     * 为任务批次批量加载节点，并仅预解析组签任务。
     */
    static void prepareGroupUsers(List<Task> tasks) {
        if (CollUtil.isEmpty(tasks)) {
            return;
        }
        Map<Long, List<Task>> tasksByDefinition = new LinkedHashMap<>();
        for (Task task : tasks) {
            tasksByDefinition.computeIfAbsent(task.getDefinitionId(), key -> new ArrayList<>()).add(task);
        }
        for (Map.Entry<Long, List<Task>> entry : tasksByDefinition.entrySet()) {
            Map<String, Node> nodes = new LinkedHashMap<>();
            for (Node node : FlowEngine.nodeService().getByDefId(entry.getKey())) {
                nodes.put(node.getNodeCode(), node);
            }
            for (Task task : entry.getValue()) {
                Node node = nodes.get(task.getNodeCode());
                if (node != null && CooperateType.isGroup(node.getCooperateType())
                    && CollUtil.isEmpty(task.getUserList())) {
                    task.setUserList(resolveGroupUsers(task, node));
                }
            }
        }
    }

    private static List<User> resolveGroupUsers(Task task, Node node) {
        GroupSignConfig config = GroupSignUtil.require(node);
        if (config.getGroups() == null || config.getGroups().isEmpty()) {
            throw unresolved(node, null, 0, null);
        }
        List<User> users = new ArrayList<>();
        for (int index = 0; index < config.getGroups().size(); index++) {
            GroupSignConfig.Group group = config.getGroups().get(index);
            List<String> members = group == null ? null : group.getMembers();
            List<String> handlers = resolveHandlers(members, node, group, index);
            for (String handler : handlers) {
                users.add(FlowEngine.userService().structureUser(task.getId(), handler
                    , UserType.APPROVAL.getKey()).setSeqNo(index));
            }
        }
        return users;
    }

    private static List<String> resolveHandlers(List<String> members, Node node, GroupSignConfig.Group group
        , int index) {
        List<String> source = new ArrayList<>();
        if (members != null) {
            for (String member : members) {
                if (StringUtils.isNotEmpty(member) && !member.trim().isEmpty()) {
                    source.add(member.trim());
                }
            }
        }
        PermissionHandler permissionHandler = FlowEngine.permissionHandler();
        List<String> resolved;
        try {
            resolved = permissionHandler == null
                ? concreteHandlers(source) : permissionHandler.resolveGroupHandlers(source);
        } catch (RuntimeException e) {
            throw unresolved(node, group, index, e);
        }
        resolved = concreteHandlers(resolved);
        if (resolved.isEmpty()) {
            throw unresolved(node, group, index, null);
        }
        return resolved;
    }

    private static List<String> concreteHandlers(List<String> handlers) {
        if (CollUtil.isEmpty(handlers)) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> concrete = new LinkedHashSet<>();
        for (String handler : handlers) {
            if (handler == null || handler.trim().isEmpty()) {
                continue;
            }
            String value = handler.trim();
            if (!value.isEmpty()) {
                concrete.add(value);
            }
        }
        return new ArrayList<>(concrete);
    }

    private static FlowException unresolved(Node node, GroupSignConfig.Group group, int index, Throwable cause) {
        String groupId = group == null || StringUtils.isEmpty(group.getId())
            ? String.valueOf(index) : group.getId();
        String message = "[" + node.getNodeCode() + "]组 " + groupId + " 的成员无法解析为办理人";
        return cause == null ? new FlowException(message) : new FlowException(message, cause);
    }
}
