package org.dromara.warm.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.warm.demo.dto.EventWaitQueryRequest;
import org.dromara.warm.demo.service.EventWaitAppService;
import org.dromara.warm.demo.service.support.WorkflowSupport;
import org.dromara.warm.demo.vo.EventWaitSummaryVo;
import org.dromara.warm.demo.vo.EventWaitVo;
import org.dromara.warm.demo.vo.PageVo;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.dto.EventWaitQuery;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.utils.page.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务（事件等待记录）应用服务实现。
 * <p>条件解析与合法性校验由引擎 {@link EventWaitQuery} 与门面层完成，本类只负责参数透传、
 * 引擎实体到展示模型的装配（流程名 / 业务主键走 {@link WorkflowSupport} 缓存）与状态文案。</p>
 *
 * @author may
 * @since 2026/10/17
 */
@Service
@RequiredArgsConstructor
public class EventWaitAppServiceImpl implements EventWaitAppService {

    /**
     * 事件类型持久化值 → 页面文案。
     */
    private static final Map<String, String> EVENT_TYPE_NAMES = new LinkedHashMap<>();

    /**
     * 等待状态持久化值 → 页面文案。
     */
    private static final Map<String, String> STATUS_NAMES = new LinkedHashMap<>();

    static {
        EVENT_TYPE_NAMES.put(EventType.MESSAGE_START.getValue(), "消息启动");
        EVENT_TYPE_NAMES.put(EventType.TIMER_START.getValue(), "定时启动");
        EVENT_TYPE_NAMES.put(EventType.TIMER_CATCH.getValue(), "定时捕获");
        EVENT_TYPE_NAMES.put(EventType.MESSAGE_CATCH.getValue(), "消息捕获");
        EVENT_TYPE_NAMES.put(EventType.MESSAGE_THROW.getValue(), "消息抛出");
        EVENT_TYPE_NAMES.put(EventType.TIMER_BOUNDARY.getValue(), "定时边界");
        EVENT_TYPE_NAMES.put(EventType.MESSAGE_BOUNDARY.getValue(), "消息边界");
        EVENT_TYPE_NAMES.put(EventType.TERMINATE_END.getValue(), "终止结束");
        EVENT_TYPE_NAMES.put(EventType.MESSAGE_END.getValue(), "消息结束");
        EVENT_TYPE_NAMES.put(EventType.ERROR_CATCH.getValue(), "错误捕获");
        EVENT_TYPE_NAMES.put(EventType.ERROR_BOUNDARY.getValue(), "错误边界");

        STATUS_NAMES.put(EventCons.STATUS_WAITING, "待触发");
        STATUS_NAMES.put(EventCons.STATUS_RETRY, "重试中");
        STATUS_NAMES.put(EventCons.STATUS_RUNNING, "执行中");
        STATUS_NAMES.put(EventCons.STATUS_CANCELLED, "已取消");
        STATUS_NAMES.put(EventCons.STATUS_DEAD, "已死");
        STATUS_NAMES.put(EventCons.STATUS_SUCCESS, "成功");
    }

    /**
     * Demo 共享底座：流程名 / 业务主键缓存与分页装配。
     */
    private final WorkflowSupport support;

    /**
     * 分页查询事件等待记录。
     *
     * @param request 查询参数
     * @return 分页等待记录
     */
    @Override
    public PageVo<EventWaitVo> pageEventWaits(EventWaitQueryRequest request) {
        Page<EventWait> page = FlowEngine.workflow().pageEventWaits(request.toQuery());
        return support.toPage(page, this::toVo);
    }

    /**
     * 按同一组筛选条件统计各状态等待记录数量。
     *
     * @param request 查询参数
     * @return 各状态计数
     */
    @Override
    public EventWaitSummaryVo summary(EventWaitQueryRequest request) {
        EventWaitSummaryVo vo = new EventWaitSummaryVo();
        vo.setWaiting(countByStatus(request, EventCons.STATUS_WAITING));
        vo.setRetry(countByStatus(request, EventCons.STATUS_RETRY));
        vo.setRunning(countByStatus(request, EventCons.STATUS_RUNNING));
        vo.setDead(countByStatus(request, EventCons.STATUS_DEAD));
        vo.setCancelled(countByStatus(request, EventCons.STATUS_CANCELLED));
        return vo;
    }

    /**
     * 统计某一状态下的等待记录数量。
     *
     * @param request 查询参数（其中状态条件被覆盖）
     * @param status  等待状态
     * @return 记录数量
     */
    private long countByStatus(EventWaitQueryRequest request, String status) {
        EventWaitQuery query = request.toQuery();
        query.setStatuses(List.of(status));
        return FlowEngine.workflow().countEventWaits(query);
    }

    /**
     * 引擎等待记录 → 页面展示模型。
     *
     * @param wait 引擎等待记录
     * @return 展示模型
     */
    private EventWaitVo toVo(EventWait wait) {
        EventWaitVo vo = new EventWaitVo();
        BeanUtils.copyProperties(wait, vo);
        vo.setFlowName(support.flowNameOf(wait.getDefinitionId()));
        vo.setBusinessId(support.businessIdOf(wait.getInstanceId()));
        vo.setEventTypeName(EVENT_TYPE_NAMES.getOrDefault(wait.getEventType(), wait.getEventType()));
        vo.setStatusName(STATUS_NAMES.getOrDefault(wait.getStatus(), wait.getStatus()));
        return vo;
    }
}
