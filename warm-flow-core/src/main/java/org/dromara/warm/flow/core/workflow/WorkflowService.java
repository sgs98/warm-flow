package org.dromara.warm.flow.core.workflow;

import org.dromara.warm.flow.core.dto.EventWaitQuery;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.utils.page.Page;
import org.dromara.warm.flow.core.workflow.command.*;
import org.dromara.warm.flow.core.workflow.result.EventView;
import org.dromara.warm.flow.core.workflow.result.WorkflowResult;

import java.util.List;

/**
 * 流程操作统一门面。
 *
 * @author may
 */
public interface WorkflowService {

    /**
     * 启动流程实例并创建首个待办。
     *
     * @param command 启动参数
     * @return 流程操作结果
     */
    WorkflowResult start(StartCommand command);

    /**
     * 完成当前待办并推动流程继续执行。
     *
     * @param command 完成参数
     * @return 流程操作结果
     */
    WorkflowResult complete(CompleteCommand command);

    /**
     * 将当前待办退回到合法的前置节点。
     *
     * @param command 退回参数
     * @return 流程操作结果
     */
    WorkflowResult reject(RejectCommand command);

    /**
     * 将当前待办跳转到指定节点。
     *
     * @param command 跳转参数
     * @return 流程操作结果
     */
    WorkflowResult jump(JumpCommand command);

    /**
     * 撤回申请人发起的流程实例。
     *
     * @param command 撤回参数
     * @return 流程操作结果
     */
    WorkflowResult revoke(RevokeCommand command);

    /**
     * 终止流程实例。
     *
     * @param command 终止参数
     * @return 流程操作结果
     */
    WorkflowResult terminate(TerminateCommand command);

    /**
     * 将当前待办转交给其他办理人。
     *
     * @param command 转办参数
     * @return 流程操作结果
     */
    WorkflowResult transfer(TransferCommand command);

    /**
     * 将当前待办委派给其他办理人。
     *
     * @param command 委派参数
     * @return 流程操作结果
     */
    WorkflowResult delegate(DelegateCommand command);

    /**
     * 为会签或票签节点增加办理人。
     *
     * @param command 加签参数
     * @return 流程操作结果
     */
    WorkflowResult addSigner(AddSignerCommand command);

    /**
     * 为会签或票签节点移除办理人。
     *
     * @param command 减签参数
     * @return 流程操作结果
     */
    WorkflowResult removeSigner(RemoveSignerCommand command);

    /**
     * 抄送当前待办：只留痕，不产生待办、不改任务与实例状态。
     *
     * <p>节点上配置的抄送（{@code node_config.copy}）由引擎在到达节点时自动执行，
     * 本操作用于办理过程中临时追加抄送人。</p>
     * <p>抄送人最终由 {@code PermissionHandler#resolveCopyHandlers} 展开成具体用户；
     * 一个都落不到人时抛 {@link org.dromara.warm.flow.core.exception.FlowException}。</p>
     *
     * @param command 抄送参数
     * @return 流程操作结果
     */
    WorkflowResult copy(CopyCommand command);

    /**
     * 复活一条已死（{@code DEAD}）的事件等待记录，供运维把「重试耗尽」的单子重新拉起。
     *
     * <p>只允许 {@code DEAD → WAITING}：{@code CANCELLED} 是刻意取消不开放；
     * 定时启动（{@code TIMER_START}）的等待记录同样不支持复活——定义可能已被取代或失效，
     * 请重新发布或挂起后激活定义，引擎会按当前定义重建记录。</p>
     *
     * <p>复活前会校验目标仍然有效：边界事件要求附着待办仍存在，实例事件要求实例存在且激活、
     * 未越过该事件节点。复活后重试计数归零，定时类事件从当前时间重算下次触发时间，
     * 等待下一轮扫描或外部消息重新触发。</p>
     *
     * @param command 复活参数
     * @return 流程操作结果
     */
    WorkflowResult reviveEvent(ReviveEventCommand command);

    /**
     * 查询流程实例当前正在等待的事件，用于区分「无当前任务但等待事件」与「流程已完成」。
     *
     * @param instanceId 流程实例主键
     * @return 当前等待事件视图集合
     */
    List<EventView> waitingEvents(Long instanceId);

    /**
     * 按条件分页查询事件等待记录（定时任务），第三方自由组合条件使用。
     * <p>
     * 条件为 {@code null} 或全空时按默认条件查询（只查定时类事件、按下次触发时间升序、第 1 页 10 条）。
     * {@code businessId} / {@code flowCode} 先解析为该业务下的实例 / 该编码下的定义主键再下推，
     * 解析不到时返回空结果而不是放弃该条件；与 {@code instanceId} / {@code definitionId} 同时指定会直接报错。
     *
     * @param query 查询条件，可为 {@code null}
     * @return 回填总数和当前页数据的查询结果
     */
    Page<EventWait> pageEventWaits(EventWaitQuery query);

    /**
     * 按条件查询事件等待记录列表，条件语义与 {@link #pageEventWaits(EventWaitQuery)} 一致，
     * 不分页，条数由调用方通过条件自行收窄。
     *
     * @param query 查询条件，可为 {@code null}
     * @return 匹配的等待记录列表
     */
    List<EventWait> listEventWaits(EventWaitQuery query);

    /**
     * 按条件统计事件等待记录数量，条件语义与 {@link #pageEventWaits(EventWaitQuery)} 一致。
     *
     * @param query 查询条件，可为 {@code null}
     * @return 匹配记录数
     */
    long countEventWaits(EventWaitQuery query);

    /**
     * 按主键查询单条事件等待记录。
     *
     * @param id 等待记录主键
     * @return 等待记录；不存在或主键为空时返回 {@code null}
     */
    EventWait getEventWait(Long id);
}
