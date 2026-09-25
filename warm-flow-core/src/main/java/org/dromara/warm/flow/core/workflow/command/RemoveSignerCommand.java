package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 减签任务参数。
 *
 * @author may
 */
@Getter
@Setter
public class RemoveSignerCommand extends WorkflowCommand {

    /**
     * 当前待办任务主键。
     */
    private Long taskId;

    /**
     * 要移除的办理人标识集合。
     */
    private List<String> targetHandlers;

    /**
     * 本次流程动作的说明。
     */
    private String message;

}
