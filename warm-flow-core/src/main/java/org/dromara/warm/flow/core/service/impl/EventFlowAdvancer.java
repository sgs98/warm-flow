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
    static void advanceFromEvent(FlowExecution execution, Node eventNode, Node interruptedNode) {
        Instance instance = execution.instance;

        // 沿事件节点的普通出口解析后续节点
        PathWayData pathWayData = new PathWayData()
            .setInsId(instance.getId())
            .setDefId(eventNode.getDefinitionId())
            .setSkipType(SkipType.PASS.getKey())
            // 以内存中的最新实例快照为合并基底：边界事件中断等场景下，
            // 实例 defJson 的本次更新尚未落库，回库加载会覆盖掉这些状态
            .setDefJson(instance.getDefJson());
        List<Node> nextNodes = FlowEngine.nodeService().getNextNodeList(eventNode, null
            , SkipType.PASS.getKey(), execution.intent.getVariables(), pathWayData
            , execution.loadCombineNoDef());
        pathWayData.getTargetNodes().addAll(nextNodes);

        // 中断型边界：被中断节点已离开（待办删除、历史归档为 INVALID），补进本次途经节点
        // 置为已办理，与终止链对当前节点的处理一致，避免其残留为「待办理」
        if (interruptedNode != null) {
            pathWayData.getPathWayNodes().add(interruptedNode);
        }

        // 写流程图元数据（事件节点本跳途经置完成、后继置待办）
        instance.setDefJson(FlowEngine.chartService().skipMetadata(pathWayData));

        // 后续节点统一分派：人工节点建待办、捕获事件起新等待、抛出 / 结束事件继续递归
        List<Task> addTasks = EventArrivalDispatcher.dispatch(execution, nextNodes
            , SkipType.PASS.getKey());

        // 办理人变量替换
        ExpressionUtil.evalVariable(addTasks, execution.intent.getVariables()
            , execution.intent.getNextHandlers(), execution.intent.isNextHandlerAppend());

        // 更新实例位置 / 状态 / 变量；后继含固定 END 时随 setInsFinishInfo 收口实例
        FlowEngine.taskService().setInsFinishInfo(instance, addTasks, execution.intent.getVariables());
        if (CollUtil.isNotEmpty(addTasks)) {
            FlowEngine.taskService().saveBatch(addTasks);
            List<org.dromara.warm.flow.core.entity.User> users =
                FlowEngine.userService().taskAddUsers(addTasks);
            FlowEngine.userService().saveBatch(users);
            // 分派监听器：与人工节点收尾语义一致，事件节点无当前任务（task 为空合法）
            ListenerUtil.executeAssignment(execution.contextListener(null, eventNode
                , nextNodes, addTasks));
        }

        // 服务任务自动推进（事件出口直连服务任务时）
        if (StreamUtils.filter(addTasks, task -> NodeType.isService(task.getNodeType())).size() > 0) {
            new AutoNodeAdvancer(new FlowPathResolver()).advance(execution, nextNodes, addTasks);
        }

        FlowEngine.insService().updateById(instance);

        // 完成与创建监听器
        ListenerUtil.endCreateListener(execution.contextListener(null, eventNode, nextNodes, addTasks));
    }
}
