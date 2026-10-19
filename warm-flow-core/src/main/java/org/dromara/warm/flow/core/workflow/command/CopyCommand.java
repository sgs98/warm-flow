package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 抄送任务参数。
 *
 * @author may
 */
@Getter
@Setter
public class CopyCommand extends WorkflowCommand {

    /**
     * 当前待办任务主键。
     */
    private Long taskId;

    /**
     * 抄送人标识（用户标识或发起人占位）。
     */
    private List<String> targetHandlers;

    /**
     * 本次流程动作的说明。
     */
    private String message;

}
