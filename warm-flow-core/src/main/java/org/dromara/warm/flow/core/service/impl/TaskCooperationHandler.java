package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.utils.*;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
    boolean handleDepute(FlowExecution execution, Task task, WorkflowContext context, String skipType) {
        List<User> entrustedUserList = StreamUtils.filter(task.getUserList(),
            user -> UserType.DEPUTE.getKey().equals(user.getType())
                && Objects.equals(context.getHandler(), user.getProcessedBy()));
        if (CollUtil.isEmpty(entrustedUserList)) {
            return false;
        }

        boolean sequenceSign = CooperateType.isSequence(execution.nowNode.getCooperateType()
            , execution.nowNode.getNodeRatio());
        User entrustedUser;
        if (sequenceSign) {
            List<User> sequenceUsers = StreamUtils.filter(task.getUserList(), user ->
                UserType.APPROVAL.getKey().equals(user.getType())
                    || UserType.TRANSFER.getKey().equals(user.getType())
                    || UserType.DEPUTE.getKey().equals(user.getType()));
            entrustedUser = SequenceSignUtil.firstBySeqNo(sequenceUsers);
            if (entrustedUser != null
                && Objects.equals(context.getHandler(), entrustedUser.getProcessedBy())
                && !UserType.DEPUTE.getKey().equals(entrustedUser.getType())) {
                return false;
            }
            AssertUtil.isTrue(entrustedUser == null
                    || !UserType.DEPUTE.getKey().equals(entrustedUser.getType())
                    || !Objects.equals(context.getHandler(), entrustedUser.getProcessedBy())
                , ExceptionCons.NOT_AUTHORITY);
        } else {
            entrustedUser = entrustedUserList.get(0);
        }

        HisTask hisTask = FlowEngine.hisTaskService().setDeputeHisTask(task, context, entrustedUser, skipType);
        FlowEngine.hisTaskService().save(hisTask);
        if (sequenceSign) {
            restoreSequenceDepute(task, entrustedUser);
            return true;
        }

        FlowEngine.userService().removeById(entrustedUser.getId());
        User deputeUser = FlowEngine.userService().getOne(FlowEngine.newUser().setAssociated(task.getId())
            .setProcessedBy(entrustedUser.getCreateBy()).setType(UserType.APPROVAL.getKey()));
        if (ObjectUtil.isNull(deputeUser)) {
            User newUser = FlowEngine.userService().structureUser(entrustedUser.getAssociated()
                , entrustedUser.getCreateBy(), UserType.APPROVAL.getKey(), entrustedUser.getProcessedBy());
            newUser.setSeqNo(entrustedUser.getSeqNo());
            FlowEngine.userService().save(newUser);
        }
        return true;
    }

    /**
     * 恢复顺签委派占用的具体顺序位；原审批记录仍在同一顺位时仅移除委派记录。
     */
    private void restoreSequenceDepute(Task task, User entrustedUser) {
        List<User> samePosition = StreamUtils.filter(task.getUserList(), user ->
            UserType.APPROVAL.getKey().equals(user.getType())
                && Objects.equals(entrustedUser.getCreateBy(), user.getProcessedBy())
                && entrustedUser.getSeqNo() != null
                && Objects.equals(entrustedUser.getSeqNo(), user.getSeqNo()));
        if (CollUtil.isNotEmpty(samePosition)) {
            FlowEngine.userService().removeById(entrustedUser.getId());
            return;
        }
        String deputee = entrustedUser.getProcessedBy();
        entrustedUser.setProcessedBy(entrustedUser.getCreateBy())
            .setType(UserType.APPROVAL.getKey())
            .setCreateBy(deputee);
        FlowEngine.userService().updateById(entrustedUser);
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
        if (CooperateType.isGroup(execution.nowNode.getCooperateType())) {
            return cooperateGroup(execution, task, context, skipType);
        }
        String rawNodeRatio = execution.nowNode.getNodeRatio();
        String nodeRatio = CooperateType.removeSequence(rawNodeRatio);
        boolean sequenceSign = CooperateType.isSequence(execution.nowNode.getCooperateType(), rawNodeRatio);
        if (!sequenceSign && CooperateType.isOrSign(nodeRatio)) {
            return false;
        }

        // 从操作内办理人全集派生待办视图，与权限校验共用同一份快照。
        List<User> todoList = execution.usersOfTypes(UserType.APPROVAL.getKey()
            , UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
        AssertUtil.isEmpty(context.getHandler(), ExceptionCons.SIGN_NULL_HANDLER);
        User todoUser;
        if (sequenceSign) {
            // 顺序模式按具体顺序位定位记录，兼容历史数据中同一办理人占有多个顺位的情况
            todoUser = SequenceSignUtil.firstBySeqNo(todoList);
            AssertUtil.isTrue(todoUser == null
                || !Objects.equals(todoUser.getProcessedBy(), context.getHandler()), ExceptionCons.NOT_AUTHORITY);
        } else {
            todoUser = CollUtil.getOne(StreamUtils.filter(todoList
                , u -> Objects.equals(u.getProcessedBy(), context.getHandler())));
            AssertUtil.isNull(todoUser, ExceptionCons.NOT_AUTHORITY);
        }
        List<User> restList = sequenceSign
            ? StreamUtils.filter(todoList, u -> !Objects.equals(u.getId(), todoUser.getId()))
            : StreamUtils.filter(todoList, u -> !Objects.equals(u.getProcessedBy(), context.getHandler()));
        if (CooperateType.isOrSign(nodeRatio)) {
            return removeRestList(restList);
        }

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
     * 组签协作处理：组内任一成员通过即完成该组，全部组完成后节点才流转。
     *
     * <p>通过时仅在仍有其它组时移除当前办理人命中的组槽位并写一条「审批通过」历史后短路；
     * 最后一组完成或驳回会继续解析后继，办理人随当前任务归档统一删除，避免后继预检失败时
     * 留下没有办理人的活动任务。</p>
     *
     * @param execution 执行作用域
     * @param task      当前待办任务
     * @param context   流程执行上下文
     * @param skipType  流转类型
     * @return 是否仅记录当前组结果；为true时本次流程不继续流转
     */
    private boolean cooperateGroup(FlowExecution execution, Task task, WorkflowContext context, String skipType) {
        List<User> todoList = execution.usersOfTypes(UserType.APPROVAL.getKey()
            , UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
        AssertUtil.isEmpty(context.getHandler(), ExceptionCons.SIGN_NULL_HANDLER);
        List<User> matched = StreamUtils.filter(todoList
            , user -> Objects.equals(user.getProcessedBy(), context.getHandler()));
        AssertUtil.isEmpty(matched, ExceptionCons.NOT_AUTHORITY);

        boolean sequence = GroupSignUtil.isSequence(execution.nowNode);
        Set<Integer> slots = new LinkedHashSet<>();
        if (sequence) {
            Integer current = SequenceSignUtil.minimumSeqNo(todoList);
            boolean currentMatched = matched.stream()
                .anyMatch(user -> Objects.equals(user.getSeqNo(), current));
            AssertUtil.isFalse(currentMatched, ExceptionCons.NOT_AUTHORITY);
            slots.add(current);
        } else {
            matched.stream().map(User::getSeqNo).filter(Objects::nonNull).forEach(slots::add);
        }
        AssertUtil.isEmpty(slots, ExceptionCons.NOT_AUTHORITY);

        if (SkipType.isReject(skipType)) {
            // 驳回会继续解析后继，当前办理人随任务归档统一删除，避免后继预检失败后留下无办理人任务
            return false;
        }

        List<User> completed = StreamUtils.filter(todoList, user -> slots.contains(user.getSeqNo()));
        List<User> rest = StreamUtils.filter(todoList, user -> !slots.contains(user.getSeqNo()));
        if (CollUtil.isEmpty(rest)) {
            // 最后一组会继续解析后继，当前办理人随任务归档统一删除
            return false;
        }
        FlowEngine.userService().removeByIds(StreamUtils.toList(completed, User::getId));
        HisTask hisTask = FlowEngine.hisTaskService().setSignHisTask(task, context
            , execution.nowNode.getNodeRatio(), true);
        FlowEngine.hisTaskService().save(hisTask);
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
