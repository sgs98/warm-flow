package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 加签任务参数。
 *
 * @author may
 */
@Getter
@Setter
public class AddSignerCommand extends WorkflowCommand {

    /**
     * 当前待办任务主键。
     */
    private Long taskId;

    /**
     * 要增加的办理人标识集合。
     */
    private List<String> targetHandlers;

    /**
     * 本次流程动作的说明。
     */
    private String message;

}
