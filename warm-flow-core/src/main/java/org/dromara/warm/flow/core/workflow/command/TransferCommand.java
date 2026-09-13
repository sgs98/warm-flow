package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;

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
}
