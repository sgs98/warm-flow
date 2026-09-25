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
import org.dromara.warm.flow.core.entity.*;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.utils.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 撤回操作链：装配 6 个有序步骤并委托 {@link FlowPipeline} 执行，入口已通过
 * {@code FlowExecution.loadInstance} 完成实例级加载与校验。
 *
 * <p><b>生命周期锁</b>：每次撤回操作新建实例，字段为本次操作中间态（待办快照、节点索引、
 * 路由结果、新建任务）；非线程安全，禁止静态化或缓存复用。</p>
 *
 * <p>步骤体自原 {@code TaskServiceImpl.revokeInternal} 逐行搬移，语句顺序与引用语义
 * 为行为契约，由特征测试锁定。</p>
 *
 * @author warm
 */
final class FlowRevokeChain {

    /**
     * 任务服务，复用既有任务持久化与校验能力。
     */
    private final TaskServiceImpl taskService;
    /**
     * 流程实例ID。
     */
    private final Long instanceId;

    /**
     * 待办快照（监听器执行前查询，撤回清理对象）。
     */
    private List<Task> taskList;
    /**
     * 节点编码索引。
     */
    private Map<String, Node> nodeMap;
    /**
     * 路由结果。
     */
    private PathWayData pathWayData;
    /**
     * 后续节点集合——必须保存 {@code getNextByCheckGateway} 的返回引用：
     * 它与 {@code pathWayData.getTargetNodes()} 内容相等但对象不同，下游建任务/历史/监听器
     * 均使用该引用，禁止从 path 派生（与办理链的别名方向相反）。
     */
    private List<Node> nextNodes;
    /**
     * 新增待办任务。
     */
    private List<Task> addTasks;

    /**
     * @param taskService 任务服务
     * @param instanceId  流程实例ID
     */
    FlowRevokeChain(TaskServiceImpl taskService, Long instanceId) {
        this.taskService = taskService;
        this.instanceId = instanceId;
    }

    /**
     * 按序装配撤回链：快照与开始监听器 → 发起人门 → 路由 → 建任务 → 持久化 → 完成监听器。
     *
     * @return 流水线
     */
    FlowPipeline pipeline() {
        return FlowPipeline.of(
            this::snapshotAndStart,
            this::promoterGate,
            this::route,
            this::buildTasks,
            this::persist,
            this::finishListeners);
    }

    /**
     * 查询撤回前的待办快照、构建节点索引，并对快照中的任务逐个执行开始监听器。
     *
     * @param execution 执行作用域
     * @return 空表示继续执行后续步骤
     */
    private Optional<Instance> snapshotAndStart(FlowExecution execution) {
        taskList = taskService.getByInsId(instanceId);
        FlowCombine flowCombine = execution.loadCombine();
        nodeMap = StreamUtils.toMap(flowCombine.getAllNodes(), Node::getNodeCode, node -> node);
        // 执行开始监听器
        taskList.forEach(task -> ListenerUtil.executeStart(execution.contextListener(task
            , nodeMap.get(task.getNodeCode()))));
        return Optional.empty();
    }

    /**
     * 校验当前处理人是否为流程发起人；忽略权限时跳过校验。
     *
     * @param execution 执行作用域
     * @return 空表示继续执行后续步骤
     */
    private Optional<Instance> promoterGate(FlowExecution execution) {
        // 验证权限是不是当前任务的发起人
        if (!execution.intent.isIgnorePermission()) {
            AssertUtil.isFalse(execution.instance.getCreateBy().equals(execution.intent.getHandler())
                , ExceptionCons.NOT_DEF_PROMOTER_NOT_CANCEL);
        }
        return Optional.empty();
    }

    /**
     * 从开始节点重新解析撤回后应创建的后续节点，并写入流程图跳转元数据。
     *
     * @param execution 执行作用域
     * @return 空表示继续执行后续步骤
     */
    private Optional<Instance> route(FlowExecution execution) {
        // 获取开始节点
        Node startNode = StreamUtils.filterOne(execution.loadCombine().getAllNodes()
            , node -> NodeType.isStart(node.getNodeType()));
        // 获取下一个节点，如果是网关节点，则重新获取后续节点
        pathWayData = new PathWayData().setInsId(instanceId).setSkipType(SkipType.REJECT.getKey());
        Node nextNode = FlowEngine.nodeService().getNextNode(startNode, null, SkipType.PASS.getKey()
            , null, execution.loadCombine());
        nextNodes = FlowEngine.nodeService().getNextByCheckGateway(execution.intent.getVariables(), nextNode
            , pathWayData, execution.loadCombine());
        pathWayData.getTargetNodes().addAll(nextNodes);
        // 设置流程图元数据
        execution.instance.setDefJson(FlowEngine.chartService().skipMetadata(pathWayData));
        return Optional.empty();
    }

    /**
     * 基于撤回目标节点创建新待办、替换办理人变量，并对原待办快照执行分派监听器。
     *
     * @param execution 执行作用域
     * @return 空表示继续执行后续步骤
     */
    private Optional<Instance> buildTasks(FlowExecution execution) {
        // R3：复用监听器执行前的待办快照作为撤回清理对象，不再于监听器后二次查询
        AssertUtil.isEmpty(taskList, ExceptionCons.NOT_FOUND_FLOW_TASK);

        // 给回退到的那个节点赋权限-给当前处理人权限
        addTasks = StreamUtils.toList(nextNodes,
            node -> taskService.addTask(node, execution.instance, execution.definition, execution.intent
                , SkipType.REJECT.getKey()));

        // 办理人变量替换
        ExpressionUtil.evalVariable(addTasks, execution.intent.getVariables(), execution.intent.getNextHandlers()
            , execution.intent.isNextHandlerAppend());

        // 执行分派监听器
        taskList.forEach(task -> ListenerUtil.executeAssignment(execution.contextListener(task
            , nodeMap.get(task.getNodeCode()), nextNodes, addTasks)));
        return Optional.empty();
    }

    /**
     * 将撤回前待办整体归档并删除，保存新待办和办理人，更新流程实例。
     *
     * @param execution 执行作用域
     * @return 空表示继续执行后续步骤
     */
    private Optional<Instance> persist(FlowExecution execution) {
        // 设置流程历史任务信息
        List<HisTask> insHisList = FlowEngine.hisTaskService().setSkipHisList(taskList, nextNodes
            , execution.intent, SkipType.REJECT.getKey());
        FlowEngine.hisTaskService().saveBatch(insHisList);
        // 待办任务和处理人（禁复用 TaskHistoryHandler.updateFlowInfo：撤回为整表快照归档 + 全量删除，
        // 且 updateById 不带 NOT_FOUNT_INSTANCE 断言——与办理链持久化形似实异）
        taskService.removeAndUser(taskList);
        List<User> users = FlowEngine.userService().taskAddUsers(addTasks);

        // 设置任务完成后的实例相关信息
        taskService.setInsFinishInfo(execution.instance, addTasks, execution.intent.getVariables());
        if (CollUtil.isNotEmpty(addTasks)) {
            taskService.saveBatch(addTasks);
        }
        FlowEngine.insService().updateById(execution.instance);
        // 保存下一个待办任务的权限人
        FlowEngine.userService().saveBatch(users);
        return Optional.empty();
    }

    /**
     * 对撤回前待办快照逐个执行完成/创建监听器。
     *
     * @param execution 执行作用域
     * @return 空表示执行完成后由流水线返回当前实例
     */
    private Optional<Instance> finishListeners(FlowExecution execution) {
        // 执行完成和创建监听器
        taskList.forEach(task -> ListenerUtil.endCreateListener(execution.contextListener(task
            , nodeMap.get(task.getNodeCode()), nextNodes, addTasks)));
        return Optional.empty();
    }
}
