package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 委派任务参数。
 *
 * @author may
 */
@Getter
@Setter
public class DelegateCommand extends WorkflowCommand {

    /**
     * 要委派的待办任务主键。
     */
    private Long taskId;

    /**
     * 被委派办理人标识。
     */
    private String targetHandler;

    /**
     * 委派说明。
     */
    private String message;

    /**
     * 可选的委派历史任务状态，未设置时使用引擎默认状态。
     */
    private String historyTaskStatus;
}
