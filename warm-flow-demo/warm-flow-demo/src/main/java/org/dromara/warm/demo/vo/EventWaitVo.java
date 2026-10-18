package org.dromara.warm.demo.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 事件等待记录（定时任务）展示模型。
 * <p>引擎只返回主键与原始字段，流程名 / 业务主键由 {@code WorkflowSupport} 带缓存解析后回填，
 * 事件类型与等待状态额外补页面文案。载荷与失败原因原样透出，由页面格式化。</p>
 *
 * @author may
 * @since 2026/10/17
 */
@Getter
@Setter
public class EventWaitVo {

    /**
     * 等待记录主键。
     */
    private Long id;

    /**
     * 流程定义主键。
     */
    private Long definitionId;

    /**
     * 流程名称。
     */
    private String flowName;

    /**
     * 流程实例主键。
     */
    private Long instanceId;

    /**
     * 业务流程主键。
     */
    private String businessId;

    /**
     * 事件节点编码。
     */
    private String nodeCode;

    /**
     * 事件类型持久化值（如 TIMER_CATCH）。
     */
    private String eventType;

    /**
     * 事件类型文案。
     */
    private String eventTypeName;

    /**
     * 事件名称（消息名 / 错误码），错误边界留空表示通配。
     */
    private String eventName;

    /**
     * 边界事件关联的待办任务主键。
     */
    private Long attachedTaskId;

    /**
     * 定时触发策略（AFTER / FIXED / POINT_IN_TIME）。
     */
    private String triggerType;

    /**
     * 定时表达式或间隔值。
     */
    private String triggerInterval;

    /**
     * 下次触发时间。
     */
    private Date nextTriggerAt;

    /**
     * 时区。
     */
    private String timezone;

    /**
     * 已触发次数。
     */
    private Integer repeatCount;

    /**
     * 最大触发次数。
     */
    private Integer maxRepeatCount;

    /**
     * 等待状态持久化值（如 WAITING）。
     */
    private String status;

    /**
     * 等待状态文案。
     */
    private String statusName;

    /**
     * 已重试次数。
     */
    private Integer retryCount;

    /**
     * 最大重试次数。
     */
    private Integer maxRetryCount;

    /**
     * 下次重试时间。
     */
    private Date retryAt;

    /**
     * 抢占持有者。
     */
    private String lockOwner;

    /**
     * 抢占租约到期时间。
     */
    private Date lockUntil;

    /**
     * 乐观锁版本号。
     */
    private Integer version;

    /**
     * 事件载荷 JSON。
     */
    private String payload;

    /**
     * 最近一次失败原因。
     */
    private String lastError;

    /**
     * 创建时间。
     */
    private Date createTime;

    /**
     * 更新时间。
     */
    private Date updateTime;
}
