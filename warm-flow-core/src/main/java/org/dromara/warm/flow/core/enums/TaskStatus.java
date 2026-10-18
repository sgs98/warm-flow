package org.dromara.warm.flow.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态：待办与历史任务的办理结果。
 * <p>
 * 与 {@link FlowStatus}（实例状态）分开：本枚举只落到 {@code flow_task.flow_status} 与
 * {@code flow_his_task.flow_status}，key 持久化、value 用于展示。
 * 编码沿用引擎既有取值，升级不需要迁移数据。
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

    WAITING("1", "待办理"),

    PASS("2", "通过"),

    FINISHED("8", "结束"),

    REJECT("9", "退回"),

    INVALID("10", "失效"),

    TASK_BACK("11", "拿回");

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
