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
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.utils.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 办理人调整（转办、委派、加签、减签）操作链：装配 5 个有序步骤并委托 {@link FlowPipeline} 执行。
 *
 * <p><b>生命周期锁</b>：每次调整操作新建实例，字段为构造期捕获的操作参数（协作三元组）；
 * 非线程安全，禁止静态化或缓存复用。</p>
 *
 * <p>步骤体自原 {@code TaskServiceImpl.updateHandlersInternal} 逐行搬移，语句顺序与引用语义
 * 为行为契约，由特征测试锁定。</p>
 *
 * @author warm
 */
final class FlowUpdateHandlersChain {

    /**
     * 任务服务，复用既有任务持久化与校验能力。
     */
    private final TaskServiceImpl taskService;
    /**
     * 待办任务ID。
     */
    private final Long taskId;
    /**
     * 新增办理人。
     */
    private final List<String> addHandlers;
    /**
     * 移除办理人。
     */
    private final List<String> removeHandlers;
    /**
     * 协作类型。
     */
    private final String cooperateType;
    /**
     * 转办 / 委派显式移除时已校验的操作人活动记录。
     */
    private User selfRemovalUser;

    /**
     * @param taskService    任务服务
     * @param taskId         待办任务ID
     * @param addHandlers    新增办理人
     * @param removeHandlers 移除办理人
     * @param cooperateType  协作类型
     */
    FlowUpdateHandlersChain(TaskServiceImpl taskService, Long taskId, List<String> addHandlers
        , List<String> removeHandlers, String cooperateType) {
        this.taskService = taskService;
        this.taskId = taskId;
        this.addHandlers = addHandlers;
        this.removeHandlers = removeHandlers;
        this.cooperateType = cooperateType;
    }

    /**
     * 按序装配办理人调整链：协作守卫 → 合并与开始监听器 → 权限 → 办理人调整与历史 → 完成监听器。
     *
     * @return 流水线
     */
    FlowPipeline pipeline() {
        return FlowPipeline.of(
            this::cooperateGuards,
            this::prepareAndStart,
            this::authGate,
            this::applyHandlers,
            this::finishListener);
    }

    /**
     * 校验协作操作参数和当前办理人快照，阻止重复转办/委派/加签或移除最后一名办理人。
     *
     * @param execution 执行作用域
     * @return 空表示继续执行后续步骤
     */
    private Optional<Instance> cooperateGuards(FlowExecution execution) {
        // R5：办理人全集一次加载，守卫与权限门从同一份快照派生，不再按类型分次查询
        //（顶部无条件加载，守卫失败路径的查询数也保持不变）
        List<User> taskUsers = execution.usersOfTypes(UserType.APPROVAL.getKey()
            , UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
        // 组签仅支持槽位转办：加签、减签、委派都会破坏组归属与 seq_no 语义
        if (CooperateType.isGroup(execution.nowNode.getCooperateType())) {
            groupTransferGuards(execution, taskUsers);
            return Optional.empty();
        }
        // 引擎级协作守卫：操作人必填、协作对象必填且不可重复持有任务、减签不可移除最后一名办理人
        if (CooperateType.TRANSFER.getKey().equals(cooperateType)) {
            AssertUtil.isNull(execution.intent.getHandler(), ExceptionCons.HANDLER_NOT_EMPTY);
            AssertUtil.isEmpty(addHandlers, ExceptionCons.NULL_TRANSFER_HANDLER);
            AssertUtil.isNotEmpty(usersOfProcessedBy(taskUsers, addHandlers, UserType.TRANSFER.getKey())
                , ExceptionCons.IS_ALREADY_TRANSFER);
            sequenceTargetGuards(execution, taskUsers, ExceptionCons.IS_ALREADY_TRANSFER);
            // 顺序会签已由 sequenceTargetGuards 给出专属错误；其余普通节点只允许单目标，避免改变既有调用语义
            AssertUtil.isTrue(!CooperateType.isSequence(execution.nowNode.getCooperateType()
                , execution.nowNode.getNodeRatio()) && addHandlers.size() > 1
                , ExceptionCons.SINGLE_TRANSFER_HANDLER);
        } else if (CooperateType.DELEGATE.getKey().equals(cooperateType)) {
            AssertUtil.isNull(execution.intent.getHandler(), ExceptionCons.HANDLER_NOT_EMPTY);
            AssertUtil.isEmpty(addHandlers, ExceptionCons.NULL_DEPUTE_HANDLER);
            AssertUtil.isNotEmpty(usersOfProcessedBy(taskUsers, addHandlers, UserType.DEPUTE.getKey())
                , ExceptionCons.IS_ALREADY_DEPUTE);
            sequenceTargetGuards(execution, taskUsers, ExceptionCons.IS_ALREADY_DEPUTE);
        } else if (CooperateType.ADD_SIGNER.getKey().equals(cooperateType)) {
            AssertUtil.isNull(execution.intent.getHandler(), ExceptionCons.HANDLER_NOT_EMPTY);
            AssertUtil.isEmpty(addHandlers, ExceptionCons.NULL_ADD_SIGNATURE_HANDLER);
            AssertUtil.isNotEmpty(usersOfProcessedBy(taskUsers, addHandlers, UserType.APPROVAL.getKey())
                , ExceptionCons.IS_ALREADY_SIGN);
            sequenceTargetGuards(execution, taskUsers, ExceptionCons.IS_ALREADY_SIGN);
        } else if (CooperateType.REMOVE_SIGNER.getKey().equals(cooperateType)) {
            AssertUtil.isNull(execution.intent.getHandler(), ExceptionCons.HANDLER_NOT_EMPTY);
            AssertUtil.isEmpty(removeHandlers, ExceptionCons.NULL_REDUCTION_SIGNATURE_HANDLER);
            List<User> users = execution.usersOfTypes(UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey());
            AssertUtil.isTrue(CollUtil.isEmpty(users) || users.size() == 1
                , ExceptionCons.REDUCTION_SIGN_ONE_ERROR);
        }
        return Optional.empty();
    }

    /**
     * 组签槽位转办守卫：只允许转办，目标必须非空、不可重复且不得已在任务任何活动行中。
     * <p>顺序组签只有当前组可以转办；并行组签任意未完成组的成员均可转办自己命中的组。</p>
     *
     * @param execution 执行作用域
     * @param taskUsers 当前任务的办理人快照
     */
    private void groupTransferGuards(FlowExecution execution, List<User> taskUsers) {
        AssertUtil.isTrue(!CooperateType.TRANSFER.getKey().equals(cooperateType)
            , ExceptionCons.GROUP_SIGN_TRANSFER_ONLY);
        AssertUtil.isNull(execution.intent.getHandler(), ExceptionCons.HANDLER_NOT_EMPTY);
        AssertUtil.isEmpty(addHandlers, ExceptionCons.NULL_TRANSFER_HANDLER);
        List<String> targets = normalizedTransferTargets();
        AssertUtil.isTrue(targets.size() != addHandlers.size(), ExceptionCons.IS_ALREADY_TRANSFER);
        AssertUtil.isTrue(targets.stream().anyMatch(target -> taskUsers.stream()
            .anyMatch(user -> Objects.equals(target, user.getProcessedBy()))), ExceptionCons.IS_ALREADY_TRANSFER);
        if (execution.intent.isIgnore()) {
            return;
        }
        String handler = execution.intent.getHandler();
        List<User> matched = StreamUtils.filter(taskUsers
            , user -> Objects.equals(handler, user.getProcessedBy()));
        AssertUtil.isEmpty(matched, ExceptionCons.NOT_AUTHORITY);
        if (GroupSignUtil.isSequence(execution.nowNode)) {
            Integer current = SequenceSignUtil.minimumSeqNo(taskUsers);
            AssertUtil.isTrue(matched.stream().noneMatch(user -> Objects.equals(user.getSeqNo(), current))
                , ExceptionCons.GROUP_SIGN_NOT_CURRENT_GROUP);
        }
    }

    /**
     * 转办目标去空、去重，保持调用方顺序；组签多目标与普通单目标共用同一规范化结果。
     *
     * @return 规范化后的目标列表
     */
    private List<String> normalizedTransferTargets() {
        List<String> targets = new ArrayList<>();
        if (addHandlers != null) {
            for (String handler : addHandlers) {
                if (handler == null) {
                    continue;
                }
                String target = handler.trim();
                if (StringUtils.isNotEmpty(target) && !targets.contains(target)) {
                    targets.add(target);
                }
            }
        }
        return targets;
    }

    /**
     * 顺序会签的活动办理人保持唯一；转办 / 委派只允许一个目标，避免一个顺位拆成多个位置。
     */
    private void sequenceTargetGuards(FlowExecution execution, List<User> taskUsers, String duplicateMessage) {
        if (!CooperateType.isSequence(execution.nowNode.getCooperateType(), execution.nowNode.getNodeRatio())) {
            return;
        }
        boolean transferOrDelegate = CooperateType.TRANSFER.getKey().equals(cooperateType)
            || CooperateType.DELEGATE.getKey().equals(cooperateType);
        AssertUtil.isTrue(transferOrDelegate && addHandlers.size() != 1
            , ExceptionCons.SEQUENCE_COOPERATE_ONE_HANDLER);
        AssertUtil.isTrue(addHandlers.stream().distinct().count() != addHandlers.size(), duplicateMessage);
        List<User> activeUsers = StreamUtils.filter(taskUsers, user -> addHandlers.contains(user.getProcessedBy())
            && (UserType.APPROVAL.getKey().equals(user.getType())
                || UserType.TRANSFER.getKey().equals(user.getType())
                || UserType.DEPUTE.getKey().equals(user.getType())));
        AssertUtil.isNotEmpty(activeUsers, duplicateMessage);
    }

    /**
     * 合并流程变量并执行当前节点开始监听器。
     *
     * @param execution 执行作用域
     * @return 空表示继续执行后续步骤
     */
    private Optional<Instance> prepareAndStart(FlowExecution execution) {
        execution.mergeVariables();
        // 执行开始监听器
        ListenerUtil.executeStart(execution.contextListener(execution.task, execution.nowNode));
        return Optional.empty();
    }

    /**
     * 使用办理人快照派生权限人集合，校验当前处理人是否具备调整办理人的权限。
     *
     * @param execution 执行作用域
     * @return 空表示继续执行后续步骤
     */
    private Optional<Instance> authGate(FlowExecution execution) {
        // 组签转办的办理人身份已在组槽位守卫中精确校验，不再用 permissions() 交集判定
        if (CooperateType.isGroup(execution.nowNode.getCooperateType())) {
            return Optional.empty();
        }
        // 协作语义守卫（isIgnore 是引擎/宿主的管理员通道，保留整体绕过能力）：
        if (!execution.intent.isIgnore()) {
            String handler = execution.intent.getHandler();
            boolean transferOrDelegate = CooperateType.TRANSFER.getKey().equals(cooperateType)
                || CooperateType.DELEGATE.getKey().equals(cooperateType);
            if (transferOrDelegate && handler != null) {
                // 转办 / 委派若指定移除办理人，只能移除自己的记录，禁止移除他人（并行会签同样适用）；
                // 不指定移除（纯新增转办 / 委派记录）是既有合法用法，保持兼容。
                // handler 为空交给后续协作守卫报 HANDLER_NOT_EMPTY，保持原有错误语义
                AssertUtil.isTrue(CollUtil.isNotEmpty(removeHandlers)
                    && removeHandlers.stream().anyMatch(item -> !Objects.equals(item, handler))
                    , ExceptionCons.NOT_AUTHORITY);
                if (CollUtil.isNotEmpty(removeHandlers)) {
                    List<User> activeUsers = execution.usersOfTypes(UserType.APPROVAL.getKey()
                        , UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
                    selfRemovalUser = SequenceSignUtil.firstByHandler(activeUsers, handler);
                    AssertUtil.isNull(selfRemovalUser, ExceptionCons.NOT_AUTHORITY);
                }
            }
            if (CooperateType.isSequence(execution.nowNode.getCooperateType(), execution.nowNode.getNodeRatio())
                && !transferOrDelegate) {
                // 顺序会签：加签 / 减签只有当前位可操作；转办 / 委派自己不受位置限制
                //（替换同一顺序号，不破坏队列）
                List<User> sequenceUsers = execution.usersOfTypes(UserType.APPROVAL.getKey()
                    , UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
                AssertUtil.isTrue(!SequenceSignUtil.isCurrentPosition(sequenceUsers, handler)
                    , ExceptionCons.NOT_AUTHORITY);
            }
        }
        // 获取给谁的权限（原样内联：本操作从不 setUserList，复用 checkAuth 会因 task.userList
        // 为 null 静默放行，且权限常量不同——NOT_AUTHORITY vs NULL_ROLE_NODE）
        if (!execution.intent.isIgnorePermission() && !execution.intent.isIgnore()) {
            // 判断当前处理人是否有权限，获取当前办理人的权限
            List<String> permissions = execution.intent.getPermissions();
            // 获取任务权限人（R5：从办理人全集派生）
            List<String> taskPermissions = StreamUtils.toList(execution.usersOfTypes(UserType.APPROVAL.getKey()
                , UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey()), User::getProcessedBy);
            AssertUtil.isTrue(CollUtil.isNotEmpty(taskPermissions) && (CollUtil.isEmpty(permissions)
                || CollUtil.notContainsAny(permissions, taskPermissions)), ExceptionCons.NOT_AUTHORITY);
        }
        return Optional.empty();
    }

    /**
     * 删除或新增办理人关系，并生成对应协作历史任务。
     *
     * @param execution 执行作用域
     * @return 空表示继续执行后续步骤
     */
    private Optional<Instance> applyHandlers(FlowExecution execution) {
        if (CooperateType.isGroup(execution.nowNode.getCooperateType())) {
            return applyGroupTransfer(execution);
        }
        // 顺签转办 / 委派直接更新原顺序位，避免混合空序号按 id 兜底时替换记录掉到队尾。
        if (replaceSequenceHandler(execution)) {
            HisTask hisTask = FlowEngine.hisTaskService().setCooperateHis(execution.task, execution.intent
                , addHandlers, cooperateType);
            FlowEngine.hisTaskService().save(hisTask);
            return Optional.empty();
        }

        // 留存历史记录
        HisTask hisTask = null;
        // 删除对应的操作人
        if (CollUtil.isNotEmpty(removeHandlers)) {
            for (String reductionHandler : removeHandlers) {
                if (CooperateType.isSequence(execution.nowNode.getCooperateType(), execution.nowNode.getNodeRatio())) {
                    User removed = Objects.equals(reductionHandler, execution.intent.getHandler())
                        && selfRemovalUser != null ? selfRemovalUser : SequenceSignUtil.firstByHandler(
                        execution.usersOfTypes(UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey()
                            , UserType.DEPUTE.getKey()), reductionHandler);
                    if (removed != null) {
                        FlowEngine.userService().removeById(removed.getId());
                    }
                } else {
                    FlowEngine.userService().remove(FlowEngine.newUser().setAssociated(taskId)
                        .setProcessedBy(reductionHandler));
                }
            }
            hisTask = FlowEngine.hisTaskService().setCooperateHis(execution.task, execution.intent
                , removeHandlers, cooperateType);
        }

        // 新增权限人
        if (CollUtil.isNotEmpty(addHandlers)) {
            String type;
            List<User> taskUsers = execution.usersOfTypes(UserType.APPROVAL.getKey()
                , UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
            // 顺序会签：转办 / 委派继承当前位顺序号；加签追加到末尾。
            // 存量数据未回填 seq_no 时保持 null（按 id 兜底排序），避免新记录因数字顺序插队
            Integer nextSeqNo;
            if (CooperateType.TRANSFER.getKey().equals(cooperateType)) {
                type = UserType.TRANSFER.getKey();
                nextSeqNo = currentHandlerSeqNo(taskUsers, execution.intent.getHandler());
            } else if (CooperateType.DELEGATE.getKey().equals(cooperateType)) {
                type = UserType.DEPUTE.getKey();
                nextSeqNo = currentHandlerSeqNo(taskUsers, execution.intent.getHandler());
            } else {
                type = UserType.APPROVAL.getKey();
                nextSeqNo = nextAddSignerSeqNo(taskUsers);
            }
            String handlerType = type;
            Integer startSeqNo = nextSeqNo;
            List<User> addUsers = new ArrayList<>(addHandlers.size());
            for (int i = 0; i < addHandlers.size(); i++) {
                User addUser = FlowEngine.userService()
                    .structureUser(taskId, addHandlers.get(i), handlerType, execution.intent.getHandler());
                if (startSeqNo != null) {
                    addUser.setSeqNo(startSeqNo + i);
                }
                addUsers.add(addUser);
            }
            FlowEngine.userService().saveBatch(addUsers);
            hisTask = FlowEngine.hisTaskService().setCooperateHis(execution.task, execution.intent
                , addHandlers, cooperateType);
        }
        if (ObjectUtil.isNotNull(hisTask)) {
            FlowEngine.hisTaskService().save(hisTask);
        }
        return Optional.empty();
    }

    /**
     * 组签槽位转办：删除当前办理人命中的所有组槽位成员行，再为每个目标新增同序号的转办行。
     * <p>并行模式下同一办理人命中多个组时一并转出；顺序模式下只转当前组。
     * 组序号保持不变，目标用户即成为该组的候选成员（多人即组内或签）。</p>
     *
     * @param execution 执行作用域
     * @return 空表示继续执行后续步骤
     */
    private Optional<Instance> applyGroupTransfer(FlowExecution execution) {
        List<User> taskUsers = execution.usersOfTypes(UserType.APPROVAL.getKey()
            , UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
        String handler = execution.intent.getHandler();
        List<User> matched = StreamUtils.filter(taskUsers
            , user -> Objects.equals(handler, user.getProcessedBy()));
        Set<Integer> slots = new LinkedHashSet<>();
        if (GroupSignUtil.isSequence(execution.nowNode)) {
            slots.add(SequenceSignUtil.minimumSeqNo(taskUsers));
        } else {
            matched.stream().map(User::getSeqNo).filter(Objects::nonNull).forEach(slots::add);
        }
        AssertUtil.isEmpty(slots, ExceptionCons.NOT_AUTHORITY);

        List<User> completed = StreamUtils.filter(taskUsers, user -> slots.contains(user.getSeqNo()));
        FlowEngine.userService().removeByIds(StreamUtils.toList(completed, User::getId));

        List<String> targets = normalizedTransferTargets();
        List<User> addUsers = new ArrayList<>(slots.size() * targets.size());
        for (Integer slot : slots) {
            for (String target : targets) {
                addUsers.add(FlowEngine.userService()
                    .structureUser(taskId, target, UserType.TRANSFER.getKey(), handler).setSeqNo(slot));
            }
        }
        FlowEngine.userService().saveBatch(addUsers);

        HisTask hisTask = FlowEngine.hisTaskService().setCooperateHis(execution.task, execution.intent
            , targets, CooperateType.TRANSFER.getKey());
        FlowEngine.hisTaskService().save(hisTask);
        return Optional.empty();
    }

    /**
     * 顺签转办 / 委派替换原记录，保留原主键与顺序号。
     */
    private boolean replaceSequenceHandler(FlowExecution execution) {
        boolean transfer = CooperateType.TRANSFER.getKey().equals(cooperateType);
        boolean delegate = CooperateType.DELEGATE.getKey().equals(cooperateType);
        if (!CooperateType.isSequence(execution.nowNode.getCooperateType(), execution.nowNode.getNodeRatio())
            || (!transfer && !delegate) || CollUtil.isEmpty(removeHandlers) || removeHandlers.size() != 1
            || addHandlers.size() != 1) {
            return false;
        }
        User replaced = selfRemovalUser != null ? selfRemovalUser : SequenceSignUtil.firstByHandler(
            execution.usersOfTypes(UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey()
                , UserType.DEPUTE.getKey()), removeHandlers.get(0));
        if (replaced == null) {
            return false;
        }
        replaced.setProcessedBy(addHandlers.get(0))
            .setType(transfer ? UserType.TRANSFER.getKey() : UserType.DEPUTE.getKey())
            .setCreateBy(execution.intent.getHandler());
        FlowEngine.userService().updateById(replaced);
        return true;
    }

    /**
     * 取当前办理人（被转办 / 委派者）的顺序号。
     * <p>存量数据未回填时返回 {@code null}，新记录同样保持未编号，按 id 兜底排序。</p>
     *
     * @param taskUsers 当前任务的办理人快照
     * @param handler   当前办理人
     * @return 顺序号；无法确定时返回 {@code null}
     */
    private Integer currentHandlerSeqNo(List<User> taskUsers, String handler) {
        User current = SequenceSignUtil.firstByHandler(taskUsers, handler);
        return current == null ? null : current.getSeqNo();
    }

    /**
     * 计算加签记录的顺序号。
     * <p>存量数据存在未编号记录时返回 {@code null}，让新记录同样按 id 排到末尾，
     * 避免带数字的记录排到未编号记录之前而插队。</p>
     *
     * @param taskUsers 当前任务的办理人快照
     * @return 顺序号；无法确定时返回 {@code null}
     */
    private Integer nextAddSignerSeqNo(List<User> taskUsers) {
        int max = -1;
        for (User user : taskUsers) {
            if (user.getSeqNo() == null) {
                return null;
            }
            if (user.getSeqNo() > max) {
                max = user.getSeqNo();
            }
        }
        return max + 1;
    }

    /**
     * 执行当前节点完成监听器。
     *
     * @param execution 执行作用域
     * @return 空表示执行完成后由流水线返回当前实例
     */
    private Optional<Instance> finishListener(FlowExecution execution) {
        // 最后判断是否存在节点监听器，存在执行节点监听器（不带调用方上下文——既有语义）
        ListenerUtil.executeFinish(execution.rawListener(execution.task, execution.nowNode));
        return Optional.empty();
    }

    /**
     * 从办理人全集派生指定办理人与类型的视图（R5：内存过滤替代按办理人+类型查询）。
     *
     * @param users        办理人全集
     * @param processedBys 办理人标识集合
     * @param type         办理人类型
     * @return 命中办理人与类型的视图
     */
    private static List<User> usersOfProcessedBy(List<User> users, List<String> processedBys, String type) {
        return StreamUtils.filter(users, user -> processedBys.contains(user.getProcessedBy())
            && type.equals(user.getType()));
    }
}
