package org.dromara.warm.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.warm.demo.common.BizException;
import org.dromara.warm.demo.dto.PageQuery;
import org.dromara.warm.demo.dto.StartInstanceRequest;
import org.dromara.warm.demo.dto.TaskActionRequest;
import org.dromara.warm.demo.enums.BusinessStatusEnum;
import org.dromara.warm.demo.enums.TaskStatusEnum;
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
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.utils.page.Page;
import org.dromara.warm.flow.core.workflow.command.AddSignerCommand;
import org.dromara.warm.flow.core.workflow.command.CompleteCommand;
import org.dromara.warm.flow.core.workflow.command.DelegateCommand;
import org.dromara.warm.flow.core.workflow.command.JumpCommand;
import org.dromara.warm.flow.core.workflow.command.RejectCommand;
import org.dromara.warm.flow.core.workflow.command.RemoveSignerCommand;
import org.dromara.warm.flow.core.workflow.command.RevokeCommand;
import org.dromara.warm.flow.core.workflow.command.StartCommand;
import org.dromara.warm.flow.core.workflow.command.TerminateCommand;
import org.dromara.warm.flow.core.workflow.command.TransferCommand;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.dromara.warm.flow.core.workflow.result.WorkflowResult;
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
            command.setOperator(operator(user));
            command.setBusinessId(request.getBusinessId());
            command.setFlowCode(request.getFlowCode());
            command.setVariables(request.getVariables());
            command.setInstanceStatus(BusinessStatusEnum.DRAFT.getStatus());
            command.setHistoryTaskStatus(TaskStatusEnum.PASS.getStatus());
            WorkflowResult result = FlowEngine.workflow().start(command);
            Instance instance = requireInstance(result.getInstanceId());
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
     * 分页查询当前用户发起的流程实例。
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
        return toPage(page, this::toInstanceVo);
    }

    /**
     * 查询流程实例详情。
     *
     * @param id 流程实例主键
     * @return 流程实例详情
     */
    @Override
    public InstanceVo detailInstance(Long id) {
        return toInstanceVo(requireInstance(id));
    }

    /**
     * 查询流程实例审批历史。
     *
     * @param instanceId 流程实例主键
     * @return 审批历史集合
     */
    @Override
    public List<HistoryVo> history(Long instanceId) {
        List<HisTask> list = FlowEngine.hisTaskService()
            .list(FlowEngine.newHisTask().setInstanceId(instanceId));
        List<HistoryVo> result = list.stream().map(this::toHistoryVo).collect(Collectors.toList());
        FlowEngine.taskService().getByInsId(instanceId).stream()
            .filter(task -> !isClosedTaskStatus(task.getFlowStatus()))
            .map(this::toCurrentHistoryVo)
            .forEach(result::add);
        Collections.reverse(result);
        return result;
    }

    /**
     * 判断任务是否已结束，结束任务不再作为当前活动待办展示。
     *
     * @param flowStatus 任务流程状态
     * @return 是否已结束
     */
    private boolean isClosedTaskStatus(String flowStatus) {
        return BusinessStatusEnum.CANCEL.getStatus().equals(flowStatus)
            || BusinessStatusEnum.TERMINATION.getStatus().equals(flowStatus)
            || BusinessStatusEnum.FINISH.getStatus().equals(flowStatus)
            || BusinessStatusEnum.INVALID.getStatus().equals(flowStatus);
    }

    /**
     * 物理删除流程实例及其关联数据。
     * <p>按流程用户、待办、历史任务、流程实例的顺序删除；方法运行在事务内，
     * 任意删除失败都会回滚已删除数据。</p>
     *
     * @param instanceId 流程实例主键
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

    /**
     * 分页查询当前用户待办任务。
     *
     * @param user     当前用户名
     * @param pageQuery 分页参数
     * @return 待办任务分页数据
     */
    @Override
    public PageVo<TaskVo> todo(String user, PageQuery pageQuery) {
        List<String> permissions = StringUtils.isEmpty(user)
            ? new ArrayList<>() : userService.permissionFlags(user);
        if (permissions.isEmpty()) {
            return new PageVo<>(0, pageQuery.normalizedPageNum(), pageQuery.normalizedPageSize(), new ArrayList<>());
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FlowTask> page = pageQuery.build();
        List<TaskVo> rows = taskQueryMapper.selectTodoPage(page, permissions).stream()
            .map(this::toTodoVo)
            .collect(Collectors.toList());
        return new PageVo<>(page.getTotal(),
            pageQuery.normalizedPageNum(), pageQuery.normalizedPageSize(), rows);
    }

    /**
     * 分页查询当前用户已办任务。
     *
     * @param user     当前用户名
     * @param pageQuery 分页参数
     * @return 已办任务分页数据
     */
    @Override
    public PageVo<TaskVo> done(String user, PageQuery pageQuery) {
        if (StringUtils.isEmpty(user)) {
            return new PageVo<>(0, pageQuery.normalizedPageNum(), pageQuery.normalizedPageSize(), new ArrayList<>());
        }
        Page<HisTask> page = FlowEngine.hisTaskService().page(
            FlowEngine.newHisTask().setApprover(user), pageQuery.build("id", "DESC"));
        return toPage(page, this::toDoneVo);
    }

    /**
     * 分页查询当前用户收到的抄送。
     *
     * @param user     当前用户名
     * @param pageQuery 分页参数
     * @return 抄送分页数据
     */
    @Override
    public PageVo<TaskVo> copy(String user, PageQuery pageQuery) {
        List<String> permissions = StringUtils.isEmpty(user)
            ? new ArrayList<>() : userService.permissionFlags(user);
        if (permissions.isEmpty()) {
            return new PageVo<>(0, pageQuery.normalizedPageNum(), pageQuery.normalizedPageSize(), new ArrayList<>());
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FlowHisTask> page = pageQuery.build();
        List<TaskVo> rows = taskQueryMapper.selectCopyPage(page, permissions).stream()
            .map(this::toDoneVo)
            .collect(Collectors.toList());
        return new PageVo<>(page.getTotal(),
            pageQuery.normalizedPageNum(), pageQuery.normalizedPageSize(), rows);
    }

    /**
     * 查询待办任务所在节点的按钮权限。
     *
     * @param taskId 待办任务主键
     * @return 按钮权限集合
     */
    @Override
    public List<ButtonPermissionVo> buttonPermissions(Long taskId) {
        Task task = requireTask(taskId);
        List<ButtonPermissionVo> permissions = ButtonPermissionUtils.list(task.getDefinitionId(), task.getNodeCode());
        if (!isSignNode(task)) {
            permissions.removeIf(item -> "addSign".equals(item.getCode()) || "subSign".equals(item.getCode()));
        }
        return permissions;
    }

    /**
     * 查询加签可选办理人，排除流程实例当前已有办理人。
     *
     * @param instanceId 流程实例主键
     * @return 加签可选办理人集合
     */
    @Override
    public List<DemoUserVo> addSignatureHandlers(Long instanceId) {
        if (!hasSignTask(instanceId)) {
            return new ArrayList<>();
        }
        Set<String> handlers = currentHandlerNames(instanceId);
        return userService.listApprovers().stream()
            .filter(user -> !handlers.contains(user.getUserName()))
            .collect(Collectors.toList());
    }

    /**
     * 查询流程实例当前待办任务已有的办理人，供减签选择使用。
     *
     * @param instanceId 流程实例主键
     * @return 可减签办理人集合
     */
    @Override
    public List<DemoUserVo> reductionSignatureHandlers(Long instanceId) {
        if (!hasSignTask(instanceId)) {
            return new ArrayList<>();
        }
        Set<String> handlers = currentHandlerNames(instanceId);
        return userService.listApprovers().stream()
            .filter(user -> handlers.contains(user.getUserName()))
            .collect(Collectors.toList());
    }

    /**
     * 查询流程实例当前待办任务的办理人用户名。
     *
     * @param instanceId 流程实例主键
     * @return 办理人用户名集合
     */
    private Set<String> currentHandlerNames(Long instanceId) {
        requireInstance(instanceId);
        Set<String> handlers = new LinkedHashSet<>();
        FlowEngine.taskService().getByInsId(instanceId).forEach(task -> handlers.addAll(currentHandlerNames(task)));
        return handlers;
    }

    /**
     * 查询单个待办任务当前节点的办理人。
     *
     * @param task 待办任务
     * @return 当前节点办理人用户名集合
     */
    private Set<String> currentHandlerNames(Task task) {
        return FlowEngine.userService().listByAssociatedAndTypes(task.getId(), UserType.APPROVAL.getKey(),
                UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey()).stream()
            .map(User::getProcessedBy)
            .map(this::userNameOf)
            .filter(StringUtils::isNotEmpty)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 判断实例当前是否存在会签或票签任务。
     *
     * @param instanceId 流程实例主键
     * @return 是否存在会签或票签任务
     */
    private boolean hasSignTask(Long instanceId) {
        requireInstance(instanceId);
        return FlowEngine.taskService().getByInsId(instanceId).stream().anyMatch(this::isSignNode);
    }

    /**
     * 校验当前任务只能在会签或票签节点执行加签、减签。
     *
     * @param task 当前待办任务
     */
    private void checkSignNode(Task task) {
        if (!isSignNode(task)) {
            throw BizException.badRequest("只有会签或票签节点支持加签、减签");
        }
    }

    /**
     * 判断任务节点是否为会签或票签节点。
     *
     * @param task 当前待办任务
     * @return 是否为会签或票签节点
     */
    private boolean isSignNode(Task task) {
        Node node = FlowEngine.nodeService().getByDefIdAndNodeCode(task.getDefinitionId(), task.getNodeCode());
        return node != null && StringUtils.isNotEmpty(node.getNodeRatio())
            && !CooperateType.isOrSign(node.getNodeRatio());
    }

    /**
     * 将流程用户中的用户权限标识转换为 Demo 用户名。
     *
     * @param processedBy 流程用户标识，例如 user:approver1
     * @return Demo 用户名
     */
    private String userNameOf(String processedBy) {
        return processedBy != null && processedBy.startsWith("user:")
            ? processedBy.substring("user:".length()) : processedBy;
    }

    /**
     * 校验减签办理人，当前任务至少保留一名办理人。
     *
     * @param task              当前待办任务
     * @param reductionHandlers 待移除办理人
     */
    private void validateReductionHandlers(Task task, List<String> reductionHandlers) {
        if (reductionHandlers == null || reductionHandlers.isEmpty()) {
            throw BizException.badRequest("减签办理人不能为空");
        }
        Set<String> currentHandlers = currentHandlerNames(task);
        Set<String> selectedHandlers = reductionHandlers.stream()
            .peek(handler -> {
                if (StringUtils.isEmpty(handler)) {
                    throw BizException.badRequest("减签办理人不能为空");
                }
            })
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!currentHandlers.containsAll(selectedHandlers)) {
            throw BizException.badRequest("减签办理人必须是当前任务办理人");
        }
        if (currentHandlers.size() - selectedHandlers.size() < 1) {
            throw BizException.badRequest("至少保留一名办理人");
        }
    }

    /**
     * 校验协作操作必须指定至少一名办理人，并统一去除首尾空格。
     *
     * @param handlers 办理人集合
     * @param message  为空时的错误信息
     * @return 规范化后的办理人集合
     */
    private List<String> requiredHandlers(List<String> handlers, String message) {
        if (handlers == null || handlers.isEmpty()) {
            throw BizException.badRequest(message);
        }
        List<String> result = handlers.stream()
            .map(handler -> handler == null ? null : handler.trim())
            .collect(Collectors.toList());
        if (result.stream().anyMatch(StringUtils::isEmpty)) {
            throw BizException.badRequest(message);
        }
        return result;
    }

    /**
     * 查询待办任务按实例变量可达的下一审批节点。
     * <p>仅对下一审批节点解析可选办理用户。</p>
     *
     * @param taskId 待办任务主键
     * @return 下一审批节点及可选办理用户集合
     */
    @Override
    public List<TaskNodeVo> nextNodes(Long taskId) {
        return nextNodes(requireTask(taskId));
    }

    /**
     * 查询流程图中当前节点的前置审批节点。
     * <p>仅用于选择退回目标，不解析可选办理用户。</p>
     *
     * @param taskId 待办任务主键
     * @return 可退回审批节点集合
     */
    @Override
    public List<TaskNodeVo> backNodes(Long taskId) {
        Task task = requireTask(taskId);
        try {
            Node currentNode = FlowEngine.nodeService()
                .getByDefIdAndNodeCode(task.getDefinitionId(), task.getNodeCode());
            if (currentNode != null && StringUtils.isNotEmpty(currentNode.getAnyNodeSkip())) {
                Node fixedNode = FlowEngine.nodeService()
                    .getByDefIdAndNodeCode(task.getDefinitionId(), currentNode.getAnyNodeSkip());
                if (fixedNode != null && NodeType.BETWEEN.getKey().equals(fixedNode.getNodeType())) {
                    return Collections.singletonList(toTaskNodeVo(fixedNode));
                }
            }
            Set<String> historyNodeCodes = FlowEngine.hisTaskService().getByInsId(task.getInstanceId()).stream()
                .map(HisTask::getNodeCode)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toSet());
            return FlowEngine.nodeService().previousNodeList(task.getDefinitionId(), task.getNodeCode()).stream()
                .filter(node -> NodeType.BETWEEN.getKey().equals(node.getNodeType()))
                .filter(node -> !node.getNodeCode().equals(task.getNodeCode())
                    && historyNodeCodes.contains(node.getNodeCode()))
                .map(this::toTaskNodeVo)
                .collect(Collectors.toList());
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /**
     * 通过待办任务，并按需保存抄送人。
     *
     * @param req 办理请求
     * @return 办理后的流程实例
     */
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
        saveCopyUsers(task, req.getCopyUsers(), req.getUser());
        return instance;
    }

    /**
     * 退回待办任务到指定节点，并按需保存抄送人。
     *
     * @param req 办理请求
     * @return 办理后的流程实例
     */
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
        saveCopyUsers(task, req.getCopyUsers(), req.getUser());
        return instance;
    }

    /**
     * 转办待办任务给指定办理人。
     *
     * @param req 办理请求
     */
    @Override
    public void transfer(TaskActionRequest req) {
        checkTaskAction(req);
        Task task = requireTask(req.getTaskId());
        checkNodeButton(task, "transfer");
        List<String> nextHandlers = requiredHandlers(req.getNextHandlers(), "转办办理人不能为空");
        String targetHandler = singleHandler(nextHandlers, "转办只能选择一个办理人");
        try {
            TransferCommand command = new TransferCommand();
            command.setOperator(operator(req.getUser()));
            command.setTaskId(task.getId());
            command.setTargetHandler(targetHandler);
            command.setMessage(req.getMessage());
            command.setHistoryTaskStatus(TaskStatusEnum.TRANSFER.getStatus());
            FlowEngine.workflow().transfer(command);
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /**
     * 委派待办任务给指定办理人。
     *
     * @param req 办理请求
     */
    @Override
    public void depute(TaskActionRequest req) {
        checkTaskAction(req);
        Task task = requireTask(req.getTaskId());
        checkNodeButton(task, "trust");
        List<String> nextHandlers = requiredHandlers(req.getNextHandlers(), "委派办理人不能为空");
        String targetHandler = singleHandler(nextHandlers, "委派只能选择一个办理人");
        try {
            DelegateCommand command = new DelegateCommand();
            command.setOperator(operator(req.getUser()));
            command.setTaskId(task.getId());
            command.setTargetHandler(targetHandler);
            command.setMessage(req.getMessage());
            command.setHistoryTaskStatus(TaskStatusEnum.DEPUTE.getStatus());
            FlowEngine.workflow().delegate(command);
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /**
     * 给待办任务增加办理人。
     *
     * @param req 办理请求
     */
    @Override
    public void addSignature(TaskActionRequest req) {
        checkTaskAction(req);
        Task task = requireTask(req.getTaskId());
        checkSignNode(task);
        checkNodeButton(task, "addSign");
        List<String> addHandlers = requiredHandlers(req.getAddHandlers(), "加签办理人不能为空");
        if (addHandlers.stream().anyMatch(currentHandlerNames(task)::contains)) {
            throw BizException.badRequest("加签办理人不能是当前任务办理人");
        }
        try {
            AddSignerCommand command = new AddSignerCommand();
            command.setOperator(operator(req.getUser()));
            command.setTaskId(task.getId());
            command.setTargetHandlers(addHandlers);
            command.setMessage(req.getMessage());
            command.setHistoryTaskStatus(TaskStatusEnum.SIGN.getStatus());
            FlowEngine.workflow().addSigner(command);
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /**
     * 减少待办任务办理人。
     *
     * @param req 办理请求
     */
    @Override
    public void reductionSignature(TaskActionRequest req) {
        checkTaskAction(req);
        Task task = requireTask(req.getTaskId());
        checkSignNode(task);
        checkNodeButton(task, "subSign");
        validateReductionHandlers(task, req.getReductionHandlers());
        try {
            RemoveSignerCommand command = new RemoveSignerCommand();
            command.setOperator(operator(req.getUser()));
            command.setTaskId(task.getId());
            command.setTargetHandlers(req.getReductionHandlers());
            command.setMessage(req.getMessage());
            command.setHistoryTaskStatus(TaskStatusEnum.SIGN_OFF.getStatus());
            FlowEngine.workflow().removeSigner(command);
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
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
            command.setOperator(operator(user));
            command.setInstanceId(instanceId);
            command.setInstanceStatus(BusinessStatusEnum.CANCEL.getStatus());
            command.setHistoryTaskStatus(TaskStatusEnum.CANCEL.getStatus());
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
        Instance instance = requireInstance(instanceId);
        checkNodeButton(instance.getDefinitionId(), instance.getNodeCode(), "termination");
        try {
            TerminateCommand command = new TerminateCommand();
            command.setOperator(operator(user));
            command.setInstanceId(instanceId);
            command.setInstanceStatus(BusinessStatusEnum.TERMINATION.getStatus());
            command.setHistoryTaskStatus(TaskStatusEnum.TERMINATION.getStatus());
            FlowEngine.workflow().terminate(command);
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    private InstanceVo operate(TaskActionRequest req, boolean reject) {
        try {
            WorkflowResult result;
            Map<String, Object> variables = nextHandlerVariables(req);
            if (reject) {
                RejectCommand command = new RejectCommand();
                command.setOperator(operator(req.getUser()));
                command.setTaskId(req.getTaskId());
                command.setTargetNodeCode(req.getNodeCode());
                command.setMessage(req.getMessage());
                command.setVariables(variables);
                command.setNextHandlers(req.getNextHandlers());
                command.setInstanceStatus(BusinessStatusEnum.BACK.getStatus());
                command.setHistoryTaskStatus(TaskStatusEnum.BACK.getStatus());
                result = FlowEngine.workflow().reject(command);
            } else if (StringUtils.isNotEmpty(req.getNodeCode())) {
                JumpCommand command = new JumpCommand();
                command.setOperator(operator(req.getUser()));
                command.setTaskId(req.getTaskId());
                command.setTargetNodeCode(req.getNodeCode());
                command.setMessage(req.getMessage());
                command.setVariables(variables);
                command.setNextHandlers(req.getNextHandlers());
                command.setInstanceStatus(BusinessStatusEnum.WAITING.getStatus());
                command.setHistoryTaskStatus(TaskStatusEnum.PASS.getStatus());
                result = FlowEngine.workflow().jump(command);
            } else {
                CompleteCommand command = new CompleteCommand();
                command.setOperator(operator(req.getUser()));
                command.setTaskId(req.getTaskId());
                command.setMessage(req.getMessage());
                command.setVariables(variables);
                command.setNextHandlers(req.getNextHandlers());
                command.setInstanceStatus(BusinessStatusEnum.WAITING.getStatus());
                command.setHistoryTaskStatus(TaskStatusEnum.PASS.getStatus());
                result = FlowEngine.workflow().complete(command);
            }
            Instance instance = requireInstance(result.getInstanceId());
            if (NodeType.isEnd(instance.getNodeType())) {
                instance.setFlowStatus(BusinessStatusEnum.FINISH.getStatus());
                FlowEngine.insService().updateById(instance);
            }
            return toInstanceVo(instance);
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

    /**
     * 创建 Demo 当前用户对应的引擎操作者上下文。
     *
     * @param user 当前用户名
     * @return 操作者上下文
     */
    private OperatorContext operator(String user) {
        return new OperatorContext(user, userService.permissionFlags(user));
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

    /**
     * 合并任务请求变量与按节点指定的后续办理人变量。
     *
     * @param req 任务操作请求
     * @return 合并后的流程变量
     */
    private Map<String, Object> nextHandlerVariables(TaskActionRequest req) {
        Map<String, Object> variables = req.getVariables() == null
            ? new HashMap<>() : new HashMap<>(req.getVariables());
        if (req.getNextHandlerMap() == null || req.getNextHandlerMap().isEmpty()) {
            return variables;
        }
        req.getNextHandlerMap().forEach((nodeCode, handlers) -> {
            List<String> validHandlers = handlers == null ? new ArrayList<>() : handlers.stream()
                .filter(StringUtils::isNotEmpty)
                .map(String::trim)
                .collect(Collectors.toList());
            if (nodeCode != null && !validHandlers.isEmpty()) {
                variables.put(SkipType.PASS.getKey() + ":" + nodeCode, String.join(",", validHandlers));
            }
        });
        return variables;
    }

    /**
     * 获取单个目标办理人。
     *
     * @param handlers 办理人集合
     * @param message 办理人数量不合法时的提示
     * @return 唯一目标办理人
     */
    private String singleHandler(List<String> handlers, String message) {
        if (handlers.size() != 1) {
            throw BizException.badRequest(message);
        }
        return handlers.get(0);
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
        vo.setVariables(instance.getVariableMap());
        BusinessStatusEnum status = BusinessStatusEnum.getByStatus(instance.getFlowStatus());
        vo.setFlowStatusKey(instance.getFlowStatus());
        vo.setFlowStatusName(status == null ? instance.getFlowStatus() : status.getDesc());
        vo.setBusinessStatus(instance.getFlowStatus());
        vo.setBusinessStatusName(status == null ? instance.getFlowStatus() : status.getDesc());
        FlowEngine.taskService().getByInsId(instance.getId()).stream()
            .min(Comparator.comparing(Task::getId))
            .ifPresent(task -> vo.setTaskId(task.getId()));
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
        vo.setFlowStatusKey(task.getFlowStatus());
        BusinessStatusEnum status = BusinessStatusEnum.getByStatus(task.getFlowStatus());
        vo.setFlowStatusName(status == null ? task.getFlowStatus() : status.getDesc());
        vo.setBusinessStatus(task.getFlowStatus());
        vo.setBusinessStatusName(status == null ? task.getFlowStatus() : status.getDesc());
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
        vo.setFlowStatusKey(hisTask.getFlowStatus());
        String statusName = displayTaskStatus(hisTask.getFlowStatus());
        vo.setFlowStatusName(StringUtils.isEmpty(statusName) ? hisTask.getFlowStatus() : statusName);
        vo.setBusinessStatus(hisTask.getFlowStatus());
        vo.setBusinessStatusName(StringUtils.isEmpty(statusName) ? hisTask.getFlowStatus() : statusName);
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
        vo.setFlowStatusKey(hisTask.getFlowStatus());
        String statusName = displayTaskStatus(hisTask.getFlowStatus());
        vo.setFlowStatusName(StringUtils.isEmpty(statusName) ? hisTask.getFlowStatus() : statusName);
        vo.setTaskStatusName(vo.getFlowStatusName());
        vo.setBusinessStatus(hisTask.getFlowStatus());
        vo.setBusinessStatusName(StringUtils.isEmpty(statusName) ? hisTask.getFlowStatus() : statusName);
        return vo;
    }

    /**
     * 将当前活动任务转换为历史时间线中的待办节点，便于退回或撤回后继续办理。
     *
     * @param task 当前活动任务
     * @return 当前待办历史信息
     */
    private HistoryVo toCurrentHistoryVo(Task task) {
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
        vo.setFlowStatusKey(task.getFlowStatus());
        vo.setFlowStatusName(isResubmittableStatus(task.getFlowStatus()) ? "待提交" : "待审核");
        vo.setTaskStatusName(vo.getFlowStatusName());
        vo.setBusinessStatus(task.getFlowStatus());
        vo.setBusinessStatusName(vo.getFlowStatusName());
        vo.setCurrent(true);
        vo.setCreateTime(task.getCreateTime());
        return vo;
    }

    /**
     * 获取任务历史展示状态；非任务操作状态回退到 Demo 业务状态。
     *
     * @param status 状态编码
     * @return 状态名称
     */
    private String displayTaskStatus(String status) {
        String taskStatus = TaskStatusEnum.findByStatus(status);
        if (StringUtils.isNotEmpty(taskStatus)) {
            return taskStatus;
        }
        BusinessStatusEnum businessStatus = BusinessStatusEnum.getByStatus(status);
        return businessStatus == null ? status : businessStatus.getDesc();
    }

    /**
     * 判断当前任务是否需要由发起人重新提交。
     *
     * @param flowStatus 任务状态
     * @return 是否可重新提交
     */
    private boolean isResubmittableStatus(String flowStatus) {
        return BusinessStatusEnum.DRAFT.getStatus().equals(flowStatus)
            || BusinessStatusEnum.BACK.getStatus().equals(flowStatus)
            || BusinessStatusEnum.CANCEL.getStatus().equals(flowStatus);
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

    private void saveCopyUsers(Task task, List<String> copyUsers, String user) {
        List<String> users = normalizeCopyUsers(copyUsers);
        if (users.isEmpty()) {
            return;
        }
        Node node = FlowEngine.nodeService().getByDefIdAndNodeCode(task.getDefinitionId(), task.getNodeCode());
        if (node == null) {
            throw BizException.badRequest("抄送节点不存在: " + task.getNodeCode());
        }
        WorkflowContext copyContext = new WorkflowContext();
        copyContext.setHandler(user);
        copyContext.setPermissions(userService.permissionFlags(user));
        copyContext.setHistoryTaskStatus(TaskStatusEnum.COPY.getStatus());
        FlowEngine.hisTaskService().save(FlowEngine.hisTaskService().setSkipHisTask(task, node, copyContext
            , SkipType.NONE.getKey()));
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

}
