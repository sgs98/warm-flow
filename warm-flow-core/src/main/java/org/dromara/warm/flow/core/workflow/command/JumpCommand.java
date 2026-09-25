package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 指定目标节点跳转参数。
 *
 * @author may
 */
@Getter
@Setter
public class JumpCommand extends WorkflowCommand {

    /**
     * 当前待办任务主键。
     */
    private Long taskId;

    /**
     * 目标节点编码。
     */
    private String targetNodeCode;

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

    /**
     * 指定后续节点办理人。
     */
    private List<String> nextHandlers;

    /**
     * 是否将指定办理人追加到引擎计算出的办理人集合。
     */
    private boolean nextHandlerAppend;

}
