package org.dromara.warm.flow.orm.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.dto.EventWaitQuery;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.orm.dao.FlowEventWaitDao;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.utils.page.Page;
import org.dromara.warm.flow.orm.entity.FlowEventWait;
import org.dromara.warm.flow.orm.mapper.FlowEventWaitMapper;

import java.util.Date;
import java.util.List;

/**
 * 事件等待记录 DAO 实现（MyBatis-Plus）。
 * <p>
 * 定时器底座的扫描、抢占、租约与重试语义通过条件 Wrapper 实现，
 * 与 MyBatis 适配层同口径；逻辑删除过滤由实体 {@code @TableLogic} 统一附加。
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
        LambdaQueryWrapper<FlowEventWait> wrapper = new LambdaQueryWrapper<FlowEventWait>()
            .eq(FlowEventWait::getStatus, EventCons.STATUS_WAITING)
            .isNotNull(FlowEventWait::getNextTriggerAt)
            .le(FlowEventWait::getNextTriggerAt, now)
            .orderByAsc(FlowEventWait::getNextTriggerAt)
            .orderByAsc(FlowEventWait::getId)
            .last(limitSql(limit));
        applyCursor(wrapper, cursorAt, cursorId, true);
        return asEventWaits(getMapper().selectList(wrapper));
    }

    @Override
    public List<EventWait> selectRetryDue(Date now, Date cursorAt, Long cursorId, int limit) {
        LambdaQueryWrapper<FlowEventWait> wrapper = new LambdaQueryWrapper<FlowEventWait>()
            .eq(FlowEventWait::getStatus, EventCons.STATUS_RETRY)
            .isNotNull(FlowEventWait::getRetryAt)
            .le(FlowEventWait::getRetryAt, now)
            .orderByAsc(FlowEventWait::getRetryAt)
            .orderByAsc(FlowEventWait::getId)
            .last(limitSql(limit));
        applyCursor(wrapper, cursorAt, cursorId, false);
        return asEventWaits(getMapper().selectList(wrapper));
    }

    @Override
    public boolean claim(Long id, Integer version, String workerId, Date now, Date lockUntil) {
        LambdaUpdateWrapper<FlowEventWait> wrapper = dueCondition(id, version)
            .set(FlowEventWait::getStatus, EventCons.STATUS_RUNNING)
            .set(FlowEventWait::getLockOwner, workerId)
            .set(FlowEventWait::getLockUntil, lockUntil)
            .set(FlowEventWait::getUpdateTime, now)
            .setSql("version = version + 1")
            .and(w -> w.nested(n -> n.eq(FlowEventWait::getStatus, EventCons.STATUS_WAITING)
                    .isNotNull(FlowEventWait::getNextTriggerAt)
                    .le(FlowEventWait::getNextTriggerAt, now))
                .or(o -> o.eq(FlowEventWait::getStatus, EventCons.STATUS_RETRY)
                    .isNotNull(FlowEventWait::getRetryAt)
                    .le(FlowEventWait::getRetryAt, now)));
        return getMapper().update(null, wrapper) == 1;
    }

    @Override
    public boolean renewLease(Long id, Integer version, String workerId, Date now, Date lockUntil) {
        LambdaUpdateWrapper<FlowEventWait> wrapper = runningCondition(id, version, workerId)
            .set(FlowEventWait::getLockUntil, lockUntil)
            .set(FlowEventWait::getUpdateTime, now);
        return getMapper().update(null, wrapper) == 1;
    }

    @Override
    public List<EventWait> selectExpired(Date now, int limit) {
        LambdaQueryWrapper<FlowEventWait> wrapper = new LambdaQueryWrapper<FlowEventWait>()
            .eq(FlowEventWait::getStatus, EventCons.STATUS_RUNNING)
            .isNotNull(FlowEventWait::getLockUntil)
            .lt(FlowEventWait::getLockUntil, now)
            .orderByAsc(FlowEventWait::getId)
            .last(limitSql(limit));
        return asEventWaits(getMapper().selectList(wrapper));
    }

    @Override
    public boolean recoverExpired(Long id, Integer version, Integer retryCount, String status,
        Date retryAt, Date now, String error) {
        LambdaUpdateWrapper<FlowEventWait> wrapper = expiredCondition(id, version, now)
            .set(FlowEventWait::getStatus, status)
            .set(FlowEventWait::getRetryCount, retryCount)
            .set(FlowEventWait::getRetryAt, retryAt)
            .set(FlowEventWait::getLastError, error)
            .set(FlowEventWait::getLockOwner, null)
            .set(FlowEventWait::getLockUntil, null)
            .set(FlowEventWait::getUpdateTime, now)
            .setSql("version = version + 1");
        return getMapper().update(null, wrapper) == 1;
    }

    @Override
    public boolean deleteCompleted(Long id, Integer version, String workerId) {
        LambdaQueryWrapper<FlowEventWait> wrapper = new LambdaQueryWrapper<FlowEventWait>()
            .eq(FlowEventWait::getId, id)
            .eq(FlowEventWait::getVersion, version)
            .eq(FlowEventWait::getStatus, EventCons.STATUS_RUNNING)
            .eq(FlowEventWait::getLockOwner, workerId);
        return getMapper().delete(wrapper) == 1;
    }

    @Override
    public boolean markRetry(Long id, Integer version, String workerId, Date now, Date retryAt,
        String error) {
        LambdaUpdateWrapper<FlowEventWait> wrapper = runningCondition(id, version, workerId)
            .set(FlowEventWait::getStatus, EventCons.STATUS_RETRY)
            .set(FlowEventWait::getRetryAt, retryAt)
            .set(FlowEventWait::getLastError, error)
            .set(FlowEventWait::getUpdateTime, now)
            .setSql("retry_count = retry_count + 1")
            .setSql("version = version + 1");
        return getMapper().update(null, wrapper) == 1;
    }

    @Override
    public boolean markDead(Long id, Integer version, String workerId, Date now, String error) {
        LambdaUpdateWrapper<FlowEventWait> wrapper = runningCondition(id, version, workerId)
            .set(FlowEventWait::getStatus, EventCons.STATUS_DEAD)
            .set(FlowEventWait::getRetryAt, null)
            .set(FlowEventWait::getLastError, error)
            .set(FlowEventWait::getUpdateTime, now)
            .setSql("retry_count = retry_count + 1")
            .setSql("version = version + 1");
        return getMapper().update(null, wrapper) == 1;
    }

    @Override
    public boolean reschedule(Long id, Integer version, String workerId, Date now,
        Date nextTriggerAt, Integer repeatCount) {
        LambdaUpdateWrapper<FlowEventWait> wrapper = runningCondition(id, version, workerId)
            .set(FlowEventWait::getStatus, EventCons.STATUS_WAITING)
            .set(FlowEventWait::getNextTriggerAt, nextTriggerAt)
            .set(FlowEventWait::getRepeatCount, repeatCount)
            .set(FlowEventWait::getRetryAt, null)
            .set(FlowEventWait::getLockOwner, null)
            .set(FlowEventWait::getLockUntil, null)
            .set(FlowEventWait::getUpdateTime, now)
            .setSql("version = version + 1");
        return getMapper().update(null, wrapper) == 1;
    }

    @Override
    public boolean releaseClaim(Long id, Integer version, String workerId, String status,
        Date nextTriggerAt, Date retryAt, Date now) {
        LambdaUpdateWrapper<FlowEventWait> wrapper = runningCondition(id, version, workerId)
            .set(FlowEventWait::getStatus, status)
            .set(FlowEventWait::getNextTriggerAt, nextTriggerAt)
            .set(FlowEventWait::getRetryAt, retryAt)
            .set(FlowEventWait::getLockOwner, null)
            .set(FlowEventWait::getLockUntil, null)
            .set(FlowEventWait::getUpdateTime, now)
            .setSql("version = version + 1");
        return getMapper().update(null, wrapper) == 1;
    }

    @Override
    public boolean cancelClaimed(Long id, Integer version, String workerId, Date now, String error) {
        LambdaUpdateWrapper<FlowEventWait> wrapper = runningCondition(id, version, workerId)
            .set(FlowEventWait::getStatus, EventCons.STATUS_CANCELLED)
            .set(FlowEventWait::getLastError, error)
            .set(FlowEventWait::getLockOwner, null)
            .set(FlowEventWait::getLockUntil, null)
            .set(FlowEventWait::getUpdateTime, now)
            .setSql("version = version + 1");
        return getMapper().update(null, wrapper) == 1;
    }

    @Override
    public boolean revive(Long id, Integer version, Date now, Date nextTriggerAt) {
        LambdaUpdateWrapper<FlowEventWait> wrapper = dueCondition(id, version)
            .eq(FlowEventWait::getStatus, EventCons.STATUS_DEAD)
            .set(FlowEventWait::getStatus, EventCons.STATUS_WAITING)
            .set(FlowEventWait::getRetryCount, 0)
            .set(FlowEventWait::getRetryAt, null)
            .set(FlowEventWait::getNextTriggerAt, nextTriggerAt)
            .set(FlowEventWait::getLockOwner, null)
            .set(FlowEventWait::getLockUntil, null)
            .set(FlowEventWait::getUpdateTime, now)
            .setSql("version = version + 1");
        return getMapper().update(null, wrapper) == 1;
    }

    @Override
    public boolean claimMessage(Long id, Integer version, String workerId, Date now, Date lockUntil) {
        LambdaUpdateWrapper<FlowEventWait> wrapper = dueCondition(id, version)
            .in(FlowEventWait::getStatus, EventCons.STATUS_WAITING, EventCons.STATUS_RETRY)
            .set(FlowEventWait::getStatus, EventCons.STATUS_RUNNING)
            .set(FlowEventWait::getLockOwner, workerId)
            .set(FlowEventWait::getLockUntil, lockUntil)
            .set(FlowEventWait::getUpdateTime, now)
            .setSql("version = version + 1");
        return getMapper().update(null, wrapper) == 1;
    }

    @Override
    public boolean claimMessageWithPayload(Long id, Integer version, String workerId, Date now,
        Date lockUntil, String payload) {
        LambdaUpdateWrapper<FlowEventWait> wrapper = dueCondition(id, version)
            .in(FlowEventWait::getStatus, EventCons.STATUS_WAITING, EventCons.STATUS_RETRY)
            .set(FlowEventWait::getStatus, EventCons.STATUS_RUNNING)
            .set(payload != null, FlowEventWait::getPayload, payload)
            .set(FlowEventWait::getLockOwner, workerId)
            .set(FlowEventWait::getLockUntil, lockUntil)
            .set(FlowEventWait::getUpdateTime, now)
            .setSql("version = version + 1");
        return getMapper().update(null, wrapper) == 1;
    }

    @Override
    public boolean bindPayload(Long id, Integer version, String payload) {
        LambdaUpdateWrapper<FlowEventWait> wrapper = dueCondition(id, version)
            .in(FlowEventWait::getStatus, EventCons.STATUS_WAITING, EventCons.STATUS_RETRY)
            .set(FlowEventWait::getPayload, payload);
        return getMapper().update(null, wrapper) == 1;
    }

    @Override
    public int cancelByAttachedTaskId(Long attachedTaskId, Date now, Long excludedWaitId) {
        LambdaUpdateWrapper<FlowEventWait> wrapper = cancelUpdate(now)
            .eq(FlowEventWait::getAttachedTaskId, attachedTaskId);
        if (excludedWaitId != null) {
            wrapper.ne(FlowEventWait::getId, excludedWaitId);
        }
        return getMapper().update(null, wrapper);
    }

    @Override
    public int cancelByInstanceId(Long instanceId, Date now, Long excludedWaitId) {
        LambdaUpdateWrapper<FlowEventWait> wrapper = cancelUpdate(now)
            .eq(FlowEventWait::getInstanceId, instanceId);
        if (excludedWaitId != null) {
            wrapper.ne(FlowEventWait::getId, excludedWaitId);
        }
        return getMapper().update(null, wrapper);
    }

    @Override
    public int cancelByDefinitionId(Long definitionId, Date now) {
        LambdaUpdateWrapper<FlowEventWait> wrapper = cancelUpdate(now)
            .eq(FlowEventWait::getDefinitionId, definitionId)
            .isNull(FlowEventWait::getInstanceId);
        return getMapper().update(null, wrapper);
    }

    @Override
    public Page<EventWait> selectPageByQuery(EventWaitQuery query, Page<EventWait> page) {
        long total = getMapper().selectCount(conditionWrapper(query));
        if (total <= 0) {
            return Page.empty();
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FlowEventWait> pagePlus =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page.getPageNum(), page.getPageSize());
        // 总数已单独统计，跳过框架内置的 count 查询
        pagePlus.setSearchCount(false);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FlowEventWait> tPage =
            getMapper().selectPage(pagePlus, listWrapper(query));
        Page<EventWait> result = new Page<>(asEventWaits(tPage.getRecords()), total);
        result.setPageNum(page.getPageNum());
        result.setPageSize(page.getPageSize());
        return result;
    }

    @Override
    public List<EventWait> selectListByQuery(EventWaitQuery query) {
        return asEventWaits(getMapper().selectList(listWrapper(query)));
    }

    @Override
    public long selectCountByQuery(EventWaitQuery query) {
        return getMapper().selectCount(conditionWrapper(query));
    }

    /**
     * 构造统计条件：多值条件取「或」、不同条件之间取「且」，逻辑删除由 {@code @TableLogic} 统一附加。
     *
     * @param query 查询条件，可为 {@code null}
     * @return 统计条件
     */
    private QueryWrapper<FlowEventWait> conditionWrapper(EventWaitQuery query) {
        EventWaitQuery condition = ObjectUtil.isNull(query) ? new EventWaitQuery() : query;
        QueryWrapper<FlowEventWait> wrapper = new QueryWrapper<>();
        wrapper.in(CollUtil.isNotEmpty(condition.resolveDefinitionIds()), "definition_id"
            , condition.resolveDefinitionIds());
        wrapper.in(CollUtil.isNotEmpty(condition.resolveInstanceIds()), "instance_id"
            , condition.resolveInstanceIds());
        wrapper.eq(StringUtils.isNotEmpty(condition.getNodeCode()), "node_code", condition.getNodeCode());
        wrapper.eq(StringUtils.isNotEmpty(condition.getEventName()), "event_name", condition.getEventName());
        wrapper.eq(ObjectUtil.isNotNull(condition.getAttachedTaskId()), "attached_task_id"
            , condition.getAttachedTaskId());
        wrapper.in(CollUtil.isNotEmpty(condition.resolveEventTypes()), "event_type", condition.resolveEventTypes());
        wrapper.in(CollUtil.isNotEmpty(condition.getTriggerTypes()), "trigger_type", condition.getTriggerTypes());
        wrapper.in(CollUtil.isNotEmpty(condition.getStatuses()), "status", condition.getStatuses());
        wrapper.ge(ObjectUtil.isNotNull(condition.getNextTriggerAtFrom()), "next_trigger_at"
            , condition.getNextTriggerAtFrom());
        wrapper.le(ObjectUtil.isNotNull(condition.getNextTriggerAtTo()), "next_trigger_at"
            , condition.getNextTriggerAtTo());
        wrapper.ge(ObjectUtil.isNotNull(condition.getCreateTimeFrom()), "create_time", condition.getCreateTimeFrom());
        wrapper.le(ObjectUtil.isNotNull(condition.getCreateTimeTo()), "create_time", condition.getCreateTimeTo());
        return wrapper;
    }

    /**
     * 构造列表条件：统计条件 + 白名单排序，缺省按下次触发时间升序。
     *
     * @param query 查询条件，可为 {@code null}
     * @return 列表条件
     */
    private QueryWrapper<FlowEventWait> listWrapper(EventWaitQuery query) {
        QueryWrapper<FlowEventWait> wrapper = conditionWrapper(query);
        EventWaitQuery condition = ObjectUtil.isNull(query) ? new EventWaitQuery() : query;
        wrapper.orderBy(true, "ASC".equals(condition.resolveOrderDirection()), condition.resolveOrderColumn());
        return wrapper;
    }

    /**
     * 到期扫描的基础条件：主键 + 版本号（del_flag 由 @TableLogic 统一附加）。
     */
    private LambdaUpdateWrapper<FlowEventWait> dueCondition(Long id, Integer version) {
        return new LambdaUpdateWrapper<FlowEventWait>()
            .eq(FlowEventWait::getId, id)
            .eq(FlowEventWait::getVersion, version);
    }

    /**
     * RUNNING 状态完成类更新的基础条件：主键 + 版本号 + 当前持有者。
     */
    private LambdaUpdateWrapper<FlowEventWait> runningCondition(Long id, Integer version,
        String workerId) {
        return dueCondition(id, version)
            .eq(FlowEventWait::getStatus, EventCons.STATUS_RUNNING)
            .eq(FlowEventWait::getLockOwner, workerId);
    }

    /**
     * 范围取消的更新体：WAITING / RETRY / RUNNING 置 CANCELLED、递增版本号并清空租约
     * （范围条件由调用方追加，del_flag 由 @TableLogic 统一附加）。
     */
    private LambdaUpdateWrapper<FlowEventWait> cancelUpdate(Date now) {
        return new LambdaUpdateWrapper<FlowEventWait>()
            .in(FlowEventWait::getStatus, EventCons.STATUS_WAITING, EventCons.STATUS_RETRY,
                EventCons.STATUS_RUNNING)
            .set(FlowEventWait::getStatus, EventCons.STATUS_CANCELLED)
            .set(FlowEventWait::getLockOwner, null)
            .set(FlowEventWait::getLockUntil, null)
            .set(FlowEventWait::getUpdateTime, now)
            .setSql("version = version + 1");
    }

    /**
     * 租约过期恢复的基础条件：主键 + 版本号 + RUNNING + 租约已过期。
     */
    private LambdaUpdateWrapper<FlowEventWait> expiredCondition(Long id, Integer version, Date now) {
        return new LambdaUpdateWrapper<FlowEventWait>()
            .eq(FlowEventWait::getId, id)
            .eq(FlowEventWait::getVersion, version)
            .eq(FlowEventWait::getStatus, EventCons.STATUS_RUNNING)
            .isNotNull(FlowEventWait::getLockUntil)
            .lt(FlowEventWait::getLockUntil, now);
    }

    /**
     * 追加游标条件：(dueAt, id) 大于上一页最后一条。
     */
    private void applyCursor(LambdaQueryWrapper<FlowEventWait> wrapper, Date cursorAt,
        Long cursorId, boolean waiting) {
        if (cursorAt == null || cursorId == null) {
            return;
        }
        if (waiting) {
            wrapper.and(w -> w.gt(FlowEventWait::getNextTriggerAt, cursorAt)
                .or(o -> o.eq(FlowEventWait::getNextTriggerAt, cursorAt)
                    .gt(FlowEventWait::getId, cursorId)));
        } else {
            wrapper.and(w -> w.gt(FlowEventWait::getRetryAt, cursorAt)
                .or(o -> o.eq(FlowEventWait::getRetryAt, cursorAt)
                    .gt(FlowEventWait::getId, cursorId)));
        }
    }

    /**
     * ORM 实体列表转 core 实体列表（同构类型，仅泛型视角转换）。
     */
    @SuppressWarnings("unchecked")
    private List<EventWait> asEventWaits(List<FlowEventWait> list) {
        return (List<EventWait>) (List<?>) list;
    }

    /**
     * 按数据源方言追加固定条数限制：Oracle / SQL Server 使用 OFFSET/FETCH。
     */
    private String limitSql(int limit) {
        if ("oracle".equals(FlowEngine.dataSourceType())
            || "sqlserver".equals(FlowEngine.dataSourceType())) {
            return " OFFSET 0 ROWS FETCH NEXT " + limit + " ROWS ONLY";
        }
        return " LIMIT " + limit;
    }
}
