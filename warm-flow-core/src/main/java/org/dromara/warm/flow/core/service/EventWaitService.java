package org.dromara.warm.flow.core.service;

import org.dromara.warm.flow.core.dto.EventWaitQuery;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.orm.service.IWarmService;
import org.dromara.warm.flow.core.utils.page.Page;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.List;
import java.util.Date;

/**
 * 事件等待记录服务。
 * <p>
 * @author may
 */
public interface EventWaitService extends IWarmService<EventWait> {

    /**
     * 按待办任务 ID 集合批量取消等待记录。
     *
     * @param taskIds 待办任务 ID 集合
     */
    void cancelByTaskIds(List<Long> taskIds);

    /**
     * 按流程实例 ID 集合批量取消等待记录。
     *
     * @param instanceIds 流程实例 ID 集合
     */
    void cancelByInstanceIds(List<Long> instanceIds);

    /**
     * 按流程定义 ID 取消定义级等待记录（如定时启动事件）。
     *
     * @param definitionId 流程定义 ID
     */
    void cancelByDefinitionId(Long definitionId);

    /**
     * 查询某实例下处于等待中的事件记录。
     *
     * @param instanceId 流程实例 ID
     * @return 等待中的事件记录列表
     */
    List<EventWait> getWaitingByInstanceId(Long instanceId);

    /**
     * 按查询条件分页查询等待记录（定时任务）。
     * <p>
     * 条件为空等价于默认条件（只查定时类事件）；{@code businessId} / {@code flowCode} 先解析为
     * 实例 / 定义主键再下推，解析不到时返回空结果而不是放弃该条件。
     *
     * @param query 查询条件，可为 {@code null}
     * @return 回填总数和当前页数据的查询结果
     */
    Page<EventWait> pageEventWaits(EventWaitQuery query);

    /**
     * 按查询条件查询等待记录列表，条件语义与 {@link #pageEventWaits(EventWaitQuery)} 一致。
     *
     * @param query 查询条件，可为 {@code null}
     * @return 匹配的等待记录列表
     */
    List<EventWait> listEventWaits(EventWaitQuery query);

    /**
     * 按查询条件统计等待记录数量，条件语义与 {@link #pageEventWaits(EventWaitQuery)} 一致。
     *
     * @param query 查询条件，可为 {@code null}
     * @return 匹配记录数
     */
    long countEventWaits(EventWaitQuery query);

    /**
     * 按边界事件关联的待办任务 ID 取消等待记录。
     *
     * @param taskId 待办任务 ID
     */
    void cancelByAttachedTaskId(Long taskId);

    /**
     * 匹配错误等待记录：任务级匹配附着该任务的 {@code ERROR_BOUNDARY} 等待，
     * 实例级匹配该实例的 {@code ERROR_CATCH} 等待；精确错误码优先于通配（错误码为空表示通配）。
     *
     * @param instanceId    流程实例 ID（实例级匹配必填）
     * @param attachedTaskId 待办任务 ID（任务级边界匹配时传入，实例级匹配传 {@code null}）
     * @param errorCode      错误码
     * @return 最佳匹配的等待记录；无匹配返回 {@code null}
     */
    EventWait matchErrorWait(Long instanceId, Long attachedTaskId, String errorCode);

    /**
     * 触发已匹配的错误等待记录：条件更新抢占（已是 {@code RUNNING / CANCELLED / DEAD}
     * 时抛出「错误已被处理」异常），随后按完成 / 失败语义同步处理。
     *
     * @param target       匹配到的错误等待记录
     * @param errorCode    错误码，仅用于日志
     * @param errorMessage 错误描述，仅用于日志
     * @return 触发成功返回 {@code true}
     */
    boolean triggerErrorWait(EventWait target, String errorCode, String errorMessage);

    /**
     * 消息记录抢占：WAITING / RETRY 条件更新为 RUNNING 并写租约。
     *
     * @param id        记录主键
     * @param version   抢占前版本号
     * @param workerId  工作节点标识
     * @param now       当前时间
     * @param lockUntil 租约到期时间
     * @return 抢占成功返回 {@code true}
     */
    boolean claimMessage(Long id, Integer version, String workerId, Date now
        , Date lockUntil);

    /**
     * 绑定消息载荷到等待记录：按 {@code id + version} 且状态为 {@code WAITING / RETRY}
     * 写入 {@code payload} 列（不递增版本号），供执行链从记录读取并合并为流程变量。
     *
     * @param id      记录主键
     * @param version 当前版本号
     * @param payload 消息载荷 JSON
     * @return 绑定成功返回 {@code true}
     */
    boolean bindPayload(Long id, Integer version, String payload);

    /**
     * 同步处理一条已抢占的消息等待记录：完成语义与定时路径一致
     * （成功删除 / 失败退避重试 / 超限 DEAD / 跳过释放）。
     *
     * @param wait 已抢占的等待记录
     */
    void processClaimedMessage(EventWait wait);

    /**
     * 同步处理一条已抢占的消息等待记录，并透传调用方上下文
     * （消息触发时携带审计操作人与租户，优先于系统兜底）。
     *
     * @param wait    已抢占的等待记录
     * @param context 调用方上下文；定时调度路径为 {@code null}
     */
    void processClaimedMessage(EventWait wait, WorkflowContext context);

}
