package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;

import java.util.List;
import java.util.Objects;

/**
 * 待办任务协作处理器。
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
     * @param task       当前待办任务
     * @param flowParams 流程操作参数
     * @return 是否已完成委派处理；为true时本次流程不继续流转
     */
    boolean handleDepute(Task task, FlowParams flowParams) {
        List<User> entrustedUserList = StreamUtils.filter(task.getUserList(),
            user -> UserType.DEPUTE.getKey().equals(user.getType())
                && Objects.equals(flowParams.getHandler(), user.getProcessedBy()));
        if (CollUtil.isEmpty(entrustedUserList)) {
            return false;
        }

        User entrustedUser = entrustedUserList.get(0);
        HisTask hisTask = FlowEngine.hisTaskService().setDeputeHisTask(task, flowParams, entrustedUser);
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
     * @param nowNode    当前流程节点
     * @param task       当前待办任务
     * @param flowParams 流程操作参数
     * @return 是否仅记录当前办理结果；为true时本次流程不继续流转
     */
    boolean cooperate(Node nowNode, Task task, FlowParams flowParams) {
        if (flowParams.isIgnore() || CooperateType.isOrSign(nowNode.getNodeRatio())) {
            return false;
        }

        String nodeRatio = nowNode.getNodeRatio();
        List<User> todoList = FlowEngine.userService().listByAssociatedAndTypes(task.getId()
            , UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
        AssertUtil.isEmpty(flowParams.getHandler(), ExceptionCons.SIGN_NULL_HANDLER);
        User todoUser = CollUtil.getOne(StreamUtils.filter(todoList
            , u -> Objects.equals(u.getProcessedBy(), flowParams.getHandler())));
        AssertUtil.isNull(todoUser, ExceptionCons.NOT_AUTHORITY);
        List<User> restList = StreamUtils.filter(todoList
            , u -> !Objects.equals(u.getProcessedBy(), flowParams.getHandler()));

        if (CooperateType.isCountersign(nodeRatio) && SkipType.isReject(flowParams.getSkipType())) {
            return removeRestList(restList);
        }

        List<HisTask> doneList = CollUtil.emptyDefault(FlowEngine.hisTaskService().listByTaskId(task.getId()));
        int allNum = todoList.size() + doneList.size();
        List<HisTask> donePassList = StreamUtils.filter(doneList
            , hisTask -> Objects.equals(hisTask.getSkipType(), SkipType.PASS.getKey()));
        List<HisTask> doneRejectList = StreamUtils.filter(doneList
            , hisTask -> Objects.equals(hisTask.getSkipType(), SkipType.REJECT.getKey()));
        boolean isPass = SkipType.isPass(flowParams.getSkipType());

        TaskCooperationRuleEvaluator.Context context = new TaskCooperationRuleEvaluator.Context(nodeRatio
            , flowParams.getSkipType(), isPass, allNum, todoList, donePassList, doneRejectList
            , flowParams.getVariable());
        if (ruleEvaluator.evaluate(context)) {
            return removeRestList(restList);
        }

        if (todoList.size() == 1) {
            return false;
        }
        HisTask hisTask = FlowEngine.hisTaskService().setSignHisTask(task, flowParams, nodeRatio, isPass);
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
