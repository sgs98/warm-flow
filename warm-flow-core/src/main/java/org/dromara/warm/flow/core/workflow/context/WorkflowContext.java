package org.dromara.warm.flow.core.workflow.context;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 流程动作的内部执行上下文。
 *
 * <p>该对象只承载多个流程动作共享的执行数据，不作为第三方公共调用参数，
 * 也不保存流程状态、历史状态和协作类型等由引擎内部决定的字段。</p>
 *
 * @author may
 */
@Getter
@Setter
public class WorkflowContext {

    /**
     * 当前操作者唯一标识。
     */
    private String handler;

    /**
     * 当前操作者可用于权限匹配的标识集合。
     */
    private List<String> permissions;

    /**
     * 是否忽略流程办理权限校验。
     */
    private boolean ignorePermission;

    /**
     * 本次流程动作的说明。
     */
    private String message;

    /**
     * 本次流程动作需要写入实例的变量。
     */
    private Map<String, Object> variables;

    /**
     * 调用方指定的流程实例状态，未设置时由引擎状态机决定。
     */
    private String instanceStatus;

    /**
     * 调用方指定的历史任务状态，未设置时由引擎状态机决定。
     */
    private String historyTaskStatus;

    /**
     * 流程动作的目标节点编码。
     */
    private String targetNodeCode;

    /**
     * 指定的后续节点办理人集合。
     */
    private List<String> nextHandlers;

    /**
     * 是否将指定办理人追加到引擎计算出的办理人集合。
     */
    private boolean nextHandlerAppend;

    /**
     * 调用方传入的扩展信息。
     */
    private String ext;
}
