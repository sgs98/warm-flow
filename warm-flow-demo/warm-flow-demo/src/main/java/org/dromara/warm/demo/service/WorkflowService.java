package org.dromara.warm.demo.service;

import org.dromara.warm.demo.dto.StartInstanceRequest;
import org.dromara.warm.demo.dto.TaskActionRequest;
import org.dromara.warm.demo.vo.ButtonPermissionVo;
import org.dromara.warm.demo.vo.HistoryVo;
import org.dromara.warm.demo.vo.InstanceVo;
import org.dromara.warm.demo.vo.PageVo;
import org.dromara.warm.demo.vo.StartInstanceVo;
import org.dromara.warm.demo.vo.TaskNodeVo;
import org.dromara.warm.demo.vo.TaskVo;

import java.util.List;

/**
 * 流程实例与任务业务接口。
 *
 * @author may
 * @since 2026/9/11
 */
public interface WorkflowService {

    /**
     * 发起流程，并返回发起后的首个待办任务。
     *
     * @param user    当前用户名
     * @param request 发起请求
     * @return 发起结果
     */
    StartInstanceVo start(String user, StartInstanceRequest request);

    /**
     * 分页查询当前用户发起的流程实例。
     *
     * @param user         当前用户名
     * @param pageNum      页码
     * @param pageSize     每页条数
     * @param definitionId 流程定义主键
     * @param flowStatus   流程状态
     * @param businessId   业务主键
     * @return 流程实例分页数据
     */
    PageVo<InstanceVo> pageInstances(String user, Integer pageNum, Integer pageSize,
                                     Long definitionId, String flowStatus, String businessId);

    /**
     * 查询流程实例详情。
     *
     * @param id 流程实例主键
     * @return 流程实例详情
     */
    InstanceVo detailInstance(Long id);

    /**
     * 查询流程实例审批历史。
     *
     * @param instanceId 流程实例主键
     * @return 审批历史集合
     */
    List<HistoryVo> history(Long instanceId);

    /**
     * 物理删除流程实例及其待办、历史任务、办理人数据。
     *
     * @param instanceId 流程实例主键
     */
    void removeInstance(Long instanceId);

    /**
     * 分页查询当前用户待办任务。
     *
     * @param user     当前用户名
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 待办任务分页数据
     */
    PageVo<TaskVo> todo(String user, Integer pageNum, Integer pageSize);

    /**
     * 分页查询当前用户已办任务。
     *
     * @param user     当前用户名
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 已办任务分页数据
     */
    PageVo<TaskVo> done(String user, Integer pageNum, Integer pageSize);

    /**
     * 查询待办任务所在节点的按钮权限。
     *
     * @param taskId 待办任务主键
     * @return 按钮权限集合
     */
    List<ButtonPermissionVo> buttonPermissions(Long taskId);

    /**
     * 查询待办任务按实例变量可达的下一审批节点，并解析各节点可选办理用户。
     *
     * @param taskId 待办任务主键
     * @return 下一审批节点及可选办理用户集合
     */
    List<TaskNodeVo> nextNodes(Long taskId);

    /**
     * 查询流程图中当前节点的前置审批节点，用于选择退回目标。
     *
     * @param taskId 待办任务主键
     * @return 可退回审批节点集合，不解析可选办理用户
     */
    List<TaskNodeVo> backNodes(Long taskId);

    /**
     * 分页查询当前用户收到的抄送。
     *
     * @param user     当前用户名
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 抄送分页数据
     */
    PageVo<TaskVo> copy(String user, Integer pageNum, Integer pageSize);

    /**
     * 通过待办任务。
     *
     * @param request 办理请求
     * @return 办理后的流程实例
     */
    InstanceVo pass(TaskActionRequest request);

    /**
     * 退回待办任务。
     *
     * @param request 办理请求
     * @return 办理后的流程实例
     */
    InstanceVo reject(TaskActionRequest request);

    /**
     * 转办待办任务。
     *
     * @param request 办理请求
     */
    void transfer(TaskActionRequest request);

    /**
     * 委派待办任务。
     *
     * @param request 办理请求
     */
    void depute(TaskActionRequest request);

    /**
     * 给待办任务增加办理人。
     *
     * @param request 办理请求
     */
    void addSignature(TaskActionRequest request);

    /**
     * 减少待办任务办理人。
     *
     * @param request 办理请求
     */
    void reductionSignature(TaskActionRequest request);

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
