/*
 *    Copyright 2024-2025, Warm-Flow (290631660@qq.com).
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.dromara.warm.flow.core.dto;

import lombok.Getter;
import lombok.Setter;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 事件等待记录只读查询条件，第三方按需自由组合过滤、分页与排序。
 * <p>
 * 条件全部对应 {@code flow_event_wait} 的实际列：{@code businessId} 与 {@code flowCode} 不是该表的列，
 * 由门面层先解析成实例 / 定义主键再下推（解析规则与互斥约束见
 * {@link org.dromara.warm.flow.core.workflow.WorkflowService}）。
 * <p>
 * 默认只查定时类事件（{@code TIMER_START} / {@code TIMER_CATCH} / {@code TIMER_BOUNDARY}）；
 * 显式传入 {@link #setEventTypes(List)} 时以传入值为准，消息类与错误类等待同样可查。
 * 多值条件取「或」、不同条件之间取「且」，状态与事件类型使用持久化原值（如 {@code WAITING}），不做大小写归一。
 * <p>
 * 引擎解析 {@code businessId} / {@code flowCode} 时基于 {@link #copy()} 副本进行，不改写调用方入参，
 * 同一查询条件可重复复用且多次调用结果一致。
 *
 * @author may
 */
@Getter
@Setter
public class EventWaitQuery {

    /**
     * 排序字段白名单：入参写法（忽略下划线与大小写）→ 实际列名。
     * <p>
     * 排序字段不做原样拼接，未命中白名单直接报错，避免第三方传入的字符串进入 SQL。
     */
    private static final Map<String, String> ORDER_COLUMNS = new LinkedHashMap<>();

    /**
     * 允许的排序方向：升序 / 降序。
     */
    private static final List<String> ORDER_DIRECTIONS = Arrays.asList("ASC", "DESC");

    /**
     * 定时类事件类型集合，未显式指定事件类型且要求只查定时任务时使用。
     */
    private static final List<String> TIMER_EVENT_TYPES;

    static {
        ORDER_COLUMNS.put("id", "id");
        ORDER_COLUMNS.put("nextTriggerAt", "next_trigger_at");
        ORDER_COLUMNS.put("retryAt", "retry_at");
        ORDER_COLUMNS.put("createTime", "create_time");
        ORDER_COLUMNS.put("updateTime", "update_time");
        ORDER_COLUMNS.put("status", "status");
        TIMER_EVENT_TYPES = Collections.unmodifiableList(StreamUtils.toList(
            StreamUtils.filter(Arrays.asList(EventType.values()), type -> EventType.isTimer(type.getValue()))
            , EventType::getValue));
    }

    /**
     * 流程定义主键。
     */
    private Long definitionId;

    /**
     * 流程定义主键集合，非空时优先于 {@link #definitionId}。
     */
    private List<Long> definitionIds;

    /**
     * 流程定义编码；由门面层解析为该编码名下的所有定义主键。
     */
    private String flowCode;

    /**
     * 流程实例主键。
     */
    private Long instanceId;

    /**
     * 流程实例主键集合，非空时优先于 {@link #instanceId}。
     */
    private List<Long> instanceIds;

    /**
     * 业务主键；由门面层解析为该业务名下的所有实例主键。
     */
    private String businessId;

    /**
     * 事件节点编码。
     */
    private String nodeCode;

    /**
     * 事件名称，消息事件使用。
     */
    private String eventName;

    /**
     * 边界事件关联的待办任务主键。
     */
    private Long attachedTaskId;

    /**
     * 事件类型集合，非空时覆盖 {@link #timerOnly} 的默认范围。
     */
    private List<String> eventTypes;

    /**
     * 定时触发策略类型集合（AFTER / FIXED / POINT_IN_TIME）。
     */
    private List<String> triggerTypes;

    /**
     * 等待状态集合（WAITING / RUNNING / CANCELLED / RETRY / DEAD）。
     */
    private List<String> statuses;

    /**
     * 下次触发时间下界（含）。
     */
    private Date nextTriggerAtFrom;

    /**
     * 下次触发时间上界（含）。
     */
    private Date nextTriggerAtTo;

    /**
     * 创建时间下界（含）。
     */
    private Date createTimeFrom;

    /**
     * 创建时间上界（含）。
     */
    private Date createTimeTo;

    /**
     * 是否只查定时类事件，默认 {@code true}；显式指定事件类型时本字段失效。
     */
    private boolean timerOnly = true;

    /**
     * 页码，从 1 开始。
     */
    private int pageNum = 1;

    /**
     * 每页条数。
     */
    private int pageSize = 10;

    /**
     * 排序字段，缺省按下次触发时间升序；取值须属于排序白名单。
     */
    private String orderBy;

    /**
     * 排序方向，缺省升序；取值 ASC / DESC。
     */
    private String isAsc = "ASC";

    /**
     * 解析排序方向。
     *
     * @return {@code ASC} 或 {@code DESC}
     * @throws FlowException 传入非白名单方向时抛出
     */
    public String resolveOrderDirection() {
        String direction = StringUtils.isEmpty(isAsc) ? "ASC" : isAsc.trim().toUpperCase();
        if (!ORDER_DIRECTIONS.contains(direction)) {
            throw new FlowException("不支持的排序方向：" + isAsc + "，可选 ASC / DESC");
        }
        return direction;
    }

    /**
     * 解析排序字段为白名单列名。
     *
     * @return 可直接用于 order by 的列名
     * @throws FlowException 传入非白名单字段时抛出
     */
    public String resolveOrderColumn() {
        String key = StringUtils.isEmpty(orderBy) ? "nextTriggerAt" : orderBy;
        String normalized = key.replace("_", "").toLowerCase();
        for (Map.Entry<String, String> entry : ORDER_COLUMNS.entrySet()) {
            if (entry.getKey().toLowerCase().equals(normalized)) {
                return entry.getValue();
            }
        }
        throw new FlowException("不支持的排序字段：" + orderBy + "，可选 " + ORDER_COLUMNS.keySet());
    }

    /**
     * 解析事件类型条件：显式指定时以指定值为准，否则按 {@link #timerOnly} 决定是否限定定时类。
     *
     * @return 事件类型集合；返回空集合表示不限事件类型
     */
    public List<String> resolveEventTypes() {
        if (CollUtil.isNotEmpty(eventTypes)) {
            return eventTypes;
        }
        return timerOnly ? TIMER_EVENT_TYPES : Collections.emptyList();
    }

    /**
     * 解析流程定义条件：定义主键集合优先，其次单个定义主键。
     *
     * @return 流程定义主键集合；返回 {@code null} 表示不限
     */
    public List<Long> resolveDefinitionIds() {
        if (CollUtil.isNotEmpty(definitionIds)) {
            return definitionIds;
        }
        return definitionId == null ? null : Collections.singletonList(definitionId);
    }

    /**
     * 解析流程实例条件：实例主键集合优先，其次单个实例主键。
     *
     * @return 流程实例主键集合；返回 {@code null} 表示不限
     */
    public List<Long> resolveInstanceIds() {
        if (CollUtil.isNotEmpty(instanceIds)) {
            return instanceIds;
        }
        return instanceId == null ? null : Collections.singletonList(instanceId);
    }

    /**
     * 校验分页参数。
     *
     * @throws FlowException 页码或每页条数小于 1 时抛出
     */
    public void checkPage() {
        AssertUtil.isFalse(pageNum >= 1, "分页页码必须大于等于 1");
        AssertUtil.isFalse(pageSize >= 1, "分页条数必须大于等于 1");
    }

    /**
     * 复制一份查询条件。
     * <p>
     * 引擎解析 {@code businessId} / {@code flowCode} 并把结果写入主键集合时基于副本进行，
     * 不改写调用方入参：同一查询条件可重复复用，多次调用结果一致。
     *
     * @return 过滤、分页与排序字段完全一致的副本
     */
    public EventWaitQuery copy() {
        EventWaitQuery target = new EventWaitQuery();
        target.setDefinitionId(definitionId);
        target.setDefinitionIds(definitionIds);
        target.setFlowCode(flowCode);
        target.setInstanceId(instanceId);
        target.setInstanceIds(instanceIds);
        target.setBusinessId(businessId);
        target.setNodeCode(nodeCode);
        target.setEventName(eventName);
        target.setAttachedTaskId(attachedTaskId);
        target.setEventTypes(eventTypes);
        target.setTriggerTypes(triggerTypes);
        target.setStatuses(statuses);
        target.setNextTriggerAtFrom(nextTriggerAtFrom);
        target.setNextTriggerAtTo(nextTriggerAtTo);
        target.setCreateTimeFrom(createTimeFrom);
        target.setCreateTimeTo(createTimeTo);
        target.setTimerOnly(timerOnly);
        target.setPageNum(pageNum);
        target.setPageSize(pageSize);
        target.setOrderBy(orderBy);
        target.setIsAsc(isAsc);
        return target;
    }
}
