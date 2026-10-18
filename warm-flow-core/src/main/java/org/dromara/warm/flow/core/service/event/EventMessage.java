package org.dromara.warm.flow.core.service.event;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.Map;

/**
 * 事件消息：{@code MESSAGE_THROW} / {@code MESSAGE_END} 到达时交给宿主发布器对外发送。
 *
 * @author may
 */
@Getter
@Setter
@Accessors(chain = true)
public class EventMessage {

    /**
     * 事件等待记录主键（抛出事件可能没有等待记录，为 {@code null}）。
     */
    private Long eventWaitId;

    /**
     * 事件类型（MESSAGE_THROW / MESSAGE_END）。
     */
    private String eventType;

    /**
     * 事件名称。
     */
    private String eventName;

    /**
     * 流程定义主键。
     */
    private Long definitionId;

    /**
     * 流程实例主键（定义级事件为 {@code null}）。
     */
    private Long instanceId;

    /**
     * 事件节点编码。
     */
    private String nodeCode;

    /**
     * 租户标识。
     */
    private String tenantId;

    /**
     * 业务载荷。
     */
    private Map<String, Object> payload;

    /**
     * 触发时间。
     */
    private Date triggerTime;
}
