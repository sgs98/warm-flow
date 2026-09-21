package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.List;
import java.util.Objects;

/**
 * 待办任务协作处理器。
 *
 * <p>处理委派回收、会签和票签的单人办理结果；返回值表示本次操作是否只记录协作结果而暂不推进节点。</p>
 *
 * @author may
 */
final class TaskCooperationHandler {

    /**
     * 票签完成条件评估器。
     */
    private final TaskCooperationRuleEvaluator ruleEvaluator = new TaskCooperationRuleEvaluator();

    /**
     * 处理受托人办理，并将任务办理权恢复给委托人。
     *
     * @param task     当前待办任务
     * @param context  流程执行上下文
     * @param skipType 流转类型
     * @return 是否已完成委派处理；为true时本次流程不继续流转
     */
    boolean handleDepute(Task task, WorkflowContext context, String skipType) {
        List<User> entrustedUserList = StreamUtils.filter(task.getUserList(),
            user -> UserType.DEPUTE.getKey().equals(user.getType())
                && Objects.equals(context.getHandler(), user.getProcessedBy()));
        if (CollUtil.isEmpty(entrustedUserList)) {
            return false;
        }

        User entrustedUser = entrustedUserList.get(0);
        HisTask hisTask = FlowEngine.hisTaskService().setDeputeHisTask(task, context, entrustedUser, skipType);
        FlowEngine.hisTaskService().save(hisTask);
        FlowEngine.userService().removeById(entrustedUser.getId());

        User deputeUser = FlowEngine.userService().getOne(FlowEngine.newUser().setAssociated(task.getId())
            .setProcessedBy(entrustedUser.getCreateBy()).setType(UserType.APPROVAL.getKey()));
        if (ObjectUtil.isNull(deputeUser)) {
            User newUser = FlowEngine.userService().structureUser(entrustedUser.getAssociated()
                , entrustedUser.getCreateBy(), UserType.APPROVAL.getKey(), entrustedUser.getProcessedBy());
            FlowEngine.userService().save(newUser);
        }
        return true;
    }

    /**
     * 处理会签和票签，判断当前办理结果是否满足节点继续流转条件。
     *
     * @param execution 执行作用域
     * @param task      当前待办任务
     * @param context   流程执行上下文
     * @param skipType  流转类型
     * @return 是否仅记录当前办理结果；为true时本次流程不继续流转
     */
    boolean cooperate(FlowExecution execution, Task task, WorkflowContext context, String skipType) {
        if (CooperateType.isOrSign(execution.nowNode.getNodeRatio())) {
            return false;
        }

        String nodeRatio = execution.nowNode.getNodeRatio();
        // 从操作内办理人全集派生待办视图，与权限校验共用同一份快照。
        List<User> todoList = execution.usersOfTypes(UserType.APPROVAL.getKey()
            , UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
        AssertUtil.isEmpty(context.getHandler(), ExceptionCons.SIGN_NULL_HANDLER);
        User todoUser = CollUtil.getOne(StreamUtils.filter(todoList
            , u -> Objects.equals(u.getProcessedBy(), context.getHandler())));
        AssertUtil.isNull(todoUser, ExceptionCons.NOT_AUTHORITY);
        List<User> restList = StreamUtils.filter(todoList
            , u -> !Objects.equals(u.getProcessedBy(), context.getHandler()));

        if (CooperateType.isCountersign(nodeRatio) && SkipType.isReject(skipType)) {
            return removeRestList(restList);
        }

        List<HisTask> doneList = CollUtil.emptyDefault(FlowEngine.hisTaskService().listByTaskId(task.getId()));
        int allNum = todoList.size() + doneList.size();
        List<HisTask> donePassList = StreamUtils.filter(doneList
            , hisTask -> Objects.equals(hisTask.getSkipType(), SkipType.PASS.getKey()));
        List<HisTask> doneRejectList = StreamUtils.filter(doneList
            , hisTask -> Objects.equals(hisTask.getSkipType(), SkipType.REJECT.getKey()));
        boolean isPass = SkipType.isPass(skipType);

        TaskCooperationRuleEvaluator.Context ruleContext = new TaskCooperationRuleEvaluator.Context(nodeRatio
            , skipType, isPass, allNum, todoList, donePassList, doneRejectList
            , context.getVariables());
        if (ruleEvaluator.evaluate(ruleContext)) {
            return removeRestList(restList);
        }

        if (todoList.size() == 1) {
            return false;
        }
        HisTask hisTask = FlowEngine.hisTaskService().setSignHisTask(task, context, nodeRatio, isPass);
        FlowEngine.hisTaskService().save(hisTask);
        FlowEngine.userService().removeById(todoUser.getId());
        return true;
    }

    /**
     * 删除无需继续办理的剩余处理人。
     *
     * @param restList 剩余处理人
     * @return 固定返回false，表示当前任务可继续流转
     */
    private boolean removeRestList(List<User> restList) {
        if (CollUtil.isNotEmpty(restList)) {
            FlowEngine.userService().removeByIds(StreamUtils.toList(restList, User::getId));
        }
        return false;
    }
}
