package org.dromara.warm.demo.service;

import org.dromara.warm.demo.dto.PageQuery;
import org.dromara.warm.demo.dto.TaskActionRequest;
import org.dromara.warm.demo.vo.ButtonPermissionVo;
import org.dromara.warm.demo.vo.DemoUserVo;
import org.dromara.warm.demo.vo.InstanceVo;
import org.dromara.warm.demo.vo.PageVo;
import org.dromara.warm.demo.vo.TaskNodeVo;
import org.dromara.warm.demo.vo.TaskVo;

import java.util.List;

/**
 * 任务应用服务。
 * <p>面向 Demo 页面的任务域用例：待办/已办/抄送查询、任务办理协作操作
 * （通过/退回/转办/委派/加签/减签）、节点与选人辅助查询。</p>
 *
 * @author may
 * @since 2026/10/17
 */
public interface TaskAppService {

    /**
     * 分页查询当前用户待办任务。
     *
     * @param user      当前用户名
     * @param pageQuery 分页参数
     * @return 待办任务分页数据
     */
    PageVo<TaskVo> todo(String user, PageQuery pageQuery);

    /**
     * 分页查询当前用户已办任务。
     *
     * @param user      当前用户名
     * @param pageQuery 分页参数
     * @return 已办任务分页数据
     */
    PageVo<TaskVo> done(String user, PageQuery pageQuery);

    /**
     * 分页查询当前用户收到的抄送。
     *
     * @param user      当前用户名
     * @param pageQuery 分页参数
     * @return 抄送分页数据
     */
    PageVo<TaskVo> copy(String user, PageQuery pageQuery);

    /**
     * 查询待办任务所在节点的可操作按钮权限。
     *
     * @param taskId 待办任务主键
     * @return 按钮权限集合
     */
    List<ButtonPermissionVo> buttonPermissions(Long taskId);

    /**
     * 查询加签可选办理人，排除流程实例当前已有办理人。
     *
     * @param instanceId 流程实例主键
     * @return 加签可选办理人集合
     */
    List<DemoUserVo> addSignatureHandlers(Long instanceId);

    /**
     * 查询流程实例当前待办任务已有的办理人，供减签使用。
     *
     * @param instanceId 流程实例主键
     * @return 可减签办理人集合
     */
    List<DemoUserVo> reductionSignatureHandlers(Long instanceId);

    /**
     * 按流程实例变量查询待办任务可达的下一审批节点。
     * <p>每个节点会解析可选办理用户，用于弹窗选人。</p>
     *
     * @param taskId 待办任务主键
     * @return 下一审批节点及可选办理用户集合
     */
    List<TaskNodeVo> nextNodes(Long taskId);

    /**
     * 查询流程图中当前节点的前置审批节点。
     * <p>仅用于选择退回目标，不解析可选办理用户。</p>
     *
     * @param taskId 待办任务主键
     * @return 可退回审批节点集合，不解析可选办理用户
     */
    List<TaskNodeVo> backNodes(Long taskId);

    /**
     * 通过待办任务，并按需保存抄送人。
     *
     * @param request 办理请求
     * @return 办理后的流程实例
     */
    InstanceVo pass(TaskActionRequest request);

    /**
     * 退回待办任务到指定节点，并按需保存抄送人。
     *
     * @param request 办理请求
     * @return 办理后的流程实例
     */
    InstanceVo reject(TaskActionRequest request);

    /**
     * 转办待办任务给指定办理人。
     *
     * @param request 办理请求
     */
    void transfer(TaskActionRequest request);

    /**
     * 委派待办任务给指定办理人。
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
}
