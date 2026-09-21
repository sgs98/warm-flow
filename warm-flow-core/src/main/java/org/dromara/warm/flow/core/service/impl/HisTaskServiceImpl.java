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
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.orm.dao.FlowHisTaskDao;
import org.dromara.warm.flow.core.orm.service.impl.WarmServiceImpl;
import org.dromara.warm.flow.core.service.HisTaskService;
import org.dromara.warm.flow.core.utils.*;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史任务服务实现。
 *
 * <p>统一构造普通流转、协作、委派和票签产生的历史记录，确保状态、变量和业务扩展信息按同一规则落库。</p>
 *
 * @author warm
 * @since 2023-03-29
 */
public class HisTaskServiceImpl extends WarmServiceImpl<FlowHisTaskDao<HisTask>, HisTask> implements HisTaskService {

    /**
     * 注入历史任务 DAO。
     *
     * @param warmDao 历史任务数据访问对象
     * @return 当前服务实例
     */
    @Override
    public HisTaskService setDao(FlowHisTaskDao<HisTask> warmDao) {
        this.warmDao = warmDao;
        return this;
    }

    /**
     * 按原待办任务主键查询全部历史办理记录。
     *
     * @param taskId 原待办任务主键
     * @return 历史办理记录
     */
    @Override
    public List<HisTask> listByTaskId(Long taskId) {
        return list(FlowEngine.newHisTask().setTaskId(taskId));
    }

    /**
     * 按原待办任务主键及协作类型查询历史记录；协作类型为空时返回该任务全部记录。
     *
     * @param taskId         原待办任务主键
     * @param cooperateTypes 协作类型
     * @return 历史办理记录
     */
    @Override
    public List<HisTask> listByTaskIdAndCooperateTypes(Long taskId, Integer... cooperateTypes) {
        if (ArrayUtil.isEmpty(cooperateTypes)) {
            return listByTaskId(taskId);
        }
        if (cooperateTypes.length == 1) {
            return list(FlowEngine.newHisTask().setTaskId(taskId).setCooperateType(cooperateTypes[0]));
        }
        return getDao().listByTaskIdAndCooperateTypes(taskId, cooperateTypes);
    }

    /**
     * 按流程实例和节点编码集合查询历史任务。
     *
     * @param instanceId 流程实例主键
     * @param nodeCodes  节点编码集合
     * @return 历史任务集合
     */
    @Override
    public List<HisTask> getByInsAndNodeCodes(Long instanceId, List<String> nodeCodes) {
        return getDao().getByInsAndNodeCodes(instanceId, nodeCodes);
    }

    /**
     * 按流程实例主键集合批量删除历史任务。
     *
     * @param instanceIds 流程实例主键集合
     * @return 是否删除成功
     */
    @Override
    public boolean deleteByInsIds(List<Long> instanceIds) {
        return SqlHelper.retBool(getDao().deleteByInsIds(instanceIds));
    }

    /**
     * 为实例级流转构造一条历史任务，目标节点可包含并行分支。
     *
     * @param task      当前待办任务
     * @param nextNodes 目标节点集合
     * @param context   流程执行上下文
     * @param skipType  跳转类型
     * @return 尚未持久化的历史任务
     */
    @Override
    public HisTask setSkipInsHis(Task task, List<Node> nextNodes, WorkflowContext context, String skipType) {
        return setSkipHis(task, nextNodes, context, skipType, customStatus(context));
    }

    /**
     * 为多个当前待办批量构造流转历史，所有记录使用同一自定义状态解析结果。
     *
     * @param taskList  当前待办任务集合
     * @param nextNodes 目标节点集合
     * @param context   流程执行上下文
     * @param skipType  跳转类型
     * @return 尚未持久化的历史任务集合
     */
    @Override
    public List<HisTask> setSkipHisList(List<Task> taskList, List<Node> nextNodes, WorkflowContext context
        , String skipType) {
        String flowStatus = customStatus(context);
        List<HisTask> hisTasks = new ArrayList<>();
        for (Task task : taskList) {
            HisTask hisTask = setSkipHis(task, nextNodes, context, skipType, flowStatus);
            hisTasks.add(hisTask);
        }
        return hisTasks;
    }

    /**
     * 为单个当前待办和目标节点构造流转历史。
     *
     * @param task     当前待办任务
     * @param nextNode 目标节点
     * @param context  流程执行上下文
     * @param skipType 跳转类型
     * @return 尚未持久化的历史任务
     */
    @Override
    public HisTask setSkipHisTask(Task task, Node nextNode, WorkflowContext context, String skipType) {
        return setSkipHis(task, CollUtil.toList(nextNode), context, skipType, customStatus(context));
    }


    /**
     * 构造加签、减签或其他协作操作的历史记录。
     *
     * <p>协作者写入 {@code collaborator}，目标节点保持为当前节点，表示任务尚未发生节点流转。</p>
     *
     * @param task          当前待办任务
     * @param context       流程执行上下文
     * @param collaborators 协作者标识集合
     * @param cooperateType 协作类型
     * @return 尚未持久化的历史任务
     */
    @Override
    public HisTask setCooperateHis(Task task, WorkflowContext context, List<String> collaborators
        , Integer cooperateType) {
        String flowStatus = customStatus(context);
        HisTask hisTask = FlowEngine.newHisTask()
            .setTaskId(task.getId())
            .setInstanceId(task.getInstanceId())
            .setCooperateType(ObjectUtil.defaultNull(cooperateType, CooperateType.APPROVAL.getKey()))
            .setCollaborator(StreamUtils.join(collaborators, c -> c))
            .setNodeCode(task.getNodeCode())
            .setNodeName(task.getNodeName())
            .setNodeType(task.getNodeType())
            .setDefinitionId(task.getDefinitionId())
            .setTargetNodeCode(task.getNodeCode())
            .setTargetNodeName(task.getNodeName())
            .setApprover(context.getHandler())
            .setSkipType(SkipType.NONE.getKey())
            .setFlowStatus(FlowStatusMachine.defaultStatus(flowStatus, FlowStatus.APPROVAL.getKey()))
            .setFormCustom(task.getFormCustom())
            .setFormPath(task.getFormPath())
            .setMessage(context.getMessage())
            .setVariable(variableStr(context))
            //业务详情添加至历史记录
            .setExt(context.getExt())
            .setCreateTime(task.getCreateTime());
        FlowEngine.dataFillHandler().idFill(hisTask);
        return hisTask;
    }

    /**
     * 构造委派办理历史，记录实际办理人和原委托人。
     *
     * @param task          当前待办任务
     * @param context       流程执行上下文
     * @param entrustedUser 委派办理人记录
     * @param skipType      跳转类型
     * @return 尚未持久化的历史任务
     */
    @Override
    public HisTask setDeputeHisTask(Task task, WorkflowContext context, User entrustedUser, String skipType) {
        String flowStatus = customStatus(context);
        HisTask hisTask = FlowEngine.newHisTask()
            .setTaskId(task.getId())
            .setInstanceId(task.getInstanceId())
            .setCooperateType(CooperateType.DEPUTE.getKey())
            .setNodeCode(task.getNodeCode())
            .setNodeName(task.getNodeName())
            .setNodeType(task.getNodeType())
            .setDefinitionId(task.getDefinitionId())
            .setTargetNodeCode(task.getNodeCode())
            .setTargetNodeName(task.getNodeName())
            .setApprover(context.getHandler())
            .setCollaborator(entrustedUser.getCreateBy())
            .setSkipType(skipType)
            .setFlowStatus(FlowStatusMachine.skipStatus(flowStatus, skipType))
            .setFormCustom(task.getFormCustom())
            .setFormPath(task.getFormPath())
            .setMessage(context.getMessage())
            .setVariable(variableStr(context))
            //业务详情添加至历史记录
            .setExt(context.getExt())
            .setCreateTime(task.getCreateTime());
        FlowEngine.dataFillHandler().idFill(hisTask);
        return hisTask;
    }

    /**
     * 构造会签或票签的单人办理历史。
     *
     * @param task      当前待办任务
     * @param context   流程执行上下文
     * @param nodeRatio 节点协作规则
     * @param isPass 当前办理结果是否通过
     * @return 尚未持久化的历史任务
     */
    @Override
    public HisTask setSignHisTask(Task task, WorkflowContext context, String nodeRatio, boolean isPass) {
        String flowStatus = customStatus(context);
        HisTask hisTask = FlowEngine.newHisTask()
            .setTaskId(task.getId())
            .setInstanceId(task.getInstanceId())
            .setCooperateType(CooperateType.isCountersign(nodeRatio)
                ? CooperateType.COUNTERSIGN.getKey() : CooperateType.VOTE.getKey())
            .setNodeCode(task.getNodeCode())
            .setNodeName(task.getNodeName())
            .setNodeType(task.getNodeType())
            .setDefinitionId(task.getDefinitionId())
            .setApprover(context.getHandler())
            .setMessage(context.getMessage())
            .setSkipType(isPass ? SkipType.PASS.getKey() : SkipType.REJECT.getKey())
            .setFlowStatus(FlowStatusMachine.defaultStatus(flowStatus
                , isPass ? FlowStatus.PASS.getKey() : FlowStatus.REJECT.getKey()))
            .setFormCustom(task.getFormCustom())
            .setFormPath(task.getFormPath())
            .setMessage(context.getMessage())
            .setVariable(variableStr(context))
            //业务详情添加至历史记录
            .setExt(context.getExt())
            .setCreateTime(task.getCreateTime());
        FlowEngine.dataFillHandler().idFill(hisTask);
        return hisTask;
    }

    /**
     * 按流程实例主键查询全部历史任务。
     *
     * @param instanceId 流程实例主键
     * @return 历史任务集合
     */
    @Override
    public List<HisTask> getByInsId(Long instanceId) {
        return FlowEngine.hisTaskService().list(FlowEngine.newHisTask().setInstanceId(instanceId));
    }

    /**
     * 构造普通节点流转历史，目标节点编码和名称支持多个分支合并记录。
     *
     * @param task       当前待办任务
     * @param nextNodes  目标节点集合
     * @param context    流程执行上下文
     * @param skipType   跳转类型
     * @param flowStatus 已解析的历史任务状态
     * @return 尚未持久化的历史任务
     */
    private HisTask setSkipHis(Task task, List<Node> nextNodes, WorkflowContext context, String skipType
        , String flowStatus) {
        HisTask hisTask = FlowEngine.newHisTask()
            .setTaskId(task.getId())
            .setInstanceId(task.getInstanceId())
            .setCooperateType(CooperateType.APPROVAL.getKey())
            .setNodeCode(task.getNodeCode())
            .setNodeName(task.getNodeName())
            .setNodeType(task.getNodeType())
            .setDefinitionId(task.getDefinitionId())
            .setTargetNodeCode(StreamUtils.join(nextNodes, Node::getNodeCode))
            .setTargetNodeName(StreamUtils.join(nextNodes, Node::getNodeName))
            .setApprover(context.getHandler())
            .setSkipType(skipType)
            .setFlowStatus(FlowStatusMachine.skipStatus(flowStatus, skipType))
            .setFormCustom(task.getFormCustom())
            .setFormPath(task.getFormPath())
            .setMessage(context.getMessage())
            .setVariable(variableStr(context))
            //业务详情添加至历史记录
            .setExt(context.getExt())
            .setCreateTime(task.getCreateTime());
        FlowEngine.dataFillHandler().idFill(hisTask);
        return hisTask;
    }

    /**
     * 按“历史任务状态优先、实例状态兜底”的规则解析自定义状态。
     *
     * @param context 流程执行上下文
     * @return 自定义状态，未指定时返回空值
     */
    private String customStatus(WorkflowContext context) {
        return FlowStatusMachine.customStatus(context.getHistoryTaskStatus(), context.getInstanceStatus());
    }

    /**
     * 将本次提交变量序列化为历史快照，避免后续实例变量变化影响审计记录。
     *
     * @param context 流程执行上下文
     * @return 序列化后的变量 JSON
     */
    private String variableStr(WorkflowContext context) {
        return FlowEngine.jsonConvert.objToStr(context.getVariables());
    }

}
