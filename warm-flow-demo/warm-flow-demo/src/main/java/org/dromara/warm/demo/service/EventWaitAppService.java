package org.dromara.warm.demo.service;

import org.dromara.warm.demo.dto.EventWaitQueryRequest;
import org.dromara.warm.demo.vo.EventWaitSummaryVo;
import org.dromara.warm.demo.vo.EventWaitVo;
import org.dromara.warm.demo.vo.PageVo;

import java.util.Map;

/**
 * 定时任务（事件等待记录）应用服务。
 * <p>查询等待记录与各状态计数，供运维观测页面使用。写操作只有一项：对定时启动任务手动补发
 * 一次实例——补发不读取、也不推进等待记录，定时任务的下次触发时间不受影响。</p>
 *
 * @author may
 * @since 2026/10/17
 */
public interface EventWaitAppService {

    /**
     * 分页查询事件等待记录。
     *
     * @param request 查询参数
     * @return 分页等待记录（含流程名与业务主键）
     */
    PageVo<EventWaitVo> pageEventWaits(EventWaitQueryRequest request);

    /**
     * 按同一组筛选条件统计各状态等待记录数量。
     *
     * @param request 查询参数（状态条件由本方法按状态逐个覆盖）
     * @return 各状态计数
     */
    EventWaitSummaryVo summary(EventWaitQueryRequest request);

    /**
     * 按定时任务手动补发一次流程实例（运维应急）。
     * <p>
     * 行为与定时到期触发一致，操作人记为调用方传入的用户。等待记录保持不变，
     * 因此「每天 9 点的任务今天提前补发一次」不会吞掉当天 9 点的那次触发。
     *
     * @param waitId   事件等待记录主键
     * @param user     点击操作的当前用户
     * @param businessId 业务主键，留空时按 {@code M{waitId}_{时间戳}} 生成
     * @param variables 额外流程变量，可为 {@code null}
     * @return 新建实例主键
     */
    Long manualTrigger(Long waitId, String user, String businessId, Map<String, Object> variables);
}
