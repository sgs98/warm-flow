package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

/**
 * 转办任务参数。
 *
 * @author may
 */
@Getter
@Setter
public class TransferCommand extends WorkflowCommand {

    /**
     * 要转办的待办任务主键。
     */
    private Long taskId;

    /**
     * 接收转办的办理人标识。
     */
    private String targetHandler;

    /**
     * 转办说明。
     */
    private String message;

    /**
     * 可选的转办历史任务状态，未设置时使用引擎默认状态。
     */
    private String historyTaskStatus;

    /**
     * 将转办参数填充到执行上下文。
     *
     * @param context 流程执行上下文
     */
    @Override
    public void fillContext(WorkflowContext context) {
        context.setMessage(getMessage());
        context.setHistoryTaskStatus(getHistoryTaskStatus());
    }
}
