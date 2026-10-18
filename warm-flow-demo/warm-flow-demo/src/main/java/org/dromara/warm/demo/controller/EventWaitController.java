package org.dromara.warm.demo.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.warm.demo.common.ApiResponse;
import org.dromara.warm.demo.dto.EventWaitQueryRequest;
import org.dromara.warm.demo.service.EventWaitAppService;
import org.dromara.warm.demo.vo.EventWaitSummaryVo;
import org.dromara.warm.demo.vo.EventWaitVo;
import org.dromara.warm.demo.vo.PageVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定时任务接口：全局只读查询事件等待记录与各状态计数。
 * <p>数据来源为引擎事件等待表，默认只查定时类事件；消息类 / 错误类等待需显式放开事件类型范围。
 * 本组接口不提供取消 / 立即触发 / 重新排期等写操作。</p>
 *
 * @author may
 * @since 2026/10/17
 */
@RestController
@RequestMapping("/api/event-waits")
@RequiredArgsConstructor
public class EventWaitController {

    /**
     * 定时任务应用服务。
     */
    private final EventWaitAppService eventWaitAppService;

    /**
     * 分页查询事件等待记录。
     *
     * @param request 查询参数
     * @return 分页等待记录
     */
    @GetMapping
    public ApiResponse<PageVo<EventWaitVo>> page(EventWaitQueryRequest request) {
        return ApiResponse.ok(eventWaitAppService.pageEventWaits(request));
    }

    /**
     * 按同一组筛选条件统计各状态等待记录数量。
     *
     * @param request 查询参数（状态条件不参与统计）
     * @return 各状态计数
     */
    @GetMapping("/summary")
    public ApiResponse<EventWaitSummaryVo> summary(EventWaitQueryRequest request) {
        return ApiResponse.ok(eventWaitAppService.summary(request));
    }
}
