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
import org.dromara.warm.flow.core.enums.*;
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
 * 待办任务Service业务层处理
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

    @Override
    public TaskService setDao(FlowTaskDao<Task> warmDao) {
        this.warmDao = warmDao;
        return this;
    }

    @Override
    public Instance execute(Long taskId, WorkflowContext context, String skipType) {
        AssertUtil.isNull(taskId, ExceptionCons.NULL_TASK_ID);
        FlowExecution execution = getAndCheck(taskId, context, FlowOp.EXECUTE);
        return new FlowExecuteChain(this, cooperationHandler, historyHandler, pathResolver, skipType)
            .pipeline().run(execution);
    }

    @Override
    public Instance revoke(Long instanceId, WorkflowContext context) {
        AssertUtil.isNull(instanceId, ExceptionCons.NULL_INSTANCE_ID);
        FlowExecution execution = FlowExecution.loadInstance(instanceId, context);
        return new FlowRevokeChain(this, instanceId).pipeline().run(execution);
    }

    @Override
    public Instance terminateByInstanceId(Long instanceId, WorkflowContext context) {
        AssertUtil.isNull(instanceId, ExceptionCons.NULL_INSTANCE_ID);
        // 获取待办任务
        List<Task> taskList = FlowEngine.taskService().getByInsId(instanceId);
        AssertUtil.isEmpty(taskList, ExceptionCons.NOT_FOUNT_TASK);
        Task task = taskList.get(0);
        return terminate(task, context);
    }

    @Override
    public Instance terminateByTaskId(Long taskId, WorkflowContext context) {
        return terminate(getById(taskId), context);
    }

    private Instance terminate(Task task, WorkflowContext context) {
        AssertUtil.isNull(task, ExceptionCons.NOT_FOUNT_TASK);
        AssertUtil.isNull(task.getId(), ExceptionCons.NULL_TASK_ID);
        // R1：复用门面已加载的任务对象，不再按主键二次查询
        FlowExecution execution = FlowExecution.loadTask(task, context, FlowOp.TERMINATE);
        return new FlowTerminateChain(this).pipeline().run(execution);
    }

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

    @Override
    public Instance updateHandlers(Long taskId, WorkflowContext context, List<String> addHandlers
        , List<String> removeHandlers, Integer cooperateType) {
        AssertUtil.isNull(taskId, ExceptionCons.NULL_TASK_ID);
        FlowExecution execution = getAndCheck(taskId, context, FlowOp.UPDATE_HANDLERS);
        return new FlowUpdateHandlersChain(this, taskId, addHandlers, removeHandlers, cooperateType)
            .pipeline().run(execution);
    }

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
            .setFlowStatus(StringUtils.emptyDefault(context.getInstanceStatus(),
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

    @Override
    public List<Task> getByInsId(Long instanceId) {
        return list(FlowEngine.newTask().setInstanceId(instanceId));
    }

    @Override
    public List<Task> getByInsIdAndNodeCodes(Long instanceId, List<String> nodeCodes) {
        return getDao().getByInsIdAndNodeCodes(instanceId, nodeCodes);
    }

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

    @Override
    public void mergeVariable(Instance instance, Map<String, Object> variable) {
        if (MapUtil.isNotEmpty(variable)) {
            String variableStr = instance.getVariable();
            Map<String, Object> deserialize = FlowEngine.jsonConvert.strToMap(variableStr);
            deserialize.putAll(variable);
            instance.setVariable(FlowEngine.jsonConvert.objToStr(deserialize));
        }
    }

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
