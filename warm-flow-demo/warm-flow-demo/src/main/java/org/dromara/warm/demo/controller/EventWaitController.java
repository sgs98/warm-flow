package org.dromara.warm.demo.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.warm.demo.common.ApiResponse;
import org.dromara.warm.demo.dto.EventWaitQueryRequest;
import org.dromara.warm.demo.dto.TimerStartRequest;
import org.dromara.warm.demo.service.EventWaitAppService;
import org.dromara.warm.demo.vo.EventWaitSummaryVo;
import org.dromara.warm.demo.vo.EventWaitVo;
import org.dromara.warm.demo.vo.PageVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定时任务接口：全局查询事件等待记录与各状态计数，并支持对定时启动任务手动补发一次实例。
 * <p>数据来源为引擎事件等待表，默认只查定时类事件；消息类 / 错误类等待需显式放开事件类型范围。
 * 手动补发只创建一次实例，不读取也不推进等待记录，定时任务的下次触发时间不受影响。</p>
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

    /**
     * 手动补发一次定时启动任务（运维应急）。
     * <p>不推进等待记录，下一次定时触发照常进行；实例的发起人记为当前用户。</p>
     *
     * @param user    当前用户名
     * @param id      事件等待记录主键
     * @param request 补发参数
     * @return 新建实例主键
     */
    @PostMapping("/{id}/trigger")
    public ApiResponse<Long> trigger(@RequestHeader(value = "X-User-Name", defaultValue = "admin") String user
        , @PathVariable("id") Long id, @RequestBody(required = false) TimerStartRequest request) {
        TimerStartRequest body = request == null ? new TimerStartRequest() : request;
        return ApiResponse.ok(eventWaitAppService.manualTrigger(id, user, body.getBusinessId()
            , body.getVariables()));
    }

    /**
     * 复活一条已死（{@code DEAD}）的等待记录（运维应急）。
     * <p>复活后重试计数归零、定时类事件重算下次触发时间，由下一轮扫描或外部消息重新触发；
     * 定时启动任务的记录不支持复活，请改用重新发布或挂起后激活定义。</p>
     *
     * @param id 事件等待记录主键
     * @return 复活成功返回 {@code true}
     */
    @PostMapping("/{id}/revive")
    public ApiResponse<Boolean> revive(@PathVariable("id") Long id) {
        return ApiResponse.ok(eventWaitAppService.revive(id));
    }
}
