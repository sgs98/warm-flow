package org.dromara.warm.flow.core.utils;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.UserType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 待办查询工具：按办理人查待办，并对顺序会签节点做「仅当前位可见」过滤。
 *
 * @author may
 */
public final class TodoQueryUtil {

    private TodoQueryUtil() {
    }

    /**
     * 按办理人查询当前待办；顺序会签节点只返回当前位的待办。
     *
     * @param handler 办理人唯一标识
     * @return 该办理人的当前待办集合
     */
    public static List<Task> listTodoByHandler(String handler) {
        if (StringUtils.isEmpty(handler)) {
            return Collections.emptyList();
        }
        // 审批 / 转办 / 委派三类办理人；抄送记录关联的是历史任务，不进入待办
        List<Long> taskIds = new ArrayList<>();
        for (User user : FlowEngine.userService().list(FlowEngine.newUser().setProcessedBy(handler))) {
            if (ObjectUtil.isNull(user.getAssociated())) {
                continue;
            }
            String type = user.getType();
            if (UserType.APPROVAL.getKey().equals(type) || UserType.TRANSFER.getKey().equals(type)
                || UserType.DEPUTE.getKey().equals(type)) {
                taskIds.add(user.getAssociated());
            }
        }
        if (CollUtil.isEmpty(taskIds)) {
            return Collections.emptyList();
        }
        List<Task> tasks = StreamUtils.filter(FlowEngine.taskService()
            .getByIds(taskIds.stream().distinct().toList()), Objects::nonNull);
        if (CollUtil.isEmpty(tasks)) {
            return Collections.emptyList();
        }
        // 批量取办理人（一次查询）与节点（按定义一次查询），避免逐任务 N+1
        Map<Long, List<User>> usersByTask = new HashMap<>();
        for (User user : FlowEngine.userService().getByAssociateds(
            StreamUtils.toList(tasks, Task::getId), UserType.APPROVAL.getKey()
            , UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey())) {
            usersByTask.computeIfAbsent(user.getAssociated(), key -> new ArrayList<>()).add(user);
        }
        Map<Long, Map<String, Node>> nodesByDefinition = new HashMap<>();
        List<Task> result = new ArrayList<>();
        for (Task task : tasks) {
            Map<String, Node> nodeMap = nodesByDefinition.computeIfAbsent(task.getDefinitionId()
                , definitionId -> StreamUtils.toMap(FlowEngine.nodeService()
                        .list(FlowEngine.newNode().setDefinitionId(definitionId))
                    , Node::getNodeCode, node -> node));
            Node node = nodeMap.get(task.getNodeCode());
            if (visibleTo(node, usersByTask.get(task.getId()), handler)) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * 判断待办对当前办理人是否可见。
     * <p>
     * 顺序组签按「最小组序号」过滤，同组任一成员都可见；并行组签不做顺序过滤。
     * 顺序会签保持原有「最小顺序位的那一名办理人」语义。
     *
     * @param node    待办节点；节点缺失时保持可见（与既有兼容行为一致）
     * @param users   待办的活动办理人集合
     * @param handler 办理人标识
     * @return true：可见
     */
    private static boolean visibleTo(Node node, List<User> users, String handler) {
        if (node == null) {
            return true;
        }
        if (CooperateType.isGroup(node.getCooperateType())) {
            return !GroupSignUtil.isSequence(node) || SequenceSignUtil.isCurrentGroup(users, handler);
        }
        return !CooperateType.isSequence(node.getCooperateType(), node.getNodeRatio())
            || SequenceSignUtil.isCurrentPosition(users, handler);
    }
}