package org.dromara.warm.flow.orm.dao;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.dto.EventWaitQuery;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.orm.dao.FlowEventWaitDao;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.page.Page;
import org.dromara.warm.flow.orm.entity.FlowEventWait;
import org.dromara.warm.flow.orm.mapper.FlowEventWaitMapper;
import org.dromara.warm.flow.orm.utils.TenantDeleteUtil;

import java.util.Date;
import java.util.List;

/**
 * 事件等待记录 DAO 实现。
 * <p>
 * 定时器底座的扫描、抢占、租约与重试语义由 {@link FlowEventWaitMapper} 的条件更新语句承载；
 * 所有语句统一使用引擎配置的逻辑未删除值过滤（逻辑删除关闭时不拼 del_flag 条件）。
 *
 * @author may
 */
public class FlowEventWaitDaoImpl extends WarmDaoImpl<FlowEventWait> implements FlowEventWaitDao<FlowEventWait> {
    @Override
    public FlowEventWaitMapper getMapper() {
        return FrameInvoker.getBean(FlowEventWaitMapper.class);
    }

    @Override
    public FlowEventWait newEntity() {
        return new FlowEventWait();
    }

    @Override
    public List<EventWait> selectWaitingDue(Date now, Date cursorAt, Long cursorId, int limit) {
        return asEventWaits(getMapper().selectWaitingDue(now, cursorAt, cursorId, limit, notDeleteValue(),
            FlowEngine.dataSourceType()));
    }

    @Override
    public List<EventWait> selectRetryDue(Date now, Date cursorAt, Long cursorId, int limit) {
        return asEventWaits(getMapper().selectRetryDue(now, cursorAt, cursorId, limit, notDeleteValue(),
            FlowEngine.dataSourceType()));
    }

    @Override
    public boolean claim(Long id, Integer version, String workerId, Date now, Date lockUntil) {
        return getMapper().claim(id, version, workerId, now, lockUntil, notDeleteValue()) == 1;
    }

    @Override
    public boolean renewLease(Long id, Integer version, String workerId, Date now, Date lockUntil) {
        return getMapper().renewLease(id, version, workerId, now, lockUntil, notDeleteValue()) == 1;
    }

    @Override
    public List<EventWait> selectExpired(Date now, int limit) {
        return asEventWaits(getMapper().selectExpired(now, limit, notDeleteValue(),
            FlowEngine.dataSourceType()));
    }

    @Override
    public boolean recoverExpired(Long id, Integer version, Integer retryCount, String status,
        Date retryAt, Date now, String error) {
        return getMapper().recoverExpired(id, version, retryCount, status, retryAt, now, error,
            notDeleteValue()) == 1;
    }

    @Override
    public boolean deleteCompleted(Long id, Integer version, String workerId) {
        return getMapper().deleteCompleted(id, version, workerId, notDeleteValue()) == 1;
    }

    @Override
    public boolean markRetry(Long id, Integer version, String workerId, Date now, Date retryAt,
        String error) {
        return getMapper().markRetry(id, version, workerId, now, retryAt, error,
            notDeleteValue()) == 1;
    }

    @Override
    public boolean markDead(Long id, Integer version, String workerId, Date now, String error) {
        return getMapper().markDead(id, version, workerId, now, error, notDeleteValue()) == 1;
    }

    @Override
    public boolean reschedule(Long id, Integer version, String workerId, Date now,
        Date nextTriggerAt, Integer repeatCount) {
        return getMapper().reschedule(id, version, workerId, now, nextTriggerAt, repeatCount,
            notDeleteValue()) == 1;
    }

    @Override
    public boolean releaseClaim(Long id, Integer version, String workerId, String status,
        Date nextTriggerAt, Date retryAt, Date now) {
        return getMapper().releaseClaim(id, version, workerId, status, nextTriggerAt, retryAt, now,
            notDeleteValue()) == 1;
    }

    @Override
    public boolean claimMessage(Long id, Integer version, String workerId, Date now, Date lockUntil) {
        return getMapper().claimMessage(id, version, workerId, now, lockUntil, notDeleteValue()) == 1;
    }

    @Override
    public boolean bindPayload(Long id, Integer version, String payload) {
        return getMapper().bindPayload(id, version, payload, notDeleteValue()) == 1;
    }

    @Override
    public Page<EventWait> selectPageByQuery(EventWaitQuery query, Page<EventWait> page) {
        EventWaitQuery condition = prepare(query);
        FlowEventWait entity = TenantDeleteUtil.getEntity(newEntity());
        String dataSourceType = FlowEngine.dataSourceType();
        long total = getMapper().selectCountByQuery(entity, condition);
        if (total <= 0) {
            return Page.empty();
        }
        Page<FlowEventWait> queryPage = new Page<>(page.getPageNum(), page.getPageSize());
        ifNecessaryChangePage(queryPage, dataSourceType);
        List<EventWait> list = asEventWaits(getMapper().selectListByQuery(entity, condition,
            orderOf(condition), queryPage, dataSourceType));
        Page<EventWait> result = new Page<>(list, total);
        result.setPageNum(page.getPageNum());
        result.setPageSize(page.getPageSize());
        return result;
    }

    @Override
    public List<EventWait> selectListByQuery(EventWaitQuery query) {
        EventWaitQuery condition = prepare(query);
        return asEventWaits(getMapper().selectListByQuery(TenantDeleteUtil.getEntity(newEntity()), condition,
            orderOf(condition), null, FlowEngine.dataSourceType()));
    }

    @Override
    public long selectCountByQuery(EventWaitQuery query) {
        return getMapper().selectCountByQuery(TenantDeleteUtil.getEntity(newEntity()), prepare(query));
    }

    /**
     * 把查询条件中「单值 / 多值二选一」与「默认事件类型范围」的解析结果落到副本的集合字段，
     * XML 只读字段拼条件，保证两条 SQL（统计与列表）条件口径一致且不改写调用方入参。
     *
     * @param query 调用方查询条件，可为 {@code null}
     * @return 可执行的查询条件
     */
    private EventWaitQuery prepare(EventWaitQuery query) {
        EventWaitQuery condition = ObjectUtil.isNull(query) ? new EventWaitQuery() : query.copy();
        condition.setDefinitionIds(condition.resolveDefinitionIds());
        condition.setInstanceIds(condition.resolveInstanceIds());
        condition.setEventTypes(condition.resolveEventTypes());
        return condition;
    }

    /**
     * 排序子句：白名单解析后的列名 + 方向，缺省按下次触发时间升序。
     *
     * @param query 查询条件
     * @return 可直接拼入 order by 的子句
     */
    private String orderOf(EventWaitQuery query) {
        return query.resolveOrderColumn() + " " + query.resolveOrderDirection();
    }

    /**
     * 获取引擎配置的逻辑未删除值；逻辑删除关闭时返回 {@code null}（语句不拼 del_flag 条件）。
     */
    private String notDeleteValue() {
        WarmFlow config = FlowEngine.getFlowConfig();
        if (config == null || !config.isLogicDelete()) {
            return null;
        }
        return config.getLogicNotDeleteValue();
    }

    /**
     * ORM 实体列表转 core 实体列表（同构类型，仅泛型视角转换）。
     */
    @SuppressWarnings("unchecked")
    private List<EventWait> asEventWaits(List<FlowEventWait> list) {
        return (List<EventWait>) (List<?>) list;
    }
}
