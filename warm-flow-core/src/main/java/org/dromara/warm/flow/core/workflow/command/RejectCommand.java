package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 退回当前待办参数。
 *
 * @author may
 */
@Getter
@Setter
public class RejectCommand extends WorkflowCommand {

    /**
     * 要退回的待办任务主键。
     */
    private Long taskId;

    /**
     * 可选的退回目标节点编码。
     */
    private String targetNodeCode;

    /**
     * 退回说明。
     */
    private String message;

    /**
     * 本次退回需要写入流程实例的变量。
     */
    private Map<String, Object> variables;

    /**
     * 可选的退回后流程实例状态，未设置时由引擎状态机决定。
     */
    private String instanceStatus;

    /**
     * 可选的退回历史任务状态，未设置时由引擎状态机决定。
     */
    private String historyTaskStatus;

    /**
     * 退回后目标节点办理人集合。
     */
    private List<String> nextHandlers;

    /**
     * 是否将指定办理人追加到引擎计算出的办理人集合。
     */
    private boolean nextHandlerAppend;
}
