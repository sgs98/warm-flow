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
     * 本次流程动作的说明。
     */
    private String message;

    /**
     * 本次操作需要写入流程实例的变量。
     */
    private Map<String, Object> variables;

    /**
     * 可选的流程实例状态。
     */
    private String flowStatus;

}
