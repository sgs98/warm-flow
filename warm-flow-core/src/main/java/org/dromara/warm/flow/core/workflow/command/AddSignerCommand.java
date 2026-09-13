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
     * 待增加办理人的任务主键。
     */
    private Long taskId;

    /**
     * 要增加的办理人标识集合。
     */
    private List<String> targetHandlers;

    /**
     * 加签说明。
     */
    private String message;

    /**
     * 可选的加签历史任务状态，未设置时使用引擎默认状态。
     */
    private String historyTaskStatus;
}
