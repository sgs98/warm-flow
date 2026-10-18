package org.dromara.warm.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.warm.demo.common.BizException;
import org.dromara.warm.demo.dto.PageQuery;
import org.dromara.warm.demo.dto.TaskActionRequest;
import org.dromara.warm.demo.enums.TaskStatusEnum;
import org.dromara.warm.demo.mapper.DemoTaskQueryMapper;
import org.dromara.warm.demo.service.TaskAppService;
import org.dromara.warm.demo.service.UserService;
import org.dromara.warm.demo.service.support.WorkflowSupport;
import org.dromara.warm.demo.utils.ButtonPermissionUtils;
import org.dromara.warm.demo.vo.ButtonPermissionVo;
import org.dromara.warm.demo.vo.DemoUserVo;
import org.dromara.warm.demo.vo.InstanceVo;
import org.dromara.warm.demo.vo.PageVo;
import org.dromara.warm.demo.vo.TaskNodeVo;
import org.dromara.warm.demo.vo.TaskVo;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.NodeType;
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
import org.dromara.warm.flow.core.workflow.command.TransferCommand;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.dromara.warm.flow.core.workflow.result.WorkflowResult;
import org.dromara.warm.flow.orm.entity.FlowHisTask;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务应用服务实现。
 * <p>负责任务域用例的流程编排、参数校验、引擎调用，VO 装配委托 {@link WorkflowSupport}。</p>
 *
 * @author may
 * @since 2026/10/17
 */
@Service
@RequiredArgsConstructor
public class TaskAppServiceImpl implements TaskAppService {

    /**
     * demo 抄送用户类型；1/2/3 已被引擎分别用于审批、转办、委派权限。
     */
    private static final String COPY_USER_TYPE = "4";

    private final DemoTaskQueryMapper taskQueryMapper;
    private final UserService userService;
    private final WorkflowSupport support;

    /**
     * 分页查询当前用户待办任务。
     *
     * @param user      当前用户名
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
            .map(support::toTodoVo)
            .collect(Collectors.toList());
        return new PageVo<>(page.getTotal(),
            pageQuery.normalizedPageNum(), pageQuery.normalizedPageSize(), rows);
    }

    /**
     * 分页查询当前用户已办任务。
     *
     * @param user      当前用户名
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
        return support.toPage(page, support::toDoneVo);
    }

    /**
     * 分页查询当前用户收到的抄送。
     *
     * @param user      当前用户名
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
            .map(support::toDoneVo)
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
        Task task = support.requireTask(taskId);
        List<ButtonPermissionVo> permissions = ButtonPermissionUtils.list(task.getDefinitionId(), task.getNodeCode());
        if (!support.isSignNode(task)) {
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
     * 查询待办任务按实例变量可达的下一审批节点。
     * <p>仅对下一审批节点解析可选办理用户。</p>
     *
     * @param taskId 待办任务主键
     * @return 下一审批节点及可选办理用户集合
     */
    @Override
    public List<TaskNodeVo> nextNodes(Long taskId) {
        return nextNodes(support.requireTask(taskId));
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
        Task task = support.requireTask(taskId);
        try {
            Node currentNode = FlowEngine.nodeService()
                .getByDefIdAndNodeCode(task.getDefinitionId(), task.getNodeCode());
            if (currentNode != null && StringUtils.isNotEmpty(currentNode.getAnyNodeSkip())) {
                Node fixedNode = FlowEngine.nodeService()
                    .getByDefIdAndNodeCode(task.getDefinitionId(), currentNode.getAnyNodeSkip());
                if (fixedNode != null && NodeType.BETWEEN.getKey().equals(fixedNode.getNodeType())) {
                    return Collections.singletonList(support.toTaskNodeVo(fixedNode));
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
                .map(support::toTaskNodeVo)
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
        Task task = support.requireTask(req.getTaskId());
        support.checkNodeButton(task, "pass");
        if (hasNextHandler(req)) {
            support.checkNodeButton(task, "pop");
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
        Task task = support.requireTask(req.getTaskId());
        support.checkNodeButton(task, "back");
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
        Task task = support.requireTask(req.getTaskId());
        support.checkNodeButton(task, "transfer");
        List<String> nextHandlers = requiredHandlers(req.getNextHandlers(), "转办办理人不能为空");
        String targetHandler = singleHandler(nextHandlers, "转办只能选择一个办理人");
        try {
            TransferCommand command = new TransferCommand();
            command.setOperator(support.operator(req.getUser()));
            command.setTaskId(task.getId());
            command.setTargetHandler(targetHandler);
            command.setMessage(req.getMessage());
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
        Task task = support.requireTask(req.getTaskId());
        support.checkNodeButton(task, "trust");
        List<String> nextHandlers = requiredHandlers(req.getNextHandlers(), "委派办理人不能为空");
        String targetHandler = singleHandler(nextHandlers, "委派只能选择一个办理人");
        try {
            DelegateCommand command = new DelegateCommand();
            command.setOperator(support.operator(req.getUser()));
            command.setTaskId(task.getId());
            command.setTargetHandler(targetHandler);
            command.setMessage(req.getMessage());
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
        Task task = support.requireTask(req.getTaskId());
        checkSignNode(task);
        support.checkNodeButton(task, "addSign");
        List<String> addHandlers = requiredHandlers(req.getAddHandlers(), "加签办理人不能为空");
        if (addHandlers.stream().anyMatch(currentHandlerNames(task)::contains)) {
            throw BizException.badRequest("加签办理人不能是当前任务办理人");
        }
        try {
            AddSignerCommand command = new AddSignerCommand();
            command.setOperator(support.operator(req.getUser()));
            command.setTaskId(task.getId());
            command.setTargetHandlers(addHandlers);
            command.setMessage(req.getMessage());
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
        Task task = support.requireTask(req.getTaskId());
        checkSignNode(task);
        support.checkNodeButton(task, "subSign");
        validateReductionHandlers(task, req.getReductionHandlers());
        try {
            RemoveSignerCommand command = new RemoveSignerCommand();
            command.setOperator(support.operator(req.getUser()));
            command.setTaskId(task.getId());
            command.setTargetHandlers(req.getReductionHandlers());
            command.setMessage(req.getMessage());
            FlowEngine.workflow().removeSigner(command);
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /**
     * 执行通过 / 退回 / 跳转三类引擎命令并装配办理后实例。
     *
     * @param req    办理请求
     * @param reject 是否退回
     * @return 办理后的流程实例
     */
    private InstanceVo operate(TaskActionRequest req, boolean reject) {
        try {
            WorkflowResult result;
            Map<String, Object> variables = nextHandlerVariables(req);
            if (reject) {
                RejectCommand command = new RejectCommand();
                command.setOperator(support.operator(req.getUser()));
                command.setTaskId(req.getTaskId());
                command.setTargetNodeCode(req.getNodeCode());
                command.setMessage(req.getMessage());
                command.setVariables(variables);
                command.setNextHandlers(req.getNextHandlers());
                command.setTaskStatus(TaskStatusEnum.BACK.getStatus());
                result = FlowEngine.workflow().reject(command);
            } else if (StringUtils.isNotEmpty(req.getNodeCode())) {
                JumpCommand command = new JumpCommand();
                command.setOperator(support.operator(req.getUser()));
                command.setTaskId(req.getTaskId());
                command.setTargetNodeCode(req.getNodeCode());
                command.setMessage(req.getMessage());
                command.setVariables(variables);
                command.setNextHandlers(req.getNextHandlers());
                command.setTaskStatus(TaskStatusEnum.PASS.getStatus());
                result = FlowEngine.workflow().jump(command);
            } else {
                CompleteCommand command = new CompleteCommand();
                command.setOperator(support.operator(req.getUser()));
                command.setTaskId(req.getTaskId());
                command.setMessage(req.getMessage());
                command.setVariables(variables);
                command.setNextHandlers(req.getNextHandlers());
                command.setTaskStatus(TaskStatusEnum.PASS.getStatus());
                result = FlowEngine.workflow().complete(command);
            }
            Instance instance = support.requireInstance(result.getInstanceId());
            return support.toInstanceVo(instance);
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /**
     * 校验任务操作请求基本参数。
     *
     * @param req 任务操作请求
     */
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
     * 校验当前任务只能在会签或票签节点执行加签、减签。
     *
     * @param task 当前待办任务
     */
    private void checkSignNode(Task task) {
        if (!support.isSignNode(task)) {
            throw BizException.badRequest("只有会签或票签节点支持加签、减签");
        }
    }

    /**
     * 判断实例当前是否存在会签或票签任务。
     *
     * @param instanceId 流程实例主键
     * @return 是否存在会签或票签任务
     */
    private boolean hasSignTask(Long instanceId) {
        support.requireInstance(instanceId);
        return FlowEngine.taskService().getByInsId(instanceId).stream().anyMatch(support::isSignNode);
    }

    /**
     * 查询流程实例当前待办任务已有的办理人，供加签、减签过滤使用。
     *
     * @param instanceId 流程实例主键
     * @return 办理人用户名集合
     */
    private Set<String> currentHandlerNames(Long instanceId) {
        support.requireInstance(instanceId);
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
            .map(support::userNameOf)
            .filter(StringUtils::isNotEmpty)
            .collect(Collectors.toCollection(LinkedHashSet::new));
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
     * 获取单个目标办理人。
     *
     * @param handlers 办理人集合
     * @param message  办理人数量不合法时的提示
     * @return 唯一目标办理人
     */
    private String singleHandler(List<String> handlers, String message) {
        if (handlers.size() != 1) {
            throw BizException.badRequest(message);
        }
        return handlers.get(0);
    }

    /**
     * 查询待办任务按实例变量可达的下一审批节点，并解析可选办理用户。
     *
     * @param task 待办任务
     * @return 下一审批节点及可选办理用户集合
     */
    private List<TaskNodeVo> nextNodes(Task task) {
        Instance instance = support.requireInstance(task.getInstanceId());
        try {
            List<TaskNodeVo> nodes = FlowEngine.nodeService().getNextNodeList(
                    task.getDefinitionId(), task.getNodeCode(), null,
                    SkipType.PASS.getKey(), instance.getVariableMap()).stream()
                .filter(node -> NodeType.BETWEEN.getKey().equals(node.getNodeType()))
                .map(support::toTaskNodeVo)
                .collect(Collectors.toList());
            nodes.forEach(node ->
                node.setSelectableUsers(userService.listByPermissionFlags(node.getPermissionFlags())));
            return nodes;
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    /**
     * 判断办理请求是否指定了后续办理人。
     *
     * @param req 办理请求
     * @return 是否指定
     */
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
     * 校验后续办理人都属于下一节点可选范围。
     *
     * @param task 当前待办任务
     * @param req  办理请求
     */
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

    /**
     * 校验按节点指定的后续办理人映射。
     *
     * @param nodes      下一审批节点集合
     * @param handlerMap 节点编码 -> 办理人集合
     */
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

    /**
     * 提取节点的可选办理人用户名集合。
     *
     * @param node 节点信息
     * @return 办理人用户名集合
     */
    private Set<String> userNamesOf(TaskNodeVo node) {
        return node.getSelectableUsers().stream()
            .map(DemoUserVo::getUserName)
            .collect(Collectors.toSet());
    }

    /**
     * 退回时目标节点未配置办理人时，回退到流程发起人办理。
     *
     * @param task 当前待办任务
     * @param req  办理请求
     */
    private void fallbackRejectHandler(Task task, TaskActionRequest req) {
        Node targetNode = FlowEngine.nodeService().getByDefIdAndNodeCode(
            task.getDefinitionId(), req.getNodeCode());
        if (ObjectUtil.isNull(targetNode)) {
            throw BizException.badRequest("退回节点不存在: " + req.getNodeCode());
        }
        if (StringUtils.isNotEmpty(targetNode.getPermissionFlag()) || hasNextHandler(req)) {
            return;
        }
        Instance instance = support.requireInstance(task.getInstanceId());
        if (StringUtils.isNotEmpty(instance.getCreateBy())) {
            req.setNextHandlers(Collections.singletonList(instance.getCreateBy()));
        }
    }

    /**
     * 保存办理时指定的抄送人。
     *
     * @param task      当前待办任务
     * @param copyUsers 抄送人集合
     * @param user      当前用户名
     */
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
        copyContext.setTaskStatus(TaskStatusEnum.COPY.getStatus());
        FlowEngine.hisTaskService().save(FlowEngine.hisTaskService().setSkipHisTask(task, node, copyContext
            , SkipType.NONE.getKey()));
        List<User> flowUsers = users.stream().map(userName -> FlowEngine.newUser()
                .setType(COPY_USER_TYPE)
                .setProcessedBy(userName)
                .setAssociated(task.getId()))
            .collect(Collectors.toList());
        FlowEngine.userService().saveBatch(flowUsers);
    }

    /**
     * 校验并去重抄送人，抄送人必须是 Demo 已存在用户。
     *
     * @param copyUsers 抄送人集合
     * @return 规范化后的抄送人集合
     */
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
}
