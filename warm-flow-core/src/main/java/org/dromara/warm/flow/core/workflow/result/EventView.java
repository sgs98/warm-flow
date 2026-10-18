package org.dromara.warm.flow.core.workflow.result;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 实例当前事件等待视图：用于区分「无当前任务但正在等待事件」与「流程已完成」。
 *
 * @author may
 */
@Getter
@Setter
public class EventView {

    /**
     * 事件类型（TIMER_CATCH / MESSAGE_CATCH 等）。
     */
    private String eventType;

    /**
     * 事件名称（消息事件）。
     */
    private String eventName;

    /**
     * 等待中的节点编码。
     */
    private String nodeCode;

    /**
     * 边界事件关联的待办任务 ID（非边界事件为空）。
     */
    private Long attachedTaskId;

    /**
     * 下次触发时间（定时事件）。
     */
    private Date nextTriggerAt;

    /**
     * 等待记录状态（WAITING / RETRY 等）。
     */
    private String status;
}
