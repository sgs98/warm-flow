package org.dromara.warm.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.warm.demo.common.ApiResponse;
import org.dromara.warm.demo.dto.PageQuery;
import org.dromara.warm.demo.dto.TaskActionRequest;
import org.dromara.warm.demo.service.WorkflowService;
import org.dromara.warm.demo.vo.ButtonPermissionVo;
import org.dromara.warm.demo.vo.InstanceVo;
import org.dromara.warm.demo.vo.PageVo;
import org.dromara.warm.demo.vo.TaskNodeVo;
import org.dromara.warm.demo.vo.TaskVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务接口：待办/已办查询与任务操作。
 *
 * @author may
 * @since 2026/9/5
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskController {

    private final WorkflowService workflowService;

    /**
     * 分页查询当前用户待办任务。
     *
     * @param user     当前用户名
     * @param pageQuery 分页参数
     * @return 待办任务分页数据
     */
    @GetMapping("/tasks/todo")
    public ApiResponse<PageVo<TaskVo>> todo(
        @RequestHeader("X-User-Name") String user,
        PageQuery pageQuery) {
        return ApiResponse.ok(workflowService.todo(user, pageQuery));
    }

    /**
     * 分页查询当前用户已办任务。
     *
     * @param user     当前用户名
     * @param pageQuery 分页参数
     * @return 已办任务分页数据
     */
    @GetMapping("/tasks/done")
    public ApiResponse<PageVo<TaskVo>> done(
        @RequestHeader("X-User-Name") String user,
        PageQuery pageQuery) {
        return ApiResponse.ok(workflowService.done(user, pageQuery));
    }

    /**
     * 查询待办任务所在节点的按钮权限。
     *
     * @param taskId 待办任务主键
     * @return 按钮权限集合
     */
    @GetMapping("/tasks/{taskId}/button-permissions")
    public ApiResponse<List<ButtonPermissionVo>> buttonPermissions(@PathVariable("taskId") Long taskId) {
        return ApiResponse.ok(workflowService.buttonPermissions(taskId));
    }

    /**
     * 查询待办任务可办理的下一审批节点。
     *
     * @param taskId 待办任务主键
     * @return 下一审批节点集合
     */
    @GetMapping("/tasks/{taskId}/next-nodes")
    public ApiResponse<List<TaskNodeVo>> nextNodes(@PathVariable("taskId") Long taskId) {
        return ApiResponse.ok(workflowService.nextNodes(taskId));
    }

    /**
     * 查询待办任务可退回的历史审批节点。
     *
     * @param taskId 待办任务主键
     * @return 可退回审批节点集合
     */
    @GetMapping("/tasks/{taskId}/back-nodes")
    public ApiResponse<List<TaskNodeVo>> backNodes(@PathVariable("taskId") Long taskId) {
        return ApiResponse.ok(workflowService.backNodes(taskId));
    }

    /**
     * 分页查询当前用户收到的抄送。
     *
     * @param user     当前用户名
     * @param pageQuery 分页参数
     * @return 抄送分页数据
     */
    @GetMapping("/tasks/copy")
    public ApiResponse<PageVo<TaskVo>> copy(
        @RequestHeader("X-User-Name") String user,
        PageQuery pageQuery) {
        return ApiResponse.ok(workflowService.copy(user, pageQuery));
    }

    /**
     * 通过待办任务。
     *
     * @param request 办理请求
     * @return 办理后的流程实例
     */
    @PostMapping("/tasks/pass")
    public ApiResponse<InstanceVo> pass(@Valid @RequestBody TaskActionRequest request) {
        return ApiResponse.ok(workflowService.pass(request));
    }

    /**
     * 退回待办任务。
     *
     * @param request 办理请求
     * @return 办理后的流程实例
     */
    @PostMapping("/tasks/reject")
    public ApiResponse<InstanceVo> reject(@Valid @RequestBody TaskActionRequest request) {
        return ApiResponse.ok(workflowService.reject(request));
    }

    /**
     * 转办待办任务。
     *
     * @param request 办理请求
     * @return 空响应
     */
    @PostMapping("/tasks/transfer")
    public ApiResponse<Void> transfer(@Valid @RequestBody TaskActionRequest request) {
        workflowService.transfer(request);
        return ApiResponse.ok();
    }

    /**
     * 委派待办任务。
     *
     * @param request 办理请求
     * @return 空响应
     */
    @PostMapping("/tasks/depute")
    public ApiResponse<Void> depute(@Valid @RequestBody TaskActionRequest request) {
        workflowService.depute(request);
        return ApiResponse.ok();
    }

    /**
     * 给待办任务增加办理人。
     *
     * @param request 办理请求
     * @return 空响应
     */
    @PostMapping("/tasks/add-signature")
    public ApiResponse<Void> addSignature(@Valid @RequestBody TaskActionRequest request) {
        workflowService.addSignature(request);
        return ApiResponse.ok();
    }

    /**
     * 减少待办任务办理人。
     *
     * @param request 办理请求
     * @return 空响应
     */
    @PostMapping("/tasks/reduction-signature")
    public ApiResponse<Void> reductionSignature(@Valid @RequestBody TaskActionRequest request) {
        workflowService.reductionSignature(request);
        return ApiResponse.ok();
    }
}
