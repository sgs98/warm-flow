package org.dromara.warm.flow.core.enums;

import lombok.Getter;
import org.dromara.warm.flow.core.utils.StringUtils;

/**
 * 流程事件类型。
 * <p>
 * 事件是独立于 {@link NodeType} 的维度：事件节点统一使用 {@link NodeType#BETWEEN} 承载，
 * 具体事件语义由本枚举表达。value 持久化到 {@code flow_node.event_type}，固定 {@code START} /
 * {@code END} 节点不承载事件（{@code eventType = null}，无 {@code NONE_START} / {@code NONE_END}）。
 *
 * @author may
 */
@Getter
public enum EventType {

    /**
     * 消息启动事件：位于固定 {@code START} 之后，按流程编码 + 消息名称启动实例。
     */
    MESSAGE_START("MESSAGE_START", true),

    /**
     * 定时启动事件：位于固定 {@code START} 之后，到期后创建流程实例。
     */
    TIMER_START("TIMER_START", true),

    /**
     * 中间定时捕获事件：等待到期后继续。
     */
    TIMER_CATCH("TIMER_CATCH", true),

    /**
     * 中间消息捕获事件：等待外部回调后继续。
     */
    MESSAGE_CATCH("MESSAGE_CATCH", true),

    /**
     * 中间消息抛出事件：到达即向外部系统发送消息。
     */
    MESSAGE_THROW("MESSAGE_THROW", true),

    /**
     * 定时边界事件：附着任务超时后中断当前任务。
     */
    TIMER_BOUNDARY("TIMER_BOUNDARY", true),

    /**
     * 消息边界事件：收到外部消息后中断当前任务。
     */
    MESSAGE_BOUNDARY("MESSAGE_BOUNDARY", true),

    /**
     * 终止结束事件：位于固定 {@code END} 之前，终止整个流程实例。
     */
    TERMINATE_END("TERMINATE_END", true),

    /**
     * 消息结束事件：位于固定 {@code END} 之前，发送消息后进入结束。
     */
    MESSAGE_END("MESSAGE_END", true),

    /**
     * 中间错误捕获事件：等待实例抛出的指定错误码后继续；错误经宿主 API 抛出。
     */
    ERROR_CATCH("ERROR_CATCH", true),

    /**
     * 错误边界事件：附着任务抛出匹配错误码后中断当前任务。
     */
    ERROR_BOUNDARY("ERROR_BOUNDARY", true);

    /**
     * -- GETTER --
     *  获取持久化值。
     *
     * @return 事件类型字符串
     */
    private final String value;

    /**
     * 当前版本是否已具备该事件的运行时能力。
     * <p>阶段 C 开放定时类与终止结束事件，阶段 D 开放消息类事件；发布校验据此做能力门禁。</p>
     * -- GETTER --
     *  当前版本是否已具备该事件的运行时能力。
     *
     * @return 已开放运行时返回 {@code true}

     */
    private final boolean runtimeEnabled;

    EventType(String value, boolean runtimeEnabled) {
        this.value = value;
        this.runtimeEnabled = runtimeEnabled;
    }

    /**
     * 根据持久化值获取事件类型。
     *
     * @param value 事件类型字符串
     * @return 事件类型；未匹配时返回 {@code null}
     */
    public static EventType getByValue(String value) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        for (EventType item : EventType.values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 判断是否为启动事件。
     *
     * @param value 事件类型字符串
     * @return 是启动事件时返回 {@code true}
     */
    public static boolean isStart(String value) {
        return MESSAGE_START.value.equals(value) || TIMER_START.value.equals(value);
    }

    /**
     * 判断是否为结束事件。
     *
     * @param value 事件类型字符串
     * @return 是结束事件时返回 {@code true}
     */
    public static boolean isEnd(String value) {
        return TERMINATE_END.value.equals(value) || MESSAGE_END.value.equals(value);
    }

    /**
     * 判断是否为边界事件。
     *
     * @param value 事件类型字符串
     * @return 是边界事件时返回 {@code true}
     */
    public static boolean isBoundary(String value) {
        return TIMER_BOUNDARY.value.equals(value) || MESSAGE_BOUNDARY.value.equals(value)
            || ERROR_BOUNDARY.value.equals(value);
    }

    /**
     * 判断是否为中间捕获事件。
     *
     * @param value 事件类型字符串
     * @return 是中间捕获事件时返回 {@code true}
     */
    public static boolean isCatch(String value) {
        return TIMER_CATCH.value.equals(value) || MESSAGE_CATCH.value.equals(value)
            || ERROR_CATCH.value.equals(value);
    }

    /**
     * 判断是否为中间抛出事件。
     *
     * @param value 事件类型字符串
     * @return 是中间抛出事件时返回 {@code true}
     */
    public static boolean isThrow(String value) {
        return MESSAGE_THROW.value.equals(value);
    }

    /**
     * 判断是否为定时类事件。
     *
     * @param value 事件类型字符串
     * @return 是定时类事件时返回 {@code true}
     */
    public static boolean isTimer(String value) {
        return TIMER_START.value.equals(value)
            || TIMER_CATCH.value.equals(value) || TIMER_BOUNDARY.value.equals(value);
    }

    /**
     * 判断是否为消息类事件。
     *
     * @param value 事件类型字符串
     * @return 是消息类事件时返回 {@code true}
     */
    public static boolean isMessage(String value) {
        return MESSAGE_START.value.equals(value) || MESSAGE_CATCH.value.equals(value)
            || MESSAGE_THROW.value.equals(value) || MESSAGE_BOUNDARY.value.equals(value)
            || MESSAGE_END.value.equals(value);
    }

    /**
     * 判断是否为错误类事件。
     *
     * @param value 事件类型字符串
     * @return 是错误类事件时返回 {@code true}
     */
    public static boolean isError(String value) {
        return ERROR_CATCH.value.equals(value) || ERROR_BOUNDARY.value.equals(value);
    }

}
