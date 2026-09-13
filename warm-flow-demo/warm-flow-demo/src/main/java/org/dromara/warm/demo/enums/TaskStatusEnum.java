package org.dromara.warm.demo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态枚举。
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
     * 待审核。
     */
    WAITING("waiting", "待审核"),

    /**
     * 作废。
     */
    INVALID("invalid", "作废"),

    /**
     * 退回。
     */
    BACK("back", "退回"),

    /**
     * 终止。
     */
    TERMINATION("termination", "终止"),

    /**
     * 转办。
     */
    TRANSFER("transfer", "转办"),

    /**
     * 委托。
     */
    DEPUTE("depute", "委托"),

    /**
     * 抄送。
     */
    COPY("copy", "抄送"),

    /**
     * 加签。
     */
    SIGN("sign", "加签"),

    /**
     * 减签。
     */
    SIGN_OFF("sign_off", "减签"),

    /**
     * 超时。
     */
    TIMEOUT("timeout", "超时");

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
