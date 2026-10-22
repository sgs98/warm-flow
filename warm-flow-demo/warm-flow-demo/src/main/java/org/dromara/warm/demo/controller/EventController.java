package org.dromara.warm.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.warm.demo.common.ApiResponse;
import org.dromara.warm.demo.common.BizException;
import org.dromara.warm.demo.config.DemoEventPublisher;
import org.dromara.warm.demo.dto.ErrorTriggerRequest;
import org.dromara.warm.demo.dto.MessageStartRequest;
import org.dromara.warm.demo.dto.MessageTriggerRequest;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.dromara.warm.flow.core.workflow.result.EventView;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 事件接口：查询实例等待中的事件，触发消息捕获与错误捕获。
 *
 * @author may
 * @since 2026/10/16
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EventController {

    /**
     * Demo 消息发布器，查询最近发布的消息事件。
     */
    private final DemoEventPublisher eventPublisher;

    /**
     * 查询实例当前等待中的事件记录。
     *
     * @param id 流程实例主键
     * @return 等待中的事件视图
     */
    @GetMapping("/instances/{id}/events")
    public ApiResponse<List<EventView>> waitingEvents(@PathVariable("id") Long id) {
        return ApiResponse.ok(FlowEngine.workflow().waitingEvents(id));
    }

    /**
     * 消息启动流程（MESSAGE_START）：按流程编码 + 消息名自动发起实例，
     * businessId 由调用方指定，引擎不按其去重，重复调用会创建多个实例。
     *
     * @param user    当前用户名
     * @param request 消息启动请求
     * @return 新实例主键
     */
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/instances/message")
    public ApiResponse<Long> startByMessage(@RequestHeader(value = "X-User-Name", defaultValue = "admin") String user
        , @Valid @RequestBody MessageStartRequest request) {
        WorkflowContext context = new WorkflowContext();
        context.setHandler(user);
        try {
            Instance instance = FlowEngine.insService().startByMessage(request.getBusinessId()
                , request.getFlowCode(), request.getEventName(), request.getVariables(), context);
            return ApiResponse.ok(instance.getId());
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /**
     * 触发实例的消息捕获事件（MESSAGE_CATCH / MESSAGE_BOUNDARY）。
     *
     * @param user    当前用户名
     * @param id      流程实例主键
     * @param request 消息触发请求
     * @return 触发后的实例主键
     */
    @PostMapping("/instances/{id}/messages")
    public ApiResponse<Long> message(@RequestHeader(value = "X-User-Name", defaultValue = "admin") String user
        , @PathVariable("id") Long id, @Valid @RequestBody MessageTriggerRequest request) {
        WorkflowContext context = new WorkflowContext();
        context.setHandler(user);
        try {
            Instance instance = FlowEngine.insService().triggerMessage(id, request.getEventName()
                , request.getPayload(), context);
            return ApiResponse.ok(instance.getId());
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /**
     * 实例级抛出流程错误，触发 ERROR_CATCH。
     *
     * @param id      流程实例主键
     * @param request 错误触发请求
     * @return 触发成功返回 {@code true}
     */
    @PostMapping("/instances/{id}/errors")
    public ApiResponse<Boolean> error(@PathVariable("id") Long id
        , @RequestBody ErrorTriggerRequest request) {
        try {
            return ApiResponse.ok(FlowEngine.insService().throwError(id, request.getErrorCode()
                , request.getMessage()));
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /**
     * 任务级抛出流程错误，触发附着该任务的 ERROR_BOUNDARY。
     *
     * @param taskId  待办任务主键
     * @param request 错误触发请求
     * @return 触发成功返回 {@code true}
     */
    @PostMapping("/tasks/{taskId}/errors")
    public ApiResponse<Boolean> taskError(@PathVariable("taskId") Long taskId
        , @RequestBody ErrorTriggerRequest request) {
        try {
            return ApiResponse.ok(FlowEngine.taskService().throwError(taskId, request.getErrorCode()
                , request.getMessage()));
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /**
     * 查询最近的消息发布记录（MESSAGE_THROW / MESSAGE_END，新消息在前）。
     *
     * @return 发布记录列表
     */
    @GetMapping("/events/published")
    public ApiResponse<List<String>> publishedMessages() {
        return ApiResponse.ok(eventPublisher.recent());
    }
}
