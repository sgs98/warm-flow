package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.dto.PathWayData;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.ChartStatus;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.spi.ListenerVariable;
import org.dromara.warm.flow.core.spi.PermissionHandler;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.ListenerUtil;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 结束流程实例重置编排器。
 *
 * <p>仅供 {@code WorkflowService} 门面实现调用，避免把写操作扩展到公开的 {@code InsService} 契约。</p>
 *
 * @author warm
 */
public final class InstanceResetCoordinator {

    private InstanceResetCoordinator() {
    }

    /**
     * 将已完成、已终止或已作废的实例重置到指定人工审批节点。
     *
     * @param instanceId     流程实例主键
     * @param targetNodeCode 目标节点编码
     * @param handlers       指定审批人
     * @param context        操作上下文
     * @return 重置后的流程实例
     */
    public static Instance reset(Long instanceId, String targetNodeCode, List<String> handlers
        , WorkflowContext context) {
        AssertUtil.isNull(instanceId, ExceptionCons.NULL_INSTANCE_ID);
        AssertUtil.isEmpty(targetNodeCode, "目标节点编码不能为空");
        AssertUtil.isTrue(CollUtil.isEmpty(handlers), "重置审批人不能为空");

        Instance instance = FlowEngine.insService().getById(instanceId);
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);
        Definition definition = FlowEngine.defService().getById(instance.getDefinitionId());
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        AssertUtil.isFalse(ActivityStatus.isActivity(definition.getActivityStatus())
            && ActivityStatus.isActivity(instance.getActivityStatus()), ExceptionCons.NOT_ACTIVITY);

        String status = instance.getFlowStatus();
        boolean resettable = Objects.equals(status, FlowStatusMachine.mapper().instanceStatus(FlowStatus.FINISHED))
            || Objects.equals(status, FlowStatusMachine.mapper().instanceStatus(FlowStatus.TERMINATE))
            || Objects.equals(status, FlowStatusMachine.mapper().instanceStatus(FlowStatus.INVALID));
        AssertUtil.isFalse(resettable, "只有已完成、已终止或已作废的流程实例才能重置");
        AssertUtil.isTrue(CollUtil.isNotEmpty(FlowEngine.taskService().getByInsId(instanceId))
            , "流程实例存在待办任务，不能重置");

        Node targetNode = FlowEngine.nodeService().getByDefIdAndNodeCode(instance.getDefinitionId(), targetNodeCode);
        AssertUtil.isNull(targetNode, "重置目标节点不存在");
        if (StringUtils.isNotEmpty(instance.getDefJson())) {
            DefJson snapshot = FlowEngine.jsonConvert.strToBean(instance.getDefJson(), DefJson.class);
            AssertUtil.isTrue(snapshot == null || CollUtil.isEmpty(snapshot.getNodeList())
                || snapshot.getNodeList().stream().noneMatch(node -> targetNodeCode.equals(node.getNodeCode()))
                , "重置目标不在流程实例定义快照中");
        }
        AssertUtil.isFalse(NodeType.isBetween(targetNode.getNodeType())
            && StringUtils.isEmpty(targetNode.getEventType()), "重置目标必须是普通人工审批节点");

        List<String> resetHandlers = normalizeHandlers(handlers);
        AssertUtil.isTrue(CollUtil.isEmpty(resetHandlers), "重置审批人不能为空");

        Task task = FlowEngine.taskService().addTask(targetNode, instance, definition, context
            , SkipType.PASS.getKey());
        task.setPermissionList(resetHandlers)
            .setCreateBy(context.getHandler())
            .setUpdateBy(context.getHandler());
        List<Node> targetNodes = Collections.singletonList(targetNode);
        List<Task> tasks = Collections.singletonList(task);
        Map<String, Object> variables = new HashMap<>(instance.getVariableMap());
        context.setVariables(variables);
        NodeDefaults.apply(context.getVariables(), targetNode);
        ListenerVariable assignment = new ListenerVariable(definition, instance, targetNode
            , context.getVariables(), task, targetNodes, tasks).setContext(context);
        ListenerUtil.executeAssignment(assignment);
        List<User> users = FlowEngine.userService().taskAddUsers(tasks);

        PathWayData pathWayData = new PathWayData()
            .setInsId(instanceId)
            .setDefId(instance.getDefinitionId())
            .setSkipType(SkipType.REJECT.getKey())
            .setDefJson(instance.getDefJson())
            .setPathWayNodes(targetNodes)
            .setTargetNodes(targetNodes);
        String defJson = StringUtils.isEmpty(instance.getDefJson())
            ? FlowEngine.chartService().startMetadata(pathWayData)
            : FlowEngine.chartService().skipMetadata(pathWayData);
        defJson = clearOtherTodoMarkers(defJson, targetNodeCode);

        FlowEngine.eventWaitService().cancelByInstanceIds(Collections.singletonList(instanceId));
        FlowEngine.taskService().save(task);
        EventArrivalDispatcher.registerBoundaryWaits(
            FlowExecution.loadForEvent(instance, targetNodeCode, context), targetNode, task);
        CopyAdvancer.copyOnArrival(tasks, instance, context);
        FlowEngine.taskService().mergeVariable(instance, context.getVariables());
        instance.setNodeType(targetNode.getNodeType())
            .setNodeCode(targetNode.getNodeCode())
            .setNodeName(targetNode.getNodeName())
            .setFlowStatus(StringUtils.emptyDefault(context.getFlowStatus()
                , FlowStatusMachine.mapper().instanceStatus(FlowStatus.WAITING)))
            .setDefJson(defJson)
            .setUpdateBy(context.getHandler())
            .setUpdateTime(new Date());
        boolean updated = FlowEngine.insService().updateById(instance);
        AssertUtil.isFalse(updated, ExceptionCons.NOT_FOUNT_INSTANCE);
        FlowEngine.userService().saveBatch(users);
        ListenerUtil.executeCreate(new ListenerVariable(definition, instance, targetNode
            , context.getVariables(), task).setContext(context));
        return instance;
    }

    /**
     * 按正常任务创建规则转换显式审批人并去重。
     */
    private static List<String> normalizeHandlers(List<String> handlers) {
        List<String> normalized = handlers.stream()
            .filter(StringUtils::isNotEmpty)
            .distinct()
            .collect(Collectors.toList());
        PermissionHandler permissionHandler = FlowEngine.permissionHandler();
        if (permissionHandler != null && CollUtil.isNotEmpty(normalized)) {
            try {
                normalized = permissionHandler.convertPermissions(normalized);
            } catch (Exception ignored) {
            }
        }
        if (CollUtil.isEmpty(normalized)) {
            return Collections.emptyList();
        }
        return normalized.stream().filter(StringUtils::isNotEmpty).distinct().collect(Collectors.toList());
    }

    /**
     * 清除目标节点以外已经没有待办或等待记录支撑的待办图标。
     */
    private static String clearOtherTodoMarkers(String defJson, String targetNodeCode) {
        DefJson snapshot = FlowEngine.jsonConvert.strToBean(defJson, DefJson.class);
        if (snapshot == null || CollUtil.isEmpty(snapshot.getNodeList())) {
            return defJson;
        }
        for (NodeJson node : snapshot.getNodeList()) {
            if (!targetNodeCode.equals(node.getNodeCode()) && ChartStatus.isToDo(node.getStatus())) {
                node.setStatus(ChartStatus.NOT_DONE.getKey());
            }
        }
        return FlowEngine.jsonConvert.objToStr(snapshot);
    }
}
