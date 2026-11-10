package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 转办任务参数。
 *
 * @author may
 */
@Getter
@Setter
public class TransferCommand extends WorkflowCommand {

    /**
     * 当前待办任务主键。
     */
    private Long taskId;

    /**
     * 接收转办的办理人标识（单目标兼容字段）。
     * <p>新调用方应使用 {@link #targetHandlers}；组签节点允许一组多目标，普通节点仍只能单目标。</p>
     */
    private String targetHandler;

    /**
     * 接收转办的办理人列表，非空时优先于 {@link #targetHandler}。
     * <p>与旧字段同时提供时，仅允许列表为同一个单目标；冲突直接拒绝。</p>
     */
    private List<String> targetHandlers;

    /**
     * 本次流程动作的说明。
     */
    private String message;

}
