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

import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.orm.service.IWarmService;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.List;

/**
 * 历史任务记录Service接口
 *
 * @author warm
 * @since 2023-03-29
 */
public interface HisTaskService extends IWarmService<HisTask> {

    List<HisTask> listByTaskId(Long taskId);

    /**
     * 根据任务id和协作类型查询
     *
     * @param taskId         任务id
     * @param cooperateTypes 协作类型集合
     * @return List<HisTask>
     */
    List<HisTask> listByTaskIdAndCooperateTypes(Long taskId, Integer... cooperateTypes);

    /**
     * 根据实例Id和节点编码查询
     *
     * @param instanceId 流程实例id
     * @param nodeCodes  节点编码集合
     * @return List<HisTask>
     */
    List<HisTask> getByInsAndNodeCodes(Long instanceId, List<String> nodeCodes);

    /**
     * 根据instanceIds删除
     *
     * @param instanceIds 流程实例id集合
     * @return boolean
     */
    boolean deleteByInsIds(List<Long> instanceIds);

    /**
     * 设置流程历史任务信息
     *
     * @param task      当前任务
     * @param nextNodes 后续任务
     * @param context   流程执行上下文
     * @param skipType  流转类型
     * @return 历史任务
     */
    HisTask setSkipInsHis(Task task, List<Node> nextNodes, WorkflowContext context, String skipType);

    /**
     * 设置流程历史任务信息
     *
     * @param taskList  当前任务集合
     * @param nextNodes 后续任务
     * @param context   流程执行上下文
     * @param skipType  流转类型
     * @return 历史任务集合
     */
    List<HisTask> setSkipHisList(List<Task> taskList, List<Node> nextNodes, WorkflowContext context, String skipType);

    /**
     * 设置协作历史记录
     *
     * @param task          当前任务
     * @param context       流程执行上下文
     * @param collaborators 协作人
     * @param cooperateType 协作类型
     * @return 协作历史任务
     */
    HisTask setCooperateHis(Task task, WorkflowContext context, List<String> collaborators, Integer cooperateType);

    /**
     * 委派历史任务
     *
     * @param task          当前任务
     * @param context       流程执行上下文
     * @param entrustedUser 委托人
     * @param skipType      流转类型
     * @return HisTask 历史任务
     */
    HisTask setDeputeHisTask(Task task, WorkflowContext context, User entrustedUser, String skipType);

    /**
     * 设置会签票签历史任务
     *
     * @param task      当前任务
     * @param context   流程执行上下文
     * @param nodeRatio 节点比率
     * @param isPass    是否通过
     * @return HisTask 历史任务
     */
    HisTask setSignHisTask(Task task, WorkflowContext context, String nodeRatio, boolean isPass);

    /**
     * 设置流程历史任务信息
     *
     * @param task     当前任务
     * @param nextNode 跳转的节点
     * @param context  流程执行上下文
     * @param skipType 流转类型
     * @return HisTask          历史任务
     * @author xiarg
     * @since 2024/9/30 11:59
     */
    HisTask setSkipHisTask(Task task, Node nextNode, WorkflowContext context, String skipType);

    /**
     * 根据流程实例id查询历史任务
     *
     * @param instanceId 流程实例id
     * @return 历史记录集合
     */
    List<HisTask> getByInsId(Long instanceId);

}
