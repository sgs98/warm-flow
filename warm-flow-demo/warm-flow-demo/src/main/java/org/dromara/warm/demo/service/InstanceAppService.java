package org.dromara.warm.demo.service;

import org.dromara.warm.demo.dto.PageQuery;
import org.dromara.warm.demo.dto.StartInstanceRequest;
import org.dromara.warm.demo.vo.HistoryVo;
import org.dromara.warm.demo.vo.InstanceVo;
import org.dromara.warm.demo.vo.PageVo;
import org.dromara.warm.demo.vo.StartInstanceVo;

import java.util.List;

/**
 * 流程实例应用服务。
 * <p>面向 Demo 页面的实例域用例：发起、查询、删除、撤回、终止。</p>
 *
 * @author may
 * @since 2026/10/17
 */
public interface InstanceAppService {

    /**
     * 发起流程，并返回当前用户需要办理的首个待办任务。
     *
     * @param user    当前用户名
     * @param request 发起请求
     * @return 发起结果
     */
    StartInstanceVo start(String user, StartInstanceRequest request);

    /**
     * 分页查询流程实例。
     *
     * @param user         当前用户名（非空时只查本人发起的）
     * @param pageQuery    分页参数
     * @param definitionId 流程定义主键
     * @param flowStatus   流程状态
     * @param businessId   业务主键
     * @return 流程实例分页数据
     */
    PageVo<InstanceVo> pageInstances(String user, PageQuery pageQuery,
                                     Long definitionId, String flowStatus, String businessId);

    /**
     * 查询流程实例详情。
     *
     * @param id 流程实例主键
     * @return 流程实例详情
     */
    InstanceVo detailInstance(Long id);

    /**
     * 查询流程实例审批历史（含当前活动待办）。
     *
     * @param instanceId 流程实例主键
     * @return 审批历史集合，最新在前
     */
    List<HistoryVo> history(Long instanceId);

    /**
     * 物理删除流程实例及其流程用户、待办、历史任务数据。
     *
     * @param instanceId 流程实例主键
     */
    void removeInstance(Long instanceId);

    /**
     * 批量物理删除流程实例及其流程用户、待办、历史任务数据。
     *
     * @param instanceIds 流程实例主键集合
     * @return 实际删除的流程实例数量
     */
    int removeInstances(List<Long> instanceIds);

    /**
     * 撤回当前用户发起的流程实例。
     *
     * @param user       当前用户名
     * @param instanceId 流程实例主键
     */
    void revoke(String user, Long instanceId);

    /**
     * 终止流程实例。
     *
     * @param user       当前用户名
     * @param instanceId 流程实例主键
     */
    void termination(String user, Long instanceId);
}
