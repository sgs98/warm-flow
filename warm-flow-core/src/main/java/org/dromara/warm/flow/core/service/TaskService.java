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
package org.dromara.warm.flow.core.service;

import org.dromara.warm.flow.core.dto.FlowDto;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.orm.service.IWarmService;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.List;
import java.util.Map;

/**
 * 待办任务数据服务。
 *
 * <p>第三方流程操作统一使用 {@code FlowEngine.workflow()}。本服务保留任务数据能力，
 * 流转、流程控制和协作方法仅供统一流程门面组织引擎内部执行链。</p>
 *
 * @author warm
 * @since 2023-03-29
 */
public interface TaskService extends IWarmService<Task> {

    /**
     * 执行当前任务并按流程定义继续流转。
     *
     * @param taskId   当前待办任务主键
     * @param context  流程执行上下文
     * @param skipType 流转类型
     * @return 更新后的流程实例
     */
    Instance execute(Long taskId, WorkflowContext context, String skipType);

    /**
     * 将流程实例撤回到申请节点。
     *
     * @param instanceId 流程实例主键
     * @param context    流程执行上下文
     * @return 撤回后的流程实例
     */
    Instance revoke(Long instanceId, WorkflowContext context);

    /**
     * 根据流程实例终止流程。
     *
     * @param instanceId 流程实例主键
     * @param context    流程执行上下文
     * @return 终止后的流程实例
     */
    Instance terminateByInstanceId(Long instanceId, WorkflowContext context);

    /**
     * 根据当前待办终止流程。
     *
     * @param taskId  当前待办任务主键
     * @param context 流程执行上下文
     * @return 终止后的流程实例
     */
    Instance terminateByTaskId(Long taskId, WorkflowContext context);

    /**
     * 调整当前待办的办理人并记录协作历史。
     *
     * @param taskId         当前待办任务主键
     * @param context        流程执行上下文
     * @param addHandlers    需要增加的办理人
     * @param removeHandlers 需要移除的办理人
     * @param cooperateType  协作类型
     * @return 调整后的流程实例
     */
    Instance updateHandlers(Long taskId, WorkflowContext context, List<String> addHandlers
        , List<String> removeHandlers, Integer cooperateType);

    /**
     * 根据流程实例主键批量删除待办任务。
     *
     * @param instanceIds 流程实例主键集合
     * @return 是否删除成功
     */
    boolean deleteByInsIds(List<Long> instanceIds);

    /**
     * 创建尚未持久化的待办任务。
     *
     * @param node       目标节点
     * @param instance   流程实例
     * @param definition 流程定义
     * @param context    流程执行上下文
     * @param skipType   产生任务的流转类型
     * @return 待办任务
     */
    Task addTask(Node node, Instance instance, Definition definition, WorkflowContext context, String skipType);

    /**
     * 根据流程实例查询当前待办。
     *
     * @param instanceId 流程实例主键
     * @return 当前待办集合
     */
    List<Task> getByInsId(Long instanceId);

    /**
     * 根据流程实例和节点编码查询当前待办。
     *
     * @param instanceId 流程实例主键
     * @param nodeCodes  节点编码集合
     * @return 当前待办集合
     */
    List<Task> getByInsIdAndNodeCodes(Long instanceId, List<String> nodeCodes);

    /**
     * 根据后续任务更新流程实例节点、状态和变量。
     *
     * @param instance  流程实例
     * @param addTasks  后续任务
     * @param variables 本次写入的流程变量
     */
    void setInsFinishInfo(Instance instance, List<Task> addTasks, Map<String, Object> variables);

    /**
     * 将变量合并到流程实例已有变量中。
     *
     * @param instance  流程实例
     * @param variables 本次写入的流程变量
     */
    void mergeVariable(Instance instance, Map<String, Object> variables);

    /**
     * 加载当前待办对应的流程表单数据。
     *
     * @param taskId 当前待办任务主键
     * @return 流程表单数据
     */
    FlowDto load(Long taskId);

    /**
     * 加载历史任务对应的流程表单数据。
     *
     * @param hisTaskId 历史任务主键
     * @return 流程表单数据
     */
    FlowDto hisLoad(Long hisTaskId);
}
