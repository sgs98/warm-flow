package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.dto.PathWayData;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.ChartStatus;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.TaskStatus;
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
 * 结束流程实例重启编排器。
 *
 * <p>仅供 {@code WorkflowService} 门面实现调用，避免把写操作扩展到公开的 {@code InsService} 契约。</p>
 *
 * @author may
 */
public final class InstanceRestartCoordinator {

    /**
     * 重启审计记录不来源于真实待办，但 {@code flow_his_task.task_id} 在四套建表脚本中均为非空，
     * 统一写不会与真实任务主键冲突的哨兵值，避免为留痕修改表结构。
     */
    private static final long RESTART_HISTORY_TASK_ID = -1L;

    private InstanceRestartCoordinator() {
    }

    /**
     * 将已完成、已终止或已作废的实例重启到指定人工审批节点。
     *
     * @param instanceId     流程实例主键
     * @param targetNodeCode 目标节点编码
     * @param handlers       指定审批人
     * @param context        操作上下文
     * @return 重启后的流程实例
     */
    public static Instance restart(Long instanceId, String targetNodeCode, List<String> handlers
        , WorkflowContext context) {
        AssertUtil.isNull(instanceId, ExceptionCons.NULL_INSTANCE_ID);
        AssertUtil.isEmpty(targetNodeCode, "目标节点编码不能为空");
        AssertUtil.isTrue(CollUtil.isEmpty(handlers), "重启审批人不能为空");

        Instance instance = FlowEngine.insService().getById(instanceId);
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);
        Definition definition = FlowEngine.defService().getById(instance.getDefinitionId());
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        AssertUtil.isFalse(ActivityStatus.isActivity(definition.getActivityStatus())
            && ActivityStatus.isActivity(instance.getActivityStatus()), ExceptionCons.NOT_ACTIVITY);

        String status = instance.getFlowStatus();
        boolean restartable = Objects.equals(status, FlowStatusMachine.mapper().instanceStatus(FlowStatus.FINISHED))
            || Objects.equals(status, FlowStatusMachine.mapper().instanceStatus(FlowStatus.TERMINATE))
            || Objects.equals(status, FlowStatusMachine.mapper().instanceStatus(FlowStatus.INVALID));
        AssertUtil.isFalse(restartable, "只有已完成、已终止或已作废的流程实例才能重启");
        AssertUtil.isTrue(CollUtil.isNotEmpty(FlowEngine.taskService().getByInsId(instanceId))
            , "流程实例存在待办任务，不能重启");

        Node targetNode = FlowEngine.nodeService().getByDefIdAndNodeCode(instance.getDefinitionId(), targetNodeCode);
        AssertUtil.isNull(targetNode, "重启目标节点不存在");
        if (StringUtils.isNotEmpty(instance.getDefJson())) {
            DefJson snapshot = FlowEngine.jsonConvert.strToBean(instance.getDefJson(), DefJson.class);
            AssertUtil.isTrue(snapshot == null || CollUtil.isEmpty(snapshot.getNodeList())
                || snapshot.getNodeList().stream().noneMatch(node -> targetNodeCode.equals(node.getNodeCode()))
                , "重启目标不在流程实例定义快照中");
        }
        AssertUtil.isFalse(NodeType.isBetween(targetNode.getNodeType())
            && StringUtils.isEmpty(targetNode.getEventType()), "重启目标必须是普通人工审批节点");
        AssertUtil.isTrue(CooperateType.isGroup(targetNode.getCooperateType()), "重启目标不支持组签节点");

        List<String> restartHandlers = normalizeHandlers(handlers);
        AssertUtil.isTrue(CollUtil.isEmpty(restartHandlers), "重启审批人不能为空");

        instance.setDefJson(RejectReturnRuntime.clear(instance.getDefJson()));
        Task task = FlowEngine.taskService().addTask(targetNode, instance, definition, context
            , SkipType.PASS.getKey());
        task.setPermissionList(restartHandlers)
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

        Date restartTime = new Date();
        HisTask restartHistory = buildRestartHistory(instance, targetNode, context, restartTime);
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
            .setUpdateTime(restartTime);
        boolean updated = FlowEngine.insService().updateById(instance);
        AssertUtil.isFalse(updated, ExceptionCons.NOT_FOUNT_INSTANCE);
        FlowEngine.userService().saveBatch(users);
        ListenerUtil.executeCreate(new ListenerVariable(definition, instance, targetNode
            , context.getVariables(), task).setContext(context));
        FlowEngine.hisTaskService().save(restartHistory);
        return instance;
    }

    /**
     * 构造一次实例重启的审计历史，来源节点固定为重启前实例当前节点。
     */
    private static HisTask buildRestartHistory(Instance instance, Node targetNode, WorkflowContext context
        , Date restartTime) {
        // 宿主 StatusMapper 对新增语义可能返回 null；历史 flow_status 非空，回落到内置编码，
        // 避免重启已完成全部写入后，才因非空约束失败而丢掉审计记录。
        String restartStatus = StringUtils.emptyDefault(context.getTaskStatus(),
            StringUtils.emptyDefault(FlowStatusMachine.mapper().taskStatus(TaskStatus.RESTART)
                , TaskStatus.RESTART.getKey()));
        HisTask history = FlowEngine.newHisTask()
            .setTaskId(RESTART_HISTORY_TASK_ID)
            .setInstanceId(instance.getId())
            .setDefinitionId(instance.getDefinitionId())
            .setCooperateType(CooperateType.NONE.getKey())
            .setNodeCode(instance.getNodeCode())
            .setNodeName(instance.getNodeName())
            .setNodeType(instance.getNodeType())
            .setTargetNodeCode(targetNode.getNodeCode())
            .setTargetNodeName(targetNode.getNodeName())
            .setApprover(context.getHandler())
            .setSkipType(SkipType.NONE.getKey())
            .setFlowStatus(restartStatus)
            .setVariable(FlowEngine.jsonConvert.objToStr(context.getVariables()))
            .setExt(context.getExt())
            .setCreateTime(restartTime);
        FlowEngine.dataFillHandler().idFill(history);
        return history;
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
