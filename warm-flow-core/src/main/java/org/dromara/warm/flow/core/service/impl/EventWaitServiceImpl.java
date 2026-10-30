package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.EventWaitQuery;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.orm.dao.FlowEventWaitDao;
import org.dromara.warm.flow.core.orm.service.impl.WarmServiceImpl;
import org.dromara.warm.flow.core.service.EventWaitService;
import org.dromara.warm.flow.core.service.event.EventConfig;
import org.dromara.warm.flow.core.timer.EventWaitProcessor;
import org.dromara.warm.flow.core.timer.EventTaskHandler;
import org.dromara.warm.flow.core.timer.TimerContext;
import org.dromara.warm.flow.core.timer.TimerScheduleStrategies;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.timer.TimerWorkerId;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.utils.page.Page;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 事件等待记录服务实现。
 * <p>
 * 基于通用 {@link FlowEventWaitDao} 提供等待记录的生命周期方法。
 *
 * @author may
 */
public class EventWaitServiceImpl extends WarmServiceImpl<FlowEventWaitDao<EventWait>, EventWait>
    implements EventWaitService {

    private static final Logger log = LoggerFactory.getLogger(EventWaitServiceImpl.class);

    /**
     * 注入事件等待记录 DAO。
     *
     * @param warmDao 事件等待记录数据访问对象
     * @return 当前服务实例
     */
    @Override
    public EventWaitService setDao(FlowEventWaitDao<EventWait> warmDao) {
        this.warmDao = warmDao;
        return this;
    }

    /**
     * 按待办任务 ID 集合批量取消等待记录。
     *
     * @param taskIds 待办任务 ID 集合
     */
    @Override
    public void cancelByTaskIds(List<Long> taskIds) {
        if (CollUtil.isEmpty(taskIds)) {
            return;
        }
        for (Long taskId : taskIds) {
            cancelByAttachedTaskId(taskId);
        }
    }

    /**
     * 按流程实例 ID 集合批量取消等待记录：条件更新置 {@code CANCELLED}、递增版本号并清空租约，
     * 覆盖 {@code WAITING / RETRY / RUNNING}（在途执行器因版本失配无法提交结果）；
     * {@code CANCELLED / DEAD} 等终态保持原状态。
     *
     * @param instanceIds 流程实例 ID 集合
     */
    @Override
    public void cancelByInstanceIds(List<Long> instanceIds) {
        if (CollUtil.isEmpty(instanceIds)) {
            return;
        }
        Date now = new Date();
        for (Long instanceId : instanceIds) {
            warmDao.cancelByInstanceId(instanceId, now, EventWaitProcessor.currentWaitId());
        }
    }

    /**
     * 按流程定义 ID 取消定义级等待记录（如定时启动事件）：条件更新置 {@code CANCELLED}、
     * 递增版本号并清空租约，仅命中定义级记录（{@code instanceId} 为空），
     * 实例级等待由实例挂起 / 撤回 / 终止等操作单独清理。
     *
     * @param definitionId 流程定义 ID
     */
    @Override
    public void cancelByDefinitionId(Long definitionId) {
        if (ObjectUtil.isNull(definitionId)) {
            return;
        }
        warmDao.cancelByDefinitionId(definitionId, new Date());
    }

    /**
     * 查询某实例下处于等待中的事件记录。
     *
     * @param instanceId 流程实例 ID
     * @return 等待中的事件记录列表
     */
    @Override
    public List<EventWait> getWaitingByInstanceId(Long instanceId) {
        if (ObjectUtil.isNull(instanceId)) {
            return new ArrayList<>();
        }
        return list(FlowEngine.newEventWait().setInstanceId(instanceId)).stream()
            .filter(wait -> EventCons.STATUS_WAITING.equals(wait.getStatus())
                || EventCons.STATUS_RETRY.equals(wait.getStatus()))
            .collect(Collectors.toList());
    }

    /**
     * 按边界事件关联的待办任务 ID 取消等待记录：条件更新置 {@code CANCELLED}、递增版本号
     * 并清空租约，覆盖 {@code WAITING / RETRY / RUNNING}（在途执行器因版本失配无法提交结果）；
     * {@code CANCELLED / DEAD} 等终态保持原状态。
     *
     * @param taskId 待办任务 ID
     */
    @Override
    public void cancelByAttachedTaskId(Long taskId) {
        if (ObjectUtil.isNull(taskId)) {
            return;
        }
        warmDao.cancelByAttachedTaskId(taskId, new Date(), EventWaitProcessor.currentWaitId());
    }

    /**
     * 取消本节点持有的一条执行中等待记录：条件更新校验 {@code id + version + RUNNING +
     * lock_owner}，未命中说明记录已被租约恢复流程或其它执行器接管。
     *
     * @param id       记录主键
     * @param version  抢占后的版本号
     * @param workerId 工作节点标识
     * @param error    取消原因（写入 {@code last_error}）
     * @return 取消成功返回 {@code true}
     */
    @Override
    public boolean cancelClaimed(Long id, Integer version, String workerId, String error) {
        return warmDao.cancelClaimed(id, version, workerId, new Date(), error);
    }

    /**
     * 复活一条已死（{@code DEAD}）的等待记录：条件更新置回 {@code WAITING}、重试计数归零，
     * 定时类事件按触发器重新计算下次触发时间；复活前校验记录所属实例 / 附着待办仍然有效。
     *
     * @param id 等待记录主键
     * @return 复活成功返回 {@code true}；主键为空返回 {@code false}
     */
    @Override
    public boolean revive(Long id) {
        if (ObjectUtil.isNull(id)) {
            return false;
        }
        EventWait wait = getById(id);
        AssertUtil.isNull(wait, ExceptionCons.NOT_FOUND_EVENT_WAIT);
        AssertUtil.isTrue(!EventCons.STATUS_DEAD.equals(wait.getStatus()),
            ExceptionCons.EVENT_WAIT_REVIVE_NOT_DEAD);
        checkRevivable(wait);
        Date now = new Date();
        Date nextTriggerAt = reviveNextTriggerAt(wait, now);
        boolean revived = warmDao.revive(wait.getId(), wait.getVersion(), now, nextTriggerAt);
        AssertUtil.isTrue(!revived, ExceptionCons.EVENT_WAIT_REVIVE_CONFLICT);
        log.info("事件等待记录已复活 id={} eventType={} nodeCode={} nextTriggerAt={}"
            , wait.getId(), wait.getEventType(), wait.getNodeCode(), nextTriggerAt);
        return true;
    }

    /**
     * 复活前的有效性校验：定时启动交给重新发布 / 激活处理，实例与边界事件要求目标仍在。
     *
     * @param wait 待复活的等待记录
     */
    private void checkRevivable(EventWait wait) {
        AssertUtil.isTrue(EventType.TIMER_START.getValue().equals(wait.getEventType()),
            ExceptionCons.EVENT_WAIT_REVIVE_TIMER_START);
        if (ObjectUtil.isNotNull(wait.getAttachedTaskId())) {
            AssertUtil.isNull(FlowEngine.taskService().getById(wait.getAttachedTaskId()),
                ExceptionCons.EVENT_WAIT_REVIVE_TASK_MISSING);
        }
        Instance instance = ObjectUtil.isNull(wait.getInstanceId()) ? null
            : FlowEngine.insService().getById(wait.getInstanceId());
        AssertUtil.isTrue(instance == null || !ActivityStatus.isActivity(instance.getActivityStatus()),
            ExceptionCons.EVENT_WAIT_REVIVE_INSTANCE_UNAVAILABLE);
        AssertUtil.isTrue(EventRuntimeHandler.alreadyPassed(instance, wait.getNodeCode()),
            ExceptionCons.EVENT_WAIT_REVIVE_NODE_PASSED);
    }

    /**
     * 计算复活后的下次触发时间：定时类事件从当前时间重算，消息 / 错误类事件不参与扫描，保持为空。
     *
     * @param wait 待复活的等待记录
     * @param now  当前时间
     * @return 下次触发时间；非定时类事件返回 {@code null}
     */
    private Date reviveNextTriggerAt(EventWait wait, Date now) {
        String triggerType = wait.getTriggerType();
        boolean timerTrigger = EventConfig.TIMER_AFTER.equals(triggerType)
            || EventConfig.TIMER_FIXED.equals(triggerType)
            || EventConfig.TIMER_POINT_IN_TIME.equals(triggerType);
        if (!timerTrigger) {
            return null;
        }
        return TimerScheduleStrategies.nextTriggerAt(triggerType, new TimerContext()
            .setPreviousTriggerAt(null)
            .setCurrentTime(now)
            .setTriggerInterval(wait.getTriggerInterval())
            .setTimezone(wait.getTimezone())
            .setRepeatCount(0));
    }

    /**
     * 按查询条件分页查询等待记录（定时任务）。
     *
     * @param query 查询条件，可为 {@code null}
     * @return 回填总数和当前页数据的查询结果
     */
    @Override
    public Page<EventWait> pageEventWaits(EventWaitQuery query) {
        EventWaitQuery resolved = resolveQuery(query);
        resolved.checkPage();
        if (resolvedEmpty(resolved)) {
            return Page.empty();
        }
        return warmDao.selectPageByQuery(resolved
            , new Page<>(resolved.getPageNum(), resolved.getPageSize()));
    }

    /**
     * 按查询条件查询等待记录列表。
     *
     * @param query 查询条件，可为 {@code null}
     * @return 匹配的等待记录列表
     */
    @Override
    public List<EventWait> listEventWaits(EventWaitQuery query) {
        EventWaitQuery resolved = resolveQuery(query);
        if (resolvedEmpty(resolved)) {
            return new ArrayList<>();
        }
        return warmDao.selectListByQuery(resolved);
    }

    /**
     * 按查询条件统计等待记录数量。
     *
     * @param query 查询条件，可为 {@code null}
     * @return 匹配记录数
     */
    @Override
    public long countEventWaits(EventWaitQuery query) {
        EventWaitQuery resolved = resolveQuery(query);
        return resolvedEmpty(resolved) ? 0L : warmDao.selectCountByQuery(resolved);
    }

    /**
     * 规范化查询条件：校验互斥条件与排序合法性，并把 businessId / flowCode 解析为主键集合。
     * <p>
     * 排序字段与方向在查询前完成白名单校验，非法值直接报错，不把调用方字符串带进 SQL。
     * 解析基于查询条件副本进行，调用方入参不被改写，同一条件可重复复用。
     *
     * @param query 调用方查询条件，可为 {@code null}
     * @return 可直接执行的查询条件
     */
    private EventWaitQuery resolveQuery(EventWaitQuery query) {
        EventWaitQuery source = ObjectUtil.isNull(query) ? new EventWaitQuery() : query;
        AssertUtil.isTrue(StringUtils.isNotEmpty(source.getBusinessId())
                && (ObjectUtil.isNotNull(source.getInstanceId()) || CollUtil.isNotEmpty(source.getInstanceIds()))
            , "businessId 与 instanceId / instanceIds 不能同时指定");
        AssertUtil.isTrue(StringUtils.isNotEmpty(source.getFlowCode())
                && (ObjectUtil.isNotNull(source.getDefinitionId()) || CollUtil.isNotEmpty(source.getDefinitionIds()))
            , "flowCode 与 definitionId / definitionIds 不能同时指定");
        source.resolveOrderColumn();
        source.resolveOrderDirection();
        if (StringUtils.isEmpty(source.getBusinessId()) && StringUtils.isEmpty(source.getFlowCode())) {
            return source;
        }
        EventWaitQuery resolved = source.copy();
        if (StringUtils.isNotEmpty(resolved.getBusinessId())) {
            resolved.setInstanceIds(StreamUtils.toList(FlowEngine.insService()
                    .list(FlowEngine.newIns().setBusinessId(resolved.getBusinessId()))
                , Instance::getId));
        }
        if (StringUtils.isNotEmpty(resolved.getFlowCode())) {
            resolved.setDefinitionIds(StreamUtils.toList(FlowEngine.defService()
                .getByFlowCode(resolved.getFlowCode()), Definition::getId));
        }
        return resolved;
    }

    /**
     * 判断业务主键 / 流程编码解析后是否必然无匹配：解析不到主键时按空结果返回，而不是放弃该条件。
     *
     * @param query 已规范化的查询条件
     * @return 必然无匹配返回 {@code true}
     */
    private boolean resolvedEmpty(EventWaitQuery query) {
        return StringUtils.isNotEmpty(query.getBusinessId()) && CollUtil.isEmpty(query.getInstanceIds())
            || StringUtils.isNotEmpty(query.getFlowCode()) && CollUtil.isEmpty(query.getDefinitionIds());
    }

    /**
     * 匹配错误等待记录：任务级匹配附着该任务的 {@code ERROR_BOUNDARY} 等待，
     * 实例级匹配该实例的 {@code ERROR_CATCH} 等待；精确错误码优先于通配（错误码为空表示通配）。
     *
     * @param instanceId    流程实例 ID
     * @param attachedTaskId 待办任务 ID
     * @param errorCode      错误码
     * @return 最佳匹配的等待记录；无匹配返回 {@code null}
     */
    @Override
    public EventWait matchErrorWait(Long instanceId, Long attachedTaskId, String errorCode) {
        List<EventWait> waits;
        String expectedType;
        if (ObjectUtil.isNotNull(attachedTaskId)) {
            expectedType = EventType.ERROR_BOUNDARY.getValue();
            waits = list(FlowEngine.newEventWait().setAttachedTaskId(attachedTaskId));
        } else {
            expectedType = EventType.ERROR_CATCH.getValue();
            waits = list(FlowEngine.newEventWait().setInstanceId(instanceId));
        }
        EventWait wildcard = null;
        for (EventWait wait : waits) {
            boolean matched = expectedType.equals(wait.getEventType())
                && (EventCons.STATUS_WAITING.equals(wait.getStatus())
                || EventCons.STATUS_RETRY.equals(wait.getStatus()));
            if (!matched) {
                continue;
            }
            if (StringUtils.isNotEmpty(wait.getEventName()) && wait.getEventName().equals(errorCode)) {
                return wait;
            }
            if (StringUtils.isEmpty(wait.getEventName()) && wildcard == null) {
                wildcard = wait;
            }
        }
        return wildcard;
    }

    /**
     * 触发已匹配的错误等待记录：条件更新抢占后同步处理，完成 / 失败语义与定时路径一致。
     *
     * @param target       匹配到的错误等待记录
     * @param errorCode    错误码，仅用于日志
     * @param errorMessage 错误描述，仅用于日志
     * @return 触发成功返回 {@code true}
     */
    @Override
    public boolean triggerErrorWait(EventWait target, String errorCode, String errorMessage) {
        checkTriggerTransaction();
        WarmFlow config = FlowEngine.getFlowConfig();
        long leaseMillis = config != null && config.getTimerLeaseSeconds() != null
            ? config.getTimerLeaseSeconds() * 1000L : 60_000L;
        Date now = new Date();
        boolean claimed = claimMessage(target.getId(), target.getVersion(), TimerWorkerId.get()
            , now, new Date(now.getTime() + leaseMillis));
        if (!claimed) {
            throw new FlowException(ExceptionCons.EVENT_ERROR_ALREADY_PROCESSED);
        }
        if (StringUtils.isNotEmpty(errorMessage)) {
            log.info("流程错误已触发路由 instanceId={} nodeCode={} errorCode={} message={}"
                , target.getInstanceId(), target.getNodeCode(), errorCode, errorMessage);
        }
        processClaimedMessage(target);
        return true;
    }

    /**
     * 消息记录抢占：条件更新为 RUNNING 并写租约。
     *
     * @param id        记录主键
     * @param version   抢占前版本号
     * @param workerId  工作节点标识
     * @param now       当前时间
     * @param lockUntil 租约到期时间
     * @return 抢占成功返回 {@code true}
     */
    @Override
    public boolean claimMessage(Long id, Integer version, String workerId, Date now
        , Date lockUntil) {
        checkTriggerTransaction();
        return warmDao.claimMessage(id, version, workerId, now, lockUntil);
    }

    @Override
    public boolean claimMessageWithPayload(Long id, Integer version, String workerId, Date now,
        Date lockUntil, String payload) {
        checkTriggerTransaction();
        return warmDao.claimMessageWithPayload(id, version, workerId, now, lockUntil, payload);
    }

    private void checkTriggerTransaction() {
        EventTaskHandler handler = FrameInvoker.getBean(EventTaskHandler.class);
        if (handler == null) {
            throw new IllegalStateException("未注册 EventTaskHandler，无法触发消息或错误事件");
        }
        handler.checkTriggerTransaction();
    }

    /**
     * 绑定消息载荷到等待记录。
     *
     * @param id      记录主键
     * @param version 当前版本号
     * @param payload 消息载荷 JSON
     * @return 绑定成功返回 {@code true}
     */
    @Override
    public boolean bindPayload(Long id, Integer version, String payload) {
        return warmDao.bindPayload(id, version, payload);
    }

    /**
     * 同步处理已抢占的消息等待记录，完成 / 失败语义与定时路径共用同一实现。
     *
     * @param wait 已抢占的等待记录
     */
    @Override
    public void processClaimedMessage(EventWait wait) {
        processClaimedMessage(wait, null);
    }

    /**
     * 同步处理已抢占的消息等待记录，并透传调用方上下文（操作人 / 租户）。
     *
     * @param wait    已抢占的等待记录
     * @param context 调用方上下文；定时调度路径为 {@code null}
     */
    @Override
    public void processClaimedMessage(EventWait wait, WorkflowContext context) {
        new EventWaitProcessor(warmDao, FlowEngine.getFlowConfig())
            .process(wait, wait.getStatus(), wait.getNextTriggerAt(), wait.getRetryAt(), context);
    }
}
