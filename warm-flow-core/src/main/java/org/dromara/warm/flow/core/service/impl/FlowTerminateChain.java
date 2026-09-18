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
import org.dromara.warm.flow.core.dto.PathWayData;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.utils.ListenerUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.Collections;
import java.util.Optional;

/**
 * 终止操作链：装配 5 个有序步骤并委托 {@link FlowPipeline} 执行，终止流程并清理剩余待办。
 *
 * <p><b>生命周期锁</b>：每次终止操作新建实例，字段为本次操作中间态（结束节点）；
 * 非线程安全，禁止静态化或缓存复用。</p>
 *
 * <p>步骤体自原 {@code TaskServiceImpl.terminateInternal} 逐行搬移（唯一等价改写：
 * authGate 的办理人查询走 {@code execution.loadTaskUsers()} 懒加载），语句顺序与引用语义
 * 为行为契约，由特征测试锁定。</p>
 *
 * @author warm
 */
final class FlowTerminateChain {

    /**
     * 任务服务，复用既有任务持久化与校验能力。
     */
    private final TaskServiceImpl taskService;

    /**
     * 结束节点。
     */
    private Node endNode;

    /**
     * @param taskService 任务服务
     */
    FlowTerminateChain(TaskServiceImpl taskService) {
        this.taskService = taskService;
    }

    /**
     * 按序装配终止链：合并与开始监听器 → 权限 → 终态构造 → 持久化 → 收尾与完成监听器。
     *
     * @return 流水线
     */
    FlowPipeline pipeline() {
        return FlowPipeline.of(
            this::prepareAndStart,
            this::authGate,
            this::buildTerminalState,
            this::persist,
            this::finalizeAndFinish);
    }

    private Optional<Instance> prepareAndStart(FlowExecution execution) {
        execution.mergeVariables();
        ListenerUtil.executeStart(execution.contextListener(execution.task, execution.nowNode));
        return Optional.empty();
    }

    private Optional<Instance> authGate(FlowExecution execution) {
        // 本链唯一办理人加载点：与原直接 listByAssociatedAndTypes 查询等价（同懒加载首次触发、同时点）；
        // 后续若新增 usersOfTypes 派生会引入快照语义，需重新评估
        execution.task.setUserList(execution.loadTaskUsers());
        // 判断当前处理人是否有权限处理（userList 在 start 监听器后设置——与办理链相反，时机契约）
        taskService.checkAuth(execution.task, execution.intent);
        return Optional.empty();
    }

    private Optional<Instance> buildTerminalState(FlowExecution execution) {
        // 所有待办转历史
        endNode = FlowEngine.nodeService().getEndNode(execution.instance.getDefinitionId());

        // 设置流程图元数据
        PathWayData pathWayData = new PathWayData()
            .setInsId(execution.task.getInstanceId())
            .setSkipType(SkipType.PASS.getKey())
            .setPathWayNodes(Collections.singletonList(execution.nowNode))
            .setTargetNodes(Collections.singletonList(endNode));
        execution.instance.setDefJson(FlowEngine.chartService().skipMetadata(pathWayData));

        // 流程实例完成
        execution.instance.setNodeType(endNode.getNodeType())
            .setNodeCode(endNode.getNodeCode())
            .setNodeName(endNode.getNodeName())
            .setFlowStatus(StringUtils.emptyDefault(execution.intent.getInstanceStatus()
                , FlowStatus.TERMINATE.getKey()));
        return Optional.empty();
    }

    private Optional<Instance> persist(FlowExecution execution) {
        // 待办任务转历史（顺序与办理链的 updateFlowInfo 不同，不合并）
        execution.intent.setInstanceStatus(execution.instance.getFlowStatus());
        HisTask insHis = FlowEngine.hisTaskService().setSkipInsHis(execution.task
            , Collections.singletonList(endNode), execution.intent, SkipType.PASS.getKey());
        FlowEngine.hisTaskService().save(insHis);
        FlowEngine.insService().updateById(execution.instance);

        // 删除流程相关办理人
        FlowEngine.userService().deleteByTaskIds(Collections.singletonList(execution.task.getId()));
        return Optional.empty();
    }

    private Optional<Instance> finalizeAndFinish(FlowExecution execution) {
        // 处理未完成的任务，当流程完成，还存在待办任务未完成，转历史任务，状态完成。
        taskService.handUndoneTask(execution.instance);
        // 最后判断是否存在节点监听器，存在执行节点监听器
        ListenerUtil.executeFinish(execution.contextListener(execution.task, execution.nowNode));
        return Optional.empty();
    }
}
