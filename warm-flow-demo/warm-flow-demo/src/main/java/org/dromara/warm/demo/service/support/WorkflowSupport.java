package org.dromara.warm.demo.service.support;

import org.dromara.warm.demo.common.BizException;
import org.dromara.warm.demo.utils.ButtonPermissionUtils;
import org.dromara.warm.demo.vo.HistoryVo;
import org.dromara.warm.demo.vo.InstanceVo;
import org.dromara.warm.demo.vo.PageVo;
import org.dromara.warm.demo.vo.TaskNodeVo;
import org.dromara.warm.demo.vo.TaskVo;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.FlowCons;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.utils.page.Page;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Demo 应用服务共享底座。
 * <p>沉淀实例/任务两个应用服务的公共能力：引擎实体校验、名称/业务键缓存、
 * 节点按钮权限校验、VO 装配与状态文案解析。只依赖引擎 API，不含业务编排逻辑。</p>
 *
 * @author may
 * @since 2026/10/17
 */
@Component
public class WorkflowSupport {

    /**
     * definitionId -> 流程名称。
     */
    private final Map<Long, String> flowNameCache = new ConcurrentHashMap<>();

    /**
     * instanceId -> 业务主键。
     */
    private final Map<Long, String> businessIdCache = new ConcurrentHashMap<>();

    /**
     * 创建 Demo 当前用户对应的引擎操作者上下文。
     *
     * @param user 当前用户名
     * @return 操作者上下文
     */
    public OperatorContext operator(String user) {
        return new OperatorContext(user);
    }

    /**
     * 校验并加载待办任务。
     *
     * @param taskId 待办任务主键
     * @return 待办任务
     */
    public Task requireTask(Long taskId) {
        if (ObjectUtil.isNull(taskId)) {
            throw BizException.badRequest("taskId 不能为空");
        }
        Task task = FlowEngine.taskService().getById(taskId);
        if (ObjectUtil.isNull(task)) {
            throw BizException.notFound("任务不存在: " + taskId);
        }
        return task;
    }

    /**
     * 校验并加载流程实例。
     *
     * @param instanceId 流程实例主键
     * @return 流程实例
     */
    public Instance requireInstance(Long instanceId) {
        if (ObjectUtil.isNull(instanceId)) {
            throw BizException.badRequest("instanceId 不能为空");
        }
        Instance instance = FlowEngine.insService().getById(instanceId);
        if (ObjectUtil.isNull(instance)) {
            throw BizException.notFound("流程实例不存在: " + instanceId);
        }
        return instance;
    }

    /**
     * 查询流程名称（带缓存）。
     *
     * @param definitionId 流程定义主键
     * @return 流程名称
     */
    public String flowNameOf(Long definitionId) {
        if (ObjectUtil.isNull(definitionId)) {
            return null;
        }
        return flowNameCache.computeIfAbsent(definitionId, id -> {
            Definition definition = FlowEngine.defService().getById(id);
            return ObjectUtil.isNull(definition) ? null : definition.getFlowName();
        });
    }

    /**
     * 查询流程实例业务主键（带缓存）。
     *
     * @param instanceId 流程实例主键
     * @return 业务主键
     */
    public String businessIdOf(Long instanceId) {
        if (ObjectUtil.isNull(instanceId)) {
            return null;
        }
        return businessIdCache.computeIfAbsent(instanceId, id -> {
            Instance instance = FlowEngine.insService().getById(id);
            return ObjectUtil.isNull(instance) ? null : instance.getBusinessId();
        });
    }

    /**
     * 将流程用户中的用户权限标识转换为 Demo 用户名。
     *
     * @param processedBy 流程用户标识，例如 user:approver1
     * @return Demo 用户名
     */
    public String userNameOf(String processedBy) {
        return processedBy != null && processedBy.startsWith("user:")
            ? processedBy.substring("user:".length()) : processedBy;
    }

    /**
     * 判断任务节点是否为会签或票签节点。
     *
     * @param task 当前待办任务
     * @return 是否为会签或票签节点
     */
    public boolean isSignNode(Task task) {
        Node node = FlowEngine.nodeService().getByDefIdAndNodeCode(task.getDefinitionId(), task.getNodeCode());
        return node != null && StringUtils.isNotEmpty(node.getNodeRatio())
            && !CooperateType.isOrSign(node.getNodeRatio());
    }

    /**
     * 判断任务是否已结束，结束任务不再作为当前活动待办展示。
     * <p>
     * 终止 / 已完成 / 失效是终态，统一由引擎的 {@link FlowStatus#isTerminalKey(String)} 判定；
     * 撤销（cancel）不是终态——撤回重建的申请人待办仍需展示并提供重新提交。
     *
     * @param flowStatus 任务流程状态
     * @return 是否已结束
     */
    public boolean isClosedTaskStatus(String flowStatus) {
        return FlowStatus.isTerminalKey(flowStatus);
    }

    /**
     * 获取任务历史展示状态：先按任务维度解析，再回退到实例维度（撤销 / 终止等由实例状态落库）。
     *
     * @param status 状态编码
     * @return 状态名称
     */
    public String displayTaskStatus(String status) {
        String taskStatus = TaskStatus.getValueByKey(status);
        if (StringUtils.isNotEmpty(taskStatus)) {
            return taskStatus;
        }
        // 回退到实例维度文案：撤销 / 终止等收口状态写的是实例编码
        return StringUtils.emptyDefault(FlowStatus.getValueByKey(status), status);
    }

    /**
     * 校验节点操作按钮已开启。
     *
     * @param definitionId 流程定义主键
     * @param nodeCode     节点编码
     * @param code         按钮编码
     */
    public void checkNodeButton(Long definitionId, String nodeCode, String code) {
        if ("pass".equals(code)) {
            return;
        }
        boolean enabled = ButtonPermissionUtils.list(definitionId, nodeCode).stream()
            .anyMatch(item -> code.equals(item.getCode()) && item.isShow());
        if (!enabled) {
            throw BizException.badRequest("当前节点未开启操作：" + code);
        }
    }

    /**
     * 校验待办任务的节点操作按钮已开启。
     *
     * @param task 当前待办任务
     * @param code 按钮编码
     */
    public void checkNodeButton(Task task, String code) {
        checkNodeButton(task.getDefinitionId(), task.getNodeCode(), code);
    }

    /**
     * 转换引擎分页结果。
     *
     * @param page   引擎分页结果
     * @param mapper 记录转换函数
     * @param <T>    源记录类型
     * @param <V>    目标 VO 类型
     * @return Demo 分页结果
     */
    public <T, V> PageVo<V> toPage(Page<T> page, Function<T, V> mapper) {
        List<V> rows = page.getList() == null ? new ArrayList<>()
            : page.getList().stream().map(mapper).collect(Collectors.toList());
        return new PageVo<>(page.getTotal(), page.getPageNum(), page.getPageSize(), rows);
    }

    /**
     * 转换下一节点或退回节点，不解析可选办理用户。
     *
     * @param node 流程节点
     * @return 节点信息
     */
    public TaskNodeVo toTaskNodeVo(Node node) {
        TaskNodeVo vo = new TaskNodeVo();
        BeanUtils.copyProperties(node, vo);
        if (StringUtils.isEmpty(node.getPermissionFlag())) {
            vo.setPermissionFlags(new ArrayList<>());
            return vo;
        }
        vo.setPermissionFlags(Arrays.stream(node.getPermissionFlag().split(FlowCons.SPLIT_AT))
            .map(String::trim)
            .filter(StringUtils::isNotEmpty)
            .collect(Collectors.toList()));
        return vo;
    }

    /**
     * 转换流程实例，并补充流程名称与实例状态展示字段。
     *
     * @param instance 流程实例
     * @return 流程实例信息
     */
    public InstanceVo toInstanceVo(Instance instance) {
        InstanceVo vo = new InstanceVo();
        BeanUtils.copyProperties(instance, vo);
        vo.setFlowName(StringUtils.isNotEmpty(instance.getFlowName())
            ? instance.getFlowName() : flowNameOf(instance.getDefinitionId()));
        vo.setVariables(instance.getVariableMap());
        vo.setFlowStatusKey(instance.getFlowStatus());
        vo.setFlowStatusName(displayInstanceStatus(instance.getFlowStatus()));
        FlowEngine.taskService().getByInsId(instance.getId()).stream()
            .min(Comparator.comparing(Task::getId))
            .ifPresent(task -> {
                vo.setTaskId(task.getId());
                vo.setTaskStatusKey(task.getFlowStatus());
            });
        return vo;
    }

    /**
     * 解析实例状态名称：直接取引擎 {@link FlowStatus} 的展示文案，未命中时回落原始值。
     *
     * @param status 状态编码
     * @return 状态名称
     */
    public String displayInstanceStatus(String status) {
        return StringUtils.emptyDefault(FlowStatus.getValueByKey(status), status);
    }

    /**
     * 转换待办任务，并补充流程名称、业务主键、任务状态与办理人。
     *
     * @param task 待办任务
     * @return 待办任务信息
     */
    public TaskVo toTodoVo(FlowTask task) {
        TaskVo vo = new TaskVo();
        BeanUtils.copyProperties(task, vo);
        vo.setFlowName(StringUtils.isNotEmpty(task.getFlowName())
            ? task.getFlowName() : flowNameOf(task.getDefinitionId()));
        vo.setBusinessId(StringUtils.isNotEmpty(task.getBusinessId())
            ? task.getBusinessId() : businessIdOf(task.getInstanceId()));
        String todoStatusName = displayTaskStatus(task.getFlowStatus());
        vo.setTaskStatus(task.getFlowStatus());
        vo.setTaskStatusName(StringUtils.isEmpty(todoStatusName) ? task.getFlowStatus() : todoStatusName);
        vo.setAssignees(FlowEngine.userService().getPermission(task.getId(),
            UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey()));
        return vo;
    }

    /**
     * 转换已办或抄送历史任务，并补充流程名称、业务主键与任务状态。
     *
     * @param hisTask 历史任务
     * @return 已办或抄送任务信息
     */
    public TaskVo toDoneVo(HisTask hisTask) {
        TaskVo vo = new TaskVo();
        BeanUtils.copyProperties(hisTask, vo);
        vo.setFlowName(StringUtils.isNotEmpty(hisTask.getFlowName())
            ? hisTask.getFlowName() : flowNameOf(hisTask.getDefinitionId()));
        vo.setBusinessId(StringUtils.isNotEmpty(hisTask.getBusinessId())
            ? hisTask.getBusinessId() : businessIdOf(hisTask.getInstanceId()));
        String statusName = displayTaskStatus(hisTask.getFlowStatus());
        vo.setTaskStatus(hisTask.getFlowStatus());
        vo.setTaskStatusName(StringUtils.isEmpty(statusName) ? hisTask.getFlowStatus() : statusName);
        return vo;
    }

    /**
     * 转换抄送记录，状态列取流程实例状态。
     *
     * <p>抄送行的 {@code flow_status} 恒为 {@code copy}——那是留痕类型，不是流程进展。
     * 直接展示会变成「流程状态：抄送」，看着像状态机坏了；这里把状态换成实例状态，
     * 「这是一条抄送」由列表本身表达。</p>
     *
     * @param hisTask  抄送历史
     * @param instance 抄送所属流程实例，可为 {@code null}
     * @return 抄送记录
     */
    public TaskVo toCopyVo(HisTask hisTask, Instance instance) {
        TaskVo vo = toDoneVo(hisTask);
        if (instance != null) {
            vo.setTaskStatus(instance.getFlowStatus());
            String statusName = displayInstanceStatus(instance.getFlowStatus());
            vo.setTaskStatusName(StringUtils.isEmpty(statusName) ? instance.getFlowStatus() : statusName);
        }
        return vo;
    }

    /**
     * 转换审批历史，并补充状态展示字段。
     *
     * @param hisTask 历史任务
     * @return 审批历史信息
     */
    public HistoryVo toHistoryVo(HisTask hisTask) {
        HistoryVo vo = new HistoryVo();
        BeanUtils.copyProperties(hisTask, vo);
        vo.setTaskStatus(hisTask.getFlowStatus());
        String statusName = displayTaskStatus(hisTask.getFlowStatus());
        vo.setTaskStatusName(StringUtils.isEmpty(statusName) ? hisTask.getFlowStatus() : statusName);
        // 抄送行没有办理人，时间线上要显示「抄送给谁」，否则只剩一个「办理人：-」
        if (TaskStatus.COPY.getKey().equals(hisTask.getFlowStatus())) {
            vo.setCopyUsers(hisTask.getCollaborator());
        }
        return vo;
    }

    /**
     * 将当前活动任务转换为历史时间线中的待办节点，便于退回或撤回后继续办理。
     *
     * @param task           当前活动任务
     * @return 当前待办历史信息
     */
    public HistoryVo toCurrentHistoryVo(Task task) {
        HistoryVo vo = new HistoryVo();
        vo.setId(-task.getId());
        vo.setInstanceId(task.getInstanceId());
        vo.setNodeCode(task.getNodeCode());
        vo.setNodeName(task.getNodeName());
        vo.setApprover(FlowEngine.userService()
            .listByAssociatedAndTypes(task.getId(), UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey(),
                UserType.DEPUTE.getKey()).stream()
            .map(User::getProcessedBy)
            .collect(Collectors.joining(",")));
        vo.setTaskStatus(task.getFlowStatus());
        String statusName = displayTaskStatus(task.getFlowStatus());
        vo.setTaskStatusName(statusName);
        vo.setCurrent(true);
        vo.setCreateTime(task.getCreateTime());
        return vo;
    }

}
