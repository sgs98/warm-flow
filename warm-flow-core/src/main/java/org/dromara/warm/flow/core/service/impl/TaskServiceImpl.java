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
import org.dromara.warm.flow.core.constant.FlowCons;
import org.dromara.warm.flow.core.dto.FlowDto;
import org.dromara.warm.flow.core.entity.*;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.listener.Listener;
import org.dromara.warm.flow.core.listener.ListenerVariable;
import org.dromara.warm.flow.core.orm.dao.FlowTaskDao;
import org.dromara.warm.flow.core.orm.service.impl.WarmServiceImpl;
import org.dromara.warm.flow.core.service.TaskService;
import org.dromara.warm.flow.core.utils.*;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 待办任务服务实现。
 *
 * <p>作为任务操作入口，编排办理、撤回、终止和办理人调整流程，并负责待办创建、权限校验、实例状态回写及表单加载。</p>
 *
 * @author warm
 * @since 2023-03-29
 */
public class TaskServiceImpl extends WarmServiceImpl<FlowTaskDao<Task>, Task> implements TaskService {

    /**
     * 委派、会签和票签处理。
     */
    private final TaskCooperationHandler cooperationHandler = new TaskCooperationHandler();
    /**
     * 当前任务归档及后续任务持久化处理。
     */
    private final TaskHistoryHandler historyHandler = new TaskHistoryHandler();
    /**
     * 节点跳转和网关路径解析。
     */
    private final FlowPathResolver pathResolver = new FlowPathResolver();

    /**
     * 注入待办任务 DAO。
     *
     * @param warmDao 待办任务 DAO
     * @return 当前任务服务
     */
    @Override
    public TaskService setDao(FlowTaskDao<Task> warmDao) {
        this.warmDao = warmDao;
        return this;
    }

    /**
     * 办理当前待办，并按传入跳转类型执行通过、退回或指定节点跳转。
     *
     * @param taskId   待办任务主键
     * @param context  流程执行上下文
     * @param skipType 跳转类型
     * @return 办理后的流程实例
     */
    @Override
    public Instance execute(Long taskId, WorkflowContext context, String skipType) {
        AssertUtil.isNull(taskId, ExceptionCons.NULL_TASK_ID);
        FlowExecution execution = getAndCheck(taskId, context, FlowOp.EXECUTE);
        return new FlowExecuteChain(this, cooperationHandler, historyHandler, pathResolver, skipType)
            .pipeline().run(execution);
    }

    /**
     * 撤回指定流程实例到发起后的首个待办节点。
     *
     * @param instanceId 流程实例主键
     * @param context    流程执行上下文
     * @return 撤回后的流程实例
     */
    @Override
    public Instance revoke(Long instanceId, WorkflowContext context) {
        AssertUtil.isNull(instanceId, ExceptionCons.NULL_INSTANCE_ID);
        FlowExecution execution = FlowExecution.loadInstance(instanceId, context);
        return new FlowRevokeChain(this, instanceId).pipeline().run(execution);
    }

    /**
     * 根据流程实例终止流程，使用实例下任一当前待办进入权限和状态校验。
     *
     * @param instanceId 流程实例主键
     * @param context    流程执行上下文
     * @return 终止后的流程实例
     */
    @Override
    public Instance terminateByInstanceId(Long instanceId, WorkflowContext context) {
        AssertUtil.isNull(instanceId, ExceptionCons.NULL_INSTANCE_ID);
        // 获取待办任务
        List<Task> taskList = FlowEngine.taskService().getByInsId(instanceId);
        AssertUtil.isEmpty(taskList, ExceptionCons.NOT_FOUNT_TASK);
        Task task = taskList.get(0);
        return terminate(task, context);
    }

    /**
     * 根据当前待办终止流程。
     *
     * @param taskId  待办任务主键
     * @param context 流程执行上下文
     * @return 终止后的流程实例
     */
    @Override
    public Instance terminateByTaskId(Long taskId, WorkflowContext context) {
        return terminate(getById(taskId), context);
    }

    /**
     * 终止流程的内部入口，复用已加载任务并装配终止执行链。
     *
     * @param task    当前待办任务
     * @param context 流程执行上下文
     * @return 终止后的流程实例
     */
    private Instance terminate(Task task, WorkflowContext context) {
        AssertUtil.isNull(task, ExceptionCons.NOT_FOUNT_TASK);
        AssertUtil.isNull(task.getId(), ExceptionCons.NULL_TASK_ID);
        // R1：复用门面已加载的任务对象，不再按主键二次查询
        FlowExecution execution = FlowExecution.loadTask(task, context, FlowOp.TERMINATE);
        return new FlowTerminateChain(this).pipeline().run(execution);
    }

    /**
     * 按流程实例批量删除待办任务，删除前先校验每个实例所属定义与实例仍处于允许清理的状态。
     *
     * @param instanceIds 流程实例主键集合
     * @return 是否删除成功
     */
    @Override
    public boolean deleteByInsIds(List<Long> instanceIds) {
        List<Instance> instanceList = FlowEngine.insService().getByIds(instanceIds);
        Definition definition;
        for (Instance instance : instanceList) {
            definition = FlowEngine.defService().getById(instance.getDefinitionId());
            AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
            // 删除行（guard 表唯一离群行）：仅要求激活，终态/已结束实例允许删除清理
            FlowStatusMachine.checkGuards(FlowOp.DELETE, definition, instance);
        }
        return SqlHelper.retBool(getDao().deleteByInsIds(instanceIds));
    }

    /**
     * 调整当前待办办理人并记录协作历史，覆盖转办、委派、加签和减签。
     *
     * @param taskId         待办任务主键
     * @param context        流程执行上下文
     * @param addHandlers    需要新增的办理人
     * @param removeHandlers 需要移除的办理人
     * @param cooperateType  协作类型
     * @return 调整后的流程实例
     */
    @Override
    public Instance updateHandlers(Long taskId, WorkflowContext context, List<String> addHandlers
        , List<String> removeHandlers, Integer cooperateType) {
        AssertUtil.isNull(taskId, ExceptionCons.NULL_TASK_ID);
        FlowExecution execution = getAndCheck(taskId, context, FlowOp.UPDATE_HANDLERS);
        return new FlowUpdateHandlersChain(this, taskId, addHandlers, removeHandlers, cooperateType)
            .pipeline().run(execution);
    }

    /**
     * 根据目标节点创建尚未持久化的待办任务，并继承节点或定义上的表单配置。
     *
     * @param node       目标节点
     * @param instance   流程实例
     * @param definition 流程定义
     * @param context    流程执行上下文
     * @param skipType   跳转类型
     * @return 尚未持久化的待办任务
     */
    @Override
    public Task addTask(Node node, Instance instance, Definition definition, WorkflowContext context
        , String skipType) {
        Task addTask = FlowEngine.newTask();
        Date now = new Date();
        FlowEngine.dataFillHandler().idFill(addTask);
        addTask.setDefinitionId(instance.getDefinitionId())
            .setInstanceId(instance.getId())
            .setNodeCode(node.getNodeCode())
            .setNodeName(node.getNodeName())
            .setNodeType(node.getNodeType())
            .setFlowStatus(StringUtils.emptyDefault(context.getFlowStatus(),
                FlowStatusMachine.taskStatus(node.getNodeType(), skipType)))
            .setCreateTime(now)
            .setPermissionList(StringUtils.str2List(node.getPermissionFlag(), FlowCons.SPLIT_AT));

        if (StringUtils.isNotEmpty(node.getFormCustom()) && StringUtils.isNotEmpty(node.getFormPath())) {
            // 节点有自定义表单则使用
            addTask.setFormCustom(node.getFormCustom()).setFormPath(node.getFormPath());
        } else {
            addTask.setFormCustom(definition.getFormCustom()).setFormPath(definition.getFormPath());
        }

        return addTask;
    }

    /**
     * 查询流程实例下的当前待办任务。
     *
     * @param instanceId 流程实例主键
     * @return 当前待办任务集合
     */
    @Override
    public List<Task> getByInsId(Long instanceId) {
        return list(FlowEngine.newTask().setInstanceId(instanceId));
    }

    /**
     * 按流程实例和节点编码集合查询当前待办任务。
     *
     * @param instanceId 流程实例主键
     * @param nodeCodes  节点编码集合
     * @return 命中的待办任务集合
     */
    @Override
    public List<Task> getByInsIdAndNodeCodes(Long instanceId, List<String> nodeCodes) {
        return getDao().getByInsIdAndNodeCodes(instanceId, nodeCodes);
    }

    /**
     * 更新实例上的当前节点、流程状态和变量。后续任务包含结束节点时会移除结束任务并以其状态收口实例。
     *
     * @param instance  流程实例
     * @param addTasks  新建待办任务集合，可被原地移除结束任务
     * @param variables 本次需要合并到实例的变量
     */
    @Override
    public void setInsFinishInfo(Instance instance, List<Task> addTasks, Map<String, Object> variables) {
        instance.setUpdateTime(new Date());
        // 合并流程变量到实例对象
        mergeVariable(instance, variables);
        if (CollUtil.isNotEmpty(addTasks)) {
            AtomicReference<Task> finallyTask = new AtomicReference<>();
            addTasks.removeIf(addTask -> {
                if (NodeType.isEnd(addTask.getNodeType())) {
                    finallyTask.set(addTask);
                    return true;
                }
                return false;
            });
            if (ObjectUtil.isNull(finallyTask.get())) {
                finallyTask.set(getNextTask(addTasks));
            }
            instance.setNodeType(finallyTask.get().getNodeType())
                .setNodeCode(finallyTask.get().getNodeCode())
                .setNodeName(finallyTask.get().getNodeName())
                .setFlowStatus(finallyTask.get().getFlowStatus());
        }
    }

    /**
     * 将本次流程变量合并到实例已持久化的变量 JSON 中。
     *
     * @param instance 流程实例
     * @param variable 本次新增或覆盖的变量
     */
    @Override
    public void mergeVariable(Instance instance, Map<String, Object> variable) {
        if (MapUtil.isNotEmpty(variable)) {
            String variableStr = instance.getVariable();
            Map<String, Object> deserialize = FlowEngine.jsonConvert.strToMap(variableStr);
            deserialize.putAll(variable);
            instance.setVariable(FlowEngine.jsonConvert.objToStr(deserialize));
        }
    }

    /**
     * 从新增待办中选择用于回写实例当前位置的任务；多任务时优先结束节点，否则取主键最大的任务。
     *
     * @param tasks 新增待办任务集合
     * @return 用于回写实例当前位置的任务
     */
    private Task getNextTask(List<Task> tasks) {
        if (tasks.size() == 1) {
            return tasks.get(0);
        }
        for (Task task : tasks) {
            if (NodeType.isEnd(task.getNodeType())) {
                return task;
            }
        }
        return tasks.stream().max(Comparator.comparingLong(Task::getId)).orElse(null);
    }

    /**
     * 删除待办任务及其关联办理人，并校验待办确实被删除。
     *
     * @param taskList 待删除任务
     */
    void removeAndUser(List<Task> taskList) {
        List<Long> taskIds = StreamUtils.toList(taskList, Task::getId);
        boolean removed = removeByIds(taskIds);
        AssertUtil.isFalse(removed, ExceptionCons.NOT_FOUNT_TASK);
        FlowEngine.userService().deleteByTaskIds(taskIds);
    }

    /**
     * 根据任务ID重新加载可执行上下文，确保使用数据库中的最新任务状态。
     *
     * @param taskId  待办任务ID
     * @param context 调用方上下文
     * @param op      发起操作（guard 表行键）
     * @return 执行作用域
     */
    private FlowExecution getAndCheck(Long taskId, WorkflowContext context, FlowOp op) {
        AssertUtil.isNull(taskId, ExceptionCons.NULL_TASK_ID);
        return getAndCheck(getById(taskId), context, op);
    }

    /**
     * 加载并校验任务执行上下文。
     *
     * @param task    当前待办任务
     * @param context 调用方上下文
     * @param op      发起操作（guard 表行键）
     * @return 执行作用域
     */
    private FlowExecution getAndCheck(Task task, WorkflowContext context, FlowOp op) {
        return FlowExecution.loadTask(task, context, op);
    }

    /**
     * 判断当前处理人是否有权限处理
     *
     * @param task    当前任务
     * @param context 流程执行上下文
     */
    void checkAuth(Task task, WorkflowContext context) {
        if (context.isIgnorePermission() || context.isIgnore()) {
            return;
        }
        // 查询审批人和转办人
        List<String> permissions = StreamUtils.toList(task.getUserList(), User::getProcessedBy);
        // 当前办理人拥有的权限和设计时候填的权限集合是否有交集，有说明有权限办理
        AssertUtil.isTrue(CollUtil.isNotEmpty(permissions) && (CollUtil.isEmpty(context.getPermissions())
            || CollUtil.notContainsAny(context.getPermissions(), permissions)), ExceptionCons.NULL_ROLE_NODE);
    }


    /**
     * 处理未完成的任务，当流程完成，还存在待办任务未完成，转历史任务，状态完成。
     *
     * @param instance 流程实例
     */
    void handUndoneTask(Instance instance) {
        if (NodeType.isEnd(instance.getNodeType())) {
            List<Task> taskList = list(FlowEngine.newTask().setInstanceId(instance.getId()));
            if (CollUtil.isNotEmpty(taskList)) {
                removeAndUser(taskList);
            }
        }
    }

    /**
     * 供任务历史处理器复用待办及办理人删除逻辑。
     *
     * @param taskList 待删除任务
     */
    void removeAndUserInternal(List<Task> taskList) {
        removeAndUser(taskList);
    }


    /**
     * 加载当前待办表单与已保存的表单数据，节点自定义表单优先于定义表单。
     *
     * @param taskId 待办任务主键
     * @return 表单与表单数据
     */
    @Override
    public FlowDto load(Long taskId) {
        FlowExecution execution = getAndCheck(taskId, null, FlowOp.LOAD);

        ListenerVariable listenerVariable = new ListenerVariable(execution.definition, execution.instance
            , execution.nowNode, execution.instance.getVariableMap(), execution.task);

        FlowDto flowDto = new FlowDto();
        if (FlowCons.FORM_CUSTOM_Y.equals(execution.nowNode.getFormCustom())) {
            ListenerUtil.execute(listenerVariable, Listener.LISTENER_FORM_LOAD, execution.nowNode.getListenerPath()
                , execution.nowNode.getListenerType());
            Form form = FlowEngine.formService().getById(Long.valueOf(execution.task.getFormPath()));
            flowDto.setForm(form);
        } else if (StringUtils.isEmpty(execution.nowNode.getFormCustom()) && FlowCons.FORM_CUSTOM_Y.equals(execution.definition.getFormCustom())) {
            ListenerUtil.execute(listenerVariable, Listener.LISTENER_FORM_LOAD, execution.definition.getListenerPath()
                , execution.definition.getListenerType());
            Form form = FlowEngine.formService().getById(Long.valueOf(execution.definition.getFormPath()));
            flowDto.setForm(form);
        }
        flowDto.setData(execution.instance.getVariableMap().get(FlowCons.FORM_DATA));

        return flowDto;
    }

    /**
     * 加载历史任务对应的表单与历史变量数据。
     *
     * @param hisTaskId 历史任务主键
     * @return 表单与表单数据
     */
    @Override
    public FlowDto hisLoad(Long hisTaskId) {
        HisTask hisTask = FlowEngine.hisTaskService().getById(hisTaskId);
        AssertUtil.isNull(hisTask, ExceptionCons.NOT_FOUND_FLOW_TASK);

        Definition definition = FlowEngine.defService().getById(hisTask.getDefinitionId());
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);

        Node nowNode = CollUtil.getOne(FlowEngine.nodeService()
            .getByNodeCodes(Collections.singletonList(hisTask.getNodeCode()), hisTask.getDefinitionId()));
        AssertUtil.isNull(nowNode, ExceptionCons.LOST_CUR_NODE);

        FlowDto flowDto = new FlowDto();
        if (FlowCons.FORM_CUSTOM_Y.equals(nowNode.getFormCustom())) {
            Form form = FlowEngine.formService().getById(Long.valueOf(hisTask.getFormPath()));
            flowDto.setForm(form);
        } else if (StringUtils.isEmpty(nowNode.getFormCustom()) && FlowCons.FORM_CUSTOM_Y.equals(definition.getFormCustom())) {
            Form form = FlowEngine.formService().getById(Long.valueOf(definition.getFormPath()));
            flowDto.setForm(form);
        }
        flowDto.setData(hisTask.getVariableMap().get(FlowCons.FORM_DATA));

        return flowDto;
    }
}
