/*
 *    Copyright 2024-2025, Warm-Flow (290631660@qq.com).
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.FlowCombine;
import org.dromara.warm.flow.core.dto.PathWayData;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.utils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 办理（通过/驳回/跳转）操作链：装配 8 个有序步骤并委托 {@link FlowPipeline} 执行。
 *
 * <p><b>生命周期锁</b>：每次办理操作新建实例，字段为本次操作中间态（skipType 参数、
 * 路由结果、新建任务）；非线程安全，禁止静态化或缓存复用。</p>
 *
 * <p>步骤体自原 {@code TaskServiceImpl.executeInternal} 逐行搬移，语句顺序与引用语义
 * 为行为契约，由特征测试锁定。</p>
 *
 * @author warm
 */
final class FlowExecuteChain {

    /**
     * 任务服务，复用既有任务持久化与校验能力。
     */
    private final TaskServiceImpl taskService;

    /**
     * 委派、会签和票签处理。
     */
    private final TaskCooperationHandler cooperationHandler;

    /**
     * 当前任务归档及后续任务持久化处理。
     */
    private final TaskHistoryHandler historyHandler;
    /**
     * 节点跳转和网关路径解析。
     */
    private final FlowPathResolver pathResolver;
    /**
     * 流转类型。
     */
    private final String skipType;

    /**
     * 路由结果，resolve 之后归本链所有。
     */
    private PathWayData pathWayData;
    /**
     * 后续节点集合。
     */
    private List<Node> nextNodes;
    /**
     * 新增待办任务（setInsFinishInfo 可能原地移除结束任务，endCreate 监听器看到删后同引用列表）。
     */
    private List<Task> addTasks;

    /**
     * @param taskService        任务服务
     * @param cooperationHandler 协作处理器
     * @param historyHandler     历史处理器
     * @param pathResolver       路径解析器
     * @param skipType           流转类型
     */
    FlowExecuteChain(TaskServiceImpl taskService, TaskCooperationHandler cooperationHandler
        , TaskHistoryHandler historyHandler, FlowPathResolver pathResolver, String skipType) {
        this.taskService = taskService;
        this.cooperationHandler = cooperationHandler;
        this.historyHandler = historyHandler;
        this.pathResolver = pathResolver;
        this.skipType = skipType;
    }

    /**
     * 按序装配办理链：准备 → start 监听器 → 委派短路 → 权限 → 协作短路 → 路由 → 建任务 → 持久化收尾。
     *
     * @return 流水线
     */
    FlowPipeline pipeline() {
        return FlowPipeline.of(
            this::prepare,
            this::startListener,
            this::deputeGate,
            this::authGate,
            this::cooperateGate,
            this::route,
            this::buildTasks,
            this::persistAndFinalize);
    }

    /**
     * 合并变量、校验非开始节点流转类型，并把任务办理人和定义图加载进执行作用域。
     *
     * @param execution 执行作用域
     * @return 空表示继续执行后续步骤
     */
    private Optional<Instance> prepare(FlowExecution execution) {
        execution.mergeVariables();
        // 非第一个记得跳转类型必传
        if (!NodeType.isStart(execution.task.getNodeType())) {
            AssertUtil.isFalse(StringUtils.isNotEmpty(skipType), ExceptionCons.NULL_CONDITION_VALUE);
        }
        // R5：办理人全集进执行作用域，task.userList 与会签/票签视图共享同一引用
        execution.task.setUserList(execution.loadTaskUsers());
        // 定义图在 start 监听器前加载进作用域（操作内快照固定复用，监听器中途改图不进入本次操作视图）
        execution.loadCombineNoDef();
        return Optional.empty();
    }

    /**
     * 执行当前节点开始监听器。
     *
     * @param execution 执行作用域
     * @return 空表示继续执行后续步骤
     */
    private Optional<Instance> startListener(FlowExecution execution) {
        // 执行开始监听器
        ListenerUtil.executeStart(execution.contextListener(execution.task, execution.nowNode));
        return Optional.empty();
    }

    /**
     * 处理委派任务短路：受托人办理后仅回到原计划审批人，不继续路由。
     *
     * @param execution 执行作用域
     * @return 需要短路时返回当前实例，否则继续执行后续步骤
     */
    private Optional<Instance> deputeGate(FlowExecution execution) {
        // 如果是受托人在处理任务，需要处理一条委派记录，并且更新委托人，回到计划审批人,然后直接返回流程实例
        if (!execution.intent.isIgnore() && cooperationHandler.handleDepute(execution.task
            , execution.intent, skipType)) {
            return Optional.of(execution.instance);
        }
        return Optional.empty();
    }

    /**
     * 校验当前处理人是否具备办理权限。
     *
     * @param execution 执行作用域
     * @return 空表示继续执行后续步骤
     */
    private Optional<Instance> authGate(FlowExecution execution) {
        // 判断当前处理人是否有权限处理
        taskService.checkAuth(execution.task, execution.intent);
        return Optional.empty();
    }

    /**
     * 处理或签、会签、票签等协作逻辑；协作尚未满足流转条件时短路返回当前实例。
     *
     * @param execution 执行作用域
     * @return 需要短路时返回当前实例，否则继续执行后续步骤
     */
    private Optional<Instance> cooperateGate(FlowExecution execution) {
        //或签、会签、票签逻辑处理
        if (!execution.intent.isIgnore() && cooperationHandler.cooperate(execution, execution.task
            , execution.intent, skipType)) {
            return Optional.of(execution.instance);
        }
        return Optional.empty();
    }

    /**
     * 按跳转类型和流程变量解析后续节点，并写入流程图跳转元数据。
     *
     * @param execution 执行作用域
     * @return 空表示继续执行后续步骤
     */
    private Optional<Instance> route(FlowExecution execution) {
        // 获取后续任务节点结合
        pathWayData = pathResolver.resolve(execution.task, execution.nowNode, execution.instance
            , execution.intent, skipType, execution.loadCombineNoDef());
        // nextNodes 必须与 pathWayData.getTargetNodes() 同一引用：retainJoinPath 可能 clear 后 addAll，
        // 下游（一票否决/监听器）看到的清空后状态依赖该同一性，禁止复制或另行派生
        nextNodes = pathWayData.getTargetNodes();

        // 设置流程图元数据
        execution.instance.setDefJson(FlowEngine.chartService().skipMetadata(pathWayData));
        return Optional.empty();
    }

    /**
     * 按后续节点创建待办任务、替换办理人变量并执行分派监听器。
     *
     * @param execution 执行作用域
     * @return 空表示继续执行后续步骤
     */
    private Optional<Instance> buildTasks(FlowExecution execution) {
        // 构建增待办任务和设置结束任务历史记录
        addTasks = StreamUtils.toList(nextNodes,
            node -> taskService.addTask(node, execution.instance, execution.definition, execution.intent, skipType));

        // 办理人变量替换
        ExpressionUtil.evalVariable(addTasks, execution.intent.getVariables(), execution.intent.getNextHandlers()
            , execution.intent.isNextHandlerAppend());

        // 执行分派监听器
        ListenerUtil.executeAssignment(execution.contextListener(execution.task, execution.nowNode, nextNodes
            , addTasks));
        return Optional.empty();
    }

    /**
     * 归档当前任务、保存后续任务，处理退回的一票否决和流程完成后的未完成任务。
     *
     * @param execution 执行作用域
     * @return 空表示执行完成后由流水线返回当前实例
     */
    private Optional<Instance> persistAndFinalize(FlowExecution execution) {
        // 更新流程信息
        historyHandler.updateFlowInfo(taskService, execution.task, execution.instance, addTasks
            , execution.intent, skipType, nextNodes);

        // 一票否决（谨慎使用），如果退回，退回指向节点后还存在其他正在执行的待办任务，转历史任务，状态都为失效,重走流程。
        if (CollUtil.isNotEmpty(nextNodes) && SkipType.isReject(skipType)) {
            oneVoteVeto(execution.task, nextNodes.get(0).getNodeCode(), execution.loadCombineNoDef());
        }

        // 处理未完成的任务，当流程完成，还存在待办任务未完成，转历史任务，状态完成。
        taskService.handUndoneTask(execution.instance);

        // 执行完成和创建监听器
        ListenerUtil.endCreateListener(execution.contextListener(execution.task, execution.nowNode, nextNodes
            , addTasks));
        return Optional.empty();
    }

    /**
     * 一票否决（谨慎使用），如果退回，退回指向节点后还存在其他正在执行的待办任务，转历史任务，状态失效,重走流程。
     *
     * @param task         当前任务
     * @param nextNodeCode 下一个节点编码
     * @param flowCombine  流程数据集合
     */
    private void oneVoteVeto(Task task, String nextNodeCode, FlowCombine flowCombine) {
        // 一票否决（谨慎使用），如果退回，退回指向节点后还存在其他正在执行的待办任务，转历史任务，状态失效,重走流程。
        List<Task> tasks = taskService.list(FlowEngine.newTask().setInstanceId(task.getInstanceId()));
        // 属于退回指向节点的后置未完成的任务
        List<Task> noDoneTasks = new ArrayList<>();
        List<Node> suffixNodeList = FlowEngine.nodeService().suffixNodeList(nextNodeCode, flowCombine);
        List<String> suffixCodes = StreamUtils.toList(suffixNodeList, Node::getNodeCode);
        for (Task flowTask : tasks) {
            if (suffixCodes.contains(flowTask.getNodeCode())) {
                noDoneTasks.add(flowTask);
            }
        }
        if (CollUtil.isNotEmpty(noDoneTasks)) {
            taskService.removeAndUser(noDoneTasks);
        }
    }
}
