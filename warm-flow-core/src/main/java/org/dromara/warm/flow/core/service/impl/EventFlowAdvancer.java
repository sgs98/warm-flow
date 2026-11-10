package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.PathWayData;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.dromara.warm.flow.core.utils.ListenerUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.List;

/**
 * 事件触发推进器：捕获 / 边界事件到期或收到消息后，沿事件节点的普通出口继续流转。
 * <p>
 * 事件节点离开时不伪造 {@code flow_his_task}；等待期间的状态通过
 * {@code flow_event_wait} 查询，一次性等待成功后删除记录。后续节点统一经
 * {@link EventArrivalDispatcher} 分派：人工节点建待办、捕获事件起新等待、
 * 服务任务交自动节点推进器。
 *
 * @author may
 */
final class EventFlowAdvancer {

    private EventFlowAdvancer() {
    }

    /**
     * 从事件节点出口推进流程（无被中断节点，用于捕获事件）。
     *
     * @param execution 执行作用域（实例已加载、当前节点为事件节点）
     * @param eventNode 已触发的事件节点
     */
    static void advanceFromEvent(FlowExecution execution, Node eventNode) {
        advanceFromEvent(execution, eventNode, null);
    }

    /**
     * 从事件节点出口推进流程。
     *
     * @param execution       执行作用域（实例已加载、当前节点为事件节点）
     * @param eventNode       已触发的事件节点
     * @param interruptedNode 中断型边界事件中断的原任务节点，无则传 {@code null}
     */
    static PreparedEventArrival prepareFromEvent(FlowExecution execution, Node eventNode, Node interruptedNode) {
        Instance instance = execution.instance;
        PathWayData pathWayData = new PathWayData()
            .setInsId(instance.getId())
            .setDefId(eventNode.getDefinitionId())
            .setSkipType(SkipType.PASS.getKey())
            .setDefJson(instance.getDefJson());
        List<Node> nextNodes = FlowEngine.nodeService().getNextNodeList(eventNode, null
            , SkipType.PASS.getKey(), execution.intent.getVariables(), pathWayData
            , execution.loadCombineNoDef());
        pathWayData.getTargetNodes().addAll(nextNodes);
        if (interruptedNode != null) {
            pathWayData.getPathWayNodes().add(interruptedNode);
        }
        String defJson = FlowEngine.chartService().skipMetadata(pathWayData);
        ArrivalPlan arrivalPlan = EventArrivalDispatcher.prepare(execution, nextNodes, SkipType.PASS.getKey());
        EventArrivalDispatcher.validateBoundaries(execution, arrivalPlan.tasks());
        return new PreparedEventArrival(nextNodes, arrivalPlan, defJson);
    }

    static void advanceFromEvent(FlowExecution execution, Node eventNode, Node interruptedNode) {
        advanceFromEvent(execution, eventNode, interruptedNode, prepareFromEvent(execution, eventNode, interruptedNode));
    }

    static void advanceFromEvent(FlowExecution execution, Node eventNode, Node interruptedNode
        , PreparedEventArrival prepared) {
        Instance instance = execution.instance;
        List<Node> nextNodes = prepared.nextNodes();
        ArrivalPlan arrivalPlan = prepared.arrivalPlan();
        instance.setDefJson(prepared.defJson());
        List<Task> addTasks = arrivalPlan.tasks();
        EventArrivalDispatcher.commit(arrivalPlan);

        // 办理人变量替换
        ExpressionUtil.evalVariable(addTasks, execution.intent.getVariables()
            , execution.intent.getNextHandlers(), execution.intent.isNextHandlerAppend());

        // assignment 保留既有位置且在所有组签解析成功后执行；任务和等待写入随后才提交。
        if (CollUtil.isNotEmpty(addTasks)) {
            ListenerUtil.executeAssignment(execution.contextListener(null, eventNode, nextNodes, addTasks));
        }
        List<org.dromara.warm.flow.core.entity.User> users =
            FlowEngine.userService().taskAddUsers(addTasks);
        // 更新实例位置 / 状态 / 变量；后继含固定 END 时随 setInsFinishInfo 收口实例
        FlowEngine.taskService().setInsFinishInfo(instance, addTasks, execution.intent.getVariables());
        // 终止结束事件：intent 里带的是「实例终止码」，而 setInsFinishInfo 只能按落点推导出「已完成」。
        // 这里按意图回写，避免靠「任务码 == 实例码」的数值比较来识别终止——两个维度独立编号后允许同号
        // （任务「通过」与实例「终止」都可能是 "2"），码值比较会把普通结束误判成终止。
        if (StringUtils.isNotEmpty(execution.intent.getFlowStatus())) {
            instance.setFlowStatus(execution.intent.getFlowStatus());
        }
        if (CollUtil.isNotEmpty(addTasks)) {
            FlowEngine.taskService().saveBatch(addTasks);
            FlowEngine.userService().saveBatch(users);
            // 到达节点：按 node_config.copy 自动抄送
            CopyAdvancer.copyOnArrival(addTasks, execution.instance, execution.intent);
        }

        // 服务任务自动推进（事件出口直连服务任务时）
        if (StreamUtils.filter(addTasks, task -> NodeType.isService(task.getNodeType())).size() > 0) {
            new AutoNodeAdvancer(new FlowPathResolver()).advance(execution, nextNodes, addTasks);
        }

        FlowEngine.insService().updateById(instance);

        // 完成与创建监听器
        ListenerUtil.endCreateListener(execution.contextListener(null, eventNode, nextNodes, addTasks));
    }

    record PreparedEventArrival(List<Node> nextNodes, ArrivalPlan arrivalPlan, String defJson) {
    }
}
