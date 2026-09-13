package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 申请人撤回流程参数。
 *
 * @author may
 */
@Getter
@Setter
public class RevokeCommand extends WorkflowCommand {

    /**
     * 要撤回的流程实例主键。
     */
    private Long instanceId;

    /**
     * 撤回说明。
     */
    private String message;

    /**
     * 撤回时需要写入流程实例的变量。
     */
    private Map<String, Object> variables;

    /**
     * 可选的撤回后流程实例状态，未设置时使用引擎默认状态。
     */
    private String instanceStatus;

    /**
     * 可选的撤回历史任务状态，未设置时使用引擎默认状态。
     */
    private String historyTaskStatus;
}
