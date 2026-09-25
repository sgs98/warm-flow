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
     * 当前待办任务主键。
     */
    private Long taskId;

    /**
     * 被委派办理人标识。
     */
    private String targetHandler;

    /**
     * 本次流程动作的说明。
     */
    private String message;

}
