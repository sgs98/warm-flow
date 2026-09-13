package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;

/**
 * 流程写操作参数基类。
 *
 * @author may
 */
@Getter
@Setter
public abstract class WorkflowCommand {

    /**
     * 当前流程操作的操作者上下文。
     */
    private OperatorContext operator;

    /**
     * 调用方传入的扩展信息。
     */
    private String ext;
}
