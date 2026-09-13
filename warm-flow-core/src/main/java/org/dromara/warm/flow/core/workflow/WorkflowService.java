package org.dromara.warm.flow.core.workflow;

import org.dromara.warm.flow.core.workflow.command.*;
import org.dromara.warm.flow.core.workflow.result.WorkflowResult;

/**
 * 流程操作统一门面。
 *
 * @author may
 */
public interface WorkflowService {

    /**
     * 启动流程实例并创建首个待办。
     *
     * @param command 启动参数
     * @return 流程操作结果
     */
    WorkflowResult start(StartCommand command);

    /**
     * 完成当前待办并推动流程继续执行。
     *
     * @param command 完成参数
     * @return 流程操作结果
     */
    WorkflowResult complete(CompleteCommand command);

    /**
     * 将当前待办退回到合法的前置节点。
     *
     * @param command 退回参数
     * @return 流程操作结果
     */
    WorkflowResult reject(RejectCommand command);

    /**
     * 将当前待办跳转到指定节点。
     *
     * @param command 跳转参数
     * @return 流程操作结果
     */
    WorkflowResult jump(JumpCommand command);

    /**
     * 撤回申请人发起的流程实例。
     *
     * @param command 撤回参数
     * @return 流程操作结果
     */
    WorkflowResult revoke(RevokeCommand command);

    /**
     * 终止流程实例。
     *
     * @param command 终止参数
     * @return 流程操作结果
     */
    WorkflowResult terminate(TerminateCommand command);

    /**
     * 将当前待办转交给其他办理人。
     *
     * @param command 转办参数
     * @return 流程操作结果
     */
    WorkflowResult transfer(TransferCommand command);

    /**
     * 将当前待办委派给其他办理人。
     *
     * @param command 委派参数
     * @return 流程操作结果
     */
    WorkflowResult delegate(DelegateCommand command);

    /**
     * 为会签或票签节点增加办理人。
     *
     * @param command 加签参数
     * @return 流程操作结果
     */
    WorkflowResult addSigner(AddSignerCommand command);

    /**
     * 为会签或票签节点移除办理人。
     *
     * @param command 减签参数
     * @return 流程操作结果
     */
    WorkflowResult removeSigner(RemoveSignerCommand command);
}
