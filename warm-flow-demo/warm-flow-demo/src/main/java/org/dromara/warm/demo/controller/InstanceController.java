package org.dromara.warm.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.warm.demo.common.ApiResponse;
import org.dromara.warm.demo.dto.StartInstanceRequest;
import org.dromara.warm.demo.service.WorkflowService;
import org.dromara.warm.demo.vo.HistoryVo;
import org.dromara.warm.demo.vo.InstanceVo;
import org.dromara.warm.demo.vo.PageVo;
import org.dromara.warm.demo.vo.StartInstanceVo;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程实例接口。
 *
 * @author may
 * @since 2026/9/5
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InstanceController {

    private final WorkflowService workflowService;

    /**
     * 发起流程实例。
     *
     * @param user    当前用户名
     * @param request 发起请求
     * @return 发起结果
     */
    @PostMapping("/instances")
    public ApiResponse<StartInstanceVo> start(@RequestHeader(value = "X-User-Name", defaultValue = "admin") String user,
                                              @Valid @RequestBody StartInstanceRequest request) {
        return ApiResponse.ok(workflowService.start(user, request));
    }

    /**
     * 分页查询流程实例。
     *
     * @param pageNum      页码
     * @param pageSize     每页条数
     * @param definitionId 流程定义主键
     * @param flowStatus   流程状态
     * @param businessId   业务主键
     * @param user         当前用户名
     * @return 流程实例分页数据
     */
    @GetMapping("/instances")
    public ApiResponse<PageVo<InstanceVo>> page(
        @RequestParam(defaultValue = "1") Integer pageNum,
        @RequestParam(defaultValue = "10") Integer pageSize,
        @RequestParam(required = false) Long definitionId,
        @RequestParam(required = false) String flowStatus,
        @RequestParam(required = false) String businessId,
        @RequestHeader(value = "X-User-Name", required = false) String user) {
        return ApiResponse.ok(workflowService.pageInstances(user, pageNum, pageSize, definitionId, flowStatus, businessId));
    }

    /**
     * 查询流程实例详情。
     *
     * @param id 流程实例主键
     * @return 流程实例详情
     */
    @GetMapping("/instances/{id}")
    public ApiResponse<InstanceVo> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(workflowService.detailInstance(id));
    }

    /**
     * 查询流程实例审批历史。
     *
     * @param id 流程实例主键
     * @return 审批历史集合
     */
    @GetMapping("/instances/{id}/history")
    public ApiResponse<List<HistoryVo>> history(@PathVariable("id") Long id) {
        return ApiResponse.ok(workflowService.history(id));
    }

    /**
     * 物理删除流程实例及相关流程数据。
     *
     * @param id 流程实例主键
     * @return 空响应
     */
    @DeleteMapping("/instances/{id}")
    public ApiResponse<Void> remove(@PathVariable("id") Long id) {
        workflowService.removeInstance(id);
        return ApiResponse.ok();
    }

    /**
     * 撤回流程实例。
     *
     * @param user 当前用户名
     * @param id   流程实例主键
     * @return 空响应
     */
    @PostMapping("/instances/{id}/revoke")
    public ApiResponse<Void> revoke(@RequestHeader("X-User-Name") String user, @PathVariable("id") Long id) {
        workflowService.revoke(user, id);
        return ApiResponse.ok();
    }

    /**
     * 终止流程实例。
     *
     * @param user 当前用户名
     * @param id   流程实例主键
     * @return 空响应
     */
    @PostMapping("/instances/{id}/termination")
    public ApiResponse<Void> termination(@RequestHeader("X-User-Name") String user, @PathVariable("id") Long id) {
        workflowService.termination(user, id);
        return ApiResponse.ok();
    }
}
