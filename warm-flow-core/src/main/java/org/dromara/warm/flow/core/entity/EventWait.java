package org.dromara.warm.flow.core.entity;

import java.util.Date;

/**
 * 事件等待记录对象 flow_event_wait。
 * <p>
 * 承载所有需要“等待”的事件：定时启动、中间定时捕获、中间消息捕获、定时边界、消息边界。
 * 状态独立于 {@link org.dromara.warm.flow.core.enums.FlowStatus}，由事件服务单独维护。
 *
 * @author may
 */
public interface EventWait extends RootEntity {

    @Override
    Long getId();

    @Override
    EventWait setId(Long id);

    @Override
    Date getCreateTime();

    @Override
    EventWait setCreateTime(Date createTime);

    @Override
    Date getUpdateTime();

    @Override
    EventWait setUpdateTime(Date updateTime);

    @Override
    String getTenantId();

    @Override
    EventWait setTenantId(String tenantId);

    @Override
    String getDelFlag();

    @Override
    EventWait setDelFlag(String delFlag);

    /**
     * 获取流程定义 ID；定时启动事件必须有值。
     *
     * @return 流程定义 ID
     */
    Long getDefinitionId();

    /**
     * 设置流程定义 ID。
     *
     * @param definitionId 流程定义 ID
     * @return 当前记录
     */
    EventWait setDefinitionId(Long definitionId);

    /**
     * 获取流程实例 ID；实例级等待事件必须有值，定义级启动事件为空。
     *
     * @return 流程实例 ID
     */
    Long getInstanceId();

    /**
     * 设置流程实例 ID。
     *
     * @param instanceId 流程实例 ID
     * @return 当前记录
     */
    EventWait setInstanceId(Long instanceId);

    /**
     * 获取事件节点编码；节点定位沿用 {@code definitionId + nodeCode}。
     *
     * @return 事件节点编码
     */
    String getNodeCode();

    /**
     * 设置事件节点编码。
     *
     * @param nodeCode 事件节点编码
     * @return 当前记录
     */
    EventWait setNodeCode(String nodeCode);

    /**
     * 获取事件类型。
     *
     * @return 事件类型
     * @see org.dromara.warm.flow.core.enums.EventType
     */
    String getEventType();

    /**
     * 设置事件类型。
     *
     * @param eventType 事件类型
     * @return 当前记录
     */
    EventWait setEventType(String eventType);

    /**
     * 获取消息事件名称。
     *
     * @return 事件名称
     */
    String getEventName();

    /**
     * 设置消息事件名称。
     *
     * @param eventName 事件名称
     * @return 当前记录
     */
    EventWait setEventName(String eventName);

    /**
     * 获取边界事件关联的待办任务 ID。
     *
     * @return 待办任务 ID
     */
    Long getAttachedTaskId();

    /**
     * 设置边界事件关联的待办任务 ID。
     *
     * @param attachedTaskId 待办任务 ID
     * @return 当前记录
     */
    EventWait setAttachedTaskId(Long attachedTaskId);

    /**
     * 获取定时触发策略类型（AFTER / FIXED / POINT_IN_TIME）。
     *
     * @return 触发策略类型
     */
    String getTriggerType();

    /**
     * 设置定时触发策略类型。
     *
     * @param triggerType 触发策略类型
     * @return 当前记录
     */
    EventWait setTriggerType(String triggerType);

    /**
     * 获取触发策略原始配置（间隔或时间点表达式）。
     *
     * @return 触发策略原始配置
     */
    String getTriggerInterval();

    /**
     * 设置触发策略原始配置。
     *
     * @param triggerInterval 触发策略原始配置
     * @return 当前记录
     */
    EventWait setTriggerInterval(String triggerInterval);

    /**
     * 获取下次触发时间。
     *
     * @return 下次触发时间
     */
    Date getNextTriggerAt();

    /**
     * 设置下次触发时间。
     *
     * @param nextTriggerAt 下次触发时间
     * @return 当前记录
     */
    EventWait setNextTriggerAt(Date nextTriggerAt);

    /**
     * 获取定时触发使用的时区。
     *
     * @return 时区
     */
    String getTimezone();

    /**
     * 设置定时触发使用的时区。
     *
     * @param timezone 时区
     * @return 当前记录
     */
    EventWait setTimezone(String timezone);

    /**
     * 获取已触发次数。
     *
     * @return 已触发次数
     */
    Integer getRepeatCount();

    /**
     * 设置已触发次数。
     *
     * @param repeatCount 已触发次数
     * @return 当前记录
     */
    EventWait setRepeatCount(Integer repeatCount);

    /**
     * 获取最大触发次数。
     *
     * @return 最大触发次数
     */
    Integer getMaxRepeatCount();

    /**
     * 设置最大触发次数。
     *
     * @param maxRepeatCount 最大触发次数
     * @return 当前记录
     */
    EventWait setMaxRepeatCount(Integer maxRepeatCount);

    /**
     * 获取等待状态（WAITING / RUNNING / CANCELLED / RETRY / DEAD；
     * SUCCESS 仅作执行结果语义，不作为持久化状态写入）。
     *
     * @return 等待状态
     */
    String getStatus();

    /**
     * 设置等待状态。
     *
     * @param status 等待状态
     * @return 当前记录
     */
    EventWait setStatus(String status);

    /**
     * 获取已重试次数。
     *
     * @return 已重试次数
     */
    Integer getRetryCount();

    /**
     * 设置已重试次数。
     *
     * @param retryCount 已重试次数
     * @return 当前记录
     */
    EventWait setRetryCount(Integer retryCount);

    /**
     * 获取最大重试次数。
     *
     * @return 最大重试次数
     */
    Integer getMaxRetryCount();

    /**
     * 设置最大重试次数。
     *
     * @param maxRetryCount 最大重试次数
     * @return 当前记录
     */
    EventWait setMaxRetryCount(Integer maxRetryCount);

    /**
     * 获取下次重试时间。
     *
     * @return 下次重试时间
     */
    Date getRetryAt();

    /**
     * 设置下次重试时间。
     *
     * @param retryAt 下次重试时间
     * @return 当前记录
     */
    EventWait setRetryAt(Date retryAt);

    /**
     * 获取抢占持有者标识。
     *
     * @return 抢占持有者标识
     */
    String getLockOwner();

    /**
     * 设置抢占持有者标识。
     *
     * @param lockOwner 抢占持有者标识
     * @return 当前记录
     */
    EventWait setLockOwner(String lockOwner);

    /**
     * 获取抢占租约到期时间。
     *
     * @return 抢占租约到期时间
     */
    Date getLockUntil();

    /**
     * 设置抢占租约到期时间。
     *
     * @param lockUntil 抢占租约到期时间
     * @return 当前记录
     */
    EventWait setLockUntil(Date lockUntil);

    /**
     * 获取乐观锁版本号。
     *
     * @return 版本号
     */
    Integer getVersion();

    /**
     * 设置乐观锁版本号。
     *
     * @param version 版本号
     * @return 当前记录
     */
    EventWait setVersion(Integer version);

    /**
     * 获取消息事件接收的业务载荷。
     *
     * @return 业务载荷
     */
    String getPayload();

    /**
     * 设置消息事件接收的业务载荷。
     *
     * @param payload 业务载荷
     * @return 当前记录
     */
    EventWait setPayload(String payload);

    /**
     * 获取最近一次失败原因摘要。
     *
     * @return 失败原因摘要
     */
    String getLastError();

    /**
     * 设置最近一次失败原因摘要。
     *
     * @param lastError 失败原因摘要
     * @return 当前记录
     */
    EventWait setLastError(String lastError);
}
