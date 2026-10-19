package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.constant.FlowCons;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.service.copy.CopyConfig;
import org.dromara.warm.flow.core.spi.PermissionHandler;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 抄送执行器。
 *
 * <p>两个入口：{@link #copyOnArrival} 在待办落库（流程到达节点）后按节点配置自动抄送；
 * {@link #copy} 供调用方在办理过程中追加抄送。两者都只写留痕（{@code flow_his_task} +
 * {@code flow_user}），不产生待办、不改实例状态、也不触发监听器。</p>
 *
 * @author may
 */
final class CopyAdvancer {

    private static final Logger log = LoggerFactory.getLogger(CopyAdvancer.class);

    private CopyAdvancer() {
    }

    /**
     * 流程到达节点后，按 {@code node_config.copy} 自动抄送。
     *
     * <p>实例由调用方直接传入而不回查：发起链的抄送发生在实例落库之前，此时按主键查不到，
     * 发起人占位也就换不成真实用户。</p>
     *
     * @param tasks    刚创建的待办任务
     * @param instance 当前流程实例，可为 {@code null}（为 {@code null} 时才按任务回查）
     * @param context  流程执行上下文
     */
    static void copyOnArrival(List<Task> tasks, Instance instance, WorkflowContext context) {
        if (CollUtil.isEmpty(tasks) || context == null) {
            return;
        }
        for (Task task : tasks) {
            Node node = FlowEngine.nodeService().getByDefIdAndNodeCode(task.getDefinitionId(), task.getNodeCode());
            if (node == null) {
                continue;
            }
            CopyConfig config = CopyConfig.parse(node.getNodeConfig());
            if (config.enabled()) {
                copy(task, config.getHandlers(), instance, context, false);
            }
        }
    }

    /**
     * 对指定待办执行抄送（调用方只有任务主键时使用，实例按主键回查）。
     *
     * @param task     待办任务
     * @param handlers 抄送人标识
     * @param context  流程执行上下文
     * @return 抄送历史；没有有效抄送人时返回 {@code null}
     */
    static HisTask copy(Task task, List<String> handlers, WorkflowContext context) {
        return copy(task, handlers, null, context, true);
    }

    /**
     * 对指定待办执行抄送。
     *
     * <p>同一条待办对同一个人只留一条抄送记录：已经抄送过的人会被过滤掉，全部过滤完则不再写留痕。
     * 这样「办理时顺手抄送 + 节点上还配了同一个抄送人」「调用方重试」都不会产生重复记录。</p>
     *
     * @param task     待办任务（办结后也可由归档历史还原）
     * @param handlers 抄送人标识
     * @param instance 当前流程实例，可为 {@code null}
     * @param context  流程执行上下文
     * @param required 调用方是否明确要求抄送：{@code true} 时抄送人一个都落不到人抛异常，
     *                 自动抄送传 {@code false}，静默跳过
     * @return 抄送历史；没有新抄送人时返回 {@code null}
     */
    private static HisTask copy(Task task, List<String> handlers, Instance instance, WorkflowContext context
        , boolean required) {
        List<String> users = resolveHandlers(task, instance, handlers);
        if (users.isEmpty()) {
            if (required) {
                throw new FlowException(ExceptionCons.UNRESOLVED_COPY_HANDLER);
            }
            return null;
        }
        Set<String> notified = notifiedUsers(task.getId());
        List<String> pending = users.stream()
            .filter(user -> !notified.contains(user))
            .collect(Collectors.toList());
        if (pending.isEmpty()) {
            log.debug("抄送人已抄送过，跳过重复留痕：taskId={}, users={}", task.getId(), users);
            return null;
        }
        HisTask hisTask = FlowEngine.hisTaskService().setCopyHisTask(task, context, pending);
        FlowEngine.hisTaskService().save(hisTask);
        // 抄送人挂在抄送历史主键上，而不是待办主键：待办办理完 / 撤回 / 终止时会被连同办理人整批删除，
        // 抄送记录却要跟着流程留档；按待办 id 关联会让抄送在办结的瞬间从「我的抄送」里消失。
        FlowEngine.userService().saveBatch(StreamUtils.toList(pending, user -> FlowEngine.userService()
            .structureUser(hisTask.getId(), user, UserType.COPY.getKey(), context.getHandler())));
        return hisTask;
    }

    /**
     * 查询这条待办已经抄送过的人。
     *
     * <p>抄送人的落库写在 {@code flow_user} 上（按抄送历史主键关联），这里直接读抄送历史行上的
     * {@code collaborator} 快照，省掉一次用户表查询——抄送历史一行的 {@code collaborator} 就是
     * 那一次抄送的完整收件人列表。</p>
     *
     * @param taskId 待办任务主键
     * @return 已抄送过的办理人标识
     */
    private static Set<String> notifiedUsers(Long taskId) {
        if (taskId == null) {
            return Collections.emptySet();
        }
        List<HisTask> hisTasks = FlowEngine.hisTaskService().list(FlowEngine.newHisTask().setTaskId(taskId));
        Set<String> users = new HashSet<>();
        for (HisTask hisTask : hisTasks) {
            if (!TaskStatus.COPY.getKey().equals(hisTask.getFlowStatus())
                || StringUtils.isEmpty(hisTask.getCollaborator())) {
                continue;
            }
            for (String user : hisTask.getCollaborator().split(",")) {
                if (StringUtils.isNotEmpty(user.trim())) {
                    users.add(user.trim());
                }
            }
        }
        return users;
    }

    /**
     * 过滤空标识、替换发起人占位、交宿主展开成具体用户并去重。
     *
     * <p>展开走 {@link PermissionHandler#resolveCopyHandlers}：设计器里配的是带类型前缀的入库标识
     * （如 {@code user:zhangsan}、{@code dept:001}），抄送必须落到具体人，所以这一步的语义是
     * 「把标识换成人」而不是「把标识换个写法」。宿主展开不到的标识会被丢弃——留着也只会落一条
     * 谁都不认识的留痕。</p>
     *
     * @param task     待办任务，用于在实例为空时回查所属实例
     * @param instance 当前流程实例，可为 {@code null}
     * @param handlers 抄送人标识
     * @return 实际抄送的办理人标识
     */
    private static List<String> resolveHandlers(Task task, Instance instance, List<String> handlers) {
        if (CollUtil.isEmpty(handlers)) {
            return List.of();
        }
        Instance target = instance == null ? FlowEngine.insService().getById(task.getInstanceId()) : instance;
        String initiator = target == null ? null : target.getCreateBy();
        List<String> users = handlers.stream()
            // 发起人占位在实例上没有发起人（历史数据 / 反查不到）时直接丢掉，
            // 不能把这个占位符当成人名落库
            .map(handler -> FlowCons.WARMFLOWINITIATOR.equals(handler) ? initiator : handler)
            .filter(StringUtils::isNotEmpty)
            .distinct()
            .collect(Collectors.toList());
        PermissionHandler permissionHandler = FlowEngine.permissionHandler();
        if (permissionHandler != null && CollUtil.isNotEmpty(users)) {
            try {
                users = permissionHandler.resolveCopyHandlers(users);
            } catch (Exception e) {
                // 抄送是旁路动作：宿主解析抛错不能拖垮流程流转，本次不抄送并留下告警
                log.warn("抄送人解析失败，本次抄送跳过：{}", e.getMessage());
                return List.of();
            }
        }
        if (CollUtil.isEmpty(users)) {
            return List.of();
        }
        return users.stream()
            .filter(StringUtils::isNotEmpty)
            .distinct()
            .collect(Collectors.toList());
    }
}
