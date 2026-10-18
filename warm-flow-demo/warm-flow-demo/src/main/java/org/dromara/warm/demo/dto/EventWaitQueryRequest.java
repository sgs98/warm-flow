package org.dromara.warm.demo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.warm.flow.core.dto.EventWaitQuery;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.List;

/**
 * 定时任务（事件等待记录）查询参数。
 * <p>过滤维度与引擎 {@link EventWaitQuery} 对应：多值条件取「或」、不同条件之间取「且」，
 * 事件类型留空时由 {@link #timerOnly} 决定是否只查定时类事件。</p>
 *
 * @author may
 * @since 2026/10/17
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EventWaitQueryRequest extends PageQuery {

    /**
     * 等待状态多选（WAITING / RETRY / RUNNING / DEAD / CANCELLED），留空表示不限。
     */
    private List<String> statuses;

    /**
     * 事件类型多选，留空时按 {@link #timerOnly} 取默认范围。
     */
    private List<String> eventTypes;

    /**
     * 是否只查定时类事件，默认 {@code true}。
     */
    private Boolean timerOnly = true;

    /**
     * 流程定义编码。
     */
    private String flowCode;

    /**
     * 业务流程主键。
     */
    private String businessId;

    /**
     * 事件节点编码。
     */
    private String nodeCode;

    /**
     * 事件名称（消息名 / 错误码）。
     */
    private String eventName;

    /**
     * 下次触发时间下界。
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextTriggerAtFrom;

    /**
     * 下次触发时间上界。
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextTriggerAtTo;

    /**
     * 创建时间下界。
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTimeFrom;

    /**
     * 创建时间上界。
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTimeTo;

    /**
     * 转换为引擎查询条件。
     *
     * @return 引擎只读查询条件
     */
    public EventWaitQuery toQuery() {
        EventWaitQuery query = new EventWaitQuery();
        query.setPageNum(normalizedPageNum());
        query.setPageSize(normalizedPageSize());
        query.setStatuses(statuses);
        query.setEventTypes(eventTypes);
        query.setTimerOnly(!Boolean.FALSE.equals(timerOnly));
        query.setFlowCode(flowCode);
        query.setBusinessId(businessId);
        query.setNodeCode(nodeCode);
        query.setEventName(eventName);
        query.setNextTriggerAtFrom(nextTriggerAtFrom);
        query.setNextTriggerAtTo(nextTriggerAtTo);
        query.setCreateTimeFrom(createTimeFrom);
        query.setCreateTimeTo(createTimeTo);
        return query;
    }
}
