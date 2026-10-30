package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.FlowCons;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 自动审批推进器：办理人提交后，本次新建待办的办理人只有当前办理人本人时，代替其自动通过。
 *
 * <p><b>开启方式</b>：流程定义配置 {@code flow_definition.def_config} 中 {@code autoPass} 显式为真，形如
 * {@code {"autoPass":true}}。定义配置由设计器与流程定义接口维护，不与业务扩展字段 {@code ext} 混用；
 * 配置格式非法时按未开启处理，不阻断既有流程办理。</p>
 *
 * <p><b>触发与范围</b>：办理（通过/跳转）操作收尾时触发，退回操作不触发——否则会把刚退回的节点
 * 立即自动通过，等于撤销本次退回。判定只覆盖本次操作新建的后续待办（含自动节点推进产生的人工待办），
 * 不扫描实例上其它已存在的待办，避免替他人或历史任务误审批。</p>
 *
 * <p><b>判定规则</b>：待办节点为人工节点，且审批权限集合包含当前办理人时，以该办理人身份自动通过。
 * 会签、票签只代投该办理人一票，任务保留给其余办理人继续办理；或签与该办理人手工通过等效；
 * 平行分支中其它分支的已有待办不受影响。</p>
 *
 * <p><b>历史与链式推进</b>：自动通过复用标准办理链，历史任务写「审批通过」与自动审批意见，
 * 办理人仍为当前办理人；自动审批产生的后续待办若仍只有该办理人，则继续自动通过，
 * 直到出现他人办理的待办、结束节点或达到跳数上限。自动审批不携带调用方变量、抄送等办理参数，
 * 业务监听器可通过 {@code context.taskStatus} 识别本次流转为自动审批。</p>
 *
 * @author may
 */
final class AutoPassAdvancer {

    /**
     * 日志。
     */
    private static final Logger log = LoggerFactory.getLogger(AutoPassAdvancer.class);

    /**
     * 自动审批写入历史任务的意见。
     */
    private static final String AUTO_PASS_MESSAGE = "流程引擎自动审批！";

    /**
     * 单次办理操作内自动审批的跳数上限：同一办理人若因流程环路反复回到自身，到此即停止自动审批，
     * 保留待办由人工办理，避免无限递归。
     */
    private static final int MAX_AUTO_PASS_STEPS = 100;

    /**
     * 当前线程自动审批嵌套深度，最外层办理操作结束时清理。
     */
    private static final ThreadLocal<Integer> AUTO_PASS_DEPTH = ThreadLocal.withInitial(() -> 0);

    /**
     * 当前线程自动审批已跳次数，一次办理操作内累计（嵌套办理共享）。
     */
    private static final ThreadLocal<Integer> AUTO_PASS_STEPS = ThreadLocal.withInitial(() -> 0);

    /**
     * 自动审批本次办理产生的后续待办。
     *
     * @param execution  执行作用域
     * @param candidates 本次操作新建的后续待办（办理链新建 + 自动节点推进新建）
     * @return 发生自动审批时返回最后一步自动审批后的实例；未发生自动审批返回 {@code null}
     */
    Instance advance(FlowExecution execution, List<Task> candidates) {
        String handler = execution.intent.getHandler();
        if (StringUtils.isEmpty(handler) || CollUtil.isEmpty(candidates)
            || !autoPassEnabled(execution.definition)) {
            return null;
        }
        boolean outermost = AUTO_PASS_DEPTH.get() == 0;
        if (outermost) {
            AUTO_PASS_STEPS.set(0);
        }
        AUTO_PASS_DEPTH.set(AUTO_PASS_DEPTH.get() + 1);
        try {
            return autoPass(execution, candidates, handler);
        } finally {
            int depth = AUTO_PASS_DEPTH.get() - 1;
            AUTO_PASS_DEPTH.set(depth);
            if (depth <= 0) {
                AUTO_PASS_DEPTH.remove();
                AUTO_PASS_STEPS.remove();
            }
        }
    }

    /**
     * 逐个自动通过满足条件的后续待办；自动通过复用办理链，链内继续处理其后继待办。
     *
     * @param execution  执行作用域
     * @param candidates 本次操作新建的后续待办
     * @param handler    当前办理人
     * @return 最后一步自动审批后的实例；未发生自动审批返回 {@code null}
     */
    private Instance autoPass(FlowExecution execution, List<Task> candidates, String handler) {
        Instance latest = null;
        for (Task candidate : candidates) {
            Task task = matchHandlerTask(candidate, handler);
            if (task == null) {
                continue;
            }
            if (AUTO_PASS_STEPS.get() >= MAX_AUTO_PASS_STEPS) {
                log.warn("自动审批超过跳数上限 {}，停止自动审批并保留待办待人工办理，instanceId={}"
                    , MAX_AUTO_PASS_STEPS, execution.instance.getId());
                break;
            }
            AUTO_PASS_STEPS.set(AUTO_PASS_STEPS.get() + 1);
            latest = FlowEngine.taskService().execute(task.getId(), autoPassContext(handler)
                , SkipType.PASS.getKey());
        }
        return latest;
    }

    /**
     * 判断候选待办是否可由当前办理人自动通过，并返回仍然活动的任务。
     *
     * <p>仅人工节点参与判定：服务任务由自动节点推进器处理，网关与结束节点不产生待办。审批权限集合
     * 包含当前办理人即可自动通过，引擎只以该办理人身份办理，不代替集合中的其它办理人；会签、票签
     * 的剩余票数由协作处理器按原规则计算。同批次的其它分支可能已推进到结束节点并清理了剩余待办，
     * 因此以库中最新记录为准，已不存在的任务直接跳过。</p>
     *
     * @param candidate 候选待办
     * @param handler   当前办理人
     * @return 可自动通过的活动任务；不满足条件时返回 {@code null}
     */
    private Task matchHandlerTask(Task candidate, String handler) {
        if (!NodeType.isBetween(candidate.getNodeType())) {
            return null;
        }
        Task task = FlowEngine.taskService().getById(candidate.getId());
        if (task == null) {
            return null;
        }
        List<String> permissions = FlowEngine.userService().getPermission(task.getId()
            , UserType.APPROVAL.getKey());
        if (!permissions.contains(handler)) {
            return null;
        }
        return task;
    }

    /**
     * 构造自动审批的办理上下文：办理人为当前办理人，权限集合即该办理人本身，
     * 历史状态写 {@link TaskStatus#AUTO_PASS}（自动审批）——与人工办理的「通过」区分开。
     *
     * @param handler 当前办理人
     * @return 自动审批办理上下文
     */
    private WorkflowContext autoPassContext(String handler) {
        WorkflowContext context = WorkflowContext.create(handler);
        context.setPermissions(List.of(handler));
        context.setMessage(AUTO_PASS_MESSAGE);
        context.setTaskStatus(FlowStatusMachine.mapper().taskStatus(TaskStatus.AUTO_PASS));
        return context;
    }

    /**
     * 读取流程定义配置中的自动审批开关。
     *
     * <p>只取定义配置 {@code def_config} 中的 {@code autoPass} 键，不回退读业务扩展字段 {@code ext}。
     * JSON 非对象或格式非法时按未开启处理，不阻断既有流程办理。</p>
     *
     * @param definition 流程定义
     * @return 是否开启自动审批
     */
    private boolean autoPassEnabled(Definition definition) {
        return Boolean.TRUE.equals(readAutoPass(definition.getDefConfig(), definition.getId()));
    }

    /**
     * 解析定义配置 JSON 中的自动审批键。
     *
     * @param json         定义配置 JSON
     * @param definitionId 流程定义主键
     * @return 已声明时返回开关键值；未声明、格式非法或解析失败返回 {@code null}
     */
    private Boolean readAutoPass(String json, Long definitionId) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        Map<String, Object> map;
        try {
            map = FlowEngine.jsonConvert.strToMap(json);
        } catch (Exception e) {
            log.warn("流程定义配置解析失败，自动审批按未开启处理，definitionId={}", definitionId, e);
            return null;
        }
        Object autoPass = map == null ? null : map.get(FlowCons.DEF_CONFIG_AUTO_PASS);
        if (Boolean.TRUE.equals(autoPass)) {
            return true;
        }
        if (Boolean.FALSE.equals(autoPass)) {
            return false;
        }
        if (autoPass instanceof String) {
            return Boolean.parseBoolean(((String) autoPass).trim());
        }
        return null;
    }
}
