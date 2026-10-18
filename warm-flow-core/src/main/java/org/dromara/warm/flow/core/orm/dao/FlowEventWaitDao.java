package org.dromara.warm.flow.core.orm.dao;

import org.dromara.warm.flow.core.dto.EventWaitQuery;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.utils.page.Page;

import java.util.Date;
import java.util.List;

/**
 * 事件等待记录 DAO 接口。
 * <p>
 * 除通用 CRUD 外，定义定时器底座所需的到期扫描、CAS 抢占、租约续期与恢复、
 * 重试与终态、重排期等条件更新语义。所有条件更新必须校验
 * {@code id + version}（完成类更新还须校验 {@code status = RUNNING + lock_owner}），
 * 由 MyBatis / MyBatis-Plus 适配层分别实现，core 不写 ORM 查询语法。
 * <p>
 * 逻辑删除与未删除值由引擎配置决定，实现不得写死 {@code del_flag = '0'}。
 *
 * @author may
 */
public interface FlowEventWaitDao<T extends EventWait> extends WarmDao<T> {

    /**
     * 分页扫描到期的等待记录（WAITING）。
     * <p>
     * 条件：{@code status = WAITING and next_trigger_at <= now}，按 {@code (next_trigger_at, id)}
     * 游标升序翻页；游标为空表示从头扫描。
     *
     * @param now      当前时间
     * @param cursorAt 上页最后一条的到期时间游标，可为 {@code null}
     * @param cursorId 上页最后一条的主键游标，可为 {@code null}
     * @param limit    单页条数
     * @return 到期的等待记录列表
     */
    List<EventWait> selectWaitingDue(Date now, Date cursorAt, Long cursorId, int limit);

    /**
     * 分页扫描到期的重试记录（RETRY）。
     * <p>
     * 条件：{@code status = RETRY and retry_at <= now}，按 {@code (retry_at, id)} 游标升序翻页；
     * 游标为空表示从头扫描。
     *
     * @param now      当前时间
     * @param cursorAt 上页最后一条的重试时间游标，可为 {@code null}
     * @param cursorId 上页最后一条的主键游标，可为 {@code null}
     * @param limit    单页条数
     * @return 到期的重试记录列表
     */
    List<EventWait> selectRetryDue(Date now, Date cursorAt, Long cursorId, int limit);

    /**
     * CAS 抢占：WAITING / RETRY 置为 RUNNING 并写入租约。
     * <p>
     * 条件更新校验 {@code version} 与对应到期时间字段（WAITING 看 {@code next_trigger_at}、
     * RETRY 看 {@code retry_at}）；受影响行数为 1 才表示抢占成功。抢占后版本号递增，
     * 执行器须使用本地递增后的新版本。
     *
     * @param id        记录主键
     * @param version   抢占前版本号
     * @param workerId  工作节点标识
     * @param now       当前时间
     * @param lockUntil 租约到期时间
     * @return 抢占成功返回 {@code true}
     */
    boolean claim(Long id, Integer version, String workerId, Date now, Date lockUntil);

    /**
     * 续租：仅允许当前持有者按 {@code status = RUNNING + lock_owner + version} 续期。
     *
     * @param id        记录主键
     * @param version   当前版本号
     * @param workerId  工作节点标识
     * @param now       当前时间
     * @param lockUntil 新的租约到期时间
     * @return 续租成功返回 {@code true}
     */
    boolean renewLease(Long id, Integer version, String workerId, Date now, Date lockUntil);

    /**
     * 分页查询租约过期的执行中记录。
     * <p>
     * 条件：{@code status = RUNNING and lock_until < now}，按主键升序分页。
     *
     * @param now   当前时间
     * @param limit 单页条数
     * @return 租约过期的记录列表
     */
    List<EventWait> selectExpired(Date now, int limit);

    /**
     * 恢复租约过期的记录：递增 {@code retry_count} 与 {@code version}、清空旧租约，
     * 按调用方计算的下一个状态（RETRY / DEAD）与重试时间落库。
     * <p>
     * 恢复成功后，旧执行器即使随后返回，也无法通过旧版本提交结果。
     *
     * @param id         记录主键
     * @param version    当前版本号
     * @param retryCount 递增后的重试次数
     * @param status     恢复后的状态（RETRY / DEAD）
     * @param retryAt    下次重试时间（DEAD 时为 {@code null}）
     * @param now        当前时间
     * @param error      失败原因摘要（租约过期）
     * @return 恢复成功返回 {@code true}
     */
    boolean recoverExpired(Long id, Integer version, Integer retryCount, String status,
        Date retryAt, Date now, String error);

    /**
     * 一次性事件执行成功后直接删除记录（校验 {@code RUNNING + lock_owner + version}）。
     *
     * @param id       记录主键
     * @param version  当前版本号
     * @param workerId 工作节点标识
     * @return 删除成功返回 {@code true}
     */
    boolean deleteCompleted(Long id, Integer version, String workerId);

    /**
     * 执行失败转重试：置 {@code RETRY}、写下次重试时间与失败原因
     * （校验 {@code RUNNING + lock_owner + version}）。
     *
     * @param id       记录主键
     * @param version  当前版本号
     * @param workerId 工作节点标识
     * @param now      当前时间
     * @param retryAt  下次重试时间
     * @param error    失败原因摘要
     * @return 更新成功返回 {@code true}
     */
    boolean markRetry(Long id, Integer version, String workerId, Date now, Date retryAt, String error);

    /**
     * 执行失败超上限转终态：置 {@code DEAD}、清空重试时间并保留失败原因
     * （校验 {@code RUNNING + lock_owner + version}）。
     *
     * @param id       记录主键
     * @param version  当前版本号
     * @param workerId 工作节点标识
     * @param now      当前时间
     * @param error    失败原因摘要
     * @return 更新成功返回 {@code true}
     */
    boolean markDead(Long id, Integer version, String workerId, Date now, String error);

    /**
     * 重复事件执行成功后重排期：更新下一次触发时间并递增触发次数
     * （校验 {@code RUNNING + lock_owner + version}，置回 {@code WAITING}）。
     *
     * @param id           记录主键
     * @param version      当前版本号
     * @param workerId     工作节点标识
     * @param now          当前时间
     * @param nextTriggerAt 下一次触发时间
     * @param repeatCount  递增后的已触发次数
     * @return 重排期成功返回 {@code true}
     */
    boolean reschedule(Long id, Integer version, String workerId, Date now,
        Date nextTriggerAt, Integer repeatCount);

    /**
     * 释放抢占：按抢占前的状态（WAITING / RETRY）与对应时间字段恢复记录，
     * 递增版本号并清空租约，不消耗 {@code retry_count}。
     * <p>
     * 用于抢占后发现记录暂不可执行（如实例挂起）等场景。
     *
     * @param id            记录主键
     * @param version       当前版本号
     * @param workerId      工作节点标识
     * @param status        抢占前状态（WAITING / RETRY）
     * @param nextTriggerAt 抢占前的下次触发时间（WAITING 时非空）
     * @param retryAt       抢占前的下次重试时间（RETRY 时非空）
     * @param now           当前时间
     * @return 释放成功返回 {@code true}
     */
    boolean releaseClaim(Long id, Integer version, String workerId, String status,
        Date nextTriggerAt, Date retryAt, Date now);

    /**
     * 按查询条件分页查询等待记录。
     * <p>
     * 条件语义与 {@link #selectListByQuery(EventWaitQuery)} 一致；
     * 排序字段与方向由 {@link EventWaitQuery#resolveOrderColumn()} /
     * {@link EventWaitQuery#resolveOrderDirection()} 解析为白名单列名，实现不得拼接调用方原文。
     *
     * @param query 查询条件
     * @param page  分页参数
     * @return 回填总数和当前页数据的查询结果
     */
    Page<EventWait> selectPageByQuery(EventWaitQuery query, Page<EventWait> page);

    /**
     * 按查询条件查询等待记录列表。
     * <p>
     * 多值条件取「或」、不同条件之间取「且」；返回 {@code null} 或空集合的解析结果表示该条件不限。
     * 租户与逻辑删除过滤沿用引擎配置，实现不得写死。
     *
     * @param query 查询条件
     * @return 匹配的等待记录列表
     */
    List<EventWait> selectListByQuery(EventWaitQuery query);

    /**
     * 按查询条件统计等待记录数量，条件语义与 {@link #selectListByQuery(EventWaitQuery)} 一致。
     *
     * @param query 查询条件
     * @return 匹配记录数
     */
    long selectCountByQuery(EventWaitQuery query);

    /**
     * 消息记录抢占：{@code WAITING / RETRY} 置为 {@code RUNNING} 并写入租约，
     * 不校验到期时间字段（消息事件的 {@code next_trigger_at} 为空，由外部消息显式触发）。
     * <p>
     * 条件更新校验 {@code id + version + status in (WAITING, RETRY)}；受影响行数为 1
     * 才表示抢占成功。记录已是 {@code RUNNING} / {@code CANCELLED} / {@code DEAD} 时
     * 返回 {@code false}，调用方不得强行覆盖其状态。
     *
     * @param id        记录主键
     * @param version   抢占前版本号
     * @param workerId  工作节点标识
     * @param now       当前时间
     * @param lockUntil 租约到期时间（崩溃恢复依据）
     * @return 抢占成功返回 {@code true}
     */
    boolean claimMessage(Long id, Integer version, String workerId, Date now, Date lockUntil);

    /**
     * 绑定消息载荷：把外部消息的载荷写入等待记录的 {@code payload} 列，
     * 供执行链从记录读取并合并为流程变量（失败重试与租约恢复后仍可读取）。
     * <p>
     * 条件更新校验 {@code id + version + status in (WAITING, RETRY)}，且<b>不递增版本号</b>，
     * 以便紧随其后的 {@link #claimMessage} 仍按同一版本抢占；记录已被并发抢占
     * （{@code RUNNING} / {@code CANCELLED} / {@code DEAD}）时返回 {@code false}，
     * 调用方应以其后的抢占结果为准。仅写入 {@code payload}，不改动状态 / 版本 / 时间字段。
     *
     * @param id      记录主键
     * @param version 当前版本号
     * @param payload 消息载荷 JSON
     * @return 绑定成功返回 {@code true}
     */
    boolean bindPayload(Long id, Integer version, String payload);
}
