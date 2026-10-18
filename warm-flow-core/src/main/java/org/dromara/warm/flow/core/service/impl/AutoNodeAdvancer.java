package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.PathWayData;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.service.task.AutoNodeContext;
import org.dromara.warm.flow.core.service.task.AutoNodeExecutor;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.dromara.warm.flow.core.utils.ListenerUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自动节点推进器：循环推进当前批次中的服务任务等自动节点，直到只剩人工节点或结束节点。
 *
 * <p>执行链负责通用编排，节点本身的执行由 {@link AutoNodeExecutor} 负责。每个自动节点的处理顺序
 * （行为契约）：start 监听器 → 执行处理器写变量 → 在临时待办仍活动时解析后继（复用汇聚判断）→
 * 合并流程图元数据（与办理链 route() 一致）→ 写服务历史 → 取消临时待办附着的边界等待并删除临时待办 → 后继建正式/临时待办并落库 →
 * finish 监听器与后继 create 监听器 → 自动节点入队，直到队列为空或超过安全上限。服务任务无办理人，
 * 自身不触发 assignment 监听器；后继人工节点在办理人落库前触发 assignment，与人工节点办理链语义一致。</p>
 *
 * @author warm
 */
final class AutoNodeAdvancer {

    /**
     * 一次流程操作内自动节点执行次数上限，防止服务任务环路。
     */
    private static final int MAX_STEPS = 1000;

    /**
     * 节点跳转和网关路径解析，与办理链复用同一实现。
     */
    private final FlowPathResolver pathResolver;

    AutoNodeAdvancer(FlowPathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    /**
     * 推进当前批次中的自动节点，直到只剩人工节点/结束节点。
     *
     * @param execution 执行作用域
     * @param nextNodes 当前批次目标节点
     * @param newTasks  当前批次新建任务（自动节点为已落库的临时待办）
     * @return 推进过程中新建的后继任务（含人工正式待办与服务临时待办，结束节点任务已移除）
     */
    List<Task> advance(FlowExecution execution, List<Node> nextNodes, List<Task> newTasks) {
        Deque<Task> queue = autoTasksOf(newTasks);
        List<Task> createdTasks = new ArrayList<>();
        if (queue.isEmpty()) {
            return createdTasks;
        }
        // 写回可变变量表，保证处理器写入对网关、办理人表达式与历史快照可见
        Map<String, Object> variables = execution.intent.getVariables();
        if (variables == null) {
            variables = new HashMap<>();
            execution.intent.setVariables(variables);
        }

        int steps = 0;
        while (!queue.isEmpty()) {
            AssertUtil.isTrue(++steps > MAX_STEPS, ExceptionCons.SERVICE_TASK_LOOP_LIMIT);
            Task temp = queue.poll();
            Node serviceNode = nodeOf(execution, temp.getNodeCode());

            // 1) start 监听器：处理器执行前触发，与人工节点办理语义对齐
            ListenerUtil.executeStart(execution.contextListener(temp, serviceNode));

            // 2) 执行处理器（写变量）
            AutoNodeExecutor executor = findExecutor(serviceNode);
            AssertUtil.isNull(executor, ExceptionCons.NULL_SERVICE_TASK_EXECUTOR);
            executor.execute(new AutoNodeContext(execution.definition, execution.instance, serviceNode
                , variables, execution.intent));

            // 3) 解析后继：临时待办此时仍活动，汇聚判断可见本分支（必须先于删除临时待办）
            PathWayData pathWayData = pathResolver.resolve(temp, serviceNode, execution.instance
                , autoIntent(variables), SkipType.PASS.getKey(), execution.loadCombineNoDef());
            List<Node> afterNodes = pathWayData.getTargetNodes();

            // 4) 流程图元数据：本跳途经节点与连线置完成、后继置待办；与办理链 route() 语义一致，
            //    否则流程图会停留在进入服务任务之前的状态；随步骤 7 的实例更新一并落库
            execution.instance.setDefJson(FlowEngine.chartService().skipMetadata(pathWayData));

            // 5) 写服务任务历史（审批通过 / NONE / node_config 快照）
            FlowEngine.hisTaskService().save(FlowEngine.hisTaskService()
                .setServiceTaskHis(serviceNode, temp, afterNodes, execution.intent, variables));

            // 6) 删除临时待办及其办理人
            removeTempTask(temp);

            // 7) 后继统一分派（人工正式待办 / 服务临时待办 / 捕获事件等待）并落库
            List<Task> afterTasks = EventArrivalDispatcher.dispatch(execution, afterNodes
                , SkipType.PASS.getKey());
            ExpressionUtil.evalVariable(afterTasks, variables, null, false);
            assignSuccessors(execution, temp, serviceNode, afterNodes, afterTasks);
            List<User> users = FlowEngine.userService().taskAddUsers(afterTasks);
            FlowEngine.taskService().setInsFinishInfo(execution.instance, afterTasks, variables);
            if (CollUtil.isNotEmpty(afterTasks)) {
                FlowEngine.taskService().saveBatch(afterTasks);
            }
            FlowEngine.insService().updateById(execution.instance);
            FlowEngine.userService().saveBatch(users);

            // 8) finish 监听器 + 后继 create 监听器：与人工节点收尾（endCreateListener）语义一致
            ListenerUtil.endCreateListener(execution.contextListener(temp, serviceNode, afterNodes, afterTasks));

            // 9) 后继中的自动节点入队继续推进
            for (Task afterTask : afterTasks) {
                if (NodeType.isService(afterTask.getNodeType())) {
                    queue.add(afterTask);
                }
            }
            // 后继任务回传给办理链，供自动审批判定本次推进新建的人工待办
            createdTasks.addAll(afterTasks);
        }
        return createdTasks;
    }

    /**
     * 从当前批次任务中挑出自动节点（服务任务）的临时待办。
     *
     * @param tasks 当前批次任务
     * @return 自动节点临时待办队列
     */
    private Deque<Task> autoTasksOf(List<Task> tasks) {
        Deque<Task> queue = new ArrayDeque<>();
        if (CollUtil.isNotEmpty(tasks)) {
            for (Task task : tasks) {
                if (NodeType.isService(task.getNodeType())) {
                    queue.add(task);
                }
            }
        }
        return queue;
    }

    /**
     * 对后继人工节点触发分派监听器，与人工节点办理链（FlowExecuteChain#buildTasks）语义一致。
     *
     * <p>必须在办理人落库前执行：监听器对 {@code permissionList} 的改写（典型如把 {@code user:xxx}
     * 展开为实际用户名）才能随办理人一起写入 {@code flow_user}。服务任务无办理人，按设计不触发
     * {@code assignment}，因此只传递后继中的人工节点与其任务。</p>
     *
     * @param execution   执行作用域
     * @param temp        当前自动节点的临时待办
     * @param serviceNode 当前自动节点
     * @param afterNodes  后继节点
     * @param afterTasks  后继任务
     */
    private void assignSuccessors(FlowExecution execution, Task temp, Node serviceNode
        , List<Node> afterNodes, List<Task> afterTasks) {
        List<Task> humanTasks = StreamUtils.filter(afterTasks, task -> !NodeType.isService(task.getNodeType()));
        if (CollUtil.isEmpty(humanTasks)) {
            return;
        }
        List<Node> humanNodes = StreamUtils.filter(afterNodes, node -> !NodeType.isService(node.getNodeType()));
        ListenerUtil.executeAssignment(execution.contextListener(temp, serviceNode, humanNodes, humanTasks));
    }

    /**
     * 按节点编码从当前定义图中定位节点。
     *
     * @param execution 执行作用域
     * @param nodeCode  节点编码
     * @return 节点
     */
    private Node nodeOf(FlowExecution execution, String nodeCode) {
        Node node = StreamUtils.filterOne(execution.loadCombineNoDef().getAllNodes()
            , item -> item.getNodeCode().equals(nodeCode));
        AssertUtil.isNull(node, ExceptionCons.LOST_CUR_NODE);
        return node;
    }

    /**
     * 查找支持该节点的自动节点执行器。
     *
     * @param node 节点
     * @return 执行器；无匹配时返回 {@code null}
     */
    private AutoNodeExecutor findExecutor(Node node) {
        for (AutoNodeExecutor executor : FlowEngine.autoNodeExecutors()) {
            if (executor != null && executor.supports(node)) {
                return executor;
            }
        }
        return null;
    }

    /**
     * 构造自动节点解析后继用的上下文：忽略调用方指定的目标节点与后续办理人，仅携带最新变量。
     *
     * @param variables 最新流程变量
     * @return 解析后继用的流程上下文
     */
    private WorkflowContext autoIntent(Map<String, Object> variables) {
        WorkflowContext context = new WorkflowContext();
        context.setVariables(variables);
        return context;
    }

    /**
     * 删除单条临时待办及其办理人。
     * <p>临时待办创建时已按节点注册边界事件等待，删除前先取消，避免残留活动等待被定时器误触发。</p>
     *
     * @param temp 临时待办
     */
    private void removeTempTask(Task temp) {
        List<Long> taskIds = StreamUtils.toList(List.of(temp), Task::getId);
        FlowEngine.eventWaitService().cancelByAttachedTaskId(temp.getId());
        boolean removed = FlowEngine.taskService().removeByIds(taskIds);
        AssertUtil.isFalse(removed, ExceptionCons.NOT_FOUNT_TASK);
        FlowEngine.userService().deleteByTaskIds(taskIds);
    }
}
