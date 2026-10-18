package org.dromara.warm.demo.service;

import org.dromara.warm.demo.dto.EventWaitQueryRequest;
import org.dromara.warm.demo.vo.EventWaitSummaryVo;
import org.dromara.warm.demo.vo.EventWaitVo;
import org.dromara.warm.demo.vo.PageVo;

/**
 * 定时任务（事件等待记录）应用服务。
 * <p>只读：查询等待记录与各状态计数，供运维观测页面使用；不做取消 / 立即触发 / 重新排期等写操作。</p>
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
}
