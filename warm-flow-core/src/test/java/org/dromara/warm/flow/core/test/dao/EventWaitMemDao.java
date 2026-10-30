package org.dromara.warm.flow.core.test.dao;

import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.dto.EventWaitQuery;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.orm.dao.FlowEventWaitDao;
import org.dromara.warm.flow.core.utils.page.Page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * 单测内存实现：事件等待记录 DAO。
 * <p>
 * 以内存行语义复刻定时器底座的条件更新 SQL（到期扫描、CAS 抢占、租约续期与恢复、
 * 重试与终态、重排期、释放抢占），使 core 调度逻辑无需数据库即可验证。
 *
 * @author may
 */
public class EventWaitMemDao extends InMemoryDao<EventWait> implements FlowEventWaitDao<EventWait> {

    public EventWaitMemDao(java.util.function.Supplier<EventWait> entityFactory, List<String> log) {
        super(entityFactory, log);
    }

    @Override
    public List<EventWait> selectWaitingDue(Date now, Date cursorAt, Long cursorId, int limit) {
        log.add(name() + ".selectWaitingDue[now=" + now + "]");
        List<EventWait> result = new ArrayList<>();
        for (EventWait row : sortedByDue()) {
            if (!EventCons.STATUS_WAITING.equals(row.getStatus())
                || row.getNextTriggerAt() == null
                || row.getNextTriggerAt().after(now)) {
                continue;
            }
            if (afterCursor(row.getNextTriggerAt(), row.getId(), cursorAt, cursorId)) {
                result.add(copyOf(row));
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    @Override
    public List<EventWait> selectRetryDue(Date now, Date cursorAt, Long cursorId, int limit) {
        log.add(name() + ".selectRetryDue[now=" + now + "]");
        List<EventWait> result = new ArrayList<>();
        for (EventWait row : sortedByRetry()) {
            if (!EventCons.STATUS_RETRY.equals(row.getStatus())
                || row.getRetryAt() == null
                || row.getRetryAt().after(now)) {
                continue;
            }
            if (afterCursor(row.getRetryAt(), row.getId(), cursorAt, cursorId)) {
                result.add(copyOf(row));
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    @Override
    public boolean claim(Long id, Integer version, String workerId, Date now, Date lockUntil) {
        log.add(name() + ".claim[" + id + ",v=" + version + "]");
        EventWait row = store.get(id);
        if (row == null || !row.getVersion().equals(version)) {
            return false;
        }
        boolean due = (EventCons.STATUS_WAITING.equals(row.getStatus())
            && row.getNextTriggerAt() != null && !row.getNextTriggerAt().after(now))
            || (EventCons.STATUS_RETRY.equals(row.getStatus())
            && row.getRetryAt() != null && !row.getRetryAt().after(now));
        if (!due) {
            return false;
        }
        row.setStatus(EventCons.STATUS_RUNNING);
        row.setLockOwner(workerId);
        row.setLockUntil(lockUntil);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
        return true;
    }

    @Override
    public boolean renewLease(Long id, Integer version, String workerId, Date now, Date lockUntil) {
        log.add(name() + ".renewLease[" + id + ",v=" + version + "]");
        EventWait row = store.get(id);
        if (row == null || !row.getVersion().equals(version)
            || !EventCons.STATUS_RUNNING.equals(row.getStatus())
            || !workerId.equals(row.getLockOwner())) {
            return false;
        }
        row.setLockUntil(lockUntil);
        row.setUpdateTime(now);
        return true;
    }

    @Override
    public List<EventWait> selectExpired(Date now, int limit) {
        log.add(name() + ".selectExpired[now=" + now + "]");
        List<EventWait> result = new ArrayList<>();
        for (EventWait row : store.values()) {
            if (EventCons.STATUS_RUNNING.equals(row.getStatus())
                && row.getLockUntil() != null && row.getLockUntil().before(now)) {
                result.add(copyOf(row));
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    @Override
    public boolean recoverExpired(Long id, Integer version, Integer retryCount, String status,
        Date retryAt, Date now, String error) {
        log.add(name() + ".recoverExpired[" + id + ",v=" + version + ",to=" + status + "]");
        EventWait row = store.get(id);
        if (row == null || !row.getVersion().equals(version)
            || !EventCons.STATUS_RUNNING.equals(row.getStatus())
            || row.getLockUntil() == null || !row.getLockUntil().before(now)) {
            return false;
        }
        row.setStatus(status);
        row.setRetryCount(retryCount);
        row.setRetryAt(retryAt);
        row.setLastError(error);
        row.setLockOwner(null);
        row.setLockUntil(null);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
        return true;
    }

    @Override
    public boolean deleteCompleted(Long id, Integer version, String workerId) {
        log.add(name() + ".deleteCompleted[" + id + ",v=" + version + "]");
        EventWait row = store.get(id);
        if (!runningByOwner(row, version, workerId)) {
            return false;
        }
        store.remove(id);
        return true;
    }

    @Override
    public boolean markRetry(Long id, Integer version, String workerId, Date now, Date retryAt,
        String error) {
        log.add(name() + ".markRetry[" + id + ",v=" + version + "]");
        EventWait row = store.get(id);
        if (!runningByOwner(row, version, workerId)) {
            return false;
        }
        row.setStatus(EventCons.STATUS_RETRY);
        row.setRetryCount(row.getRetryCount() == null ? 1 : row.getRetryCount() + 1);
        row.setRetryAt(retryAt);
        row.setLastError(error);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
        return true;
    }

    @Override
    public boolean markDead(Long id, Integer version, String workerId, Date now, String error) {
        log.add(name() + ".markDead[" + id + ",v=" + version + "]");
        EventWait row = store.get(id);
        if (!runningByOwner(row, version, workerId)) {
            return false;
        }
        row.setStatus(EventCons.STATUS_DEAD);
        row.setRetryCount(row.getRetryCount() == null ? 1 : row.getRetryCount() + 1);
        row.setRetryAt(null);
        row.setLastError(error);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
        return true;
    }

    @Override
    public boolean reschedule(Long id, Integer version, String workerId, Date now,
        Date nextTriggerAt, Integer repeatCount) {
        log.add(name() + ".reschedule[" + id + ",v=" + version + "]");
        EventWait row = store.get(id);
        if (!runningByOwner(row, version, workerId)) {
            return false;
        }
        row.setStatus(EventCons.STATUS_WAITING);
        row.setNextTriggerAt(nextTriggerAt);
        row.setRepeatCount(repeatCount);
        row.setRetryAt(null);
        row.setLockOwner(null);
        row.setLockUntil(null);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
        return true;
    }

    @Override
    public boolean releaseClaim(Long id, Integer version, String workerId, String status,
        Date nextTriggerAt, Date retryAt, Date now) {
        log.add(name() + ".releaseClaim[" + id + ",v=" + version + "]");
        EventWait row = store.get(id);
        if (!runningByOwner(row, version, workerId)) {
            return false;
        }
        row.setStatus(status);
        row.setNextTriggerAt(nextTriggerAt);
        row.setRetryAt(retryAt);
        row.setLockOwner(null);
        row.setLockUntil(null);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
        return true;
    }

    @Override
    public boolean cancelClaimed(Long id, Integer version, String workerId, Date now, String error) {
        log.add(name() + ".cancelClaimed[" + id + ",v=" + version + "]");
        EventWait row = store.get(id);
        if (!runningByOwner(row, version, workerId)) {
            return false;
        }
        row.setStatus(EventCons.STATUS_CANCELLED);
        row.setLastError(error);
        row.setLockOwner(null);
        row.setLockUntil(null);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
        return true;
    }

    @Override
    public boolean revive(Long id, Integer version, Date now, Date nextTriggerAt) {
        log.add(name() + ".revive[" + id + ",v=" + version + "]");
        EventWait row = store.get(id);
        if (row == null || row.getVersion() == null || !row.getVersion().equals(version)
            || !EventCons.STATUS_DEAD.equals(row.getStatus())) {
            return false;
        }
        row.setStatus(EventCons.STATUS_WAITING);
        row.setRetryCount(0);
        row.setRetryAt(null);
        row.setNextTriggerAt(nextTriggerAt);
        row.setLockOwner(null);
        row.setLockUntil(null);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
        return true;
    }

    @Override
    public boolean claimMessage(Long id, Integer version, String workerId, Date now, Date lockUntil) {
        log.add(name() + ".claimMessage[" + id + ",v=" + version + "]");
        EventWait row = store.get(id);
        if (row == null || !row.getVersion().equals(version)
            || !(EventCons.STATUS_WAITING.equals(row.getStatus())
            || EventCons.STATUS_RETRY.equals(row.getStatus()))) {
            return false;
        }
        row.setStatus(EventCons.STATUS_RUNNING);
        row.setLockOwner(workerId);
        row.setLockUntil(lockUntil);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
        return true;
    }

    @Override
    public boolean bindPayload(Long id, Integer version, String payload) {
        log.add(name() + ".bindPayload[" + id + ",v=" + version + "]");
        EventWait row = store.get(id);
        if (row == null || !row.getVersion().equals(version)
            || !(EventCons.STATUS_WAITING.equals(row.getStatus())
            || EventCons.STATUS_RETRY.equals(row.getStatus()))) {
            return false;
        }
        row.setPayload(payload);
        return true;
    }

    @Override
    public synchronized boolean claimMessageWithPayload(Long id, Integer version, String workerId,
        Date now, Date lockUntil, String payload) {
        if (!claimMessage(id, version, workerId, now, lockUntil)) {
            return false;
        }
        if (payload != null) {
            store.get(id).setPayload(payload);
        }
        return true;
    }

    @Override
    public int cancelByAttachedTaskId(Long attachedTaskId, Date now, Long excludedWaitId) {
        log.add(name() + ".cancelByAttachedTaskId[" + attachedTaskId + "]");
        return cancelRows(row -> attachedTaskId.equals(row.getAttachedTaskId())
            && !row.getId().equals(excludedWaitId), now);
    }

    @Override
    public int cancelByInstanceId(Long instanceId, Date now, Long excludedWaitId) {
        log.add(name() + ".cancelByInstanceId[" + instanceId + "]");
        return cancelRows(row -> instanceId.equals(row.getInstanceId())
            && !row.getId().equals(excludedWaitId), now);
    }

    @Override
    public int cancelByDefinitionId(Long definitionId, Date now) {
        log.add(name() + ".cancelByDefinitionId[" + definitionId + "]");
        return cancelRows(row -> definitionId.equals(row.getDefinitionId())
            && row.getInstanceId() == null, now);
    }

    /**
     * 范围取消：范围内 WAITING / RETRY / RUNNING 行置 CANCELLED、递增版本号并清空租约；
     * 终态行保持原状态。
     */
    private int cancelRows(java.util.function.Predicate<EventWait> scope, Date now) {
        int cancelled = 0;
        for (EventWait row : store.values()) {
            if (!scope.test(row)
                || !(EventCons.STATUS_WAITING.equals(row.getStatus())
                || EventCons.STATUS_RETRY.equals(row.getStatus())
                || EventCons.STATUS_RUNNING.equals(row.getStatus()))) {
                continue;
            }
            row.setStatus(EventCons.STATUS_CANCELLED);
            row.setLockOwner(null);
            row.setLockUntil(null);
            row.setVersion(row.getVersion() + 1);
            row.setUpdateTime(now);
            cancelled++;
        }
        return cancelled;
    }

    @Override
    public Page<EventWait> selectPageByQuery(EventWaitQuery query, Page<EventWait> page) {
        log.add(name() + ".selectPageByQuery");
        List<EventWait> matched = matchQuery(query);
        if (matched.isEmpty()) {
            return Page.empty();
        }
        int from = Math.max(0, (page.getPageNum() - 1) * page.getPageSize());
        int to = Math.min(matched.size(), from + Math.max(page.getPageSize(), 0));
        Page<EventWait> result = new Page<>(from >= matched.size()
            ? new ArrayList<>() : new ArrayList<>(matched.subList(from, to)), matched.size());
        result.setPageNum(page.getPageNum());
        result.setPageSize(page.getPageSize());
        return result;
    }

    @Override
    public List<EventWait> selectListByQuery(EventWaitQuery query) {
        log.add(name() + ".selectListByQuery");
        return matchQuery(query);
    }

    @Override
    public long selectCountByQuery(EventWaitQuery query) {
        log.add(name() + ".selectCountByQuery");
        return matchQuery(query).size();
    }

    /**
     * 按查询条件过滤行：多值条件取「或」、不同条件之间取「且」，并按白名单字段排序。
     */
    private List<EventWait> matchQuery(EventWaitQuery query) {
        EventWaitQuery condition = query == null ? new EventWaitQuery() : query;
        List<Long> definitionIds = condition.resolveDefinitionIds();
        List<Long> instanceIds = condition.resolveInstanceIds();
        List<String> eventTypes = condition.resolveEventTypes();
        List<EventWait> result = new ArrayList<>();
        for (EventWait row : store.values()) {
            if (!inList(definitionIds, row.getDefinitionId()) || !inList(instanceIds, row.getInstanceId())
                || !inList(eventTypes, row.getEventType())
                || !inList(condition.getTriggerTypes(), row.getTriggerType())
                || !inList(condition.getStatuses(), row.getStatus())) {
                continue;
            }
            if (!eq(condition.getNodeCode(), row.getNodeCode())
                || !eq(condition.getEventName(), row.getEventName())
                || !eq(condition.getAttachedTaskId(), row.getAttachedTaskId())) {
                continue;
            }
            if (!ge(condition.getNextTriggerAtFrom(), row.getNextTriggerAt())
                || !le(condition.getNextTriggerAtTo(), row.getNextTriggerAt())
                || !ge(condition.getCreateTimeFrom(), row.getCreateTime())
                || !le(condition.getCreateTimeTo(), row.getCreateTime())) {
                continue;
            }
            result.add(copyOf(row));
        }
        result.sort(comparatorOf(condition));
        return result;
    }

    /**
     * 白名单列名映射为内存比较器，缺省按下次触发时间升序。
     */
    private Comparator<EventWait> comparatorOf(EventWaitQuery query) {
        String column = query.resolveOrderColumn();
        Comparator<EventWait> comparator;
        if ("id".equals(column)) {
            comparator = Comparator.comparing(EventWait::getId);
        } else if ("retry_at".equals(column)) {
            comparator = Comparator.comparing(EventWait::getRetryAt, Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("create_time".equals(column)) {
            comparator = Comparator.comparing(EventWait::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("update_time".equals(column)) {
            comparator = Comparator.comparing(EventWait::getUpdateTime, Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("status".equals(column)) {
            comparator = Comparator.comparing(EventWait::getStatus, Comparator.nullsLast(Comparator.naturalOrder()));
        } else {
            comparator = Comparator.comparing(EventWait::getNextTriggerAt,
                Comparator.nullsLast(Comparator.naturalOrder()));
        }
        return "DESC".equals(query.resolveOrderDirection()) ? comparator.reversed() : comparator;
    }

    /**
     * 多值条件匹配：条件为空表示不限，取值须落在集合内。
     */
    private boolean inList(List<?> values, Object value) {
        return values == null || values.isEmpty() || values.contains(value);
    }

    /**
     * 等值条件匹配：条件为空表示不限。
     */
    private boolean eq(Object expected, Object actual) {
        return expected == null || expected.equals(actual);
    }

    /**
     * 时间下界匹配（含）：条件为空表示不限，行值为空视为不匹配。
     */
    private boolean ge(Date from, Date value) {
        return from == null || (value != null && !value.before(from));
    }

    /**
     * 时间上界匹配（含）：条件为空表示不限，行值为空视为不匹配。
     */
    private boolean le(Date to, Date value) {
        return to == null || (value != null && !value.after(to));
    }

    /**
     * 判断 RUNNING 行是否由指定持有者按指定版本持有。
     */
    private boolean runningByOwner(EventWait row, Integer version, String workerId) {
        return row != null && row.getVersion().equals(version)
            && EventCons.STATUS_RUNNING.equals(row.getStatus())
            && workerId.equals(row.getLockOwner());
    }

    /**
     * 游标判断：(dueAt, id) 严格大于游标。
     */
    private boolean afterCursor(Date dueAt, Long id, Date cursorAt, Long cursorId) {
        if (cursorAt == null || cursorId == null) {
            return true;
        }
        int cmp = dueAt.compareTo(cursorAt);
        return cmp > 0 || (cmp == 0 && id > cursorId);
    }

    /**
     * 按 (next_trigger_at, id) 升序返回行快照列表。
     */
    private List<EventWait> sortedByDue() {
        List<EventWait> rows = new ArrayList<>(store.values());
        rows.sort(Comparator
            .comparing(EventWait::getNextTriggerAt,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(EventWait::getId));
        return rows;
    }

    /**
     * 按 (retry_at, id) 升序返回行快照列表。
     */
    private List<EventWait> sortedByRetry() {
        List<EventWait> rows = new ArrayList<>(store.values());
        rows.sort(Comparator
            .comparing(EventWait::getRetryAt,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(EventWait::getId));
        return rows;
    }
}
