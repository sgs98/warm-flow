package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 完成当前待办参数。
 *
 * @author may
 */
@Getter
@Setter
public class CompleteCommand extends WorkflowCommand {

    /**
     * 要完成的待办任务主键。
     */
    private Long taskId;

    /**
     * 办理说明。
     */
    private String message;

    /**
     * 本次办理需要写入流程实例的变量。
     */
    private Map<String, Object> variables;

    /**
     * 可选的办理后流程实例状态，未设置时由引擎状态机决定。
     */
    private String instanceStatus;

    /**
     * 可选的已完成历史任务状态，未设置时由引擎状态机决定。
     */
    private String historyTaskStatus;

    /**
     * 指定后续节点办理人集合。
     */
    private List<String> nextHandlers;

    /**
     * 是否将指定办理人追加到引擎计算出的办理人集合。
     */
    private boolean nextHandlerAppend;
}
