package org.dromara.warm.flow.core.test;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.List;
import java.util.Map;

/**
 * 单测流程数据构建器：串行流程 start(0) → apply(1, zhangsan) → end(2)，
 * apply 节点挂四类记录型监听器，全部连线为 PASS。
 *
 * @author warm
 */
public final class TestFlows {

    public static final String HANDLER = "zhangsan";

    public static final String LISTENER_TYPES = "start,assignment,finish,create";

    public static final String LISTENER_PATH =
            "org.dromara.warm.flow.core.test.listener.StartListener@@"
                    + "org.dromara.warm.flow.core.test.listener.AssignmentListener@@"
                    + "org.dromara.warm.flow.core.test.listener.FinishListener@@"
                    + "org.dromara.warm.flow.core.test.listener.CreateListener";

    private TestFlows() {
    }

    /**
     * 构建并保存已发布的串行测试流程。
     */
    public static Definition serialFlow(String flowCode) {
        return serialFlow(flowCode, "1", PublishStatus.PUBLISHED.getKey());
    }

    /**
     * 构建并保存指定版本与发布状态的串行测试流程。
     */
    public static Definition serialFlow(String flowCode, String version, Integer isPublish) {
        Definition def = FlowEngine.newDef()
                .setFlowCode(flowCode)
                .setFlowName("串行测试流程")
                .setVersion(version)
                .setIsPublish(isPublish)
                .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(def);
        node(def, "start", NodeType.START.getKey(), null);
        node(def, "apply", NodeType.BETWEEN.getKey(), HANDLER);
        node(def, "end", NodeType.END.getKey(), null);
        skip(def, "start", NodeType.START.getKey(), "apply", NodeType.BETWEEN.getKey());
        skip(def, "apply", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        return def;
    }

    /**
     * 构建并保存带驳回线的串行测试流程：start(0) → apply(1) → audit(1) → end(2)，
     * 前向连线全为 PASS，另含 audit → apply 的 REJECT 驳回线。
     */
    public static Definition rejectFlow(String flowCode) {
        Definition def = FlowEngine.newDef()
                .setFlowCode(flowCode)
                .setFlowName("驳回测试流程")
                .setVersion("1")
                .setIsPublish(PublishStatus.PUBLISHED.getKey())
                .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(def);
        node(def, "start", NodeType.START.getKey(), null);
        node(def, "apply", NodeType.BETWEEN.getKey(), HANDLER);
        node(def, "audit", NodeType.BETWEEN.getKey(), HANDLER);
        node(def, "end", NodeType.END.getKey(), null);
        skip(def, "start", NodeType.START.getKey(), "apply", NodeType.BETWEEN.getKey());
        skip(def, "apply", NodeType.BETWEEN.getKey(), "audit", NodeType.BETWEEN.getKey());
        skip(def, "audit", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        skip(def, "audit", NodeType.BETWEEN.getKey(), "apply", NodeType.BETWEEN.getKey()
                , SkipType.REJECT.getKey());
        return def;
    }

    /**
     * 构建协作测试流程：start(0) → draft(1) → apply(1, 多办理人+协作规则) → end(2)，
     * 含 apply → draft 的 REJECT 驳回线，用于会签/票签/或签语义。
     */
    public static Definition cooperateFlow(String flowCode, String applyRatio, String applyPermission) {
        Definition def = FlowEngine.newDef()
                .setFlowCode(flowCode)
                .setFlowName("协作测试流程")
                .setVersion("1")
                .setIsPublish(PublishStatus.PUBLISHED.getKey())
                .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(def);
        node(def, "start", NodeType.START.getKey(), null);
        node(def, "draft", NodeType.BETWEEN.getKey(), HANDLER);
        node(def, "apply", NodeType.BETWEEN.getKey(), applyPermission, applyRatio);
        node(def, "end", NodeType.END.getKey(), null);
        skip(def, "start", NodeType.START.getKey(), "draft", NodeType.BETWEEN.getKey());
        skip(def, "draft", NodeType.BETWEEN.getKey(), "apply", NodeType.BETWEEN.getKey());
        skip(def, "apply", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        skip(def, "apply", NodeType.BETWEEN.getKey(), "draft", NodeType.BETWEEN.getKey()
                , SkipType.REJECT.getKey());
        return def;
    }

    /**
     * 构建并行网关测试流程：start(0) → forkP(4) → a1/b1(1) → joinP(4) → end(2)。
     */
    public static Definition parallelFlow(String flowCode) {
        Definition def = FlowEngine.newDef()
                .setFlowCode(flowCode)
                .setFlowName("并行网关测试流程")
                .setVersion("1")
                .setIsPublish(PublishStatus.PUBLISHED.getKey())
                .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(def);
        node(def, "start", NodeType.START.getKey(), null);
        node(def, "forkP", NodeType.PARALLEL.getKey(), null);
        node(def, "a1", NodeType.BETWEEN.getKey(), HANDLER);
        node(def, "b1", NodeType.BETWEEN.getKey(), HANDLER);
        node(def, "joinP", NodeType.PARALLEL.getKey(), null);
        node(def, "end", NodeType.END.getKey(), null);
        skip(def, "start", NodeType.START.getKey(), "forkP", NodeType.PARALLEL.getKey());
        skip(def, "forkP", NodeType.PARALLEL.getKey(), "a1", NodeType.BETWEEN.getKey());
        skip(def, "forkP", NodeType.PARALLEL.getKey(), "b1", NodeType.BETWEEN.getKey());
        skip(def, "a1", NodeType.BETWEEN.getKey(), "joinP", NodeType.PARALLEL.getKey());
        skip(def, "b1", NodeType.BETWEEN.getKey(), "joinP", NodeType.PARALLEL.getKey());
        skip(def, "joinP", NodeType.PARALLEL.getKey(), "end", NodeType.END.getKey());
        return def;
    }

    /**
     * 构建网关路由测试流程：start(0) → apply(1) → gate(gatewayType) → a1/b1(1) → end(2)，
     * gate 的两条出口线可分别配置跳转条件（null 表示无条件出口）。
     */
    public static Definition gatewayFlow(String flowCode, Integer gatewayType, String condA, String condB) {
        Definition def = FlowEngine.newDef()
                .setFlowCode(flowCode)
                .setFlowName("网关路由测试流程")
                .setVersion("1")
                .setIsPublish(PublishStatus.PUBLISHED.getKey())
                .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(def);
        node(def, "start", NodeType.START.getKey(), null);
        node(def, "apply", NodeType.BETWEEN.getKey(), HANDLER);
        node(def, "gate", gatewayType, null);
        node(def, "a1", NodeType.BETWEEN.getKey(), HANDLER);
        node(def, "b1", NodeType.BETWEEN.getKey(), HANDLER);
        node(def, "end", NodeType.END.getKey(), null);
        skip(def, "start", NodeType.START.getKey(), "apply", NodeType.BETWEEN.getKey());
        skip(def, "apply", NodeType.BETWEEN.getKey(), "gate", gatewayType);
        skip(def, "gate", gatewayType, "a1", NodeType.BETWEEN.getKey(), SkipType.PASS.getKey(), condA);
        skip(def, "gate", gatewayType, "b1", NodeType.BETWEEN.getKey(), SkipType.PASS.getKey(), condB);
        skip(def, "a1", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        skip(def, "b1", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        return def;
    }

    private static void node(Definition def, String code, Integer nodeType, String permissionFlag) {
        node(def, code, nodeType, permissionFlag, "0");
    }

    private static void node(Definition def, String code, Integer nodeType, String permissionFlag
            , String nodeRatio) {
        org.dromara.warm.flow.core.entity.Node node = FlowEngine.newNode()
                .setDefinitionId(def.getId())
                .setNodeCode(code)
                .setNodeName(code)
                .setNodeType(nodeType)
                .setNodeRatio(nodeRatio);
        if (permissionFlag != null) {
            node.setPermissionFlag(permissionFlag);
        }
        if (NodeType.BETWEEN.getKey().equals(nodeType)) {
            node.setListenerType(LISTENER_TYPES).setListenerPath(LISTENER_PATH);
        }
        FlowEngine.nodeService().save(node);
    }

    private static void skip(Definition def, String now, Integer nowType, String next, Integer nextType) {
        skip(def, now, nowType, next, nextType, SkipType.PASS.getKey());
    }

    private static void skip(Definition def, String now, Integer nowType, String next, Integer nextType
            , String skipType) {
        skip(def, now, nowType, next, nextType, skipType, null);
    }

    private static void skip(Definition def, String now, Integer nowType, String next, Integer nextType
            , String skipType, String skipCondition) {
        org.dromara.warm.flow.core.entity.Skip skip = FlowEngine.newSkip()
                .setDefinitionId(def.getId())
                .setNowNodeCode(now)
                .setNowNodeType(nowType)
                .setNextNodeCode(next)
                .setNextNodeType(nextType)
                .setSkipName(now + "_" + next)
                .setSkipType(skipType);
        if (skipCondition != null) {
            skip.setSkipCondition(skipCondition);
        }
        FlowEngine.skipService().save(skip);
    }

    /**
     * 构建串行流程并以 HANDLER 发起流程实例。
     */
    public static Instance start(String flowCode, String businessId) {
        serialFlow(flowCode);
        return FlowEngine.insService().start(businessId, flowCode, context(HANDLER));
    }

    /**
     * 构建带驳回线的串行流程并以 HANDLER 发起流程实例。
     */
    public static Instance startRejectFlow(String flowCode, String businessId) {
        rejectFlow(flowCode);
        return FlowEngine.insService().start(businessId, flowCode, context(HANDLER));
    }

    /**
     * 构建协作流程并以 HANDLER 发起流程实例（首待办在 draft 节点）。
     */
    public static Instance startCooperateFlow(String flowCode, String applyRatio, String applyPermission
            , String businessId) {
        cooperateFlow(flowCode, applyRatio, applyPermission);
        return FlowEngine.insService().start(businessId, flowCode, context(HANDLER));
    }

    /**
     * 构建并行网关流程并以 HANDLER 发起流程实例（分叉后 a1/b1 各一待办）。
     */
    public static Instance startParallelFlow(String flowCode, String businessId) {
        parallelFlow(flowCode);
        return FlowEngine.insService().start(businessId, flowCode, context(HANDLER));
    }

    /**
     * 构建网关路由流程并以 HANDLER 发起流程实例，可携带路由变量。
     * 变量包一层可变 Map：引擎在合并点会原地写回 context.variables。
     */
    public static Instance startGatewayFlow(String flowCode, Integer gatewayType, String condA, String condB
            , String businessId, Map<String, Object> variables) {
        gatewayFlow(flowCode, gatewayType, condA, condB);
        WorkflowContext context = context(HANDLER);
        if (variables != null) {
            context.setVariables(new java.util.HashMap<>(variables));
        }
        return FlowEngine.insService().start(businessId, flowCode, context);
    }

    /**
     * 构造忽略权限校验的办理上下文。
     */
    public static WorkflowContext context(String handler) {
        WorkflowContext context = new WorkflowContext();
        context.setHandler(handler);
        context.setIgnorePermission(true);
        return context;
    }

    /**
     * 以 PASS 办理指定任务。
     */
    public static Instance pass(Long taskId, String handler) {
        return FlowEngine.taskService().execute(taskId, context(handler), SkipType.PASS.getKey());
    }

    /**
     * 以 REJECT 驳回指定任务。
     */
    public static Instance reject(Long taskId, String handler) {
        return FlowEngine.taskService().execute(taskId, context(handler), SkipType.REJECT.getKey());
    }

    /**
     * 取实例唯一待办任务。
     */
    public static Task currentTask(Long instanceId) {
        List<Task> tasks = pendingTasks(instanceId);
        if (tasks.size() != 1) {
            throw new IllegalStateException("期望唯一待办任务，实际 " + tasks.size());
        }
        return tasks.get(0);
    }

    /**
     * 取实例全部待办任务。
     */
    public static List<Task> pendingTasks(Long instanceId) {
        return FlowEngine.taskService().list(FlowEngine.newTask().setInstanceId(instanceId));
    }
}
