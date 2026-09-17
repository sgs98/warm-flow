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
import org.dromara.warm.flow.core.dto.FlowCombine;
import org.dromara.warm.flow.core.dto.FlowDto;
import org.dromara.warm.flow.core.dto.PathWayData;
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
        R r = getAndCheck(taskId);
        return executeInternal(context, skipType, r.task, r);
    }

    /**
     * 执行节点流转，调用方负责完成上下文校验。
     *
     * @param context  流程执行上下文
     * @param skipType 流转类型
     * @param task     当前待办任务
     * @param r        任务执行上下文
     * @return 更新后的流程实例
     */
    private Instance executeInternal(WorkflowContext context, String skipType, Task task, R r) {
        context.setVariables(MapUtil.mergeAll(r.instance.getVariableMap(), context.getVariables()));
        // 非第一个记得跳转类型必传
        if (!NodeType.isStart(task.getNodeType())) {
            AssertUtil.isFalse(StringUtils.isNotEmpty(skipType), ExceptionCons.NULL_CONDITION_VALUE);
        }
        task.setUserList(FlowEngine.userService().listByAssociatedAndTypes(task.getId()));
        FlowCombine flowCombine = FlowEngine.defService().getFlowCombineNoDef(r.definition.getId());

        // 执行开始监听器
        ListenerUtil.executeStart(new ListenerVariable(r.definition, r.instance, r.nowNode, context.getVariables()
            , task).setContext(context));

        // 如果是受托人在处理任务，需要处理一条委派记录，并且更新委托人，回到计划审批人,然后直接返回流程实例
        if (!context.isIgnore() && cooperationHandler.handleDepute(task, context, skipType)) {
            return r.instance;
        }

        // 判断当前处理人是否有权限处理
        checkAuth(task, context);

        //或签、会签、票签逻辑处理
        if (!context.isIgnore() && cooperationHandler.cooperate(r.nowNode, task, context, skipType)) {
            return r.instance;
        }

        // 获取后续任务节点结合
        PathWayData pathWayData = pathResolver.resolve(task, r.nowNode, r.instance, context, skipType, flowCombine);
        List<Node> nextNodes = pathWayData.getTargetNodes();

        // 设置流程图元数据
        r.instance.setDefJson(FlowEngine.chartService().skipMetadata(pathWayData));

        // 构建增待办任务和设置结束任务历史记录
        List<Task> addTasks = StreamUtils.toList(nextNodes,
            node -> addTask(node, r.instance, r.definition, context, skipType));

        // 办理人变量替换
        ExpressionUtil.evalVariable(addTasks, context.getVariables(), context.getNextHandlers()
            , context.isNextHandlerAppend());

        // 执行分派监听器
        ListenerUtil.executeAssignment(new ListenerVariable(r.definition, r.instance, r.nowNode, context.getVariables()
            , task, nextNodes, addTasks).setContext(context));

        // 更新流程信息
        historyHandler.updateFlowInfo(this, task, r.instance, addTasks, context, skipType, nextNodes);

        // 一票否决（谨慎使用），如果退回，退回指向节点后还存在其他正在执行的待办任务，转历史任务，状态都为失效,重走流程。
        if (CollUtil.isNotEmpty(nextNodes) && SkipType.isReject(skipType)) {
            oneVoteVeto(task, nextNodes.get(0).getNodeCode(), flowCombine);
        }

        // 处理未完成的任务，当流程完成，还存在待办任务未完成，转历史任务，状态完成。
        handUndoneTask(r.instance);

        // 执行完成和创建监听器
        ListenerUtil.endCreateListener(new ListenerVariable(r.definition, r.instance, r.nowNode
            , context.getVariables(), task, nextNodes, addTasks).setContext(context));

        return r.instance;
    }

    @Override
    public Instance revoke(Long instanceId, WorkflowContext context) {
        AssertUtil.isNull(instanceId, ExceptionCons.NULL_INSTANCE_ID);
        return revokeInternal(instanceId, context);
    }

    /**
     * 执行实例撤回。
     *
     * @param instanceId 流程实例ID
     * @param context    流程执行上下文
     * @return 撤回后的流程实例
     */
    private Instance revokeInternal(Long instanceId, WorkflowContext context) {
        if (StringUtils.isEmpty(context.getInstanceStatus())) {
            context.setInstanceStatus(FlowStatus.CANCEL.getKey());
        }

        Instance instance = FlowEngine.insService().getById(instanceId);
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);
        context.setVariables(MapUtil.mergeAll(instance.getVariableMap(), context.getVariables()));
        Definition definition = FlowEngine.defService().getById(instance.getDefinitionId());
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        AssertUtil.isFalse(judgeActivityStatus(definition, instance), ExceptionCons.NOT_ACTIVITY);
        AssertUtil.isTrue(FlowStatusMachine.isTerminal(instance.getFlowStatus()), ExceptionCons.FLOW_FINISH);
        AssertUtil.isTrue(NodeType.isEnd(instance.getNodeType()), ExceptionCons.FLOW_FINISH);

        List<Task> taskList = getByInsId(instanceId);
        FlowCombine flowCombine = FlowEngine.defService().getFlowCombine(definition);
        Map<String, Node> nodeMap = StreamUtils.toMap(flowCombine.getAllNodes(), Node::getNodeCode, node -> node);
        // 执行开始监听器
        taskList.forEach(task -> ListenerUtil.executeStart(new ListenerVariable(definition, instance
            , nodeMap.get(task.getNodeCode()), context.getVariables(), task).setContext(context)));

        // 验证权限是不是当前任务的发起人
        if (!context.isIgnorePermission()) {
            AssertUtil.isFalse(instance.getCreateBy().equals(context.getHandler())
                , ExceptionCons.NOT_DEF_PROMOTER_NOT_CANCEL);
        }

        // 获取开始节点
        Node startNode = StreamUtils.filterOne(flowCombine.getAllNodes(), node -> NodeType.isStart(node.getNodeType()));
        // 获取下一个节点，如果是网关节点，则重新获取后续节点
        PathWayData pathWayData = new PathWayData().setInsId(instanceId).setSkipType(SkipType.REJECT.getKey());
        Node nextNode = FlowEngine.nodeService().getNextNode(startNode, null, SkipType.PASS.getKey()
            , null, flowCombine);
        List<Node> nextNodes = FlowEngine.nodeService().getNextByCheckGateway(context.getVariables(), nextNode
            , pathWayData, flowCombine);
        pathWayData.getTargetNodes().addAll(nextNodes);
        // 设置流程图元数据
        instance.setDefJson(FlowEngine.chartService().skipMetadata(pathWayData));

        // 查询任务,如果前一个节点是并行网关，可能任务表有多个任务,增加查询和判断
        List<Task> curTaskList = list(FlowEngine.newTask().setInstanceId(instance.getId()));
        AssertUtil.isEmpty(curTaskList, ExceptionCons.NOT_FOUND_FLOW_TASK);

        // 给回退到的那个节点赋权限-给当前处理人权限
        List<Task> addTasks = StreamUtils.toList(nextNodes,
            node -> addTask(node, instance, definition, context, SkipType.REJECT.getKey()));

        // 办理人变量替换
        ExpressionUtil.evalVariable(addTasks, context.getVariables(), context.getNextHandlers()
            , context.isNextHandlerAppend());

        // 执行分派监听器
        taskList.forEach(task -> ListenerUtil.executeAssignment(new ListenerVariable(definition, instance,
            nodeMap.get(task.getNodeCode()), context.getVariables(), task, nextNodes, addTasks)
            .setContext(context)));

        // 设置流程历史任务信息
        List<HisTask> insHisList = FlowEngine.hisTaskService().setSkipHisList(curTaskList, nextNodes, context
            , SkipType.REJECT.getKey());
        FlowEngine.hisTaskService().saveBatch(insHisList);
        // 待办任务和处理人
        removeAndUser(curTaskList);
        List<User> users = FlowEngine.userService().taskAddUsers(addTasks);

        // 设置任务完成后的实例相关信息
        setInsFinishInfo(instance, addTasks, context.getVariables());
        if (CollUtil.isNotEmpty(addTasks)) {
            saveBatch(addTasks);
        }
        FlowEngine.insService().updateById(instance);
        // 保存下一个待办任务的权限人
        FlowEngine.userService().saveBatch(users);

        // 执行完成和创建监听器
        taskList.forEach(task -> ListenerUtil.endCreateListener(new ListenerVariable(definition, instance,
            nodeMap.get(task.getNodeCode()), context.getVariables(), task, nextNodes, addTasks).setContext(context)));
        return instance;
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
        R r = getAndCheck(task.getId());
        return terminateInternal(r.task, context, r);
    }

    /**
     * 终止流程并清理剩余待办。
     *
     * @param task    当前待办任务
     * @param context 流程执行上下文
     * @param r       任务执行上下文
     * @return 终止后的流程实例
     */
    private Instance terminateInternal(Task task, WorkflowContext context, R r) {
        context.setVariables(MapUtil.mergeAll(r.instance.getVariableMap(), context.getVariables()));
        ListenerUtil.executeStart(new ListenerVariable(r.definition, r.instance, r.nowNode, context.getVariables()
            , task).setContext(context));

        // 判断当前处理人是否有权限处理
        task.setUserList(FlowEngine.userService().listByAssociatedAndTypes(task.getId()));
        checkAuth(task, context);

        // 所有待办转历史
        Node endNode = FlowEngine.nodeService().getEndNode(r.instance.getDefinitionId());

        // 设置流程图元数据
        PathWayData pathWayData = new PathWayData()
            .setInsId(task.getInstanceId())
            .setSkipType(SkipType.PASS.getKey())
            .setPathWayNodes(Collections.singletonList(r.nowNode))
            .setTargetNodes(Collections.singletonList(endNode));
        r.instance.setDefJson(FlowEngine.chartService().skipMetadata(pathWayData));

        // 流程实例完成
        r.instance.setNodeType(endNode.getNodeType())
            .setNodeCode(endNode.getNodeCode())
            .setNodeName(endNode.getNodeName())
            .setFlowStatus(StringUtils.emptyDefault(context.getInstanceStatus(), FlowStatus.TERMINATE.getKey()));

        // 待办任务转历史
        context.setInstanceStatus(r.instance.getFlowStatus());
        HisTask insHis = FlowEngine.hisTaskService().setSkipInsHis(task, Collections.singletonList(endNode)
            , context, SkipType.PASS.getKey());
        FlowEngine.hisTaskService().save(insHis);
        FlowEngine.insService().updateById(r.instance);

        // 删除流程相关办理人
        FlowEngine.userService().deleteByTaskIds(Collections.singletonList(task.getId()));

        // 处理未完成的任务，当流程完成，还存在待办任务未完成，转历史任务，状态完成。
        handUndoneTask(r.instance);
        // 最后判断是否存在节点监听器，存在执行节点监听器
        ListenerUtil.executeFinish(new ListenerVariable(r.definition, r.instance, r.nowNode, context.getVariables()
            , task).setContext(context));
        return r.instance;
    }

    @Override
    public boolean deleteByInsIds(List<Long> instanceIds) {
        List<Instance> instanceList = FlowEngine.insService().getByIds(instanceIds);
        Definition definition;
        for (Instance instance : instanceList) {
            definition = FlowEngine.defService().getById(instance.getDefinitionId());
            AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
            AssertUtil.isFalse(judgeActivityStatus(definition, instance), ExceptionCons.NOT_ACTIVITY);
        }
        return SqlHelper.retBool(getDao().deleteByInsIds(instanceIds));
    }

    @Override
    public Instance updateHandlers(Long taskId, WorkflowContext context, List<String> addHandlers
        , List<String> removeHandlers, Integer cooperateType) {
        AssertUtil.isNull(taskId, ExceptionCons.NULL_TASK_ID);
        R r = getAndCheck(taskId);
        return updateHandlersInternal(taskId, context, addHandlers, removeHandlers, cooperateType, r);
    }

    /**
     * 完成转办、委派、加签或减签的办理人调整。
     *
     * @param taskId         待办任务ID
     * @param context        流程执行上下文
     * @param addHandlers    新增办理人
     * @param removeHandlers 移除办理人
     * @param cooperateType  协作类型
     * @param r              任务执行上下文
     * @return 调整后的流程实例
     */
    private Instance updateHandlersInternal(Long taskId, WorkflowContext context, List<String> addHandlers
        , List<String> removeHandlers, Integer cooperateType, R r) {
        // 引擎级协作守卫：操作人必填、协作对象必填且不可重复持有任务、减签不可移除最后一名办理人
        if (CooperateType.TRANSFER.getKey().equals(cooperateType)) {
            AssertUtil.isNull(context.getHandler(), ExceptionCons.HANDLER_NOT_EMPTY);
            AssertUtil.isEmpty(addHandlers, ExceptionCons.NULL_TRANSFER_HANDLER);
            AssertUtil.isNotEmpty(FlowEngine.userService().getByProcessedBys(taskId, addHandlers
                , UserType.TRANSFER.getKey()), ExceptionCons.IS_ALREADY_TRANSFER);
        } else if (CooperateType.DEPUTE.getKey().equals(cooperateType)) {
            AssertUtil.isNull(context.getHandler(), ExceptionCons.HANDLER_NOT_EMPTY);
            AssertUtil.isEmpty(addHandlers, ExceptionCons.NULL_DEPUTE_HANDLER);
            AssertUtil.isNotEmpty(FlowEngine.userService().getByProcessedBys(taskId, addHandlers
                , UserType.DEPUTE.getKey()), ExceptionCons.IS_ALREADY_DEPUTE);
        } else if (CooperateType.ADD_SIGNATURE.getKey().equals(cooperateType)) {
            AssertUtil.isNull(context.getHandler(), ExceptionCons.HANDLER_NOT_EMPTY);
            AssertUtil.isEmpty(addHandlers, ExceptionCons.NULL_ADD_SIGNATURE_HANDLER);
            AssertUtil.isNotEmpty(FlowEngine.userService().getByProcessedBys(taskId, addHandlers
                , UserType.APPROVAL.getKey()), ExceptionCons.IS_ALREADY_SIGN);
        } else if (CooperateType.REDUCTION_SIGNATURE.getKey().equals(cooperateType)) {
            AssertUtil.isNull(context.getHandler(), ExceptionCons.HANDLER_NOT_EMPTY);
            AssertUtil.isEmpty(removeHandlers, ExceptionCons.NULL_REDUCTION_SIGNATURE_HANDLER);
            List<User> users = FlowEngine.userService().listByAssociatedAndTypes(taskId
                , UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey());
            AssertUtil.isTrue(CollUtil.isEmpty(users) || users.size() == 1
                , ExceptionCons.REDUCTION_SIGN_ONE_ERROR);
        }
        context.setVariables(MapUtil.mergeAll(r.instance.getVariableMap(), context.getVariables()));
        // 执行开始监听器
        ListenerUtil.executeStart(new ListenerVariable(r.definition, r.instance, r.nowNode
            , context.getVariables(), r.task).setContext(context));

        // 获取给谁的权限
        if (!context.isIgnorePermission() && !context.isIgnore()) {
            // 判断当前处理人是否有权限，获取当前办理人的权限
            List<String> permissions = context.getPermissions();
            // 获取任务权限人
            List<String> taskPermissions = FlowEngine.userService().getPermission(taskId
                , UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
            AssertUtil.isTrue(CollUtil.isNotEmpty(taskPermissions) && (CollUtil.isEmpty(permissions)
                || CollUtil.notContainsAny(permissions, taskPermissions)), ExceptionCons.NOT_AUTHORITY);
        }
        // 留存历史记录
        HisTask hisTask = null;
        // 删除对应的操作人
        if (CollUtil.isNotEmpty(removeHandlers)) {
            for (String reductionHandler : removeHandlers) {
                FlowEngine.userService().remove(FlowEngine.newUser().setAssociated(taskId)
                    .setProcessedBy(reductionHandler));
            }
            hisTask = FlowEngine.hisTaskService().setCooperateHis(r.task, context, removeHandlers, cooperateType);
        }

        // 新增权限人
        if (CollUtil.isNotEmpty(addHandlers)) {
            String type;
            if (CooperateType.TRANSFER.getKey().equals(cooperateType)) {
                type = UserType.TRANSFER.getKey();
            } else if (CooperateType.DEPUTE.getKey().equals(cooperateType)) {
                type = UserType.DEPUTE.getKey();
            } else {
                type = UserType.APPROVAL.getKey();
            }
            FlowEngine.userService().saveBatch(StreamUtils.toList(addHandlers, permission ->
                FlowEngine.userService().structureUser(taskId, permission
                    , type, context.getHandler())));
            hisTask = FlowEngine.hisTaskService().setCooperateHis(r.task, context, addHandlers, cooperateType);
        }
        if (ObjectUtil.isNotNull(hisTask)) {
            FlowEngine.hisTaskService().save(hisTask);
        }
        // 最后判断是否存在节点监听器，存在执行节点监听器
        ListenerUtil.executeFinish(new ListenerVariable(r.definition, r.instance, r.nowNode, context.getVariables()
            , r.task));
        return r.instance;
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
    private void removeAndUser(List<Task> taskList) {
        List<Long> taskIds = StreamUtils.toList(taskList, Task::getId);
        boolean removed = removeByIds(taskIds);
        AssertUtil.isFalse(removed, ExceptionCons.NOT_FOUNT_TASK);
        FlowEngine.userService().deleteByTaskIds(taskIds);
    }

    /**
     * 根据任务ID重新加载可执行上下文，确保使用数据库中的最新任务状态。
     *
     * @param taskId 待办任务ID
     * @return 任务执行上下文
     */
    private R getAndCheck(Long taskId) {
        AssertUtil.isNull(taskId, ExceptionCons.NULL_TASK_ID);
        return getAndCheck(getById(taskId));
    }

    /**
     * 加载并校验任务执行上下文。
     *
     * @param task 当前待办任务
     * @return 任务执行上下文
     */
    private R getAndCheck(Task task) {
        return FlowTaskContextLoader.load(task);
    }

    /**
     * 流程操作需要的任务、实例、定义和当前节点上下文。
     */
    static class R {
        public final Instance instance;
        public final Definition definition;
        public final Node nowNode;
        public final Task task;

        /**
         * 创建不可变的任务执行上下文。
         *
         * @param instance   流程实例
         * @param definition 流程定义
         * @param nowNode    当前节点
         * @param task       当前待办任务
         */
        public R(Instance instance, Definition definition, Node nowNode, Task task) {
            this.instance = instance;
            this.definition = definition;
            this.nowNode = nowNode;
            this.task = task;
        }
    }

    /**
     * 判断当前处理人是否有权限处理
     *
     * @param task    当前任务
     * @param context 流程执行上下文
     */
    private void checkAuth(Task task, WorkflowContext context) {
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
     * 一票否决（谨慎使用），如果退回，退回指向节点后还存在其他正在执行的待办任务，转历史任务，状态都为退回,重走流程。
     *
     * @param task         当前任务
     * @param nextNodeCode 下一个节点编码
     * @param flowCombine  流程数据集合
     */
    private void oneVoteVeto(Task task, String nextNodeCode, FlowCombine flowCombine) {
        // 一票否决（谨慎使用），如果退回，退回指向节点后还存在其他正在执行的待办任务，转历史任务，状态失效,重走流程。
        List<Task> tasks = list(FlowEngine.newTask().setInstanceId(task.getInstanceId()));
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
            removeAndUser(noDoneTasks);
        }
    }


    /**
     * 处理未完成的任务，当流程完成，还存在待办任务未完成，转历史任务，状态完成。
     *
     * @param instance 流程实例
     */
    private void handUndoneTask(Instance instance) {
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
     * 判断流程定义和实例是否都处于激活状态。
     *
     * @param definition 流程定义
     * @param instance   流程实例
     * @return 是否允许执行流程操作
     */
    private boolean judgeActivityStatus(Definition definition, Instance instance) {
        return ActivityStatus.isActivity(definition.getActivityStatus())
            && ActivityStatus.isActivity(instance.getActivityStatus());
    }


    @Override
    public FlowDto load(Long taskId) {
        R r = getAndCheck(taskId);

        ListenerVariable listenerVariable = new ListenerVariable(r.definition, r.instance, r.nowNode
            , r.instance.getVariableMap(), r.task);

        FlowDto flowDto = new FlowDto();
        if (FlowCons.FORM_CUSTOM_Y.equals(r.nowNode.getFormCustom())) {
            ListenerUtil.execute(listenerVariable, Listener.LISTENER_FORM_LOAD, r.nowNode.getListenerPath()
                , r.nowNode.getListenerType());
            Form form = FlowEngine.formService().getById(Long.valueOf(r.task.getFormPath()));
            flowDto.setForm(form);
        } else if (StringUtils.isEmpty(r.nowNode.getFormCustom()) && FlowCons.FORM_CUSTOM_Y.equals(r.definition.getFormCustom())) {
            ListenerUtil.execute(listenerVariable, Listener.LISTENER_FORM_LOAD, r.definition.getListenerPath()
                , r.definition.getListenerType());
            Form form = FlowEngine.formService().getById(Long.valueOf(r.definition.getFormPath()));
            flowDto.setForm(form);
        }
        flowDto.setData(r.instance.getVariableMap().get(FlowCons.FORM_DATA));

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
