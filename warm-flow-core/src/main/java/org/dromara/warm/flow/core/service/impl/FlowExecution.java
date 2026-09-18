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
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.listener.ListenerVariable;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.MapUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 流程执行作用域：一次流程操作期间已加载聚合（任务、实例、定义、当前节点、定义图）的统一存放处。
 *
 * <p>包内私有，零公共面。加载方法的校验顺序逐条镜像原 {@code FlowTaskContextLoader} 与
 * {@code revokeInternal} 的既有次序，异常消息与触发顺序是行为契约，不得增删或重排。</p>
 *
 * <p>定义图（combine）与办理人全集同策略：一次操作内只加载一次，操作内所有消费点
 * 复用同一引用，回调后不重载——监听器不可达 combine 内部列表，办理人视图则与权限校验
 * 共用同一份快照。变量相反：合并只发生在既有调用点，并保持向 {@code intent}
 * 原地写回合并结果的现状语义。</p>
 *
 * @author warm
 */
final class FlowExecution {

    /**
     * 当前待办任务，仅任务级操作存在。
     */
    final Task task;
    /**
     * 流程实例。
     */
    final Instance instance;
    /**
     * 流程定义。
     */
    final Definition definition;
    /**
     * 当前节点，任务级操作加载；实例级操作不强行伪造。
     */
    final Node nowNode;
    /**
     * 调用方意图，引擎编排原则上不写（既有兼容写回见加载与合并方法）；读取路径（load）为 null。
     */
    final WorkflowContext intent;

    /**
     * 定义图缓存，经 {@link #loadCombine()} / {@link #loadCombineNoDef()} 懒加载。
     */
    private FlowCombine combine;

    /**
     * 办理人全集缓存，经 {@link #loadTaskUsers()} 懒加载。
     */
    private List<User> taskUsers;

    /**
     * 加载任务执行需要的实例、定义和当前节点，并统一校验可执行状态。
     *
     * @param task   当前待办任务（复用调用方已加载的对象，不重复查询）
     * @param intent 调用方上下文
     * @param op     发起操作（guard 表行键，决定状态前置谓词集合）
     * @return 执行作用域
     */
    static FlowExecution loadTask(Task task, WorkflowContext intent, FlowOp op) {
        AssertUtil.isNull(task, ExceptionCons.NOT_FOUNT_TASK);
        Instance instance = FlowEngine.insService().getById(task.getInstanceId());
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);
        Definition definition = FlowEngine.defService().getById(instance.getDefinitionId());
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        FlowStatusMachine.checkGuards(op, definition, instance);
        Node nowNode = FlowEngine.nodeService().getByDefIdAndNodeCode(task.getDefinitionId(), task.getNodeCode());
        AssertUtil.isNull(nowNode, ExceptionCons.LOST_CUR_NODE);
        return new FlowExecution(task, instance, definition, nowNode, intent);
    }

    /**
     * 加载实例级操作需要的实例与定义并校验，撤回（revoke）的默认状态补写与变量合并
     * 保留在原有次序：仅空时补 CANCEL → 实例判空 → 变量合并 → 定义判空 → 激活 → 终态 → 结束节点。
     * 不自动加载待办列表，撤回的两次任务查询仍由原编排点分别发起。
     *
     * @param instanceId 流程实例ID
     * @param intent     调用方上下文
     * @return 执行作用域
     */
    static FlowExecution loadInstance(Long instanceId, WorkflowContext intent) {
        if (StringUtils.isEmpty(intent.getInstanceStatus())) {
            intent.setInstanceStatus(FlowStatus.CANCEL.getKey());
        }
        Instance instance = FlowEngine.insService().getById(instanceId);
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);
        intent.setVariables(MapUtil.mergeAll(instance.getVariableMap(), intent.getVariables()));
        Definition definition = FlowEngine.defService().getById(instance.getDefinitionId());
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        FlowStatusMachine.checkGuards(FlowOp.REVOKE, definition, instance);
        return new FlowExecution(null, instance, definition, null, intent);
    }

    private FlowExecution(Task task, Instance instance, Definition definition, Node nowNode
        , WorkflowContext intent) {
        this.task = task;
        this.instance = instance;
        this.definition = definition;
        this.nowNode = nowNode;
        this.intent = intent;
    }

    /**
     * 加载含定义对象的定义图（撤回路径使用），一次操作内只加载一次。
     *
     * @return 流程数据集合
     */
    FlowCombine loadCombine() {
        if (combine == null) {
            combine = FlowEngine.defService().getFlowCombine(definition);
        }
        return combine;
    }

    /**
     * 加载不含定义对象的定义图（办理路径使用，与既有 getFlowCombineNoDef 调用点对应），
     * 一次操作内只加载一次。
     *
     * @return 流程数据集合
     */
    FlowCombine loadCombineNoDef() {
        if (combine == null) {
            combine = FlowEngine.defService().getFlowCombineNoDef(definition.getId());
        }
        return combine;
    }

    /**
     * 加载当前任务的办理人全集，一次操作内只查询一次；办理路径与 {@code task.userList}
     * 共享同一引用。回调后不重载：监听器期间的办理人表写库不进入本次操作视图（R5 语义，
     * 与权限校验使用同一份快照）。
     *
     * @return 办理人全集
     */
    List<User> loadTaskUsers() {
        if (taskUsers == null) {
            taskUsers = FlowEngine.userService().listByAssociatedAndTypes(task.getId());
        }
        return taskUsers;
    }

    /**
     * 从办理人全集派生指定类型的视图（R5：内存过滤替代按类型 SQL 查询）。
     *
     * @param types 办理人类型
     * @return 命中类型的办理人视图
     */
    List<User> usersOfTypes(String... types) {
        return StreamUtils.filter(loadTaskUsers(), user -> Arrays.asList(types).contains(user.getType()));
    }

    /**
     * 在既有合并点合并实例变量与意图变量，并保持向意图原地写回合并结果的现状语义。
     *
     * @return 合并后的变量
     */
    Map<String, Object> mergeVariables() {
        Map<String, Object> merged = MapUtil.mergeAll(instance.getVariableMap(), intent.getVariables());
        intent.setVariables(merged);
        return merged;
    }

    /**
     * 构建不带调用方上下文的监听器变量（读取路径与不带 context 的既有监听点使用）。
     * 每次调用必须新建实例：endCreateListener 以游标方式原地修改传入对象，禁止缓存复用。
     *
     * @param task 监听器对应任务
     * @param node 监听器对应节点
     * @return 监听器变量
     */
    ListenerVariable rawListener(Task task, Node node) {
        return new ListenerVariable(definition, instance, node, intent.getVariables(), task);
    }

    /**
     * 构建附带调用方上下文的监听器变量。
     *
     * @param task 监听器对应任务
     * @param node 监听器对应节点
     * @return 监听器变量
     */
    ListenerVariable contextListener(Task task, Node node) {
        return rawListener(task, node).setContext(intent);
    }

    /**
     * 构建附带后续节点与新建任务集合的监听器变量，列表保持原引用不复制，
     * 原地修改时机（endCreateListener 逐节点推进）不变。
     *
     * @param task      监听器对应任务
     * @param node      监听器对应节点
     * @param nextNodes 后续节点集合
     * @param nextTasks 新建任务集合
     * @return 监听器变量
     */
    ListenerVariable contextListener(Task task, Node node, List<Node> nextNodes, List<Task> nextTasks) {
        return new ListenerVariable(definition, instance, node, intent.getVariables(), task
            , nextNodes, nextTasks).setContext(intent);
    }
}
