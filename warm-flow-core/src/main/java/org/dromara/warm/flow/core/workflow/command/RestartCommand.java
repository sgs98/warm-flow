package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 已结束流程实例重启参数。
 *
 * @author may
 */
@Getter
@Setter
public class RestartCommand extends WorkflowCommand {

    /**
     * 流程实例主键。
     */
    private Long instanceId;

    /**
     * 重启目标审批节点编码。
     */
    private String targetNodeCode;

    /**
     * 重启后待办的审批人。
     */
    private List<String> handlers;

    /**
     * 重启后的流程实例状态，未设置时默认为审批中。
     */
    private String flowStatus;

}
