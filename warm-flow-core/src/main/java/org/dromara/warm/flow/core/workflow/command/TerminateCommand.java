package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 终止流程参数，实例级和任务级标识至少提供一个。
 *
 * @author may
 */
@Getter
@Setter
public class TerminateCommand extends WorkflowCommand {

    /**
     * 要终止的流程实例主键。
     */
    private Long instanceId;

    /**
     * 当前待办任务主键，可用于任务级权限校验。
     */
    private Long taskId;

    /**
     * 终止说明。
     */
    private String message;

    /**
     * 可选的终止后流程实例状态，未设置时使用引擎默认状态。
     */
    private String instanceStatus;

    /**
     * 可选的终止历史任务状态，未设置时使用引擎默认状态。
     */
    private String historyTaskStatus;
}
