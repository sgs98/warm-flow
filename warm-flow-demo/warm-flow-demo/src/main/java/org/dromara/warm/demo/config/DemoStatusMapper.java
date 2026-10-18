package org.dromara.warm.demo.config;

import jakarta.annotation.PostConstruct;
import org.dromara.warm.demo.enums.BusinessStatusEnum;
import org.dromara.warm.demo.enums.TaskStatusEnum;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.handler.StatusMapper;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * demo 的状态映射：把引擎的三个语义维度翻译成 demo 的业务编码。
 * <p>
 * 实例状态落到 {@link BusinessStatusEnum}，任务状态落到 {@link TaskStatusEnum}，
 * 协作动作映射为任务状态；注册后业务层不再需要每次调用手动传 {@code flowStatus} / {@code taskStatus}。
 *
 * @author may
 */
@Component
public class DemoStatusMapper implements StatusMapper {

    /**
     * demo 视为终态的实例状态：到这些状态后实例不再允许办理、流转或撤回。
     */
    private static final Set<String> TERMINAL = Set.of(
        BusinessStatusEnum.FINISH.getStatus(), BusinessStatusEnum.TERMINATION.getStatus(),
        BusinessStatusEnum.INVALID.getStatus(), BusinessStatusEnum.CANCEL.getStatus());

    /**
     * 向引擎注册本映射器。
     */
    @PostConstruct
    public void register() {
        FlowEngine.setStatusMapper(this);
    }

    /**
     * 实例状态语义转业务编码。
     *
     * @param semantic 实例状态语义
     * @return 业务状态编码
     */
    @Override
    public String instanceStatus(FlowStatus semantic) {
        return switch (semantic) {
            case TOBESUBMIT -> BusinessStatusEnum.DRAFT.getStatus();
            case APPROVAL -> BusinessStatusEnum.WAITING.getStatus();
            case FINISHED -> BusinessStatusEnum.FINISH.getStatus();
            case REJECT -> BusinessStatusEnum.BACK.getStatus();
            case TERMINATE -> BusinessStatusEnum.TERMINATION.getStatus();
            case CANCEL -> BusinessStatusEnum.CANCEL.getStatus();
            case NULLIFY -> BusinessStatusEnum.INVALID.getStatus();
            default -> semantic.getKey();
        };
    }

    /**
     * 任务状态语义转业务编码。
     *
     * @param semantic 任务状态语义
     * @return 任务状态编码
     */
    @Override
    public String taskStatus(TaskStatus semantic) {
        return switch (semantic) {
            case WAITING -> TaskStatusEnum.WAITING.getStatus();
            case PASS -> TaskStatusEnum.PASS.getStatus();
            case REJECT -> TaskStatusEnum.BACK.getStatus();
            case INVALID -> TaskStatusEnum.INVALID.getStatus();
            // 任务维度的「结束」（END 收口临时待办、结束事件留痕）在办理历史时间线上统一展示为
            // 「通过」，保持时间线标签一致；「结束」语义由该记录的 message 承载（如「流程由该结束事件节点结束」）。
            // 实例维度的完成不走这里，仍由 instanceStatus 映射为 BusinessStatusEnum.FINISH（已完成）。
            case FINISHED -> TaskStatusEnum.PASS.getStatus();
            // 引擎当前没有写入「拿回」的路径，保留内置编码，展示层回落「拿回」
            case TASK_BACK -> semantic.getKey();
        };
    }

    /**
     * 协作动作转任务状态编码：转办 / 委派 / 加签 / 减签落成业务编码，
     * 业务层不再需要为这几个动作手动传 {@code taskStatus}。
     *
     * @param type 协作类型
     * @return 任务状态编码
     */
    @Override
    public String cooperateStatus(CooperateType type) {
        return switch (type) {
            case TRANSFER -> TaskStatusEnum.TRANSFER.getStatus();
            case DEPUTE -> TaskStatusEnum.DEPUTE.getStatus();
            case ADD_SIGNATURE -> TaskStatusEnum.SIGN.getStatus();
            case REDUCTION_SIGNATURE -> TaskStatusEnum.SIGN_OFF.getStatus();
            default -> StatusMapper.super.cooperateStatus(type);
        };
    }

    /**
     * 判断业务编码是否为实例终态。
     *
     * @param code 业务状态编码
     * @return 终态返回 {@code true}
     */
    @Override
    public boolean isTerminal(String code) {
        return TERMINAL.contains(code);
    }

    /**
     * 状态编码转展示文案。
     *
     * @param code 状态编码
     * @return 展示文案
     */
    @Override
    public String label(String code) {
        BusinessStatusEnum business = BusinessStatusEnum.getByStatus(code);
        if (business != null) {
            return business.getDesc();
        }
        String task = TaskStatusEnum.findByStatus(code);
        return task.isEmpty() ? StatusMapper.super.label(code) : task;
    }
}
