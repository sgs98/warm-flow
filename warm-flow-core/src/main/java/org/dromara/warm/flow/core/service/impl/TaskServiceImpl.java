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
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.listener.Listener;
import org.dromara.warm.flow.core.listener.ListenerVariable;
import org.dromara.warm.flow.core.orm.dao.FlowTaskDao;
import org.dromara.warm.flow.core.orm.service.impl.WarmServiceImpl;
import org.dromara.warm.flow.core.service.TaskService;
import org.dromara.warm.flow.core.utils.*;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 待办任务服务实现。
 *
 * <p>作为任务操作入口，编排办理、撤回、终止和办理人调整流程，并负责待办创建、权限校验、实例状态回写及表单加载。</p>
 *
 * @author warm
 * @since 2023-03-29
 */
public class TaskServiceImpl extends WarmServiceImpl<FlowTaskDao<Task>, Task> implements TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);

    /**
     * 流程结束时未办待办的归档说明。
     */
    private static final String UNDONE_MESSAGE = "流程结束时待办未办理，自动失效";

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
        if (CollUtil.isEmpty(taskList)) {
            // 实例无普通待办但正在等待事件：走实例级终止分支；
            // 既无待办也无等待事件时保留原有 NOT_FOUNT_TASK 契约
            AssertUtil.isEmpty(FlowEngine.eventWaitService().getWaitingByInstanceId(instanceId)
                , ExceptionCons.NOT_FOUNT_TASK);
            return terminateWaitingInstance(instanceId, context);
        }
        Task task = taskList.get(0);
        return terminate(task, context);
    }

    /**
     * 无普通待办实例的终止分支：实例置为终态并取消所有等待事件，不做任务归档。
     *
     * @param instanceId 流程实例主键
     * @param context    流程执行上下文
     * @return 终止后的流程实例
     */
    private Instance terminateWaitingInstance(Long instanceId, WorkflowContext context) {
        Instance instance = FlowEngine.insService().getById(instanceId);
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);
        Node endNode = FlowEngine.nodeService().getEndNode(instance.getDefinitionId());
        AssertUtil.isNull(endNode, ExceptionCons.NOT_FOUND_END_NODE);
        if (StringUtils.isNotEmpty(context.getFlowStatus())) {
            instance.setFlowStatus(context.getFlowStatus());
        } else {
            instance.setFlowStatus(FlowStatusMachine.mapper().instanceStatus(FlowStatus.TERMINATE));
        }
        instance.setNodeType(endNode.getNodeType())
            .setNodeCode(endNode.getNodeCode())
            .setNodeName(endNode.getNodeName())
            .setUpdateTime(new Date());
        FlowEngine.eventWaitService().cancelByInstanceIds(Collections.singletonList(instanceId));
        FlowEngine.insService().updateById(instance);
        return instance;
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
     * 任务级抛出流程错误，触发匹配的错误边界事件。
     * <p>
     * 按 {@code taskId + 错误码} 匹配附着在该任务节点上的 {@code ERROR_BOUNDARY} 等待
     * （精确错误码优先于通配），匹配成功后中断当前任务（归档 {@code INVALID}）并沿错误出口推进。
     * 无边界或码不匹配时抛出「流程错误未被捕获」异常，实例、任务与等待记录状态不变，
     * 由调用方事务回滚；任务失败需要路由到实例级错误捕获时，宿主显式改调
     * {@code InsService.throwError}。
     *
     * @param taskId       当前待办任务主键
     * @param errorCode    错误码
     * @param errorMessage 错误描述，仅用于日志与异常信息
     * @return 抛出成功返回 {@code true}
     */
    @Override
    public boolean throwError(Long taskId, String errorCode, String errorMessage) {
        Task task = getById(taskId);
        AssertUtil.isNull(task, ExceptionCons.NOT_FOUNT_TASK);
        AssertUtil.isNull(task.getInstanceId(), ExceptionCons.NOT_FOUNT_INSTANCE);

        // 任务级抛出只路由到附着在该任务节点上的错误边界等待
        EventWait target = FlowEngine.eventWaitService().matchErrorWait(task.getInstanceId()
            , task.getId(), errorCode);
        if (target == null) {
            log.warn("流程错误未被捕获，流程不变 taskId={} errorCode={} message={}"
                , taskId, errorCode, errorMessage);
            throw new FlowException("[" + (errorCode == null ? "" : errorCode) + "]"
                + ExceptionCons.EVENT_ERROR_UNCAUGHT
                + (StringUtils.isEmpty(errorMessage) ? "" : "：" + errorMessage));
        }
        return FlowEngine.eventWaitService().triggerErrorWait(target, errorCode, errorMessage);
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
            .setPermissionList(resolvePermissions(StringUtils.str2List(node.getPermissionFlag()
                , FlowCons.SPLIT_AT), instance));

        if (StringUtils.isNotEmpty(node.getFormCustom()) && StringUtils.isNotEmpty(node.getFormPath())) {
            // 节点有自定义表单则使用
            addTask.setFormCustom(node.getFormCustom()).setFormPath(node.getFormPath());
        } else {
            addTask.setFormCustom(definition.getFormCustom()).setFormPath(definition.getFormPath());
        }

        return addTask;
    }

    /**
     * 解析节点办理人权限，将发起人标识替换为实际发起人。
     * <p>设计器中可将申请类首个节点办理人配置为 {@link FlowCons#WARMFLOWINITIATOR}，
     * 创建任务时统一替换为流程实例发起人（createBy），实现“谁发起、谁提交”；
     * 实例缺少发起人信息（如系统自动发起）时保留原标识不替换。</p>
     *
     * @param permissions 节点配置的办理人权限集合
     * @param instance    所属流程实例
     * @return 解析后的办理人权限集合
     */
    private List<String> resolvePermissions(List<String> permissions, Instance instance) {
        if (CollUtil.isEmpty(permissions) || StringUtils.isEmpty(instance.getCreateBy())) {
            return permissions;
        }
        return permissions.stream()
            .map(permission -> FlowCons.WARMFLOWINITIATOR.equals(permission)
                ? instance.getCreateBy() : permission)
            .collect(Collectors.toList());
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
                .setFlowStatus(instanceStatusOf(finallyTask.get()));
        }
        if (NodeType.isEnd(instance.getNodeType())) {
            // 实例收口即清理本实例的事件等待：普通结束同样需要，否则完成后仍会残留等待记录，
            // 外部消息或定时到点后会去推进一个已完成的实例
            FlowEngine.eventWaitService().cancelByInstanceIds(Collections.singletonList(instance.getId()));
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
     * 由落点待办推导实例状态。
     * <p>
     * 实例状态与任务状态是两个维度、两套编码：落到结束节点即「已完成」，落点待办为退回则为「已退回」，
     * 其余情况实例仍处于「审批中」。不能直接复制待办的状态码，否则宿主自定义映射后两套编码会串列。
     * <p>
     * 唯一例外是「终止」：终止结束事件经由执行意图把实例终止编码带到固定 END 的临时待办上，
     * 这里再还原成实例语义，使严重异常终止的单子状态为「终止」而不是「已完成」。
     *
     * @param task 本次落点待办
     * @return 实例状态编码
     */
    private String instanceStatusOf(Task task) {
        if (NodeType.isEnd(task.getNodeType())) {
            String finishedTaskCode = FlowStatusMachine.mapper().taskStatus(TaskStatus.FINISHED);
            if (finishedTaskCode.equals(task.getFlowStatus())) {
                return FlowStatusMachine.mapper().instanceStatus(FlowStatus.FINISHED);
            }
            // 只认「终止」这一个语义，宿主任意指定的任务状态仍按正常结束收口，不污染实例状态
            String terminateCode = FlowStatusMachine.mapper().instanceStatus(FlowStatus.TERMINATE);
            return terminateCode.equals(task.getFlowStatus())
                ? terminateCode
                : FlowStatusMachine.mapper().instanceStatus(FlowStatus.FINISHED);
        }
        String rejectCode = FlowStatusMachine.mapper().taskStatus(TaskStatus.REJECT);
        return FlowStatusMachine.mapper().instanceStatus(
            rejectCode.equals(task.getFlowStatus()) ? FlowStatus.REJECT : FlowStatus.APPROVAL);
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
     * 处理未完成的任务：流程结束时仍存在的待办，按「失效」归档进历史后删除。
     * <p>
     * 归档而非直接删除，保证审计上能看到「这一单结束时还有哪些待办没办、原本该谁办」；
     * 归档状态走 {@code StatusMapper#taskStatus(TaskStatus.INVALID)}，宿主自定义编码同样生效。
     *
     * @param instance 流程实例
     */
    void handUndoneTask(Instance instance) {
        if (NodeType.isEnd(instance.getNodeType())) {
            List<Task> taskList = list(FlowEngine.newTask().setInstanceId(instance.getId()));
            if (CollUtil.isNotEmpty(taskList)) {
                // 已归档的待办（本次办理 / 终止的那条）不再重复归档
                List<Long> archivedTaskIds = StreamUtils.toList(FlowEngine.hisTaskService()
                    .list(FlowEngine.newHisTask().setInstanceId(instance.getId())), HisTask::getTaskId);
                List<Task> undone = StreamUtils.filter(taskList
                    , task -> !archivedTaskIds.contains(task.getId()));
                if (CollUtil.isNotEmpty(undone)) {
                    archiveUndone(undone);
                }
                removeAndUser(taskList);
            }
        }
    }

    /**
     * 把未办待办按「失效」写入历史任务。
     *
     * @param taskList 未办待办
     */
    private void archiveUndone(List<Task> taskList) {
        Date now = new Date();
        String invalid = FlowStatusMachine.mapper().taskStatus(TaskStatus.INVALID);
        List<HisTask> hisTasks = new ArrayList<>();
        for (Task task : taskList) {
            HisTask hisTask = FlowEngine.newHisTask()
                .setTaskId(task.getId())
                .setInstanceId(task.getInstanceId())
                .setDefinitionId(task.getDefinitionId())
                .setNodeCode(task.getNodeCode())
                .setNodeName(task.getNodeName())
                .setNodeType(task.getNodeType())
                .setTargetNodeCode(task.getNodeCode())
                .setTargetNodeName(task.getNodeName())
                .setApprover(undoneApprover(task))
                .setCooperateType(CooperateType.APPROVAL.getKey())
                .setSkipType(SkipType.NONE.getKey())
                .setFlowStatus(invalid)
                .setMessage(UNDONE_MESSAGE)
                .setCreateTime(task.getCreateTime())
                .setUpdateTime(now);
            FlowEngine.dataFillHandler().idFill(hisTask);
            hisTasks.add(hisTask);
        }
        FlowEngine.hisTaskService().saveBatch(hisTasks);
    }

    /**
     * 取未办待办的办理人标识，多人以逗号拼接，便于历史审计。
     *
     * @param task 未办待办
     * @return 办理人标识；无办理人时返回 {@code null}
     */
    private String undoneApprover(Task task) {
        List<User> users = FlowEngine.userService().list(FlowEngine.newUser().setAssociated(task.getId()));
        if (CollUtil.isEmpty(users)) {
            return null;
        }
        return StreamUtils.join(users, User::getProcessedBy);
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
