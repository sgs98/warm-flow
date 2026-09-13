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
     * 要跳转的待办任务主键。
     */
    private Long taskId;

    /**
     * 跳转目标节点编码。
     */
    private String targetNodeCode;

    /**
     * 跳转说明。
     */
    private String message;

    /**
     * 本次跳转需要写入流程实例的变量。
     */
    private Map<String, Object> variables;

    /**
     * 可选的跳转后流程实例状态，未设置时由引擎状态机决定。
     */
    private String instanceStatus;

    /**
     * 可选的跳转历史任务状态，未设置时由引擎状态机决定。
     */
    private String historyTaskStatus;

    /**
     * 目标节点办理人集合。
     */
    private List<String> nextHandlers;

    /**
     * 是否将指定办理人追加到目标节点办理人集合。
     */
    private boolean nextHandlerAppend;
}
