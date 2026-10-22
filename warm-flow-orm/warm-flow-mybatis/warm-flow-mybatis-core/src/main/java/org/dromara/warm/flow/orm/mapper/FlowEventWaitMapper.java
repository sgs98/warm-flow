package org.dromara.warm.flow.orm.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.warm.flow.core.dto.EventWaitQuery;
import org.dromara.warm.flow.core.utils.page.Page;
import org.dromara.warm.flow.orm.entity.FlowEventWait;

import java.util.Date;
import java.util.List;

/**
 * 事件等待记录 Mapper。
 * <p>
 * 除通用 CRUD 外，提供定时器底座的到期扫描、CAS 抢占、租约续期与恢复、
 * 重试与终态、重排期等条件更新语句；SQL 实现见 FlowEventWaitMapper.xml。
 *
 * @author may
 */
public interface FlowEventWaitMapper extends WarmMapper<FlowEventWait> {

    /**
     * 分页扫描到期的等待记录（WAITING，按 (next_trigger_at, id) 游标升序）。
     *
     * @param now      当前时间
     * @param cursorAt 上页到期时间游标，可为 {@code null}
     * @param cursorId 上页主键游标，可为 {@code null}
     * @param limit    单页条数
     * @param delFlag  未删除值（逻辑删除关闭时为 {@code null}，不拼条件）
     * @return 到期的等待记录列表
     */
    List<FlowEventWait> selectWaitingDue(@Param("now") Date now, @Param("cursorAt") Date cursorAt,
        @Param("cursorId") Long cursorId, @Param("limit") int limit, @Param("delFlag") String delFlag,
        @Param("dataSourceType") String dataSourceType);

    /**
     * 分页扫描到期的重试记录（RETRY，按 (retry_at, id) 游标升序）。
     *
     * @param now      当前时间
     * @param cursorAt 上页重试时间游标，可为 {@code null}
     * @param cursorId 上页主键游标，可为 {@code null}
     * @param limit    单页条数
     * @param delFlag  未删除值（逻辑删除关闭时为 {@code null}，不拼条件）
     * @return 到期的重试记录列表
     */
    List<FlowEventWait> selectRetryDue(@Param("now") Date now, @Param("cursorAt") Date cursorAt,
        @Param("cursorId") Long cursorId, @Param("limit") int limit, @Param("delFlag") String delFlag,
        @Param("dataSourceType") String dataSourceType);

    /**
     * CAS 抢占：WAITING / RETRY 置 RUNNING 并写租约，校验版本号与对应到期时间字段。
     *
     * @param id        记录主键
     * @param version   抢占前版本号
     * @param workerId  工作节点标识
     * @param now       当前时间
     * @param lockUntil 租约到期时间
     * @param delFlag   未删除值
     * @return 受影响行数（1 表示抢占成功）
     */
    int claim(@Param("id") Long id, @Param("version") Integer version,
        @Param("workerId") String workerId, @Param("now") Date now,
        @Param("lockUntil") Date lockUntil, @Param("delFlag") String delFlag);

    /**
     * 续租：仅当前持有者按 RUNNING + lock_owner + version 续期。
     *
     * @param id        记录主键
     * @param version   当前版本号
     * @param workerId  工作节点标识
     * @param now       当前时间
     * @param lockUntil 新租约到期时间
     * @param delFlag   未删除值
     * @return 受影响行数
     */
    int renewLease(@Param("id") Long id, @Param("version") Integer version,
        @Param("workerId") String workerId, @Param("now") Date now,
        @Param("lockUntil") Date lockUntil, @Param("delFlag") String delFlag);

    /**
     * 分页查询租约过期的执行中记录。
     *
     * @param now     当前时间
     * @param limit   单页条数
     * @param delFlag 未删除值
     * @return 租约过期的记录列表
     */
    List<FlowEventWait> selectExpired(@Param("now") Date now, @Param("limit") int limit,
        @Param("delFlag") String delFlag, @Param("dataSourceType") String dataSourceType);

    /**
     * 按查询条件查询等待记录（定时任务），条件见 {@code query_condition}。
     *
     * @param entity         租户与逻辑删除条件实体
     * @param query          查询条件，多值字段由调用方解析后落入集合字段
     * @param order          排序子句（白名单列名 + 方向），可为 {@code null}
     * @param page           分页参数，可为 {@code null} 表示不分页
     * @param dataSourceType 数据源类型
     * @return 匹配的等待记录列表
     */
    List<FlowEventWait> selectListByQuery(@Param("entity") FlowEventWait entity,
        @Param("query") EventWaitQuery query, @Param("order") String order,
        @Param("page") Page<FlowEventWait> page, @Param("dataSourceType") String dataSourceType);

    /**
     * 按查询条件统计等待记录数量，条件与 {@link #selectListByQuery} 一致。
     *
     * @param entity 租户与逻辑删除条件实体
     * @param query  查询条件
     * @return 匹配记录数
     */
    long selectCountByQuery(@Param("entity") FlowEventWait entity, @Param("query") EventWaitQuery query);

    /**
     * 恢复租约过期的记录：递增 retry_count 与 version、清空旧租约，按目标状态落库。
     *
     * @param id         记录主键
     * @param version    当前版本号
     * @param retryCount 递增后的重试次数
     * @param status     恢复后的状态（RETRY / DEAD）
     * @param retryAt    下次重试时间（DEAD 时为 {@code null}）
     * @param now        当前时间
     * @param error      失败原因摘要
     * @param delFlag    未删除值
     * @return 受影响行数
     */
    int recoverExpired(@Param("id") Long id, @Param("version") Integer version,
        @Param("retryCount") Integer retryCount, @Param("status") String status,
        @Param("retryAt") Date retryAt, @Param("now") Date now, @Param("error") String error,
        @Param("delFlag") String delFlag);

    /**
     * 一次性事件执行成功后删除记录（校验 RUNNING + lock_owner + version）。
     *
     * @param id       记录主键
     * @param version  当前版本号
     * @param workerId 工作节点标识
     * @param delFlag  未删除值
     * @return 受影响行数
     */
    int deleteCompleted(@Param("id") Long id, @Param("version") Integer version,
        @Param("workerId") String workerId, @Param("delFlag") String delFlag);

    /**
     * 执行失败转重试。
     *
     * @param id       记录主键
     * @param version  当前版本号
     * @param workerId 工作节点标识
     * @param now      当前时间
     * @param retryAt  下次重试时间
     * @param error    失败原因摘要
     * @param delFlag  未删除值
     * @return 受影响行数
     */
    int markRetry(@Param("id") Long id, @Param("version") Integer version,
        @Param("workerId") String workerId, @Param("now") Date now,
        @Param("retryAt") Date retryAt, @Param("error") String error,
        @Param("delFlag") String delFlag);

    /**
     * 执行失败超上限转终态 DEAD，清空重试时间并保留失败原因。
     *
     * @param id       记录主键
     * @param version  当前版本号
     * @param workerId 工作节点标识
     * @param now      当前时间
     * @param error    失败原因摘要
     * @param delFlag  未删除值
     * @return 受影响行数
     */
    int markDead(@Param("id") Long id, @Param("version") Integer version,
        @Param("workerId") String workerId, @Param("now") Date now,
        @Param("error") String error, @Param("delFlag") String delFlag);

    /**
     * 重复事件执行成功后重排期：更新下一次触发时间、递增触发次数并置回 WAITING。
     *
     * @param id            记录主键
     * @param version       当前版本号
     * @param workerId      工作节点标识
     * @param now           当前时间
     * @param nextTriggerAt 下一次触发时间
     * @param repeatCount   递增后的已触发次数
     * @param delFlag       未删除值
     * @return 受影响行数
     */
    int reschedule(@Param("id") Long id, @Param("version") Integer version,
        @Param("workerId") String workerId, @Param("now") Date now,
        @Param("nextTriggerAt") Date nextTriggerAt, @Param("repeatCount") Integer repeatCount,
        @Param("delFlag") String delFlag);

    /**
     * 释放抢占：按抢占前状态与时间字段恢复，递增版本号并清空租约。
     *
     * @param id            记录主键
     * @param version       当前版本号
     * @param workerId      工作节点标识
     * @param status        抢占前状态（WAITING / RETRY）
     * @param nextTriggerAt 抢占前的下次触发时间
     * @param retryAt       抢占前的下次重试时间
     * @param now           当前时间
     * @param delFlag       未删除值
     * @return 受影响行数
     */
    int releaseClaim(@Param("id") Long id, @Param("version") Integer version,
        @Param("workerId") String workerId, @Param("status") String status,
        @Param("nextTriggerAt") Date nextTriggerAt, @Param("retryAt") Date retryAt,
        @Param("now") Date now, @Param("delFlag") String delFlag);

    /**
     * 取消本节点持有的一条执行中记录：RUNNING + lock_owner + version 匹配时置 CANCELLED、
     * 写入取消原因、清空租约并递增版本号；未命中不更新。
     *
     * @param id       记录主键
     * @param version  抢占后的版本号
     * @param workerId 工作节点标识
     * @param now      当前时间
     * @param error    取消原因（写入 last_error）
     * @param delFlag  未删除值
     * @return 受影响行数
     */
    int cancelClaimed(@Param("id") Long id, @Param("version") Integer version,
        @Param("workerId") String workerId, @Param("now") Date now, @Param("error") String error,
        @Param("delFlag") String delFlag);

    /**
     * 消息记录抢占：WAITING / RETRY 置为 RUNNING 并写入租约，不校验到期时间字段。
     *
     * @param id       记录主键
     * @param version  抢占前版本号
     * @param workerId 工作节点标识
     * @param now      当前时间
     * @param delFlag  未删除值
     * @return 受影响行数
     */
    int claimMessage(@Param("id") Long id, @Param("version") Integer version,
        @Param("workerId") String workerId, @Param("now") Date now,
        @Param("lockUntil") Date lockUntil, @Param("delFlag") String delFlag);

    int claimMessageWithPayload(@Param("id") Long id, @Param("version") Integer version,
        @Param("workerId") String workerId, @Param("now") Date now,
        @Param("lockUntil") Date lockUntil, @Param("payload") String payload,
        @Param("delFlag") String delFlag);

    /**
     * 绑定消息载荷：WAITING / RETRY 条件下按 id + version 写入 payload，不递增版本号。
     *
     * @param id      记录主键
     * @param version 当前版本号
     * @param payload 消息载荷 JSON
     * @param delFlag 未删除值
     * @return 受影响行数
     */
    int bindPayload(@Param("id") Long id, @Param("version") Integer version,
        @Param("payload") String payload, @Param("delFlag") String delFlag);

    /**
     * 按边界事件附着的待办任务范围取消：WAITING / RETRY / RUNNING 置 CANCELLED，
     * 递增版本号并清空租约；终态记录不受影响。
     *
     * @param attachedTaskId 待办任务 ID
     * @param now            当前时间
     * @param delFlag        未删除值
     * @return 取消的记录数
     */
    int cancelByAttachedTaskId(@Param("attachedTaskId") Long attachedTaskId, @Param("now") Date now,
        @Param("excludedWaitId") Long excludedWaitId,
        @Param("delFlag") String delFlag);

    /**
     * 按流程实例范围取消：WAITING / RETRY / RUNNING 置 CANCELLED，
     * 递增版本号并清空租约；终态记录不受影响。
     *
     * @param instanceId 流程实例 ID
     * @param now        当前时间
     * @param delFlag    未删除值
     * @return 取消的记录数
     */
    int cancelByInstanceId(@Param("instanceId") Long instanceId, @Param("now") Date now,
        @Param("excludedWaitId") Long excludedWaitId,
        @Param("delFlag") String delFlag);

    /**
     * 按流程定义范围取消定义级记录（instance_id 为空）：WAITING / RETRY / RUNNING 置 CANCELLED，
     * 递增版本号并清空租约；终态记录不受影响。
     *
     * @param definitionId 流程定义 ID
     * @param now          当前时间
     * @param delFlag      未删除值
     * @return 取消的记录数
     */
    int cancelByDefinitionId(@Param("definitionId") Long definitionId, @Param("now") Date now,
        @Param("delFlag") String delFlag);
}
