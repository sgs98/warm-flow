package org.dromara.warm.demo.enums;

import lombok.Getter;

/**
 * BusinessStatusEnum
 *
 * @author may
 * @since 2026/9/6
 */
@Getter
public enum BusinessStatusEnum {
    CANCEL("cancel", "已撤销"), DRAFT("draft", "草稿"), WAITING("waiting", "待审核"),
    FINISH("finish", "已完成"), INVALID("invalid", "已作废"), BACK("back", "已退回"),
    TERMINATION("termination", "已终止");

    /**
     * -- GETTER --
     *  获取业务状态编码。
     */
    private final String status;
    /**
     * -- GETTER --
     *  获取业务状态描述。
     */
    private final String desc;

    BusinessStatusEnum(String status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    /**
     * 按业务状态编码获取枚举。
     *
     * @param status 业务状态编码
     * @return 业务状态枚举
     */
    public static BusinessStatusEnum getByStatus(String status) {
        for (BusinessStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return item;
            }
        }
        return null;
    }
}
