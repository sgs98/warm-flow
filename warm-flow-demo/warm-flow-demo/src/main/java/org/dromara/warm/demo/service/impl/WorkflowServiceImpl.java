package org.dromara.warm.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.warm.demo.common.BizException;
import org.dromara.warm.demo.dto.StartInstanceRequest;
import org.dromara.warm.demo.dto.TaskActionRequest;
import org.dromara.warm.demo.enums.BusinessStatusEnum;
import org.dromara.warm.demo.mapper.DemoInstanceDeleteMapper;
import org.dromara.warm.demo.mapper.DemoTaskQueryMapper;
import org.dromara.warm.demo.service.UserService;
import org.dromara.warm.demo.service.WorkflowService;
import org.dromara.warm.demo.utils.ButtonPermissionUtils;
import org.dromara.warm.demo.vo.ButtonPermissionVo;
import org.dromara.warm.demo.vo.DemoUserVo;
import org.dromara.warm.demo.vo.HistoryVo;
import org.dromara.warm.demo.vo.InstanceVo;
import org.dromara.warm.demo.vo.PageVo;
import org.dromara.warm.demo.vo.StartInstanceVo;
import org.dromara.warm.demo.vo.TaskNodeVo;
import org.dromara.warm.demo.vo.TaskVo;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.FlowCons;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.utils.page.Page;
import org.dromara.warm.flow.orm.entity.FlowHisTask;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 流程实例与任务业务实现。
 * <p>负责流程编排、参数校验、引擎调用和响应 VO 装配。</p>
 *
 * @author may
 * @since 2026/9/5
 */
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    /**
     * demo 抄送用户类型；1/2/3 已被引擎分别用于审批、转办、委派权限。
     */
    private static final String COPY_USER_TYPE = "4";

    /**
     * definitionId -> 流程名称。
     */
    private final Map<Long, String> flowNameCache = new ConcurrentHashMap<>();

    /**
     * instanceId -> 业务主键。
     */
    private final Map<Long, String> businessIdCache = new ConcurrentHashMap<>();

    private final DemoTaskQueryMapper taskQueryMapper;
    private final DemoInstanceDeleteMapper instanceDeleteMapper;
    private final UserService userService;

    /** {@inheritDoc} */
    @Override
    public StartInstanceVo start(String user, StartInstanceRequest request) {
        try {
            FlowParams flowParams = identity(user).flowCode(request.getFlowCode());
            if (request.getVariables() != null) {
                flowParams.variable(request.getVariables());
            }
            Instance instance = FlowEngine.insService().start(request.getBusinessId(), flowParams);
            StartInstanceVo vo = new StartInstanceVo();
            vo.setInstanceId(instance.getId());
            FlowEngine.taskService().getByInsId(instance.getId()).stream()
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

    /** {@inheritDoc} */
    @Override
    public PageVo<InstanceVo> pageInstances(String user, Integer pageNum, Integer pageSize,
                                            Long definitionId, String flowStatus, String businessId) {
        int pn = normalizePage(pageNum);
        int ps = normalizeSize(pageSize);
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
        Page<Instance> page = FlowEngine.insService().page(query, new Page<>(pn, ps, "id", "DESC"));
        return toPage(page, this::toInstanceVo);
    }

    /** {@inheritDoc} */
    @Override
    public InstanceVo detailInstance(Long id) {
        return toInstanceVo(requireInstance(id));
    }

    /** {@inheritDoc} */
    @Override
    public List<HistoryVo> history(Long instanceId) {
        List<HisTask> list = FlowEngine.hisTaskService()
            .list(FlowEngine.newHisTask().setInstanceId(instanceId));
        return list.stream().map(this::toHistoryVo).collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>按流程用户、待办、历史任务、流程实例的顺序物理删除；方法运行在事务内，
     * 任意删除失败都会回滚已删除数据。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeInstance(Long instanceId) {
        requireInstance(instanceId);
        instanceDeleteMapper.deleteUsers(instanceId);
        instanceDeleteMapper.deleteTasks(instanceId);
        instanceDeleteMapper.deleteHisTasks(instanceId);
        if (instanceDeleteMapper.deleteInstance(instanceId) != 1) {
            throw BizException.badRequest("流程实例删除失败: " + instanceId);
        }
    }

    /** {@inheritDoc} */
    @Override
    public PageVo<TaskVo> todo(String user, Integer pageNum, Integer pageSize) {
        int pn = normalizePage(pageNum);
        int ps = normalizeSize(pageSize);
        List<String> permissions = StringUtils.isEmpty(user)
            ? new ArrayList<>() : userService.permissionFlags(user);
        if (permissions.isEmpty()) {
            return new PageVo<>(0, pn, ps, new ArrayList<>());
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FlowTask> page =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pn, ps);
        List<TaskVo> rows = taskQueryMapper.selectTodoPage(page, permissions).stream()
            .map(this::toTodoVo)
            .collect(Collectors.toList());
        return new PageVo<>(page.getTotal(), pn, ps, rows);
    }

    /** {@inheritDoc} */
    @Override
    public PageVo<TaskVo> done(String user, Integer pageNum, Integer pageSize) {
        int pn = normalizePage(pageNum);
        int ps = normalizeSize(pageSize);
        if (StringUtils.isEmpty(user)) {
            return new PageVo<>(0, pn, ps, new ArrayList<>());
        }
        Page<HisTask> page = FlowEngine.hisTaskService().page(
            FlowEngine.newHisTask().setApprover(user), new Page<>(pn, ps, "id", "DESC"));
        return toPage(page, this::toDoneVo);
    }

    /** {@inheritDoc} */
    @Override
    public PageVo<TaskVo> copy(String user, Integer pageNum, Integer pageSize) {
        int pn = normalizePage(pageNum);
        int ps = normalizeSize(pageSize);
        List<String> permissions = StringUtils.isEmpty(user)
            ? new ArrayList<>() : userService.permissionFlags(user);
        if (permissions.isEmpty()) {
            return new PageVo<>(0, pn, ps, new ArrayList<>());
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FlowHisTask> page =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pn, ps);
        List<TaskVo> rows = taskQueryMapper.selectCopyPage(page, permissions).stream()
            .map(this::toDoneVo)
            .collect(Collectors.toList());
        return new PageVo<>(page.getTotal(), pn, ps, rows);
    }

    /** {@inheritDoc} */
    @Override
    public List<ButtonPermissionVo> buttonPermissions(Long taskId) {
        Task task = requireTask(taskId);
        return ButtonPermissionUtils.list(task.getDefinitionId(), task.getNodeCode());
    }

    /**
     * {@inheritDoc}
     * <p>使用实例变量计算条件跳转；仅对下一审批节点解析可选办理用户。</p>
     */
    @Override
    public List<TaskNodeVo> nextNodes(Long taskId) {
        return nextNodes(requireTask(taskId));
    }

    /**
     * {@inheritDoc}
     * <p>返回流程图上的前置审批节点，仅用于选择退回目标，不解析可选办理用户。</p>
     */
    @Override
    public List<TaskNodeVo> backNodes(Long taskId) {
        Task task = requireTask(taskId);
        try {
            return FlowEngine.nodeService().previousNodeList(task.getDefinitionId(), task.getNodeCode()).stream()
                .filter(node -> NodeType.BETWEEN.getKey().equals(node.getNodeType()))
                .filter(node -> !node.getNodeCode().equals(task.getNodeCode()))
                .map(this::toTaskNodeVo)
                .collect(Collectors.toList());
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InstanceVo pass(TaskActionRequest req) {
        checkTaskAction(req);
        Task task = requireTask(req.getTaskId());
        checkNodeButton(task, "pass");
        if (hasNextHandler(req)) {
            checkNodeButton(task, "pop");
        }
        validateNextHandlers(task, req);
        InstanceVo instance = operate(req, false);
        saveCopyUsers(task, req.getCopyUsers());
        return instance;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InstanceVo reject(TaskActionRequest req) {
        checkTaskAction(req);
        if (StringUtils.isEmpty(req.getNodeCode())) {
            throw BizException.badRequest("退回节点不能为空");
        }
        Task task = requireTask(req.getTaskId());
        checkNodeButton(task, "back");
        fallbackRejectHandler(task, req);
        InstanceVo instance = operate(req, true);
        saveCopyUsers(task, req.getCopyUsers());
        return instance;
    }

    /** {@inheritDoc} */
    @Override
    public void transfer(TaskActionRequest req) {
        checkTaskAction(req);
        Task task = requireTask(req.getTaskId());
        checkNodeButton(task, "transfer");
        run(req.getUser(), flowParams -> {
            if (req.getNextHandlers() == null) {
                flowParams.addHandlers(new ArrayList<>());
            } else {
                flowParams.addHandlers(req.getNextHandlers().stream()
                    .filter(StringUtils::isNotEmpty)
                    .map(String::trim)
                    .collect(Collectors.toList()));
            }
            FlowEngine.taskService().transfer(task.getId(), flowParams);
        });
    }

    /** {@inheritDoc} */
    @Override
    public void depute(TaskActionRequest req) {
        checkTaskAction(req);
        Task task = requireTask(req.getTaskId());
        checkNodeButton(task, "trust");
        run(req.getUser(), flowParams -> {
            if (req.getNextHandlers() == null) {
                flowParams.addHandlers(new ArrayList<>());
            } else {
                flowParams.addHandlers(req.getNextHandlers().stream()
                    .filter(StringUtils::isNotEmpty)
                    .map(String::trim)
                    .collect(Collectors.toList()));
            }
            FlowEngine.taskService().depute(task.getId(), flowParams);
        });
    }

    /** {@inheritDoc} */
    @Override
    public void addSignature(TaskActionRequest req) {
        checkTaskAction(req);
        Task task = requireTask(req.getTaskId());
        checkNodeButton(task, "addSign");
        run(req.getUser(), flowParams ->
            FlowEngine.taskService().addSignature(task.getId(), flowParams.addHandlers(req.getAddHandlers())));
    }

    /** {@inheritDoc} */
    @Override
    public void reductionSignature(TaskActionRequest req) {
        checkTaskAction(req);
        Task task = requireTask(req.getTaskId());
        checkNodeButton(task, "subSign");
        run(req.getUser(), flowParams ->
            FlowEngine.taskService().reductionSignature(task.getId(),
                flowParams.reductionHandlers(req.getReductionHandlers())));
    }

    /** {@inheritDoc} */
    @Override
    public void revoke(String user, Long instanceId) {
        run(user, flowParams -> FlowEngine.taskService().revoke(instanceId, flowParams));
    }

    /** {@inheritDoc} */
    @Override
    public void termination(String user, Long instanceId) {
        Instance instance = requireInstance(instanceId);
        checkNodeButton(instance.getDefinitionId(), instance.getNodeCode(), "termination");
        run(user, flowParams -> FlowEngine.taskService().terminationByInsId(instanceId, flowParams));
    }

    private InstanceVo operate(TaskActionRequest req, boolean reject) {
        try {
            FlowParams flowParams = identity(req.getUser())
                .message(req.getMessage())
                .skipType(reject ? SkipType.REJECT.getKey() : SkipType.PASS.getKey())
                .nodeCode(req.getNodeCode());
            if (req.getVariables() != null) {
                flowParams.variable(req.getVariables());
            }
            applyNextHandlerMap(req, flowParams);
            if (req.getNextHandlers() != null && !req.getNextHandlers().isEmpty()) {
                flowParams.nextHandler(req.getNextHandlers().toArray(new String[0]));
            }
            return toInstanceVo(FlowEngine.taskService().skip(req.getTaskId(), flowParams));
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    private void checkTaskAction(TaskActionRequest req) {
        if (req == null) {
            throw BizException.badRequest("任务操作请求不能为空");
        }
        if (StringUtils.isEmpty(req.getUser())) {
            throw BizException.badRequest("user 不能为空");
        }
        if (ObjectUtil.isNull(req.getTaskId())) {
            throw BizException.badRequest("taskId 不能为空");
        }
    }

    private void run(String user, Consumer<FlowParams> action) {
        try {
            action.accept(identity(user));
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    private FlowParams identity(String user) {
        return FlowParams.build()
            .handler(user)
            .permissionFlag(userService.permissionFlags(user));
    }

    private List<TaskNodeVo> nextNodes(Task task) {
        Instance instance = requireInstance(task.getInstanceId());
        try {
            List<TaskNodeVo> nodes = FlowEngine.nodeService().getNextNodeList(
                task.getDefinitionId(), task.getNodeCode(), null,
                SkipType.PASS.getKey(), instance.getVariableMap()).stream()
                .filter(node -> NodeType.BETWEEN.getKey().equals(node.getNodeType()))
                .map(this::toTaskNodeVo)
                .collect(Collectors.toList());
            nodes.forEach(node ->
                node.setSelectableUsers(userService.listByPermissionFlags(node.getPermissionFlags())));
            return nodes;
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    private boolean hasNextHandler(TaskActionRequest req) {
        return (req.getNextHandlers() != null && !req.getNextHandlers().isEmpty())
            || (req.getNextHandlerMap() != null && !req.getNextHandlerMap().isEmpty());
    }

    private void applyNextHandlerMap(TaskActionRequest req, FlowParams flowParams) {
        if (req.getNextHandlerMap() == null || req.getNextHandlerMap().isEmpty()) {
            return;
        }
        Map<String, Object> variables = req.getVariables() == null
            ? new HashMap<>() : new HashMap<>(req.getVariables());
        req.getNextHandlerMap().forEach((nodeCode, handlers) -> {
            List<String> validHandlers = handlers == null ? new ArrayList<>() : handlers.stream()
                .filter(StringUtils::isNotEmpty)
                .map(String::trim)
                .collect(Collectors.toList());
            if (nodeCode != null && !validHandlers.isEmpty()) {
                variables.put(SkipType.PASS.getKey() + ":" + nodeCode, String.join(",", validHandlers));
            }
        });
        flowParams.variable(variables);
    }

    private void validateNextHandlers(Task task, TaskActionRequest req) {
        if (!hasNextHandler(req)) {
            return;
        }
        List<TaskNodeVo> nodes = nextNodes(task);
        if (req.getNextHandlerMap() != null && !req.getNextHandlerMap().isEmpty()) {
            validateNextHandlerMap(nodes, req.getNextHandlerMap());
            return;
        }
        if (nodes.size() != 1) {
            throw BizException.badRequest("存在多个下一节点时必须按节点选择办理人");
        }
        Set<String> allowed = userNamesOf(nodes.get(0));
        req.getNextHandlers().forEach(handler -> {
            if (handler == null || !allowed.contains(handler.trim())) {
                throw BizException.badRequest("办理人不属于下一节点可选范围: " + handler);
            }
        });
    }

    private void validateNextHandlerMap(List<TaskNodeVo> nodes, Map<String, List<String>> handlerMap) {
        Map<String, Set<String>> allowedUsers = nodes.stream()
            .collect(Collectors.toMap(TaskNodeVo::getNodeCode, this::userNamesOf));
        handlerMap.forEach((nodeCode, handlers) -> {
            Set<String> allowed = allowedUsers.get(nodeCode);
            if (allowed == null) {
                throw BizException.badRequest("下一节点不存在或不可达: " + nodeCode);
            }
            handlers.forEach(handler -> {
                if (handler == null || !allowed.contains(handler.trim())) {
                    throw BizException.badRequest("办理人不属于下一节点可选范围: " + handler);
                }
            });
        });
    }

    private Set<String> userNamesOf(TaskNodeVo node) {
        return node.getSelectableUsers().stream()
            .map(DemoUserVo::getUserName)
            .collect(Collectors.toSet());
    }

    /**
     * 转换下一节点或退回节点，不解析可选办理用户。
     *
     * @param node 流程节点
     * @return 节点信息
     */
    private TaskNodeVo toTaskNodeVo(Node node) {
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
     * 转换流程实例，并补充流程名称与业务状态展示字段。
     *
     * @param instance 流程实例
     * @return 流程实例信息
     */
    private InstanceVo toInstanceVo(Instance instance) {
        InstanceVo vo = new InstanceVo();
        BeanUtils.copyProperties(instance, vo);
        vo.setFlowName(StringUtils.isNotEmpty(instance.getFlowName())
            ? instance.getFlowName() : flowNameOf(instance.getDefinitionId()));
        BusinessStatusEnum status = BusinessStatusEnum.fromFlowStatus(instance.getFlowStatus());
        vo.setFlowStatusKey(instance.getFlowStatus());
        vo.setFlowStatusName(FlowStatus.getValueByKey(instance.getFlowStatus()));
        vo.setBusinessStatus(status.getStatus());
        vo.setBusinessStatusName(status.getDesc());
        return vo;
    }

    /**
     * 转换待办任务，并补充流程名称、业务主键、状态与办理人。
     *
     * @param task 待办任务
     * @return 待办任务信息
     */
    private TaskVo toTodoVo(FlowTask task) {
        TaskVo vo = new TaskVo();
        BeanUtils.copyProperties(task, vo);
        vo.setFlowName(StringUtils.isNotEmpty(task.getFlowName())
            ? task.getFlowName() : flowNameOf(task.getDefinitionId()));
        vo.setBusinessId(StringUtils.isNotEmpty(task.getBusinessId())
            ? task.getBusinessId() : businessIdOf(task.getInstanceId()));
        BusinessStatusEnum status = BusinessStatusEnum.fromFlowStatus(task.getFlowStatus());
        vo.setFlowStatusKey(task.getFlowStatus());
        vo.setFlowStatusName(FlowStatus.getValueByKey(task.getFlowStatus()));
        vo.setBusinessStatus(status.getStatus());
        vo.setBusinessStatusName(status.getDesc());
        vo.setAssignees(FlowEngine.userService().getPermission(task.getId(),
            UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey()));
        return vo;
    }

    /**
     * 转换已办或抄送历史任务，并补充流程名称、业务主键与状态。
     *
     * @param hisTask 历史任务
     * @return 已办或抄送任务信息
     */
    private TaskVo toDoneVo(HisTask hisTask) {
        TaskVo vo = new TaskVo();
        BeanUtils.copyProperties(hisTask, vo);
        vo.setFlowName(StringUtils.isNotEmpty(hisTask.getFlowName())
            ? hisTask.getFlowName() : flowNameOf(hisTask.getDefinitionId()));
        vo.setBusinessId(StringUtils.isNotEmpty(hisTask.getBusinessId())
            ? hisTask.getBusinessId() : businessIdOf(hisTask.getInstanceId()));
        BusinessStatusEnum status = BusinessStatusEnum.fromFlowStatus(hisTask.getFlowStatus());
        vo.setFlowStatusKey(hisTask.getFlowStatus());
        vo.setFlowStatusName(FlowStatus.getValueByKey(hisTask.getFlowStatus()));
        vo.setBusinessStatus(status.getStatus());
        vo.setBusinessStatusName(status.getDesc());
        return vo;
    }

    /**
     * 转换审批历史，并补充状态展示字段。
     *
     * @param hisTask 历史任务
     * @return 审批历史信息
     */
    private HistoryVo toHistoryVo(HisTask hisTask) {
        HistoryVo vo = new HistoryVo();
        BeanUtils.copyProperties(hisTask, vo);
        BusinessStatusEnum status = BusinessStatusEnum.fromFlowStatus(hisTask.getFlowStatus());
        vo.setFlowStatusKey(hisTask.getFlowStatus());
        vo.setFlowStatusName(FlowStatus.getValueByKey(hisTask.getFlowStatus()));
        vo.setBusinessStatus(status.getStatus());
        vo.setBusinessStatusName(status.getDesc());
        return vo;
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
    private <T, V> PageVo<V> toPage(Page<T> page, Function<T, V> mapper) {
        List<V> rows = page.getList() == null ? new ArrayList<>()
            : page.getList().stream().map(mapper).collect(Collectors.toList());
        return new PageVo<>(page.getTotal(), page.getPageNum(), page.getPageSize(), rows);
    }

    /**
     * 查询流程名称。
     *
     * @param definitionId 流程定义主键
     * @return 流程名称
     */
    private String flowNameOf(Long definitionId) {
        if (ObjectUtil.isNull(definitionId)) {
            return null;
        }
        return flowNameCache.computeIfAbsent(definitionId, id -> {
            Definition definition = FlowEngine.defService().getById(id);
            return ObjectUtil.isNull(definition) ? null : definition.getFlowName();
        });
    }

    /**
     * 查询流程实例业务主键。
     *
     * @param instanceId 流程实例主键
     * @return 业务主键
     */
    private String businessIdOf(Long instanceId) {
        if (ObjectUtil.isNull(instanceId)) {
            return null;
        }
        return businessIdCache.computeIfAbsent(instanceId, id -> {
            Instance instance = FlowEngine.insService().getById(id);
            return ObjectUtil.isNull(instance) ? null : instance.getBusinessId();
        });
    }

    private Task requireTask(Long taskId) {
        if (ObjectUtil.isNull(taskId)) {
            throw BizException.badRequest("taskId 不能为空");
        }
        Task task = FlowEngine.taskService().getById(taskId);
        if (ObjectUtil.isNull(task)) {
            throw BizException.notFound("任务不存在: " + taskId);
        }
        return task;
    }

    private Instance requireInstance(Long instanceId) {
        if (ObjectUtil.isNull(instanceId)) {
            throw BizException.badRequest("instanceId 不能为空");
        }
        Instance instance = FlowEngine.insService().getById(instanceId);
        if (ObjectUtil.isNull(instance)) {
            throw BizException.notFound("流程实例不存在: " + instanceId);
        }
        return instance;
    }

    private void fallbackRejectHandler(Task task, TaskActionRequest req) {
        Node targetNode = FlowEngine.nodeService().getByDefIdAndNodeCode(
            task.getDefinitionId(), req.getNodeCode());
        if (ObjectUtil.isNull(targetNode)) {
            throw BizException.badRequest("退回节点不存在: " + req.getNodeCode());
        }
        if (StringUtils.isNotEmpty(targetNode.getPermissionFlag()) || hasNextHandler(req)) {
            return;
        }
        Instance instance = requireInstance(task.getInstanceId());
        if (StringUtils.isNotEmpty(instance.getCreateBy())) {
            req.setNextHandlers(Collections.singletonList(instance.getCreateBy()));
        }
    }

    private void saveCopyUsers(Task task, List<String> copyUsers) {
        List<String> users = normalizeCopyUsers(copyUsers);
        if (users.isEmpty()) {
            return;
        }
        List<User> flowUsers = users.stream().map(userName -> FlowEngine.newUser()
                .setType(COPY_USER_TYPE)
                .setProcessedBy(userName)
                .setAssociated(task.getId()))
            .collect(Collectors.toList());
        FlowEngine.userService().saveBatch(flowUsers);
    }

    private List<String> normalizeCopyUsers(List<String> copyUsers) {
        if (copyUsers == null || copyUsers.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> memoryUsers = userService.listApprovers().stream()
            .map(DemoUserVo::getUserName)
            .collect(Collectors.toSet());
        Set<String> result = new LinkedHashSet<>();
        copyUsers.forEach(copyUser -> {
            if (StringUtils.isNotEmpty(copyUser)) {
                String userName = copyUser.trim();
                if (!memoryUsers.contains(userName)) {
                    throw BizException.badRequest("抄送人不存在: " + userName);
                }
                result.add(userName);
            }
        });
        return new ArrayList<>(result);
    }

    private void checkNodeButton(Task task, String code) {
        checkNodeButton(task.getDefinitionId(), task.getNodeCode(), code);
    }

    private void checkNodeButton(Long definitionId, String nodeCode, String code) {
        if ("pass".equals(code)) {
            return;
        }
        boolean enabled = ButtonPermissionUtils.list(definitionId, nodeCode).stream()
            .anyMatch(item -> code.equals(item.getCode()) && item.isShow());
        if (!enabled) {
            throw BizException.badRequest("当前节点未开启操作：" + code);
        }
    }

    private int normalizePage(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizeSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }
}
