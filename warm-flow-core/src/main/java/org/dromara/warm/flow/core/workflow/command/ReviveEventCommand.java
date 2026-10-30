package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 事件等待记录复活参数。
 *
 * @author may
 */
@Getter
@Setter
public class ReviveEventCommand extends WorkflowCommand {

    /**
     * 等待记录主键。
     */
    private Long eventWaitId;

}
