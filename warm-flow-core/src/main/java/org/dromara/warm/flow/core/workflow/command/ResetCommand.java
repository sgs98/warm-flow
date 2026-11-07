package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 已结束流程实例重置参数。
 *
 * @author may
 */
@Getter
@Setter
public class ResetCommand extends WorkflowCommand {

    /**
     * 流程实例主键。
     */
    private Long instanceId;

    /**
     * 重置目标审批节点编码。
     */
    private String targetNodeCode;

    /**
     * 重置后待办的审批人。
     */
    private List<String> handlers;

    /**
     * 重置后的流程实例状态，未设置时默认为审批中。
     */
    private String flowStatus;

}
