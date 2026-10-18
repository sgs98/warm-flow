package org.dromara.warm.demo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态枚举：demo 业务侧的任务状态编码与展示文案。
 * <p>
 * 编码与引擎 {@link org.dromara.warm.flow.core.enums.TaskStatus} 保持一致，因此引擎默认的
 * {@code StatusMapper} 即可直接落库，demo 不再注册自定义映射器。本枚举只承担
 * <b>展示文案</b>（{@code findByStatus}）与少量业务判断（{@code isPassOrBack}）。
 *
 * @author may
 * @since 2026/9/13
 */
@Getter
@AllArgsConstructor
public enum TaskStatusEnum {

    /**
     * 撤销。
     */
    CANCEL("cancel", "撤销"),

    /**
     * 通过。
     */
    PASS("pass", "通过"),

    /**
     * 自动审批：定义级自动审批（def_config.autoPass）代办产生的历史状态。
     */
    AUTO_PASS("auto_pass", "自动审批"),

    /**
     * 待审批。
     */
    PENDING("pending", "待审批"),

    /**
     * 失效。
     */
    INVALID("invalid", "失效"),

    /**
     * 退回。
     */
    BACK("back", "退回"),

    /**
     * 终止。
     */
    TERMINATION("termination", "终止"),

    /**
     * 抄送：办理时抄送给相关人，不参与流程流转，由业务层手动写入。
     */
    COPY("copy", "抄送");

    /**
     * 状态编码。
     */
    private final String status;

    /**
     * 状态描述。
     */
    private final String desc;

    /**
     * 按状态编码获取任务状态描述。
     *
     * @param status 状态编码
     * @return 状态描述，未知状态返回空字符串
     */
    public static String findByStatus(String status) {
        for (TaskStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return item.desc;
            }
        }
        return "";
    }

    /**
     * 判断状态是否为通过或退回。
     *
     * @param status 状态编码
     * @return 是否为通过或退回
     */
    public static boolean isPassOrBack(String status) {
        return PASS.status.equals(status) || BACK.status.equals(status);
    }
}
