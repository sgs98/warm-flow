package org.dromara.warm.flow.core.workflow;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.constant.FlowCons;
import org.dromara.warm.flow.core.spi.PermissionHandler;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.workflow.command.*;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

/**
 * 将场景化流程命令转换为引擎执行上下文。
 *
 * @author may
 */
final class WorkflowContextMapper {

    private WorkflowContextMapper() {
    }

    static WorkflowContext toContext(StartCommand command) {
        WorkflowContext context = baseContext(command);
        context.setVariables(command.getVariables());
        context.setFlowStatus(command.getFlowStatus());
        context.setTaskStatus(command.getTaskStatus());
        context.setNextHandlers(command.getNextHandlers());
        context.setNextHandlerAppend(command.isNextHandlerAppend());
        return context;
    }

    static WorkflowContext toContext(CompleteCommand command) {
        WorkflowContext context = baseContext(command);
        context.setMessage(command.getMessage());
        context.setVariables(command.getVariables());
        context.setFlowStatus(command.getFlowStatus());
        context.setTaskStatus(command.getTaskStatus());
        context.setNextHandlers(command.getNextHandlers());
        context.setNextHandlerAppend(command.isNextHandlerAppend());
        return context;
    }

    static WorkflowContext toContext(JumpCommand command) {
        WorkflowContext context = baseContext(command);
        context.setTargetNodeCode(command.getTargetNodeCode());
        context.setMessage(command.getMessage());
        context.setVariables(command.getVariables());
        context.setFlowStatus(command.getFlowStatus());
        context.setTaskStatus(command.getTaskStatus());
        context.setNextHandlers(command.getNextHandlers());
        context.setNextHandlerAppend(command.isNextHandlerAppend());
        return context;
    }

    static WorkflowContext toContext(RejectCommand command) {
        WorkflowContext context = baseContext(command);
        context.setTargetNodeCode(command.getTargetNodeCode());
        context.setMessage(command.getMessage());
        context.setVariables(command.getVariables());
        context.setFlowStatus(command.getFlowStatus());
        context.setTaskStatus(command.getTaskStatus());
        context.setNextHandlers(command.getNextHandlers());
        context.setNextHandlerAppend(command.isNextHandlerAppend());
        return context;
    }

    static WorkflowContext toContext(RevokeCommand command) {
        WorkflowContext context = baseContext(command);
        context.setMessage(command.getMessage());
        context.setVariables(command.getVariables());
        context.setFlowStatus(command.getFlowStatus());
        context.setTaskStatus(command.getTaskStatus());
        return context;
    }

    static WorkflowContext toContext(TerminateCommand command) {
        WorkflowContext context = baseContext(command);
        context.setMessage(command.getMessage());
        context.setFlowStatus(command.getFlowStatus());
        context.setTaskStatus(command.getTaskStatus());
        return context;
    }

    static WorkflowContext toContext(ResetCommand command) {
        WorkflowContext context = baseContext(command);
        context.setFlowStatus(command.getFlowStatus());
        return context;
    }

    static WorkflowContext toContext(TransferCommand command) {
        WorkflowContext context = baseContext(command);
        context.setMessage(command.getMessage());
        context.setTaskStatus(command.getTaskStatus());
        return context;
    }

    static WorkflowContext toContext(DelegateCommand command) {
        WorkflowContext context = baseContext(command);
        context.setMessage(command.getMessage());
        context.setTaskStatus(command.getTaskStatus());
        return context;
    }

    static WorkflowContext toContext(AddSignerCommand command) {
        WorkflowContext context = baseContext(command);
        context.setMessage(command.getMessage());
        context.setTaskStatus(command.getTaskStatus());
        return context;
    }

    static WorkflowContext toContext(RemoveSignerCommand command) {
        WorkflowContext context = baseContext(command);
        context.setMessage(command.getMessage());
        context.setTaskStatus(command.getTaskStatus());
        return context;
    }

    static WorkflowContext toContext(CopyCommand command) {
        WorkflowContext context = baseContext(command);
        context.setMessage(command.getMessage());
        return context;
    }

    private static WorkflowContext baseContext(WorkflowCommand command) {
        WorkflowContext context = new WorkflowContext();
        context.setExt(command.getExt());
        OperatorContext operator = command.getOperator();
        if (operator != null) {
            if (StringUtils.isNotEmpty(operator.getHandler())) {
                context.setHandler(operator.getHandler());
            }
            context.setIgnorePermission(operator.isIgnorePermission());
            context.setIgnore(operator.isIgnore());
        }
        // ignore 表示系统内部调用（定时任务 / 自动节点），整段跳过：无登录态时访问
        // 宿主 PermissionHandler 会直接把鉴权异常带进流程操作
        if (!context.isIgnore()) {
            PermissionHandler permissionHandler = FlowEngine.permissionHandler();
            if (permissionHandler != null) {
                // handler 是操作人身份（写 createBy / approver），不是权限：没显式传时仍要补，
                // 否则代办理场景的历史留痕会缺办理人，调整办理人 / 加签也会报 HANDLER_NOT_EMPTY
                if (StringUtils.isEmpty(context.getHandler())) {
                    context.setHandler(resolveHandler(context, permissionHandler));
                }
                // 权限集合只在校验时使用，校验本身在 ignorePermission 时已提前返回，
                // 这里再拉一次既无用、又会在无登录态场景抛异常
                if (!context.isIgnorePermission()) {
                    context.setPermissions(permissionHandler.permissions());
                }
            }
        }
        // 系统调用（ignore）不接触 PermissionHandler，跳过权限时宿主也可能取不到登录人：
        // 这两种情况下仍必须有操作人身份，否则历史 approver / 实例 createBy 会为空
        if (StringUtils.isEmpty(context.getHandler())
            && (context.isIgnore() || context.isIgnorePermission())) {
            context.setHandler(systemHandler());
        }
        return context;
    }

    /**
     * 系统兜底操作人：取配置 {@code warm-flow.system-handler}，未配置时用 {@code system}。
     *
     * @return 系统操作人标识
     */
    private static String systemHandler() {
        WarmFlow config = FlowEngine.getFlowConfig();
        return config != null && StringUtils.isNotEmpty(config.getSystemHandler())
            ? config.getSystemHandler() : FlowCons.DEFAULT_SYSTEM_HANDLER;
    }

    /**
     * 解析操作人身份：优先取宿主登录用户；显式跳过权限时，宿主取不到（甚至抛出未登录异常）
     * 则回落到系统操作人，避免补偿 / 系统调用把鉴权异常带进流程操作。
     *
     * @param context           流程上下文
     * @param permissionHandler 宿主权限处理器
     * @return 操作人标识
     */
    private static String resolveHandler(WorkflowContext context, PermissionHandler permissionHandler) {
        try {
            String handler = permissionHandler.getHandler();
            if (StringUtils.isNotEmpty(handler)) {
                return handler;
            }
        } catch (RuntimeException ex) {
            // 正常办理保留原有异常语义；显式跳过权限时按「取不到登录人」处理
            if (!context.isIgnorePermission()) {
                throw ex;
            }
        }
        return systemHandler();
    }
}
