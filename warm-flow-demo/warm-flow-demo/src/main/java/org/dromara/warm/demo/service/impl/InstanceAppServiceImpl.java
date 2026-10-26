package org.dromara.warm.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.warm.demo.common.BizException;
import org.dromara.warm.demo.dto.PageQuery;
import org.dromara.warm.demo.dto.StartInstanceRequest;
import org.dromara.warm.demo.mapper.DemoInstanceDeleteMapper;
import org.dromara.warm.demo.service.InstanceAppService;
import org.dromara.warm.demo.service.support.WorkflowSupport;
import org.dromara.warm.demo.vo.HistoryVo;
import org.dromara.warm.demo.vo.InstanceVo;
import org.dromara.warm.demo.vo.PageVo;
import org.dromara.warm.demo.vo.StartInstanceVo;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.utils.page.Page;
import org.dromara.warm.flow.core.workflow.command.RevokeCommand;
import org.dromara.warm.flow.core.workflow.command.StartCommand;
import org.dromara.warm.flow.core.workflow.command.TerminateCommand;
import org.dromara.warm.flow.core.workflow.result.WorkflowResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程实例应用服务实现。
 * <p>负责实例域用例的流程编排、参数校验、引擎调用，VO 装配委托 {@link WorkflowSupport}。</p>
 *
 * @author may
 * @since 2026/10/17
 */
@Service
@RequiredArgsConstructor
public class InstanceAppServiceImpl implements InstanceAppService {

    private final DemoInstanceDeleteMapper instanceDeleteMapper;
    private final WorkflowSupport support;

    /**
     * 发起流程，并返回当前用户需要办理的首个待办任务。
     *
     * @param user    当前用户名
     * @param request 发起请求
     * @return 发起结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StartInstanceVo start(String user, StartInstanceRequest request) {
        try {
            StartCommand command = new StartCommand();
            command.setOperator(support.operator(user));
            command.setBusinessId(request.getBusinessId());
            command.setFlowCode(request.getFlowCode());
            command.setVariables(request.getVariables());
            WorkflowResult result = FlowEngine.workflow().start(command);
            Instance instance = support.requireInstance(result.getInstanceId());
            List<Task> tasks = FlowEngine.taskService().getByInsId(instance.getId());
            StartInstanceVo vo = new StartInstanceVo();
            vo.setInstanceId(instance.getId());
            tasks.stream()
                .min(Comparator.comparing(Task::getId))
                .ifPresent(task -> {
                    vo.setTaskId(task.getId());
                    vo.setNodeName(task.getNodeName());
                });
            return vo;
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /**
     * 分页查询流程实例。
     *
     * @param user         当前用户名
     * @param pageQuery    分页参数
     * @param definitionId 流程定义主键
     * @param flowStatus   流程状态
     * @param businessId   业务主键
     * @return 流程实例分页数据
     */
    @Override
    public PageVo<InstanceVo> pageInstances(String user, PageQuery pageQuery,
                                            Long definitionId, String flowStatus, String businessId) {
        Instance query = FlowEngine.newIns();
        query.setDefinitionId(definitionId);
        if (StringUtils.isNotEmpty(flowStatus)) {
            query.setFlowStatus(flowStatus);
        }
        if (StringUtils.isNotEmpty(businessId)) {
            query.setBusinessId(businessId);
        }
        if (StringUtils.isNotEmpty(user)) {
            query.setCreateBy(user);
        }
        Page<Instance> page = FlowEngine.insService().page(query, pageQuery.build("id", "DESC"));
        return support.toPage(page, support::toInstanceVo);
    }

    /**
     * 查询流程实例详情。
     *
     * @param id 流程实例主键
     * @return 流程实例详情
     */
    @Override
    public InstanceVo detailInstance(Long id) {
        return support.toInstanceVo(support.requireInstance(id));
    }

    /**
     * 查询流程实例审批历史（含当前活动待办）。
     *
     * @param instanceId 流程实例主键
     * @return 审批历史集合，最新在前
     */
    @Override
    public List<HistoryVo> history(Long instanceId) {
        List<HisTask> list = FlowEngine.hisTaskService()
            .list(FlowEngine.newHisTask().setInstanceId(instanceId));
        List<HistoryVo> result = list.stream().map(support::toHistoryVo).collect(Collectors.toList());
        FlowEngine.taskService().getByInsId(instanceId).stream()
            .filter(task -> !support.isClosedTaskStatus(task.getFlowStatus()))
            .map(task -> support.toCurrentHistoryVo(task))
            .forEach(result::add);
        Collections.reverse(result);
        return result;
    }

    /**
     * 物理删除流程实例及其关联数据。
     * <p>按等待事件、流程用户、待办、历史任务、流程实例的顺序删除；方法运行在事务内，
     * 任意删除失败都会回滚已删除数据。</p>
     *
     * @param instanceId 流程实例主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeInstance(Long instanceId) {
        support.requireInstance(instanceId);
        instanceDeleteMapper.deleteEventWaits(instanceId);
        instanceDeleteMapper.deleteUsers(instanceId);
        instanceDeleteMapper.deleteTasks(instanceId);
        instanceDeleteMapper.deleteHisTasks(instanceId);
        if (instanceDeleteMapper.deleteInstance(instanceId) != 1) {
            throw BizException.badRequest("流程实例删除失败: " + instanceId);
        }
    }

    /**
     * 批量物理删除流程实例及其关联数据。
     * <p>先校验全部实例存在，再按等待事件、流程用户、待办、历史任务、流程实例的顺序批量删除；
     * 方法运行在事务内，任意一步失败都会整体回滚。</p>
     *
     * @param instanceIds 流程实例主键集合
     * @return 实际删除的流程实例数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeInstances(List<Long> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            throw BizException.badRequest("请选择要删除的流程实例");
        }
        List<Long> ids = instanceIds.stream()
            .filter(ObjectUtil::isNotNull)
            .distinct()
            .collect(Collectors.toList());
        if (ids.isEmpty()) {
            throw BizException.badRequest("请选择要删除的流程实例");
        }
        ids.forEach(support::requireInstance);
        instanceDeleteMapper.deleteEventWaitsByInstanceIds(ids);
        instanceDeleteMapper.deleteUsersByInstanceIds(ids);
        instanceDeleteMapper.deleteTasksByInstanceIds(ids);
        instanceDeleteMapper.deleteHisTasksByInstanceIds(ids);
        if (instanceDeleteMapper.deleteInstancesByInstanceIds(ids) != ids.size()) {
            throw BizException.badRequest("流程实例批量删除失败");
        }
        return ids.size();
    }

    /**
     * 撤回当前用户发起的流程实例。
     *
     * @param user       当前用户名
     * @param instanceId 流程实例主键
     */
    @Override
    public void revoke(String user, Long instanceId) {
        try {
            RevokeCommand command = new RevokeCommand();
            command.setOperator(support.operator(user));
            command.setInstanceId(instanceId);
            FlowEngine.workflow().revoke(command);
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /**
     * 终止流程实例。
     *
     * @param user       当前用户名
     * @param instanceId 流程实例主键
     */
    @Override
    public void termination(String user, Long instanceId) {
        Instance instance = support.requireInstance(instanceId);
        support.checkNodeButton(instance.getDefinitionId(), instance.getNodeCode(), "termination");
        try {
            TerminateCommand command = new TerminateCommand();
            command.setOperator(support.operator(user));
            command.setInstanceId(instanceId);
            FlowEngine.workflow().terminate(command);
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }
}
