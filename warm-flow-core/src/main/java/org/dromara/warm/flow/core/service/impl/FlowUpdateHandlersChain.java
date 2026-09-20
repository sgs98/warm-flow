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
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.ListenerUtil;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;

import java.util.List;
import java.util.Optional;

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
    private final Integer cooperateType;

    /**
     * @param taskService    任务服务
     * @param taskId         待办任务ID
     * @param addHandlers    新增办理人
     * @param removeHandlers 移除办理人
     * @param cooperateType  协作类型
     */
    FlowUpdateHandlersChain(TaskServiceImpl taskService, Long taskId, List<String> addHandlers
        , List<String> removeHandlers, Integer cooperateType) {
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
        List<User> taskUsers = execution.loadTaskUsers();
        // 引擎级协作守卫：操作人必填、协作对象必填且不可重复持有任务、减签不可移除最后一名办理人
        if (CooperateType.TRANSFER.getKey().equals(cooperateType)) {
            AssertUtil.isNull(execution.intent.getHandler(), ExceptionCons.HANDLER_NOT_EMPTY);
            AssertUtil.isEmpty(addHandlers, ExceptionCons.NULL_TRANSFER_HANDLER);
            AssertUtil.isNotEmpty(usersOfProcessedBy(taskUsers, addHandlers, UserType.TRANSFER.getKey())
                , ExceptionCons.IS_ALREADY_TRANSFER);
        } else if (CooperateType.DEPUTE.getKey().equals(cooperateType)) {
            AssertUtil.isNull(execution.intent.getHandler(), ExceptionCons.HANDLER_NOT_EMPTY);
            AssertUtil.isEmpty(addHandlers, ExceptionCons.NULL_DEPUTE_HANDLER);
            AssertUtil.isNotEmpty(usersOfProcessedBy(taskUsers, addHandlers, UserType.DEPUTE.getKey())
                , ExceptionCons.IS_ALREADY_DEPUTE);
        } else if (CooperateType.ADD_SIGNATURE.getKey().equals(cooperateType)) {
            AssertUtil.isNull(execution.intent.getHandler(), ExceptionCons.HANDLER_NOT_EMPTY);
            AssertUtil.isEmpty(addHandlers, ExceptionCons.NULL_ADD_SIGNATURE_HANDLER);
            AssertUtil.isNotEmpty(usersOfProcessedBy(taskUsers, addHandlers, UserType.APPROVAL.getKey())
                , ExceptionCons.IS_ALREADY_SIGN);
        } else if (CooperateType.REDUCTION_SIGNATURE.getKey().equals(cooperateType)) {
            AssertUtil.isNull(execution.intent.getHandler(), ExceptionCons.HANDLER_NOT_EMPTY);
            AssertUtil.isEmpty(removeHandlers, ExceptionCons.NULL_REDUCTION_SIGNATURE_HANDLER);
            List<User> users = execution.usersOfTypes(UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey());
            AssertUtil.isTrue(CollUtil.isEmpty(users) || users.size() == 1
                , ExceptionCons.REDUCTION_SIGN_ONE_ERROR);
        }
        return Optional.empty();
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
        // 留存历史记录
        HisTask hisTask = null;
        // 删除对应的操作人
        if (CollUtil.isNotEmpty(removeHandlers)) {
            for (String reductionHandler : removeHandlers) {
                FlowEngine.userService().remove(FlowEngine.newUser().setAssociated(taskId)
                    .setProcessedBy(reductionHandler));
            }
            hisTask = FlowEngine.hisTaskService().setCooperateHis(execution.task, execution.intent
                , removeHandlers, cooperateType);
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
                    , type, execution.intent.getHandler())));
            hisTask = FlowEngine.hisTaskService().setCooperateHis(execution.task, execution.intent
                , addHandlers, cooperateType);
        }
        if (ObjectUtil.isNotNull(hisTask)) {
            FlowEngine.hisTaskService().save(hisTask);
        }
        return Optional.empty();
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
