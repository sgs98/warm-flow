package org.dromara.warm.flow.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态：待办与历史任务的办理结果。
 * <p>
 * 与 {@link FlowStatus}（实例状态）是<b>不同的表、不同的维度</b>：本枚举只落到
 * {@code flow_task.flow_status} 与 {@code flow_his_task.flow_status}，实例状态只落
 * {@code flow_instance.flow_status}。两者各自拥有独立编码空间，编码互不干扰。
 * key 持久化、value 用于展示。
 * <p>
 * 持久化编码直接用<b>小写英文</b>（如 {@code "pass"}），落库即可自解释。
 * 任务维度只描述「这条待办/历史是怎么收口的」：待审批、通过、自动审批、退回、失效、拿回、抄送；
 * 流程是否结束由实例状态表达，任务维度不设「结束」。
 * <p>
 * 与 {@link CooperateType}（协作动作）正交：协作动作是<b>行为维度</b>
 * （转给谁、加谁、减谁，落 {@code cooperate_type}），本枚举是<b>结果维度</b>；
 * 一次协作动作完成后写什么任务状态，由 {@code StatusMapper#cooperateStatus(CooperateType)} 决定。
 *
 * @author may
 */
@Getter
@AllArgsConstructor
public enum TaskStatus {

    /**
     * 待审批：任务已生成、等待办理人处理。
     */
    PENDING("pending", "待审批"),

    /**
     * 通过：办理人同意，任务已完成（结束事件的留痕记录同样按「通过」归档）。
     */
    PASS("pass", "通过"),

    /**
     * 退回：办理人退回，任务以退回结果收口。
     */
    BACK("back", "退回"),

    /**
     * 失效：因流程终止 / 节点被跳过等原因归档，不再可办。
     */
    INVALID("invalid", "失效"),

    /**
     * 拿回：办理人取回已办任务。引擎当前无写入路径，保留编码供宿主直接置位。
     */
    TAKE_BACK("take_back", "拿回"),

    /**
     * 自动审批：由定义级自动审批（{@code def_config.autoPass}）代替办理人自动通过。
     * <p>
     * 与 {@link #PASS} 区分：两者都表示「通过」，但自动审批没有真实办理动作，
     * 历史时间线上需要能看出「这条是引擎代办的」。
     */
    AUTO_PASS("auto_pass", "自动审批"),

    /**
     * 抄送：办理时抄送给相关人，不参与流程流转。
     * <p>
     * 引擎不内置抄送能力，本编码供宿主在办理时手动指定（{@code WorkflowContext#setTaskStatus}）。
     */
    COPY("copy", "抄送");

    private final String key;
    private final String value;

    /**
     * 根据状态 key 获取展示文案。
     *
     * @param key 状态 key
     * @return 展示文案；未匹配时返回 {@code null}
     */
    public static String getValueByKey(String key) {
        for (TaskStatus item : TaskStatus.values()) {
            if (item.getKey().equals(key)) {
                return item.getValue();
            }
        }
        return null;
    }

}
