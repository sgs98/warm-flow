package org.dromara.warm.flow.core.workflow;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.handler.PermissionHandler;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.workflow.command.*;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.dromara.warm.flow.core.workflow.result.WorkflowResult;
import org.dromara.warm.flow.core.workflow.result.WorkflowTaskView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 流程统一门面默认实现。
 *
 * <p>当前阶段将场景化 Command 转换为内部执行参数，流程状态和路径逻辑仍由既有引擎组件维护。</p>
 *
 * @author may
 */
public class WorkflowServiceImpl implements WorkflowService {

    /**
     * 启动流程实例并创建首个待办，不办理首节点。
     *
     * @param command 启动参数
     * @return 流程操作结果
     */
    @Override
    public WorkflowResult start(StartCommand command) {
        check(command, "启动流程参数不能为空");
        AssertUtil.isEmpty(command.getBusinessId(), "业务ID不能为空");
        AssertUtil.isEmpty(command.getFlowCode(), "流程编码不能为空");
        WorkflowContext context = context(command);
        Instance instance = FlowEngine.insService().start(command.getBusinessId(), command.getFlowCode(), context);
        return result("start", instance, null);
    }

    /**
     * 完成当前待办并推动流程继续执行。
     *
     * @param command 完成参数
     * @return 流程操作结果
     */
    @Override
    public WorkflowResult complete(CompleteCommand command) {
        check(command, "完成任务参数不能为空");
        AssertUtil.isNull(command.getTaskId(), "任务ID不能为空");
        WorkflowContext context = context(command);
        Instance instance = FlowEngine.taskService().execute(command.getTaskId(), context, SkipType.PASS.getKey());
        return result("complete", instance, command.getTaskId());
    }

    /**
     * 将当前待办退回到合法前置节点。
     *
     * @param command 退回参数
     * @return 流程操作结果
     */
    @Override
    public WorkflowResult reject(RejectCommand command) {
        check(command, "退回任务参数不能为空");
        AssertUtil.isNull(command.getTaskId(), "任务ID不能为空");
        WorkflowContext context = context(command);
        Instance instance = FlowEngine.taskService().execute(command.getTaskId(), context, SkipType.REJECT.getKey());
        return result("reject", instance, command.getTaskId());
    }

    /**
     * 将当前待办跳转到指定节点。
     *
     * @param command 跳转参数
     * @return 流程操作结果
     */
    @Override
    public WorkflowResult jump(JumpCommand command) {
        check(command, "跳转任务参数不能为空");
        AssertUtil.isNull(command.getTaskId(), "任务ID不能为空");
        AssertUtil.isEmpty(command.getTargetNodeCode(), "目标节点编码不能为空");
        WorkflowContext context = context(command);
        Instance instance = FlowEngine.taskService().execute(command.getTaskId(), context, SkipType.PASS.getKey());
        return result("jump", instance, command.getTaskId());
    }

    /**
     * 撤回申请人发起的流程实例。
     *
     * @param command 撤回参数
     * @return 流程操作结果
     */
    @Override
    public WorkflowResult revoke(RevokeCommand command) {
        check(command, "撤回流程参数不能为空");
        AssertUtil.isNull(command.getInstanceId(), "流程实例ID不能为空");
        Instance instance = FlowEngine.taskService().revoke(command.getInstanceId(), context(command));
        return result("revoke", instance, null);
    }

    /**
     * 终止流程实例。
     *
     * @param command 终止参数
     * @return 流程操作结果
     */
    @Override
    public WorkflowResult terminate(TerminateCommand command) {
        check(command, "终止流程参数不能为空");
        AssertUtil.isTrue(command.getTaskId() == null && command.getInstanceId() == null
            , "流程实例ID和任务ID不能同时为空");
        WorkflowContext context = context(command);
        Instance instance = command.getTaskId() == null
            ? FlowEngine.taskService().terminateByInstanceId(command.getInstanceId(), context)
            : FlowEngine.taskService().terminateByTaskId(command.getTaskId(), context);
        return result("terminate", instance, command.getTaskId());
    }

    /**
     * 将当前待办转交给其他办理人。
     *
     * @param command 转办参数
     * @return 流程操作结果
     */
    @Override
    public WorkflowResult transfer(TransferCommand command) {
        check(command, "转办参数不能为空");
        AssertUtil.isNull(command.getTaskId(), "任务ID不能为空");
        AssertUtil.isEmpty(command.getTargetHandler(), "转办办理人不能为空");
        WorkflowContext context = context(command);
        FlowEngine.taskService().updateHandlers(command.getTaskId(), context
            , Collections.singletonList(command.getTargetHandler()), Collections.singletonList(context.getHandler())
            , CooperateType.TRANSFER.getKey());
        return result("transfer", findInstance(command.getTaskId()), command.getTaskId());
    }

    /**
     * 将当前待办委派给其他办理人。
     *
     * @param command 委派参数
     * @return 流程操作结果
     */
    @Override
    public WorkflowResult delegate(DelegateCommand command) {
        check(command, "委派参数不能为空");
        AssertUtil.isNull(command.getTaskId(), "任务ID不能为空");
        AssertUtil.isEmpty(command.getTargetHandler(), "委派办理人不能为空");
        WorkflowContext context = context(command);
        FlowEngine.taskService().updateHandlers(command.getTaskId(), context
            , Collections.singletonList(command.getTargetHandler()), Collections.singletonList(context.getHandler())
            , CooperateType.DEPUTE.getKey());
        return result("delegate", findInstance(command.getTaskId()), command.getTaskId());
    }

    /**
     * 为会签或票签节点增加办理人。
     *
     * @param command 加签参数
     * @return 流程操作结果
     */
    @Override
    public WorkflowResult addSigner(AddSignerCommand command) {
        check(command, "加签参数不能为空");
        AssertUtil.isNull(command.getTaskId(), "任务ID不能为空");
        AssertUtil.isTrue(CollUtil.isEmpty(command.getTargetHandlers()), "加签办理人不能为空");
        WorkflowContext context = context(command);
        FlowEngine.taskService().updateHandlers(command.getTaskId(), context, command.getTargetHandlers(), null
            , CooperateType.ADD_SIGNATURE.getKey());
        return result("addSigner", findInstance(command.getTaskId()), command.getTaskId());
    }

    /**
     * 为会签或票签节点移除办理人。
     *
     * @param command 减签参数
     * @return 流程操作结果
     */
    @Override
    public WorkflowResult removeSigner(RemoveSignerCommand command) {
        check(command, "减签参数不能为空");
        AssertUtil.isNull(command.getTaskId(), "任务ID不能为空");
        AssertUtil.isTrue(CollUtil.isEmpty(command.getTargetHandlers()), "减签办理人不能为空");
        WorkflowContext context = context(command);
        FlowEngine.taskService().updateHandlers(command.getTaskId(), context, null, command.getTargetHandlers()
            , CooperateType.REDUCTION_SIGNATURE.getKey());
        return result("removeSigner", findInstance(command.getTaskId()), command.getTaskId());
    }

    /**
     * 将公共 Command 转换为内部执行上下文。
     *
     * @param command 流程操作参数
     * @return 内部执行上下文
     */
    private WorkflowContext context(WorkflowCommand command) {
        WorkflowContext context = new WorkflowContext();
        context.setExt(command.getExt());
        PermissionHandler permissionHandler = FlowEngine.permissionHandler();
        if (permissionHandler != null) {
            context.setHandler(permissionHandler.getHandler());
            context.setPermissions(permissionHandler.permissions());
        } else {
            OperatorContext operator = command.getOperator();
            if (operator != null) {
                context.setHandler(operator.getHandler());
                context.setPermissions(operator.getPermissions());
                context.setIgnorePermission(operator.isIgnorePermission());
            }
        }
        if (command instanceof CompleteCommand) {
            CompleteCommand action = (CompleteCommand) command;
            context.setMessage(action.getMessage());
            context.setVariables(action.getVariables());
            context.setInstanceStatus(action.getInstanceStatus());
            context.setHistoryTaskStatus(action.getHistoryTaskStatus());
            context.setNextHandlers(action.getNextHandlers());
            context.setNextHandlerAppend(action.isNextHandlerAppend());
        } else if (command instanceof RejectCommand) {
            RejectCommand action = (RejectCommand) command;
            context.setMessage(action.getMessage());
            context.setVariables(action.getVariables());
            context.setInstanceStatus(action.getInstanceStatus());
            context.setHistoryTaskStatus(action.getHistoryTaskStatus());
            context.setTargetNodeCode(action.getTargetNodeCode());
            context.setNextHandlers(action.getNextHandlers());
            context.setNextHandlerAppend(action.isNextHandlerAppend());
        } else if (command instanceof JumpCommand) {
            JumpCommand action = (JumpCommand) command;
            context.setMessage(action.getMessage());
            context.setVariables(action.getVariables());
            context.setInstanceStatus(action.getInstanceStatus());
            context.setHistoryTaskStatus(action.getHistoryTaskStatus());
            context.setTargetNodeCode(action.getTargetNodeCode());
            context.setNextHandlers(action.getNextHandlers());
            context.setNextHandlerAppend(action.isNextHandlerAppend());
        } else if (command instanceof RevokeCommand) {
            RevokeCommand action = (RevokeCommand) command;
            context.setMessage(action.getMessage());
            context.setVariables(action.getVariables());
            context.setInstanceStatus(action.getInstanceStatus());
            context.setHistoryTaskStatus(action.getHistoryTaskStatus());
        } else if (command instanceof TerminateCommand) {
            TerminateCommand action = (TerminateCommand) command;
            context.setMessage(action.getMessage());
            context.setInstanceStatus(action.getInstanceStatus());
            context.setHistoryTaskStatus(action.getHistoryTaskStatus());
        } else if (command instanceof TransferCommand) {
            TransferCommand action = (TransferCommand) command;
            context.setMessage(action.getMessage());
            context.setHistoryTaskStatus(action.getHistoryTaskStatus());
        } else if (command instanceof DelegateCommand) {
            DelegateCommand action = (DelegateCommand) command;
            context.setMessage(action.getMessage());
            context.setHistoryTaskStatus(action.getHistoryTaskStatus());
        } else if (command instanceof AddSignerCommand) {
            AddSignerCommand action = (AddSignerCommand) command;
            context.setMessage(action.getMessage());
            context.setHistoryTaskStatus(action.getHistoryTaskStatus());
        } else if (command instanceof RemoveSignerCommand) {
            RemoveSignerCommand action = (RemoveSignerCommand) command;
            context.setMessage(action.getMessage());
            context.setHistoryTaskStatus(action.getHistoryTaskStatus());
        } else if (command instanceof StartCommand) {
            StartCommand action = (StartCommand) command;
            context.setVariables(action.getVariables());
            context.setInstanceStatus(action.getInstanceStatus());
            context.setHistoryTaskStatus(action.getHistoryTaskStatus());
        }
        return context;
    }

    private Instance findInstance(Long taskId) {
        Task task = FlowEngine.taskService().getById(taskId);
        return task == null ? null : FlowEngine.insService().getById(task.getInstanceId());
    }

    private WorkflowResult result(String operation, Instance instance, Long taskId) {
        WorkflowResult result = new WorkflowResult();
        result.setSuccess(instance != null);
        result.setOperation(operation);
        result.setCompletedTaskId(taskId);
        if (instance != null) {
            result.setInstanceId(instance.getId());
            result.setBusinessId(instance.getBusinessId());
            result.setInstanceStatus(instance.getFlowStatus());
            result.setCurrentTasks(currentTasks(instance.getId()));
        } else {
            result.setCurrentTasks(Collections.emptyList());
        }
        return result;
    }

    /**
     * 查询流程实例当前待办并转换为稳定的返回视图。
     *
     * @param instanceId 流程实例主键
     * @return 当前待办视图集合
     */
    private List<WorkflowTaskView> currentTasks(Long instanceId) {
        List<Task> tasks = FlowEngine.taskService().getByInsId(instanceId);
        if (CollUtil.isEmpty(tasks)) {
            return Collections.emptyList();
        }
        List<WorkflowTaskView> views = new ArrayList<>();
        for (Task task : tasks) {
            WorkflowTaskView view = new WorkflowTaskView();
            view.setTaskId(task.getId());
            view.setInstanceId(task.getInstanceId());
            view.setNodeCode(task.getNodeCode());
            view.setNodeName(task.getNodeName());
            view.setNodeType(task.getNodeType());
            view.setTaskStatus(task.getFlowStatus());
            view.setHandlers(FlowEngine.userService().getPermission(task.getId(), UserType.APPROVAL.getKey()
                , UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey()));
            views.add(view);
        }
        return views;
    }

    private void check(WorkflowCommand command, String message) {
        if (command == null) {
            throw new IllegalArgumentException(message);
        }
    }
}
